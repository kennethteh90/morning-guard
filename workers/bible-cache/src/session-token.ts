import { tokensMatch } from "./auth";
import type { Env } from "./upstream";

export const SESSION_HEADER = "X-Morning-Guard-Session";
export const SESSION_TTL_SEC = 3600;
const TOKEN_PREFIX = "mg1";

export interface LicensedSessionClaims {
  exp: number;
  pkg: string;
  plat: "android" | "ios";
}

function sessionSecret(env: Env): string {
  return env.BIBLE_PROXY_SESSION_HMAC_SECRET?.trim() ?? "";
}

function base64UrlEncode(bytes: Uint8Array | string): string {
  const binary =
    typeof bytes === "string"
      ? btoa(bytes)
      : btoa(String.fromCharCode(...bytes));
  return binary.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecodeToString(input: string): string | null {
  let b64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const pad = b64.length % 4;
  if (pad === 1) return null;
  if (pad) b64 += "=".repeat(4 - pad);
  try {
    return atob(b64);
  } catch {
    return null;
  }
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

/** True when the worker can mint/verify licensed session tokens. */
export function sessionTokensConfigured(env: Env): boolean {
  return sessionSecret(env).length >= 16;
}

export async function issueSessionToken(
  env: Env,
  claims: Omit<LicensedSessionClaims, "exp"> & { exp?: number },
  nowSec: number = Math.floor(Date.now() / 1000),
): Promise<{ token: string; expiresAt: number } | null> {
  const secret = sessionSecret(env);
  if (secret.length < 16) return null;
  const exp = claims.exp ?? nowSec + SESSION_TTL_SEC;
  const payload: LicensedSessionClaims = {
    exp,
    pkg: claims.pkg,
    plat: claims.plat,
  };
  const body = base64UrlEncode(JSON.stringify(payload));
  const signingInput = `${TOKEN_PREFIX}.${body}`;
  const sig = await hmacSha256(secret, signingInput);
  return { token: `${signingInput}.${sig}`, expiresAt: exp };
}

export async function verifySessionToken(
  env: Env,
  token: string,
  nowSec: number = Math.floor(Date.now() / 1000),
): Promise<LicensedSessionClaims | null> {
  const secret = sessionSecret(env);
  if (secret.length < 16) return null;
  const parts = token.trim().split(".");
  if (parts.length !== 3) return null;
  const [prefix, body, sig] = parts;
  if (prefix !== TOKEN_PREFIX || !body || !sig) return null;

  const signingInput = `${prefix}.${body}`;
  const expected = await hmacSha256(secret, signingInput);
  if (!tokensMatch(expected, sig)) return null;

  const json = base64UrlDecodeToString(body);
  if (!json) return null;
  let parsed: Partial<LicensedSessionClaims>;
  try {
    parsed = JSON.parse(json) as Partial<LicensedSessionClaims>;
  } catch {
    return null;
  }
  if (typeof parsed.exp !== "number" || !Number.isFinite(parsed.exp)) return null;
  if (parsed.exp <= nowSec) return null;
  if (typeof parsed.pkg !== "string" || !parsed.pkg.trim()) return null;
  if (parsed.plat !== "android" && parsed.plat !== "ios") return null;

  const packageName = env.PLAY_INTEGRITY_PACKAGE_NAME?.trim() || "com.morningguard.app";
  const bundleId = env.APPLE_APP_ATTEST_BUNDLE_ID?.trim() || "com.morningguard.app";
  if (parsed.plat === "android" && parsed.pkg !== packageName) return null;
  if (parsed.plat === "ios" && parsed.pkg !== bundleId) return null;

  return { exp: parsed.exp, pkg: parsed.pkg, plat: parsed.plat };
}
