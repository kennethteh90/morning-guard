import { verifyAppAttestLicensedRequest } from "./app-attest";
import { consumeChallenge } from "./challenge";
import {
  decodeIntegrityTokenWithGoogle,
  playIntegrityPasses,
  type DecodeIntegrityToken,
  type PlayIntegrityVerdict,
} from "./play-integrity";
import {
  issueSessionToken,
  SESSION_HEADER,
  sessionTokensConfigured,
  verifySessionToken,
} from "./session-token";
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
): Promise<PlayIntegrityVerdict | null> {
  const token = request.headers.get(INTEGRITY_HEADER)?.trim() ?? "";
  if (!token) return null;
  const decode = testHooks.decodeIntegrityToken ?? decodeIntegrityTokenWithGoogle;
  try {
    const verdict = await decode(token, env);
    if (!playIntegrityPasses(verdict, nonce, env)) return null;
    return verdict;
  } catch (e) {
    console.warn("play_integrity_decode_failed", { message: String(e).slice(0, 300) });
    return null;
  }
}

type AttestationResult =
  | { ok: true; pkg: string; plat: "android" | "ios" }
  | { ok: false };

/**
 * Verify one-time challenge + Play Integrity or App Attest, and consume the nonce.
 */
async function verifyFreshAttestation(
  request: Request,
  env: Env,
): Promise<AttestationResult> {
  const nonce = request.headers.get(NONCE_HEADER)?.trim() ?? "";
  if (!nonce) return { ok: false };

  const playVerdict = await verifyPlayIntegrity(request, nonce, env);
  if (playVerdict) {
    const consumed = await consumeChallenge(env.BIBLE_CACHE, nonce);
    if (!consumed) return { ok: false };
    const pkg =
      playVerdict.requestPackageName ||
      env.PLAY_INTEGRITY_PACKAGE_NAME?.trim() ||
      "com.morningguard.app";
    return { ok: true, pkg, plat: "android" };
  }

  const appAttestFn = testHooks.verifyAppAttest ?? verifyAppAttestLicensedRequest;
  const iosOk = await appAttestFn(request, nonce, env);
  if (!iosOk) return { ok: false };

  const consumed = await consumeChallenge(env.BIBLE_CACHE, nonce);
  if (!consumed) return { ok: false };
  const pkg = env.APPLE_APP_ATTEST_BUNDLE_ID?.trim() || "com.morningguard.app";
  return { ok: true, pkg, plat: "ios" };
}

async function sessionHeaderMatches(request: Request, env: Env): Promise<boolean> {
  const token = request.headers.get(SESSION_HEADER)?.trim() ?? "";
  if (!token) return false;
  const claims = await verifySessionToken(env, token);
  return claims !== null;
}

/**
 * Authorize a licensed `/v1/chapter` request. Returns an error Response or null when OK.
 * Accepts (in order): legacy token, short-lived session token, or fresh attestation.
 */
export async function authorizeLicensedChapterRequest(
  request: Request,
  env: Env,
): Promise<Response | null> {
  if (legacyTokenMatches(request, env)) {
    return null;
  }

  if (await sessionHeaderMatches(request, env)) {
    return null;
  }

  const attested = await verifyFreshAttestation(request, env);
  if (!attested.ok) {
    return json({ error: "Unauthorized" }, 401);
  }
  return null;
}

/**
 * Exchange a one-time challenge + attestation for a short-lived session token.
 */
export async function issueLicensedSessionResponse(
  request: Request,
  env: Env,
): Promise<Response> {
  if (!sessionTokensConfigured(env)) {
    return json({ error: "Session tokens are not configured on the worker" }, 503);
  }

  const attested = await verifyFreshAttestation(request, env);
  if (!attested.ok) {
    return json({ error: "Unauthorized" }, 401);
  }

  const issued = await issueSessionToken(env, { pkg: attested.pkg, plat: attested.plat });
  if (!issued) {
    return json({ error: "Session tokens are not configured on the worker" }, 503);
  }

  return json(
    {
      sessionToken: issued.token,
      expiresAt: issued.expiresAt,
      expiresIn: Math.max(0, issued.expiresAt - Math.floor(Date.now() / 1000)),
    },
    200,
  );
}
