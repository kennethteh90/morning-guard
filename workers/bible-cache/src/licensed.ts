import type { Env } from "./upstream";

export class LicensedTranslationBlockedError extends Error {
  constructor() {
    super("Licensed Bible translations are not available until redistribution is confirmed");
    this.name = "LicensedTranslationBlockedError";
  }
}

/** API.Bible NIV/NKJV/NLT — blocked until ops sets API_BIBLE_LICENSED_ENABLED. */
export function licensedContentAllowed(env: Env): boolean {
  const v = env.API_BIBLE_LICENSED_ENABLED?.trim().toLowerCase();
  return v === "1" || v === "true" || v === "yes";
}
