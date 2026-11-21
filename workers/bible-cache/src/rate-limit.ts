import type { RateLimit } from "./rate-limit-binding";
import type { Env } from "./upstream";

export const RATE_LIMIT_BUCKETS = {
  /** Public WEB chapter fetches. */
  chapter: { maxRequests: 60, windowSec: 60 },
  /** One-time attestation nonces; cap abuse of KV writes. */
  challenge: { maxRequests: 30, windowSec: 60 },
  /** Licensed session minting; ties to Play Integrity / App Attest upstream. */
  session: { maxRequests: 20, windowSec: 60 },
} as const;

export type RateLimitBucket = keyof typeof RATE_LIMIT_BUCKETS;

/** @deprecated Limits are enforced by Rate Limiting bindings; constant kept for tests/docs. */
export const RATE_LIMIT_MAX_REQUESTS = RATE_LIMIT_BUCKETS.chapter.maxRequests;
/** @deprecated Limits are enforced by Rate Limiting bindings; constant kept for tests/docs. */
export const RATE_LIMIT_WINDOW_SEC = RATE_LIMIT_BUCKETS.chapter.windowSec;

export function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP")?.trim() || "unknown";
}

function rateLimiterForBucket(env: Env, bucket: RateLimitBucket): RateLimit | undefined {
  switch (bucket) {
    case "chapter":
      return env.RATE_LIMIT_CHAPTER;
    case "challenge":
      return env.RATE_LIMIT_CHALLENGE;
    case "session":
      return env.RATE_LIMIT_SESSION;
    default:
      return undefined;
  }
}

/** Returns true when the request should be rejected (limit exceeded). */
export async function isRateLimitedForBucket(
  request: Request,
  env: Env,
  bucket: RateLimitBucket,
): Promise<boolean> {
  const limiter = rateLimiterForBucket(env, bucket);
  if (!limiter) {
    console.warn("rate_limit_binding_missing", { bucket });
    return false;
  }
  const ip = clientIp(request);
  try {
    const { success } = await limiter.limit({ key: ip });
    return !success;
  } catch (e) {
    console.warn("rate_limit_check_failed", {
      bucket,
      message: e instanceof Error ? e.message : String(e).slice(0, 200),
    });
    return false;
  }
}

/** Chapter bucket — backward-compatible entry point. */
export async function isRateLimited(request: Request, env: Env): Promise<boolean> {
  return isRateLimitedForBucket(request, env, "chapter");
}