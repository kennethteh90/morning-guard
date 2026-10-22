export type PassageBlock = { type: "heading" | "paragraph"; text: string };

export type ApiBibleJsonNode = {
  name?: string;
  type?: string;
  attrs?: Record<string, string>;
  items?: ApiBibleJsonNode[];
  text?: string;
};

/** Section / title paragraph styles in API.Bible JSON (USFM). */
export function isHeadingParaStyle(style: string): boolean {
  const s = style.trim().toLowerCase();
  if (!s) return false;
  if (s === "ms" || s === "d" || s === "sp" || s === "sd") return true;
  return /^s\d*$/.test(s);
}

export function parseApiBibleChapterContent(raw: unknown): PassageBlock[] {
  const nodes = normalizeContentRoot(raw);
  const blocks: PassageBlock[] = [];

  for (const node of nodes) {
    if (node.name !== "para") continue;
    const style = node.attrs?.style ?? "";
    const text = paragraphText(node.items ?? []);
    if (!text) continue;

    if (isHeadingParaStyle(style)) {
      blocks.push({ type: "heading", text });
    } else {
      blocks.push({ type: "paragraph", text });
    }
  }

  return blocks;
}

function normalizeContentRoot(raw: unknown): ApiBibleJsonNode[] {
  if (Array.isArray(raw)) return raw as ApiBibleJsonNode[];
  if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (!trimmed) return [];
    const parsed = JSON.parse(trimmed) as unknown;
    return Array.isArray(parsed) ? (parsed as ApiBibleJsonNode[]) : [];
  }
  return [];
}

function paragraphText(items: ApiBibleJsonNode[]): string {
  const parts: string[] = [];
  for (const item of items) {
    appendItemText(item, parts);
  }
  const joined = parts
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
  return normalizeDuplicateVerseDigits(joined);
}

/**
 * API.Bible often repeats the verse digit after our `[n]` marker (`[1] 1 Some…`).
 * Clients render `[n]` separately, so strip the redundant digit here once.
 */
export function normalizeDuplicateVerseDigits(paragraph: string): string {
  return paragraph.replace(
    /\[(\d+)\]\s*\1(?:\.(?=\s)|(?=\s)|$|\[)/g,
    "[$1]",
  );
}

function appendItemText(item: ApiBibleJsonNode, parts: string[]): void {
  const style = item.attrs?.style ?? "";
  if (style === "v" && item.attrs?.number) {
    const verseNumber = item.attrs.number.trim();
    parts.push(`[${verseNumber}]`);
    if (item.text?.trim()) {
      const stripped = stripRedundantVersePrefix(item.text.trim(), verseNumber);
      if (stripped) parts.push(stripped);
    }
    for (const child of item.items ?? []) {
      appendVerseChildText(child, parts, verseNumber);
    }
    return;
  }
  if (item.text?.trim()) {
    parts.push(item.text.trim());
    return;
  }
  for (const child of item.items ?? []) {
    appendItemText(child, parts);
  }
}

function appendVerseChildText(
  item: ApiBibleJsonNode,
  parts: string[],
  verseNumber: string,
): void {
  if (item.text?.trim()) {
    const stripped = stripRedundantVersePrefix(item.text.trim(), verseNumber);
    if (stripped) parts.push(stripped);
    return;
  }
  for (const child of item.items ?? []) {
    appendVerseChildText(child, parts, verseNumber);
  }
}

function stripRedundantVersePrefix(text: string, verseNumber: string): string {
  const n = verseNumber.trim();
  if (!n) return text.trim();
  let t = text.trim();
  const redundant = new RegExp(`^${n}(?:\\.(?=\\s)|(?=\\s)|$)`);
  while (redundant.test(t)) {
    t = t.replace(redundant, "").trimStart();
  }
  return t.trim();
}

/** Plain-text fallback body for clients that ignore structured blocks. */
export function blocksToPlainBody(blocks: PassageBlock[]): string {
  return blocks
    .map((b) => (b.type === "heading" ? b.text : b.text))
    .join("\n\n");
}
