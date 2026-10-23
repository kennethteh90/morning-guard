import type { Env } from "./upstream";

export const RATE_LIMIT_MAX_REQUESTS = 60;
export const RATE_LIMIT_WINDOW_SEC = 60;

function rateLimitKey(ip: string, windowIndex: number): string {
  return `ratelimit/${ip}/${windowIndex}`;
}

export function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP")?.trim() || "unknown";
}

export function currentRateLimitWindowIndex(nowMs = Date.now()): number {
  return Math.floor(nowMs / 1000 / RATE_LIMIT_WINDOW_SEC);
}

/** Returns true when the request should be rejected (limit exceeded). */
export async function isRateLimited(request: Request, env: Env, nowMs = Date.now()): Promise<boolean> {
  const ip = clientIp(request);
  const windowIndex = currentRateLimitWindowIndex(nowMs);
  const key = rateLimitKey(ip, windowIndex);
  const raw = await env.BIBLE_CACHE.get(key);
  const count = raw ? parseInt(raw, 10) : 0;
  if (!Number.isFinite(count) || count >= RATE_LIMIT_MAX_REQUESTS) {
    return count >= RATE_LIMIT_MAX_REQUESTS;
  }
  await env.BIBLE_CACHE.put(key, String(count + 1), {
    expirationTtl: RATE_LIMIT_WINDOW_SEC * 2,
  });
  return false;
}
