import { RATE_LIMIT_BUCKETS } from "../src/rate-limit";
import type { RateLimit } from "../src/rate-limit-binding";
import type { Env } from "../src/upstream";

export function mockKv(): KVNamespace {
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
    getWithMetadata: async (key: string) => ({
      value: store.get(key) ?? null,
      metadata: null,
      cacheStatus: null,
    }),
  } as unknown as KVNamespace;
}

export function mockRateLimit(maxRequests: number): RateLimit {
  const counts = new Map<string, number>();
  return {
    limit: async ({ key }) => {
      const count = counts.get(key) ?? 0;
      if (count >= maxRequests) {
        return { success: false };
      }
      counts.set(key, count + 1);
      return { success: true };
    },
  };
}

export function mockRateLimiters(): Pick<Env, "RATE_LIMIT_CHAPTER" | "RATE_LIMIT_CHALLENGE" | "RATE_LIMIT_SESSION"> {
  return {
    RATE_LIMIT_CHAPTER: mockRateLimit(RATE_LIMIT_BUCKETS.chapter.maxRequests),
    RATE_LIMIT_CHALLENGE: mockRateLimit(RATE_LIMIT_BUCKETS.challenge.maxRequests),
    RATE_LIMIT_SESSION: mockRateLimit(RATE_LIMIT_BUCKETS.session.maxRequests),
  };
}

export function baseEnv(overrides: Partial<Env> = {}): Env {
  return {
    BIBLE_CACHE: mockKv(),
    ...mockRateLimiters(),
    API_BIBLE_KEY: "test-api-key",
    API_BIBLE_LICENSED_ENABLED: "true",
    BIBLE_PROXY_TOKEN: "test-proxy-token",
    ...overrides,
  };
}