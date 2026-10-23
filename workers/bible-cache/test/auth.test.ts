import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { tokensMatch } from "../src/auth";
import {
  authorizeLicensedChapterRequest,
  clearLicensedAuthTestHooks,
  issueLicensedSessionResponse,
  setLicensedAuthTestHooks,
} from "../src/licensed-auth";
import { issueChallenge } from "../src/challenge";
import { SESSION_HEADER } from "../src/session-token";
import type { Env } from "../src/upstream";

function mockKv(): KVNamespace {
  const store = new Map<string, string>();
  return {
    get: async (key: string) => store.get(key) ?? null,
    put: async (key: string, value: string) => {
      store.set(key, value);
    },
    delete: async (key: string) => {
      store.delete(key);
    },
    list: async () => ({ keys: [], list_complete: true, cacheStatus: null }),
    getWithMetadata: async (key: string) => ({ value: store.get(key) ?? null, metadata: null, cacheStatus: null }),
  } as unknown as KVNamespace;
}

describe("proxy auth helpers", () => {
  it("tokensMatch is constant-time on equal length", () => {
    assert.equal(tokensMatch("abc", "abc"), true);
    assert.equal(tokensMatch("abc", "abd"), false);
    assert.equal(tokensMatch("abc", "ab"), false);
  });
});

describe("licensed chapter auth", () => {
  it("accepts legacy token when configured", async () => {
    const env: Env = { BIBLE_CACHE: mockKv(), API_BIBLE_KEY: "k", BIBLE_PROXY_TOKEN: "secret" };
    const req = new Request("https://example.com/v1/chapter?translation=niv", {
      headers: { "X-Morning-Guard-Token": "secret" },
    });
    assert.equal(await authorizeLicensedChapterRequest(req, env), null);
  });

  it("rejects wrong legacy token", async () => {
    const env: Env = { BIBLE_CACHE: mockKv(), API_BIBLE_KEY: "k", BIBLE_PROXY_TOKEN: "secret" };
    const req = new Request("https://example.com/v1/chapter?translation=niv", {
      headers: { "X-Morning-Guard-Token": "wrong" },
    });
    const res = await authorizeLicensedChapterRequest(req, env);
    assert.ok(res);
    assert.equal(res!.status, 401);
  });

  it("rejects reused nonce after attestation", async () => {
    const kv = mockKv();
    const playCertB64url = "No9Ytde5U4WlIF9SDEgt4i2_rfhxfgnvtCQ8syfzk0A";
    const env: Env = {
      BIBLE_CACHE: kv,
      API_BIBLE_KEY: "k",
      BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true",
      PLAY_APP_SIGNING_CERT_SHA256:
        "36:8F:58:B5:D7:B9:53:85:A5:20:5F:52:0C:48:2D:E2:2D:BF:AD:F8:71:7E:09:EF:B4:24:3C:B3:27:F3:93:40",
    };
    const { nonce } = await issueChallenge(kv);
    setLicensedAuthTestHooks({
      decodeIntegrityToken: async () => ({
        requestPackageName: "com.morningguard.app",
        appRecognitionVerdict: "PLAY_RECOGNIZED",
        certificateSha256Digests: [playCertB64url],
        deviceRecognitionVerdicts: ["MEETS_DEVICE_INTEGRITY"],
        requestDetailsNonce: nonce,
      }),
    });
    const headers = {
      "X-Morning-Guard-Nonce": nonce,
      "X-Morning-Guard-Play-Integrity": "token",
    };
    const first = new Request("https://example.com/v1/chapter?translation=niv", { headers });
    assert.equal(await authorizeLicensedChapterRequest(first, env), null);
    const second = new Request("https://example.com/v1/chapter?translation=niv", { headers });
    const res = await authorizeLicensedChapterRequest(second, env);
    assert.ok(res);
    assert.equal(res!.status, 401);
    clearLicensedAuthTestHooks();
  });

  it("accepts a short-lived session token without re-attestation", async () => {
    const kv = mockKv();
    const playCertB64url = "No9Ytde5U4WlIF9SDEgt4i2_rfhxfgnvtCQ8syfzk0A";
    const env: Env = {
      BIBLE_CACHE: kv,
      API_BIBLE_KEY: "k",
      BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true",
      BIBLE_PROXY_SESSION_HMAC_SECRET: "test-session-hmac-secret-value",
      PLAY_APP_SIGNING_CERT_SHA256:
        "36:8F:58:B5:D7:B9:53:85:A5:20:5F:52:0C:48:2D:E2:2D:BF:AD:F8:71:7E:09:EF:B4:24:3C:B3:27:F3:93:40",
    };
    const { nonce } = await issueChallenge(kv);
    setLicensedAuthTestHooks({
      decodeIntegrityToken: async () => ({
        requestPackageName: "com.morningguard.app",
        appRecognitionVerdict: "PLAY_RECOGNIZED",
        certificateSha256Digests: [playCertB64url],
        deviceRecognitionVerdicts: ["MEETS_DEVICE_INTEGRITY"],
        requestDetailsNonce: nonce,
      }),
    });
    const sessionRes = await issueLicensedSessionResponse(
      new Request("https://example.com/v1/session", {
        method: "POST",
        headers: {
          "X-Morning-Guard-Nonce": nonce,
          "X-Morning-Guard-Play-Integrity": "token",
        },
      }),
      env,
    );
    assert.equal(sessionRes.status, 200);
    const body = (await sessionRes.json()) as { sessionToken?: string };
    assert.ok(body.sessionToken);

    const chapter = new Request("https://example.com/v1/chapter?translation=niv", {
      headers: { [SESSION_HEADER]: body.sessionToken! },
    });
    assert.equal(await authorizeLicensedChapterRequest(chapter, env), null);
    clearLicensedAuthTestHooks();
  });

  it("keeps legacy token behaviour unchanged when session secret is unset", async () => {
    const env: Env = { BIBLE_CACHE: mockKv(), API_BIBLE_KEY: "k", BIBLE_PROXY_TOKEN: "secret" };
    const req = new Request("https://example.com/v1/chapter?translation=niv", {
      headers: { "X-Morning-Guard-Token": "secret" },
    });
    assert.equal(await authorizeLicensedChapterRequest(req, env), null);
  });
});
