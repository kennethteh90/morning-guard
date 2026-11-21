import { tokensMatch } from "./auth";
import { kvPutBestEffort } from "./kv-safe";
import type { Env } from "./upstream";

const CHALLENGE_TOKEN_PREFIX = "mgc1";
const CHALLENGE_TTL_SEC = 120;
const LEGACY_CHALLENGE_PREFIX = "challenge:";
const USED_CHALLENGE_PREFIX = "challenge-used:";

function randomId(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function base64UrlEncode(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

async function hmacSha256(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return base64UrlEncode(new Uint8Array(sig));
}

function challengeSigningSecret(env: Env): string {
  const dedicated = env.CHALLENGE_HMAC_SECRET?.trim() ?? "";
  if (dedicated.length >= 16) return dedicated;
  return env.BIBLE_PROXY_SESSION_HMAC_SECRET?.trim() ?? "";
}

function statelessChallengesEnabled(env: Env): boolean {
  return challengeSigningSecret(env).length >= 16;
}

async function issueStatelessChallenge(env: Env): Promise<{ nonce: string }> {
  const secret = challengeSigningSecret(env);
  const id = randomId();
  const exp = Math.floor(Date.now() / 1000) + CHALLENGE_TTL_SEC;
  const signingInput = `${CHALLENGE_TOKEN_PREFIX}.${id}.${exp}`;
  const sig = await hmacSha256(secret, signingInput);
  return { nonce: `${signingInput}.${sig}` };
}

/** Legacy KV-backed nonce when no HMAC secret is configured (local dev only). */
async function issueLegacyChallenge(kv: KVNamespace): Promise<{ nonce: string }> {
  const nonce = randomId();
  await kvPutBestEffort(kv, `${LEGACY_CHALLENGE_PREFIX}${nonce}`, "1", {
    expirationTtl: CHALLENGE_TTL_SEC,
  });
  return { nonce };
}

export async function issueChallenge(env: Env): Promise<{ nonce: string }> {
  if (statelessChallengesEnabled(env)) {
    return issueStatelessChallenge(env);
  }
  return issueLegacyChallenge(env.BIBLE_CACHE);
}

async function verifyStatelessChallengeParts(
  env: Env,
  nonce: string,
): Promise<{ id: string; exp: number } | null> {
  const secret = challengeSigningSecret(env);
  if (secret.length < 16) return null;
  const parts = nonce.trim().split(".");
  if (parts.length !== 4) return null;
  const [prefix, id, expRaw, sig] = parts;
  if (prefix !== CHALLENGE_TOKEN_PREFIX || !id || !expRaw || !sig) return null;
  const exp = parseInt(expRaw, 10);
  if (!Number.isFinite(exp)) return null;
  const signingInput = `${prefix}.${id}.${expRaw}`;
  const expected = await hmacSha256(secret, signingInput);
  if (!tokensMatch(expected, sig)) return null;
  if (exp <= Math.floor(Date.now() / 1000)) return null;
  return { id, exp };
}

async function consumeStatelessChallenge(env: Env, nonce: string): Promise<boolean> {
  const parsed = await verifyStatelessChallengeParts(env, nonce);
  if (!parsed) return false;
  const usedKey = `${USED_CHALLENGE_PREFIX}${parsed.id}`;
  const existing = await env.BIBLE_CACHE.get(usedKey);
  if (existing) return false;
  const ttl = Math.max(60, parsed.exp - Math.floor(Date.now() / 1000) + 30);
  const stored = await kvPutBestEffort(env.BIBLE_CACHE, usedKey, "1", { expirationTtl: ttl });
  return stored;
}

/** Returns true when the nonce was present and is now consumed. */
async function consumeLegacyChallenge(kv: KVNamespace, nonce: string): Promise<boolean> {
  const key = `${LEGACY_CHALLENGE_PREFIX}${nonce.trim()}`;
  if (!nonce.trim()) return false;
  const existing = await kv.get(key);
  if (!existing) return false;
  try {
    await kv.delete(key);
  } catch (e) {
    console.warn("challenge_consume_delete_failed", {
      message: e instanceof Error ? e.message : String(e).slice(0, 200),
    });
    return false;
  }
  return true;
}

export async function consumeChallenge(env: Env, nonce: string): Promise<boolean> {
  if (nonce.trim().startsWith(`${CHALLENGE_TOKEN_PREFIX}.`)) {
    return consumeStatelessChallenge(env, nonce);
  }
  return consumeLegacyChallenge(env.BIBLE_CACHE, nonce);
}