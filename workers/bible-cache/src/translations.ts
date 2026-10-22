export type TranslationSource = "api_bible" | "open_bible";

export interface TranslationDef {
  id: string;
  source: TranslationSource;
  /** API.Bible abbreviation match (NIV, NKJV, NLT). */
  apiBibleAbbrev?: string;
}

const TRANSLATIONS: TranslationDef[] = [
  { id: "niv", source: "api_bible", apiBibleAbbrev: "NIV" },
  { id: "nkjv", source: "api_bible", apiBibleAbbrev: "NKJV" },
  { id: "nlt", source: "api_bible", apiBibleAbbrev: "NLT" },
  { id: "web", source: "open_bible" },
  { id: "kjv", source: "open_bible" },
  { id: "asv", source: "open_bible" },
  { id: "bbe", source: "open_bible" },
  { id: "darby", source: "open_bible" },
  { id: "dra", source: "open_bible" },
  { id: "ylt", source: "open_bible" },
  { id: "oeb-us", source: "open_bible" },
  { id: "oeb-cw", source: "open_bible" },
  { id: "webbe", source: "open_bible" },
  { id: "clementine", source: "open_bible" },
  { id: "cuv", source: "open_bible" },
  { id: "bkr", source: "open_bible" },
  { id: "almeida", source: "open_bible" },
  { id: "rccv", source: "open_bible" },
  { id: "cherokee", source: "open_bible" },
];

export function findTranslation(id: string): TranslationDef | undefined {
  const norm = id.trim().toLowerCase();
  return TRANSLATIONS.find((t) => t.id === norm);
}

export function isLicensedApiBibleTranslation(id: string): boolean {
  const def = findTranslation(id);
  return def?.source === "api_bible";
}
