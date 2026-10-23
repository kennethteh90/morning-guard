import { issueChallenge } from "./challenge";
import {
  authorizeLicensedChapterRequest,
  issueLicensedSessionResponse,
} from "./licensed-auth";
import { cacheControlHeader, LICENSED_KV_TTL_SEC } from "./cache-policy";
import { LicensedTranslationBlockedError, licensedContentAllowed } from "./licensed";
import { isRateLimited } from "./rate-limit";
import { SESSION_HEADER } from "./session-token";
import { isLicensedApiBibleTranslation } from "./translations";
import { bookKeySegment } from "./usfm";
import { fetchChapterFromUpstream, type Env } from "./upstream";

const CACHE_VERSION = "v2";
const DEFAULT_TRANSLATION = "web";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }

    if (url.pathname === "/health") {
      return json({ ok: true });
    }

    if (url.pathname === "/v1/challenge" && request.method === "POST") {
      const { nonce } = await issueChallenge(env.BIBLE_CACHE);
      return cors(json({ nonce }));
    }

    if (url.pathname === "/v1/session" && request.method === "POST") {
      return cors(await issueLicensedSessionResponse(request, env));
    }

    if (url.pathname !== "/v1/chapter" || request.method !== "GET") {
      if (request.method !== "GET") {
        return json({ error: "Method not allowed" }, 405);
      }
      return json({ error: "Not found" }, 404);
    }

    if (await isRateLimited(request, env)) {
      return json({ error: "Rate limit exceeded" }, 429);
    }

    const translation = (url.searchParams.get("translation") ?? DEFAULT_TRANSLATION).trim().toLowerCase();
    const book = (url.searchParams.get("book") ?? "").trim();
    const chapterParam = url.searchParams.get("chapter");
    const chapter = chapterParam ? parseInt(chapterParam, 10) : NaN;

    if (!book || !Number.isFinite(chapter) || chapter < 1) {
      return json({ error: "Missing or invalid book/chapter" }, 400);
    }

    const licensed = isLicensedApiBibleTranslation(translation);
    if (licensed && !licensedContentAllowed(env)) {
      return json({ error: new LicensedTranslationBlockedError().message }, 403);
    }

    if (licensed) {
      const authFailure = await authorizeLicensedChapterRequest(request, env);
      if (authFailure) {
        return cors(authFailure);
      }
    }

    const cacheKey = `${CACHE_VERSION}/${translation}/${bookKeySegment(book)}/${chapter}.json`;
    const cacheControl = cacheControlHeader(licensed);

    const cached = await env.BIBLE_CACHE.get(cacheKey);
    if (cached) {
      const headers = new Headers({
        "Content-Type": "application/json; charset=utf-8",
        "X-Cache": "HIT",
        "Cache-Control": cacheControl,
      });
      return cors(new Response(cached, { status: 200, headers }));
    }

    try {
      const payload = await fetchChapterFromUpstream(env, translation, book, chapter);
      const body = JSON.stringify({
        reference: payload.reference,
        body: payload.body,
        blocks: payload.blocks,
      });
      if (licensed) {
        await env.BIBLE_CACHE.put(cacheKey, body, { expirationTtl: LICENSED_KV_TTL_SEC });
      } else {
        await env.BIBLE_CACHE.put(cacheKey, body);
      }
      return cors(
        jsonResponse(body, {
          "X-Cache": "MISS",
          "Cache-Control": cacheControl,
        }),
      );
    } catch (e) {
      if (e instanceof LicensedTranslationBlockedError) {
        return json({ error: e.message }, 403);
      }
      const message = e instanceof Error ? e.message : "Upstream error";
      return json({ error: message }, 502);
    }
  },
};

function json(data: unknown, status = 200): Response {
  return cors(jsonResponse(JSON.stringify(data), { "Content-Type": "application/json" }, status));
}

function jsonResponse(body: string, extraHeaders: Record<string, string>, status = 200): Response {
  const headers = new Headers(extraHeaders);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  return new Response(body, { status, headers });
}

function cors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  headers.set(
    "Access-Control-Allow-Headers",
    [
      "Content-Type",
      "X-Morning-Guard-Token",
      "X-Morning-Guard-Nonce",
      "X-Morning-Guard-Play-Integrity",
      SESSION_HEADER,
      "X-Morning-Guard-App-Attest-Key-Id",
      "X-Morning-Guard-App-Attest-Assertion",
      "X-Morning-Guard-App-Attest-Attestation",
    ].join(", "),
  );
  return new Response(response.body, { status: response.status, headers });
}
