const CACHE_ORIGIN = "https://morning-guard-bible-cache.internal";

function chapterCacheRequest(cacheKey: string): Request {
  return new Request(`${CACHE_ORIGIN}/${cacheKey}`);
}

function cacheApi(): Cache | null {
  try {
    return typeof caches !== "undefined" ? caches.default : null;
  } catch {
    return null;
  }
}

/** Read chapter JSON from the Workers Cache API. */
export async function readChapterFromEdgeCache(cacheKey: string): Promise<string | null> {
  const cache = cacheApi();
  if (!cache) return null;
  try {
    const hit = await cache.match(chapterCacheRequest(cacheKey));
    if (!hit) return null;
    return await hit.text();
  } catch (e) {
    console.warn("chapter_cache_read_failed", {
      message: e instanceof Error ? e.message : String(e).slice(0, 200),
    });
    return null;
  }
}

/** Store chapter JSON in the Workers Cache API (does not throw). */
export async function writeChapterToEdgeCache(
  cacheKey: string,
  body: string,
  cacheControl: string,
): Promise<void> {
  const cache = cacheApi();
  if (!cache) return;
  try {
    const response = new Response(body, {
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": cacheControl,
      },
    });
    await cache.put(chapterCacheRequest(cacheKey), response);
  } catch (e) {
    console.warn("chapter_cache_put_failed", {
      message: e instanceof Error ? e.message : String(e).slice(0, 200),
    });
  }
}