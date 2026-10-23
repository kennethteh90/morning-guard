import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { tokensMatch } from "../src/auth";
import {
  authorizeLicensedChapterRequest,
  clearLicensedAuthTestHooks,
  setLicensedAuthTestHooks,
} from "../src/licensed-auth";
import { issueChallenge } from "../src/challenge";
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
    const env: Env = { BIBLE_CACHE: kv, API_BIBLE_KEY: "k", BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true" };
    const { nonce } = await issueChallenge(kv);
    setLicensedAuthTestHooks({
      decodeIntegrityToken: async () => ({
        requestPackageName: "com.morningguard.app",
        appRecognitionVerdict: "PLAY_RECOGNIZED",
        certificateSha256Digests: ["abc"],
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
});
