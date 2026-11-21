/** Cloudflare Workers Rate Limiting API (see developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/). */
export interface RateLimit {
  limit(options: { key: string; delta?: number }): Promise<{ success: boolean }>;
}