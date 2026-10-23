import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";
import { LicensedTranslationBlockedError } from "../src/licensed";
import { fetchChapterFromUpstream, type Env } from "../src/upstream";

const API_BIBLE_HOST = "api.scripture.api.bible";
const OPEN_BIBLE_HOST = "bible-api.com";

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
    ...overrides,
  };
}

describe("fetchChapterFromUpstream", () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    mock.restoreAll();
  });

  it("does not call API.Bible for translation=niv when licensed content is disabled", async () => {
    const urls: string[] = [];
    globalThis.fetch = mock.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      urls.push(url);
      return new Response("{}", { status: 200 });
    }) as typeof fetch;

    await assert.rejects(
      () => fetchChapterFromUpstream(baseEnv(), "niv", "Genesis", 1),
      LicensedTranslationBlockedError,
    );

    assert.equal(
      urls.some((u) => u.includes(API_BIBLE_HOST)),
      false,
      `expected no API.Bible fetch, got: ${urls.join(", ")}`,
    );
  });

  it("serves translation=web from bible-api.com", async () => {
    const urls: string[] = [];
    globalThis.fetch = mock.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      urls.push(url);
      return new Response(
        JSON.stringify({
          reference: "Genesis 1",
          text: "1. In the beginning",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }) as typeof fetch;

    const payload = await fetchChapterFromUpstream(baseEnv(), "web", "Genesis", 1);

    assert.match(payload.body, /In the beginning/);
    assert.equal(
      urls.some((u) => u.includes(OPEN_BIBLE_HOST)),
      true,
      `expected bible-api.com fetch, got: ${urls.join(", ")}`,
    );
    assert.equal(
      urls.some((u) => u.includes(API_BIBLE_HOST)),
      false,
    );
  });
});
