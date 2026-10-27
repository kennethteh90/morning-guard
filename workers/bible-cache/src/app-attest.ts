import type { AssertionResult, AttestationResult } from "@bradford-tech/supabase-integrity-attest";
import type { Env } from "./upstream";

const KEY_PREFIX = "appattest:";
const DEFAULT_BUNDLE = "com.morningguard.app";

type AttestLib = {
  verifyAttestation: (
    appInfo: { appId: string; developmentEnv?: boolean },
    keyId: string,
    clientDataHash: Uint8Array,
    attestation: string,
  ) => Promise<AttestationResult>;
  verifyAssertion: (
    appInfo: { appId: string },
    assertion: string,
    clientData: string,
    publicKeyPem: string,
    previousSignCount: number,
  ) => Promise<AssertionResult>;
};

let attestLibPromise: Promise<AttestLib> | null = null;

async function loadAttestLib(): Promise<AttestLib> {
  if (!attestLibPromise) {
    attestLibPromise = import("@bradford-tech/supabase-integrity-attest") as Promise<AttestLib>;
  }
  return attestLibPromise;
}

export interface AppAttestKeyRecord {
  publicKeyPem: string;
  signCount: number;
}

export type AppAttestVerifier = (
  request: Request,
  nonce: string,
  env: Env,
) => Promise<boolean>;

function resolveAppId(env: Env): string | null {
  const teamId = env.APPLE_TEAM_ID?.trim();
  const bundleId = env.APPLE_APP_ATTEST_BUNDLE_ID?.trim() || DEFAULT_BUNDLE;
  if (!teamId) return null;
  return `${teamId}.${bundleId}`;
}

function developmentAttest(env: Env): boolean {
  const raw = env.APPLE_APP_ATTEST_DEVELOPMENT?.trim().toLowerCase();
  return raw === "1" || raw === "true" || raw === "yes";
}

async function clientDataHashForNonce(nonce: string): Promise<Uint8Array> {
  return new Uint8Array(
    await crypto.subtle.digest("SHA-256", new TextEncoder().encode(nonce)),
  );
}

export async function verifyAppAttestLicensedRequest(
  request: Request,
  nonce: string,
  env: Env,
): Promise<boolean> {
  const appId = resolveAppId(env);
  if (!appId || !nonce.trim()) return false;

  const keyId = request.headers.get("X-Morning-Guard-App-Attest-Key-Id")?.trim() ?? "";
  const assertionB64 = request.headers.get("X-Morning-Guard-App-Attest-Assertion")?.trim() ?? "";
  const attestationB64 = request.headers.get("X-Morning-Guard-App-Attest-Attestation")?.trim() ?? "";

  if (!keyId) return false;

  if (attestationB64) {
    return registerAppAttestKey(env, appId, keyId, attestationB64, nonce);
  }

  if (!assertionB64) return false;
  return verifyAppAttestAssertion(env, appId, keyId, assertionB64, nonce);
}

async function registerAppAttestKey(
  env: Env,
  appId: string,
  keyId: string,
  attestationB64: string,
  nonce: string,
): Promise<boolean> {
  try {
    const { verifyAttestation } = await loadAttestLib();
    const clientDataHash = await clientDataHashForNonce(nonce);
    const { publicKeyPem, signCount } = await verifyAttestation(
      { appId, developmentEnv: developmentAttest(env) },
      keyId,
      clientDataHash,
      attestationB64,
    );
    const record: AppAttestKeyRecord = { publicKeyPem, signCount };
    await env.BIBLE_CACHE.put(`${KEY_PREFIX}${keyId}`, JSON.stringify(record));
    return true;
  } catch (error) {
    console.warn("app_attest_register_failed", {
      message: String(error).slice(0, 300),
    });
    return false;
  }
}

async function verifyAppAttestAssertion(
  env: Env,
  appId: string,
  keyId: string,
  assertionB64: string,
  nonce: string,
): Promise<boolean> {
  const raw = await env.BIBLE_CACHE.get(`${KEY_PREFIX}${keyId}`);
  if (!raw) return false;

  let record: AppAttestKeyRecord;
  try {
    record = JSON.parse(raw) as AppAttestKeyRecord;
  } catch {
    return false;
  }
  if (!record.publicKeyPem) return false;

  try {
    const { verifyAssertion } = await loadAttestLib();
    const { signCount } = await verifyAssertion(
      { appId },
      assertionB64,
      nonce,
      record.publicKeyPem,
      record.signCount ?? 0,
    );
    record.signCount = signCount;
    await env.BIBLE_CACHE.put(`${KEY_PREFIX}${keyId}`, JSON.stringify(record));
    return true;
  } catch (error) {
    console.warn("app_attest_assertion_failed", {
      message: String(error).slice(0, 300),
    });
    return false;
  }
}
