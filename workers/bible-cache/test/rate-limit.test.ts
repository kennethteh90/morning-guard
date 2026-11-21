import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { RATE_LIMIT_BUCKETS, isRateLimitedForBucket } from "../src/rate-limit";
import { baseEnv, mockRateLimit } from "./helpers";
import type { Env } from "../src/upstream";

describe("rate-limit", () => {
  it("limits challenge bucket independently of chapter budget", async () => {
    const env: Env = baseEnv();
    const request = new Request("https://example.com/v1/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.50" },
    });
    const max = RATE_LIMIT_BUCKETS.challenge.maxRequests;

    for (let i = 0; i < max; i++) {
      assert.equal(await isRateLimitedForBucket(request, env, "challenge"), false);
    }
    assert.equal(await isRateLimitedForBucket(request, env, "challenge"), true);
  });

  it("fails open when the rate limit binding is missing", async () => {
    const env = baseEnv({
      RATE_LIMIT_CHALLENGE: undefined as unknown as Env["RATE_LIMIT_CHALLENGE"],
    });
    const request = new Request("https://example.com/v1/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.99" },
    });
    assert.equal(await isRateLimitedForBucket(request, env, "challenge"), false);
  });

  it("fails open when limit() throws", async () => {
    const env = baseEnv({
      RATE_LIMIT_CHALLENGE: {
        limit: async () => {
          throw new Error("quota");
        },
      },
    });
    const request = new Request("https://example.com/v1/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.100" },
    });
    assert.equal(await isRateLimitedForBucket(request, env, "challenge"), false);
  });

  it("uses a fresh budget when the mock limiter is replaced", async () => {
    const request = new Request("https://example.com/v1/chapter", {
      headers: { "CF-Connecting-IP": "198.51.100.1" },
    });
    let env = baseEnv({
      RATE_LIMIT_CHAPTER: mockRateLimit(1),
    });
    assert.equal(await isRateLimitedForBucket(request, env, "chapter"), false);
    assert.equal(await isRateLimitedForBucket(request, env, "chapter"), true);

    env = baseEnv({
      RATE_LIMIT_CHAPTER: mockRateLimit(1),
    });
    assert.equal(await isRateLimitedForBucket(request, env, "chapter"), false);
  });
});