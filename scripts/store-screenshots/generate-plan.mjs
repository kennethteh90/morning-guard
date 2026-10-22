#!/usr/bin/env node
/**
 * Step 1 — infer listing slides from repo context (optional OpenAI), write campaign.json.
 *
 * Usage:
 *   node generate-plan.mjs [--ai] [--out campaign.json]
 *
 * Env:
 *   OPENAI_API_KEY — when --ai is set, refines copy via chat completions.
 */

import { readFileSync, writeFileSync, copyFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dir, "../..");
const defaultCampaignPath = join(__dir, "default-campaign.json");

const args = process.argv.slice(2);
const useAi = args.includes("--ai");
const outIdx = args.indexOf("--out");
const outPath = outIdx >= 0 ? resolve(args[outIdx + 1]) : join(__dir, "campaign.json");

function readRepoContext() {
  const readme = readFileSync(join(repoRoot, "README.md"), "utf8");
  const blockers = existsSync(join(repoRoot, "BLOCKERS.md"))
    ? readFileSync(join(repoRoot, "BLOCKERS.md"), "utf8")
    : "";
  return { readme, blockers };
}

function baselineCampaign() {
  return JSON.parse(readFileSync(defaultCampaignPath, "utf8"));
}

async function refineWithOpenAi(campaign, context) {
  const key = process.env.OPENAI_API_KEY?.trim();
  if (!key) {
    console.warn("OPENAI_API_KEY not set; keeping baseline copy.");
    return campaign;
  }

  const system = `You are an ASO copywriter for the Android app "Morning Guard".
Return ONLY valid JSON matching this shape (no markdown):
{
  "slides": [ { "id", "scene", "title", "subtitle", "rawFile" } ],
  "features": [ { "id", "summary" } ]
}
Rules: benefit-led titles (max ~6 words), subtitles plain language, 4 slides mapping scenes home|digest|goal|stats.
Keep rawFile names unchanged.`;

  const user = `App context:\n${context.readme.slice(0, 6000)}\n\nCurrent draft:\n${JSON.stringify(
    { slides: campaign.slides, features: campaign.features },
    null,
    2,
  )}`;

  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      temperature: 0.4,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
    }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`OpenAI ${res.status}: ${text.slice(0, 400)}`);
  }

  const data = await res.json();
  const content = data.choices?.[0]?.message?.content?.trim() ?? "";
  const jsonText = content.replace(/^```json\s*/i, "").replace(/```$/i, "").trim();
  const parsed = JSON.parse(jsonText);

  return {
    ...campaign,
    generatedBy: "openai",
    features: parsed.features ?? campaign.features,
    slides: (parsed.slides ?? campaign.slides).map((slide, i) => ({
      ...campaign.slides[i],
      ...slide,
      rawFile: campaign.slides[i]?.rawFile ?? slide.rawFile,
      scene: campaign.slides[i]?.scene ?? slide.scene,
    })),
  };
}

async function main() {
  mkdirSync(dirname(outPath), { recursive: true });
  const context = readRepoContext();
  let campaign = baselineCampaign();

  if (useAi) {
    campaign = await refineWithOpenAi(campaign, context);
  } else {
    campaign = {
      ...campaign,
      generatedBy: "default+readme",
      notes: "Run with --ai and OPENAI_API_KEY to refine ASO copy.",
    };
  }

  writeFileSync(outPath, JSON.stringify(campaign, null, 2));
  console.log(`Wrote ${outPath} (${campaign.slides.length} slides, source=${campaign.generatedBy})`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
