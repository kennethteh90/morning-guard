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

/** @deprecated Use bucket-specific limits via {@link isRateLimitedForBucket}. */
export const RATE_LIMIT_MAX_REQUESTS = RATE_LIMIT_BUCKETS.chapter.maxRequests;
export const RATE_LIMIT_WINDOW_SEC = RATE_LIMIT_BUCKETS.chapter.windowSec;

function rateLimitKey(bucket: RateLimitBucket, ip: string, windowIndex: number): string {
  return `ratelimit/${bucket}/${ip}/${windowIndex}`;
}

export function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP")?.trim() || "unknown";
}

export function currentRateLimitWindowIndex(windowSec: number, nowMs = Date.now()): number {
  return Math.floor(nowMs / 1000 / windowSec);
}

/** Returns true when the request should be rejected (limit exceeded). */
export async function isRateLimitedForBucket(
  request: Request,
  env: Env,
  bucket: RateLimitBucket,
  nowMs = Date.now(),
): Promise<boolean> {
  const { maxRequests, windowSec } = RATE_LIMIT_BUCKETS[bucket];
  const ip = clientIp(request);
  const windowIndex = currentRateLimitWindowIndex(windowSec, nowMs);
  const key = rateLimitKey(bucket, ip, windowIndex);
  const raw = await env.BIBLE_CACHE.get(key);
  const count = raw ? parseInt(raw, 10) : 0;
  if (!Number.isFinite(count) || count >= maxRequests) {
    return count >= maxRequests;
  }
  await env.BIBLE_CACHE.put(key, String(count + 1), {
    expirationTtl: windowSec * 2,
  });
  return false;
}

/** Chapter bucket — backward-compatible entry point. */
export async function isRateLimited(request: Request, env: Env, nowMs = Date.now()): Promise<boolean> {
  return isRateLimitedForBucket(request, env, "chapter", nowMs);
}
