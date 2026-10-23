import { licensedContentAllowed, LicensedTranslationBlockedError } from "./licensed";
import { chapterUsfmId } from "./usfm";
import { findTranslation, isLicensedApiBibleTranslation } from "./translations";

import { blocksToPlainBody, parseApiBibleChapterContent, type PassageBlock } from "./api-bible-json";

export interface ChapterPayload {
  reference: string;
  body: string;
  blocks?: PassageBlock[];
}

export interface Env {
  BIBLE_CACHE: KVNamespace;
  API_BIBLE_KEY: string;
  /** Legacy shared token for licensed chapters during app rollout. */
  BIBLE_PROXY_TOKEN?: string;
  /** When true/1/yes, licensed requests no longer accept the legacy token. */
  BIBLE_PROXY_LEGACY_TOKEN_DISABLED?: string;
  /**
   * HMAC secret for short-lived licensed session tokens (min 16 chars).
   * Set with: npx wrangler secret put BIBLE_PROXY_SESSION_HMAC_SECRET --name morning-guard-bible-cache
   */
  BIBLE_PROXY_SESSION_HMAC_SECRET?: string;
  API_BIBLE_NIV_ID?: string;
  API_BIBLE_NKJV_ID?: string;
  API_BIBLE_NLT_ID?: string;
  /** When true/1/yes, NIV/NKJV/NLT may be fetched from API.Bible. */
  API_BIBLE_LICENSED_ENABLED?: string;
  PLAY_INTEGRITY_PACKAGE_NAME?: string;
  PLAY_APP_SIGNING_CERT_SHA256?: string;
  PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON?: string;
  PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT?: string;
  APPLE_TEAM_ID?: string;
  APPLE_APP_ATTEST_BUNDLE_ID?: string;
}

const API_BIBLE_BASE = "https://api.scripture.api.bible/v1";
const OPEN_BIBLE_BASE = "https://bible-api.com";

/** In-memory bible_id cache for this isolate (also persisted in R2 meta/). */
const resolvedBibleIds = new Map<string, string>();

function bibleIdOverride(env: Env, translationId: string): string | undefined {
  switch (translationId) {
    case "niv":
      return env.API_BIBLE_NIV_ID?.trim() || undefined;
    case "nkjv":
      return env.API_BIBLE_NKJV_ID?.trim() || undefined;
    case "nlt":
      return env.API_BIBLE_NLT_ID?.trim() || undefined;
    default:
      return undefined;
  }
}

async function loadResolvedIdsFromCache(env: Env): Promise<void> {
  if (resolvedBibleIds.size > 0) return;
  const raw = await env.BIBLE_CACHE.get("meta/api-bible-ids.json");
  if (!raw) return;
  try {
    const data = JSON.parse(raw) as Record<string, string>;
    for (const [k, v] of Object.entries(data)) {
      if (v) resolvedBibleIds.set(k, v);
    }
  } catch {
    /* ignore corrupt meta */
  }
}

async function persistResolvedIds(env: Env): Promise<void> {
  const payload = JSON.stringify(Object.fromEntries(resolvedBibleIds));
  await env.BIBLE_CACHE.put("meta/api-bible-ids.json", payload);
}

function matchesAbbrev(summary: Record<string, string>, abbrev: string): boolean {
  const target = abbrev.toUpperCase();
  const fields = [
    summary.abbreviationLocal,
    summary.abbreviation,
    summary.nameLocal,
    summary.name,
  ].filter(Boolean);
  return fields.some((f) => f.toUpperCase() === target || f.toUpperCase().includes(target));
}

async function resolveBibleId(env: Env, translationId: string, abbrev: string): Promise<string> {
  const override = bibleIdOverride(env, translationId);
  if (override) return override;

  await loadResolvedIdsFromCache(env);
  const cached = resolvedBibleIds.get(translationId);
  if (cached) return cached;

  const res = await fetch(`${API_BIBLE_BASE}/bibles`, {
    headers: { "api-key": env.API_BIBLE_KEY },
  });
  if (!res.ok) {
    throw new Error(`API.Bible list bibles failed: ${res.status}`);
  }
  const json = (await res.json()) as { data?: Record<string, string>[] };
  const match = json.data?.find((b) => matchesAbbrev(b, abbrev));
  if (!match?.id) {
    throw new Error(`No licensed ${abbrev} Bible on API.Bible account`);
  }
  resolvedBibleIds.set(translationId, match.id);
  await persistResolvedIds(env);
  return match.id;
}

async function fetchApiBibleChapter(
  env: Env,
  translationId: string,
  abbrev: string,
  book: string,
  chapter: number,
): Promise<ChapterPayload> {
  if (!env.API_BIBLE_KEY?.trim()) {
    throw new Error("API_BIBLE_KEY is not configured on the worker");
  }
  const chapterId = chapterUsfmId(book, chapter);
  if (!chapterId) throw new Error(`Unknown book: ${book}`);

  const bibleId = await resolveBibleId(env, translationId, abbrev);
  const query =
    "content-type=json&include-titles=true&include-verse-numbers=true&include-verse-spans=false";
  const url = `${API_BIBLE_BASE}/bibles/${bibleId}/chapters/${chapterId}?${query}`;
  const res = await fetch(url, { headers: { "api-key": env.API_BIBLE_KEY } });
  if (!res.ok) {
    throw new Error(`API.Bible chapter failed: ${res.status}`);
  }
  const json = (await res.json()) as {
    data?: { content?: unknown; reference?: string };
  };
  const reference = json.data?.reference?.trim() || `${book} ${chapter}`;
  const blocks = parseApiBibleChapterContent(json.data?.content);
  if (blocks.length === 0) {
    throw new Error("Empty chapter from API.Bible JSON");
  }
  const content = blocksToPlainBody(blocks);
  return {
    reference,
    body: `${reference}\n\n${content}`,
    blocks,
  };
}

async function fetchOpenBibleChapter(
  translationId: string,
  book: string,
  chapter: number,
): Promise<ChapterPayload> {
  const bookSlug = book.toLowerCase().replace(/\s+/g, "+");
  const path = `${bookSlug}+${chapter}?translation=${encodeURIComponent(translationId)}`;
  const res = await fetch(`${OPEN_BIBLE_BASE}/${path}`);
  if (!res.ok) {
    throw new Error(`bible-api.com failed: ${res.status}`);
  }
  const json = (await res.json()) as {
    reference?: string;
    text?: string;
    verses?: { verse: number; text: string }[];
  };
  const reference = json.reference?.trim() || `${book} ${chapter}`;
  let bodyText =
    json.text?.trim() ||
    (json.verses ?? [])
      .map((v) => `${v.verse}. ${v.text.trim()}`)
      .join("\n");
  bodyText = bodyText.trim();
  if (!bodyText) bodyText = `${reference} (content unavailable)`;
  return { reference, body: `${reference}\n\n${bodyText}` };
}

export async function fetchChapterFromUpstream(
  env: Env,
  translationId: string,
  book: string,
  chapter: number,
): Promise<ChapterPayload> {
  const def = findTranslation(translationId);
  if (!def) {
    return fetchOpenBibleChapter("web", book, chapter);
  }
  if (def.source === "api_bible") {
    if (!licensedContentAllowed(env)) {
      throw new LicensedTranslationBlockedError();
    }
    const abbrev = def.apiBibleAbbrev;
    if (!abbrev) throw new Error("Missing API.Bible abbreviation");
    return fetchApiBibleChapter(env, def.id, abbrev, book, chapter);
  }
  return fetchOpenBibleChapter(def.id, book, chapter);
}
