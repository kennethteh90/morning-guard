import { verifyAppAttestLicensedRequest } from "./app-attest";
import { consumeChallenge } from "./challenge";
import {
  decodeIntegrityTokenWithGoogle,
  playIntegrityPasses,
  type DecodeIntegrityToken,
  type PlayIntegrityVerdict,
} from "./play-integrity";
import type { Env } from "./upstream";
import { tokensMatch } from "./auth";

const TOKEN_HEADER = "X-Morning-Guard-Token";
const NONCE_HEADER = "X-Morning-Guard-Nonce";
const INTEGRITY_HEADER = "X-Morning-Guard-Play-Integrity";

export interface LicensedAuthTestHooks {
  decodeIntegrityToken?: DecodeIntegrityToken;
  verifyAppAttest?: (request: Request, nonce: string, env: Env) => Promise<boolean>;
}

let testHooks: LicensedAuthTestHooks = {};

export function setLicensedAuthTestHooks(hooks: LicensedAuthTestHooks): void {
  testHooks = hooks;
}

export function clearLicensedAuthTestHooks(): void {
  testHooks = {};
}

function json(data: unknown, status: number): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function legacyTokenCutover(env: Env): boolean {
  const raw = env.BIBLE_PROXY_LEGACY_TOKEN_DISABLED?.trim().toLowerCase();
  return raw === "1" || raw === "true" || raw === "yes";
}

function legacyTokenMatches(request: Request, env: Env): boolean {
  if (legacyTokenCutover(env)) return false;
  const expected = env.BIBLE_PROXY_TOKEN?.trim() ?? "";
  if (!expected) return false;
  const provided = request.headers.get(TOKEN_HEADER)?.trim() ?? "";
  return tokensMatch(expected, provided);
}

async function verifyPlayIntegrity(
  request: Request,
  nonce: string,
  env: Env,
): Promise<boolean> {
  const token = request.headers.get(INTEGRITY_HEADER)?.trim() ?? "";
  if (!token) return false;
  const decode = testHooks.decodeIntegrityToken ?? decodeIntegrityTokenWithGoogle;
  try {
    const verdict: PlayIntegrityVerdict = await decode(token, env);
    return playIntegrityPasses(verdict, nonce, env);
  } catch {
    return false;
  }
}

/**
 * Authorize a licensed `/v1/chapter` request. Returns an error Response or null when OK.
 * Consumes the challenge nonce on success.
 */
export async function authorizeLicensedChapterRequest(
  request: Request,
  env: Env,
): Promise<Response | null> {
  if (legacyTokenMatches(request, env)) {
    return null;
  }

  const nonce = request.headers.get(NONCE_HEADER)?.trim() ?? "";
  if (!nonce) {
    return json({ error: "Unauthorized" }, 401);
  }

  const playOk = await verifyPlayIntegrity(request, nonce, env);
  const appAttestFn = testHooks.verifyAppAttest ?? verifyAppAttestLicensedRequest;
  const iosOk = !playOk && (await appAttestFn(request, nonce, env));

  if (!playOk && !iosOk) {
    return json({ error: "Unauthorized" }, 401);
  }

  const consumed = await consumeChallenge(env.BIBLE_CACHE, nonce);
  if (!consumed) {
    return json({ error: "Unauthorized" }, 401);
  }
  return null;
}
