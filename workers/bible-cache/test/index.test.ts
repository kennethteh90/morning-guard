import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";
import worker from "../src/index";
import { clearLicensedAuthTestHooks, setLicensedAuthTestHooks } from "../src/licensed-auth";
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

function baseEnv(overrides: Partial<Env> = {}): Env {
  return {
    BIBLE_CACHE: mockKv(),
    API_BIBLE_KEY: "test-api-key",
    API_BIBLE_LICENSED_ENABLED: "true",
    BIBLE_PROXY_TOKEN: "test-proxy-token",
    ...overrides,
  };
}

describe("worker fetch", () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
    clearLicensedAuthTestHooks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    mock.restoreAll();
    clearLicensedAuthTestHooks();
  });

  it("serves translation=web with no auth headers", async () => {
    globalThis.fetch = mock.fn(async () =>
      new Response(
        JSON.stringify({ reference: "John 3", text: "16. For God so loved the world" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ) as typeof fetch;

    const request = new Request(
      "https://example.com/v1/chapter?translation=web&book=John&chapter=3",
      { headers: { "CF-Connecting-IP": "203.0.113.2" } },
    );
    const response = await worker.fetch(request, baseEnv());
    const body = (await response.json()) as { body?: string };

    assert.equal(response.status, 200);
    assert.match(body.body ?? "", /loved the world/);
  });

  it("returns 401 for translation=niv without attestation or token", async () => {
    const request = new Request(
      "https://example.com/v1/chapter?translation=niv&book=Genesis&chapter=1",
      { headers: { "CF-Connecting-IP": "203.0.113.1" } },
    );
    const response = await worker.fetch(request, baseEnv());
    assert.equal(response.status, 401);
  });

  it("returns 200 for translation=niv with legacy token", async () => {
    globalThis.fetch = mock.fn(async () =>
      new Response(
        JSON.stringify({
          data: {
            reference: "Genesis 1",
            content: [
              {
                name: "para",
                attrs: { style: "p" },
                items: [
                  {
                    attrs: { number: "1", style: "v" },
                    text: "In the beginning God created the heavens and the earth.",
                  },
                ],
              },
            ],
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ) as typeof fetch;

    const request = new Request(
      "https://example.com/v1/chapter?translation=niv&book=Genesis&chapter=1",
      {
        headers: {
          "CF-Connecting-IP": "203.0.113.3",
          "X-Morning-Guard-Token": "test-proxy-token",
        },
      },
    );
    const response = await worker.fetch(
      request,
      baseEnv({ API_BIBLE_NIV_ID: "test-niv-bible-id" }),
    );
    assert.equal(response.status, 200);
  });

  it("returns 403 for translation=niv when licensed disabled", async () => {
    const request = new Request(
      "https://example.com/v1/chapter?translation=niv&book=Genesis&chapter=1",
      {
        headers: {
          "CF-Connecting-IP": "203.0.113.1",
          "X-Morning-Guard-Token": "test-proxy-token",
        },
      },
    );
    const response = await worker.fetch(
      request,
      baseEnv({ API_BIBLE_LICENSED_ENABLED: "false" }),
    );
    assert.equal(response.status, 403);
  });

  it("does not require token for /health", async () => {
    const request = new Request("https://example.com/health");
    const response = await worker.fetch(request, baseEnv());
    assert.equal(response.status, 200);
  });

  it("issues challenge nonce", async () => {
    const request = new Request("https://example.com/v1/challenge", { method: "POST" });
    const response = await worker.fetch(request, baseEnv());
    const body = (await response.json()) as { nonce?: string };
    assert.equal(response.status, 200);
    assert.ok(body.nonce && body.nonce.length > 8);
  });

  it("issues a session token after Play Integrity and accepts it on chapter", async () => {
    const kv = mockKv();
    const env = baseEnv({
      BIBLE_CACHE: kv,
      BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true",
      BIBLE_PROXY_SESSION_HMAC_SECRET: "test-session-hmac-secret-value",
      API_BIBLE_NIV_ID: "test-niv-bible-id",
      PLAY_APP_SIGNING_CERT_SHA256:
        "36:8F:58:B5:D7:B9:53:85:A5:20:5F:52:0C:48:2D:E2:2D:BF:AD:F8:71:7E:09:EF:B4:24:3C:B3:27:F3:93:40",
    });
    const challengeRes = await worker.fetch(
      new Request("https://example.com/v1/challenge", { method: "POST" }),
      env,
    );
    const { nonce } = (await challengeRes.json()) as { nonce: string };
    setLicensedAuthTestHooks({
      decodeIntegrityToken: async () => ({
        requestPackageName: "com.morningguard.app",
        appRecognitionVerdict: "PLAY_RECOGNIZED",
        certificateSha256Digests: ["No9Ytde5U4WlIF9SDEgt4i2_rfhxfgnvtCQ8syfzk0A"],
        deviceRecognitionVerdicts: ["MEETS_DEVICE_INTEGRITY"],
        requestDetailsNonce: nonce,
      }),
    });

    const sessionRes = await worker.fetch(
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
    const sessionBody = (await sessionRes.json()) as { sessionToken?: string };
    assert.ok(sessionBody.sessionToken);

    globalThis.fetch = mock.fn(async () =>
      new Response(
        JSON.stringify({
          data: {
            reference: "Genesis 1",
            content: [
              {
                name: "para",
                attrs: { style: "p" },
                items: [
                  {
                    attrs: { number: "1", style: "v" },
                    text: "In the beginning God created the heavens and the earth.",
                  },
                ],
              },
            ],
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ) as typeof fetch;

    const chapterRes = await worker.fetch(
      new Request("https://example.com/v1/chapter?translation=niv&book=Genesis&chapter=1", {
        headers: {
          "CF-Connecting-IP": "203.0.113.9",
          "X-Morning-Guard-Session": sessionBody.sessionToken!,
        },
      }),
      env,
    );
    assert.equal(chapterRes.status, 200);
  });

  it("serves translation=niv with Play Integrity when legacy token disabled", async () => {
    const kv = mockKv();
    const challengeRes = await worker.fetch(
      new Request("https://example.com/v1/challenge", { method: "POST" }),
      baseEnv({ BIBLE_CACHE: kv, BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true" }),
    );
    const { nonce } = (await challengeRes.json()) as { nonce: string };

    setLicensedAuthTestHooks({
      decodeIntegrityToken: async () => ({
        requestPackageName: "com.morningguard.app",
        appRecognitionVerdict: "PLAY_RECOGNIZED",
        certificateSha256Digests: ["No9Ytde5U4WlIF9SDEgt4i2_rfhxfgnvtCQ8syfzk0A"],
        deviceRecognitionVerdicts: ["MEETS_DEVICE_INTEGRITY"],
        requestDetailsNonce: nonce,
      }),
    });

    globalThis.fetch = mock.fn(async () =>
      new Response(
        JSON.stringify({
          data: {
            reference: "Genesis 1",
            content: [
              {
                name: "para",
                attrs: { style: "p" },
                items: [
                  {
                    attrs: { number: "1", style: "v" },
                    text: "In the beginning God created the heavens and the earth.",
                  },
                ],
              },
            ],
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ) as typeof fetch;

    const request = new Request(
      "https://example.com/v1/chapter?translation=niv&book=Genesis&chapter=1",
      {
        headers: {
          "CF-Connecting-IP": "203.0.113.4",
          "X-Morning-Guard-Nonce": nonce,
          "X-Morning-Guard-Play-Integrity": "token",
        },
      },
    );
    const response = await worker.fetch(
      request,
      baseEnv({
        BIBLE_CACHE: kv,
        BIBLE_PROXY_LEGACY_TOKEN_DISABLED: "true",
        API_BIBLE_NIV_ID: "test-niv-bible-id",
        PLAY_APP_SIGNING_CERT_SHA256:
          "36:8F:58:B5:D7:B9:53:85:A5:20:5F:52:0C:48:2D:E2:2D:BF:AD:F8:71:7E:09:EF:B4:24:3C:B3:27:F3:93:40",
      }),
    );
    const failureBody = response.status !== 200 ? await response.text() : "";
    assert.equal(response.status, 200, failureBody);
  });
});
