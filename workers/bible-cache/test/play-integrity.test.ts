import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  normalizeSha256Digest,
  playIntegrityPasses,
  signingCertMatches,
  type PlayIntegrityVerdict,
} from "../src/play-integrity";
import type { Env } from "../src/upstream";

/** Production Play app signing SHA-256 (public fingerprint). */
const COLON_HEX =
  "36:8F:58:B5:D7:B9:53:85:A5:20:5F:52:0C:48:2D:E2:2D:BF:AD:F8:71:7E:09:EF:B4:24:3C:B3:27:F3:93:40";
const PLAIN_HEX = "368F58B5D7B95385A5205F520C482DE22DBFADF8717E09EFB4243CB327F39340";
const BASE64URL = "No9Ytde5U4WlIF9SDEgt4i2_rfhxfgnvtCQ8syfzk0A";
const CANONICAL = PLAIN_HEX.toLowerCase();

function mockKv(): KVNamespace {
  return {
    get: async () => null,
    put: async () => {},
    delete: async () => {},
    list: async () => ({ keys: [], list_complete: true, cacheStatus: null }),
    getWithMetadata: async () => ({ value: null, metadata: null, cacheStatus: null }),
  } as unknown as KVNamespace;
}

function envWithCert(cert: string | undefined): Env {
  return {
    BIBLE_CACHE: mockKv(),
    API_BIBLE_KEY: "k",
    PLAY_APP_SIGNING_CERT_SHA256: cert,
  };
}

function passingVerdict(digests: string[]): PlayIntegrityVerdict {
  return {
    requestPackageName: "com.morningguard.app",
    appRecognitionVerdict: "PLAY_RECOGNIZED",
    certificateSha256Digests: digests,
    deviceRecognitionVerdicts: ["MEETS_DEVICE_INTEGRITY"],
    requestDetailsNonce: "nonce",
  };
}

describe("normalizeSha256Digest", () => {
  it("accepts colon-separated hex (any case)", () => {
    assert.equal(normalizeSha256Digest(COLON_HEX), CANONICAL);
    assert.equal(normalizeSha256Digest(COLON_HEX.toLowerCase()), CANONICAL);
  });

  it("accepts plain hex", () => {
    assert.equal(normalizeSha256Digest(PLAIN_HEX), CANONICAL);
    assert.equal(normalizeSha256Digest(PLAIN_HEX.toLowerCase()), CANONICAL);
  });

  it("accepts base64url (unpadded) and standard base64", () => {
    assert.equal(normalizeSha256Digest(BASE64URL), CANONICAL);
    let std = BASE64URL.replace(/-/g, "+").replace(/_/g, "/");
    const pad = std.length % 4;
    if (pad) std += "=".repeat(4 - pad);
    assert.equal(normalizeSha256Digest(std), CANONICAL);
  });
});

describe("signingCertMatches", () => {
  it("matches Play base64url digest against colon-hex secret", () => {
    assert.equal(signingCertMatches([BASE64URL], COLON_HEX), true);
  });

  it("matches Play base64url digest against plain hex secret", () => {
    assert.equal(signingCertMatches([BASE64URL], PLAIN_HEX), true);
  });

  it("matches Play base64url digest against base64url secret", () => {
    assert.equal(signingCertMatches([BASE64URL], BASE64URL), true);
  });

  it("rejects a mismatch", () => {
    const other =
      "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF";
    assert.equal(signingCertMatches([BASE64URL], other), false);
  });

  it("fails closed when secret is empty or missing", () => {
    assert.equal(signingCertMatches([BASE64URL], ""), false);
    assert.equal(signingCertMatches([BASE64URL], undefined), false);
    assert.equal(signingCertMatches([BASE64URL], "   "), false);
  });
});

describe("playIntegrityPasses cert allowlist", () => {
  it("accepts configured colon-hex when Play reports base64url", () => {
    assert.equal(
      playIntegrityPasses(passingVerdict([BASE64URL]), "nonce", envWithCert(COLON_HEX)),
      true,
    );
  });

  it("rejects when secret is empty even if Play returns a digest", () => {
    assert.equal(
      playIntegrityPasses(passingVerdict([BASE64URL]), "nonce", envWithCert("")),
      false,
    );
  });

  it("PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT still requires a matching cert", () => {
    const verdict: PlayIntegrityVerdict = {
      ...passingVerdict([BASE64URL]),
      appRecognitionVerdict: "UNRECOGNIZED",
    };
    const envEmpty: Env = {
      ...envWithCert(""),
      PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT: "true",
    };
    assert.equal(playIntegrityPasses(verdict, "nonce", envEmpty), false);

    const envOk: Env = {
      ...envWithCert(COLON_HEX),
      PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT: "true",
    };
    assert.equal(playIntegrityPasses(verdict, "nonce", envOk), true);
  });
});
