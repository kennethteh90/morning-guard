/** API.Bible: refresh cached licensed content at least every 30 days. */
export const LICENSED_KV_TTL_SEC = 30 * 24 * 60 * 60;

export const OPEN_CACHE_CONTROL = "public, max-age=86400";
export const LICENSED_CACHE_CONTROL = "private, max-age=86400";

export function cacheControlHeader(licensed: boolean): string {
  return licensed ? LICENSED_CACHE_CONTROL : OPEN_CACHE_CONTROL;
}
