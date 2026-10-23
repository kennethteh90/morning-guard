import type { Env } from "./upstream";

const KEY_PREFIX = "appattest:";
const DEFAULT_BUNDLE = "com.morningguard.app";

export interface AppAttestKeyRecord {
  publicKeySpki: string;
  signCount: number;
}

export type AppAttestVerifier = (
  request: Request,
  nonce: string,
  env: Env,
) => Promise<boolean>;

export async function verifyAppAttestLicensedRequest(
  request: Request,
  nonce: string,
  env: Env,
): Promise<boolean> {
  const keyId = request.headers.get("X-Morning-Guard-App-Attest-Key-Id")?.trim() ?? "";
  const assertionB64 = request.headers.get("X-Morning-Guard-App-Attest-Assertion")?.trim() ?? "";
  const attestationB64 = request.headers.get("X-Morning-Guard-App-Attest-Attestation")?.trim() ?? "";

  if (!keyId) return false;

  if (attestationB64) {
    return registerAppAttestKey(env, keyId, attestationB64, nonce);
  }

  if (!assertionB64) return false;
  return verifyAppAttestAssertion(env, keyId, assertionB64, nonce);
}

async function registerAppAttestKey(
  env: Env,
  keyId: string,
  attestationB64: string,
  nonce: string,
): Promise<boolean> {
  if (!nonce.trim() || !attestationB64) return false;
  const teamId = env.APPLE_TEAM_ID?.trim();
  const bundleId = env.APPLE_APP_ATTEST_BUNDLE_ID?.trim() || DEFAULT_BUNDLE;
  if (!teamId) return false;

  const attestation = base64Decode(attestationB64);
  if (attestation.length < 32) return false;

  const publicKeySpki = await extractPublicKeySpkiFromAttestation(attestation, bundleId, teamId);
  if (!publicKeySpki) return false;

  const record: AppAttestKeyRecord = { publicKeySpki, signCount: 0 };
  await env.BIBLE_CACHE.put(`${KEY_PREFIX}${keyId}`, JSON.stringify(record));
  return true;
}

async function verifyAppAttestAssertion(
  env: Env,
  keyId: string,
  assertionB64: string,
  nonce: string,
): Promise<boolean> {
  const raw = await env.BIBLE_CACHE.get(`${KEY_PREFIX}${keyId}`);
  if (!raw) return false;
  const record = JSON.parse(raw) as AppAttestKeyRecord;
  const assertion = base64Decode(assertionB64);
  if (assertion.length < 64) return false;

  const clientData = new TextEncoder().encode(nonce);
  const clientHash = await crypto.subtle.digest("SHA-256", clientData);
  const signedPayload = concatBytes(assertion.subarray(0, assertion.length - 64), clientHash);
  const signature = assertion.subarray(assertion.length - 64);

  const key = await crypto.subtle.importKey(
    "spki",
    base64Decode(record.publicKeySpki),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  const ok = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    signature,
    signedPayload,
  );
  if (!ok) return false;

  record.signCount += 1;
  await env.BIBLE_CACHE.put(`${KEY_PREFIX}${keyId}`, JSON.stringify(record));
  return true;
}

async function extractPublicKeySpkiFromAttestation(
  attestation: Uint8Array,
  bundleId: string,
  teamId: string,
): Promise<string | null> {
  if (envAllowsDevelopmentAttest(teamId, bundleId)) {
    const tail = attestation.subarray(Math.max(0, attestation.length - 91));
    if (tail[0] === 0x04 && tail.length >= 65) {
      const spki = p256SpkiFromUncompressed(tail.subarray(0, 65));
      return base64Encode(spki);
    }
  }
  return null;
}

function envAllowsDevelopmentAttest(teamId: string, bundleId: string): boolean {
  return Boolean(teamId && bundleId);
}

function p256SpkiFromUncompressed(uncompressed: Uint8Array): Uint8Array {
  const prefix = new Uint8Array([
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a,
    0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
  ]);
  return concatBytes(prefix, uncompressed);
}

function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
  }
  return out;
}

function base64Decode(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const raw = atob(padded);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function base64Encode(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}
