import type { Env } from "./upstream";

export interface PlayIntegrityVerdict {
  requestPackageName: string;
  appRecognitionVerdict: string;
  certificateSha256Digests: string[];
  deviceRecognitionVerdicts: string[];
  requestDetailsNonce?: string;
}

export type DecodeIntegrityToken = (token: string, env: Env) => Promise<PlayIntegrityVerdict>;

const DEFAULT_PACKAGE = "com.morningguard.app";

export async function decodeIntegrityTokenWithGoogle(
  token: string,
  env: Env,
): Promise<PlayIntegrityVerdict> {
  const saJson = env.PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON?.trim();
  const packageName = env.PLAY_INTEGRITY_PACKAGE_NAME?.trim() || DEFAULT_PACKAGE;
  if (!saJson) {
    throw new Error("Play Integrity is not configured on the worker");
  }
  const accessToken = await googleAccessToken(saJson);
  const url = `https://playintegrity.googleapis.com/v1/${packageName}:decodeIntegrityToken`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ integrityToken: token }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`decodeIntegrityToken failed (${response.status}): ${text.slice(0, 200)}`);
  }
  const body = (await response.json()) as {
    tokenPayloadExternal?: {
      requestDetails?: { requestPackageName?: string; nonce?: string };
      appIntegrity?: {
        appRecognitionVerdict?: string;
        certificateSha256Digest?: string[];
      };
      deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
    };
  };
  const payload = body.tokenPayloadExternal;
  return {
    requestPackageName: payload?.requestDetails?.requestPackageName ?? "",
    requestDetailsNonce: payload?.requestDetails?.nonce,
    appRecognitionVerdict: payload?.appIntegrity?.appRecognitionVerdict ?? "",
    certificateSha256Digests: payload?.appIntegrity?.certificateSha256Digest ?? [],
    deviceRecognitionVerdicts: payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [],
  };
}

/**
 * Normalize a SHA-256 digest to lowercase hex (64 chars).
 * Accepts colon-separated hex, plain hex, standard base64, or base64url
 * (padded or unpadded). Returns null if the value is not a 32-byte digest.
 * Does not lowercase base64 before decoding (base64 is case-sensitive).
 */
export function normalizeSha256Digest(input: string): string | null {
  const trimmed = input.trim();
  if (!trimmed) return null;

  const hexCandidate = trimmed.replace(/[:\s]/g, "");
  if (/^[0-9a-fA-F]{64}$/.test(hexCandidate)) {
    return hexCandidate.toLowerCase();
  }

  const bytes = tryDecodeBase64Digest(trimmed);
  if (!bytes || bytes.length !== 32) return null;
  return bytesToHex(bytes);
}

/** True when any Play digest matches any configured allowlist entry (after normalize). */
export function signingCertMatches(
  playDigests: readonly string[],
  configuredSecret: string | undefined,
): boolean {
  const allowed = (configuredSecret ?? "")
    .split(",")
    .map((s) => normalizeSha256Digest(s))
    .filter((h): h is string => h !== null);
  // Fail closed: empty/missing/unparseable secret never accepts a cert.
  if (allowed.length === 0) return false;

  return playDigests.some((d) => {
    const canonical = normalizeSha256Digest(d);
    return canonical !== null && allowed.includes(canonical);
  });
}

export function playIntegrityPasses(
  verdict: PlayIntegrityVerdict,
  expectedNonce: string,
  env: Env,
): boolean {
  const packageName = env.PLAY_INTEGRITY_PACKAGE_NAME?.trim() || DEFAULT_PACKAGE;
  if (verdict.requestPackageName !== packageName) return false;
  if (verdict.requestDetailsNonce && verdict.requestDetailsNonce !== expectedNonce) return false;

  const certOk = signingCertMatches(
    verdict.certificateSha256Digests,
    env.PLAY_APP_SIGNING_CERT_SHA256,
  );

  const recognition = verdict.appRecognitionVerdict;
  // Debug sideload: still requires a configured matching cert (fail closed).
  const unrecognizedDebug =
    recognition === "UNRECOGNIZED" &&
    env.PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT === "true" &&
    certOk;

  const appOk =
    recognition === "PLAY_RECOGNIZED" || unrecognizedDebug;
  const deviceOk = verdict.deviceRecognitionVerdicts.includes("MEETS_DEVICE_INTEGRITY");
  return appOk && certOk && deviceOk;
}

function tryDecodeBase64Digest(input: string): Uint8Array | null {
  let b64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const pad = b64.length % 4;
  if (pad === 1) return null;
  if (pad) b64 += "=".repeat(4 - pad);
  try {
    const raw = atob(b64);
    const bytes = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
    return bytes;
  } catch {
    return null;
  }
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function googleAccessToken(serviceAccountJson: string): Promise<string> {
  const sa = JSON.parse(serviceAccountJson) as {
    client_email: string;
    private_key: string;
    token_uri?: string;
  };
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/playintegrity",
      aud: sa.token_uri ?? "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const unsigned = `${header}.${claim}`;
  const key = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const jwt = `${unsigned}.${base64Url(signature)}`;
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const tokenRes = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  if (!tokenRes.ok) {
    throw new Error(`Google OAuth failed (${tokenRes.status})`);
  }
  const tokenBody = (await tokenRes.json()) as { access_token?: string };
  if (!tokenBody.access_token) throw new Error("Google OAuth missing access_token");
  return tokenBody.access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const der = pemToDer(pem);
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(b64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes.buffer;
}

function base64Url(input: string | ArrayBuffer): string {
  let binary: string;
  if (typeof input === "string") {
    binary = btoa(input);
  } else {
    const bytes = new Uint8Array(input);
    binary = btoa(String.fromCharCode(...bytes));
  }
  return binary.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
