import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  RATE_LIMIT_BUCKETS,
  currentRateLimitWindowIndex,
  isRateLimitedForBucket,
} from "../src/rate-limit";
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
    getWithMetadata: async () => ({ value: null, metadata: null, cacheStatus: null }),
  } as unknown as KVNamespace;
}

describe("rate-limit", () => {
  it("limits challenge bucket independently of chapter budget", async () => {
    const env: Env = { BIBLE_CACHE: mockKv(), API_BIBLE_KEY: "k" };
    const request = new Request("https://example.com/v1/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.50" },
    });
    const windowSec = RATE_LIMIT_BUCKETS.challenge.windowSec;
    const max = RATE_LIMIT_BUCKETS.challenge.maxRequests;
    const nowMs = currentRateLimitWindowIndex(windowSec) * windowSec * 1000;

    for (let i = 0; i < max; i++) {
      assert.equal(await isRateLimitedForBucket(request, env, "challenge", nowMs), false);
    }
    assert.equal(await isRateLimitedForBucket(request, env, "challenge", nowMs), true);
  });
});
