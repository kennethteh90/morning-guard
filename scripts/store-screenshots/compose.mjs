#!/usr/bin/env node
/**
 * Step 3 — frame raw device captures with screenshot-studio (studio-core.js).
 *
 * Usage:
 *   npm install   # once, in scripts/store-screenshots
 *   node compose.mjs [--campaign campaign.json] [--raw dir] [--out dir]
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createCanvas, loadImage } from "canvas";

const __dir = dirname(fileURLToPath(import.meta.url));
const studioCoreUrl = pathToFileURL(
  resolve(__dir, "../../site/screenshot-studio/studio-core.js"),
).href;
const { renderSlideToCanvas, defaultProject, PRESETS } = await import(studioCoreUrl);

const args = process.argv.slice(2);
function arg(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
}

const campaignPath = resolve(arg("--campaign", join(__dir, "campaign.json")));
const rawDir = resolve(arg("--raw", join(__dir, "raw")));
const outDir = resolve(arg("--out", join(__dir, "out")));

if (!existsSync(campaignPath)) {
  console.error(`Missing ${campaignPath}. Run generate-plan.mjs first.`);
  process.exit(1);
}

const campaign = JSON.parse(readFileSync(campaignPath, "utf8"));
mkdirSync(outDir, { recursive: true });

const slides = [];
for (const spec of campaign.slides) {
  const rawPath = join(rawDir, spec.rawFile);
  if (!existsSync(rawPath)) {
    console.error(`Missing raw capture: ${rawPath}`);
    process.exit(1);
  }
  const image = await loadImage(rawPath);
  slides.push({
    title: spec.title,
    subtitle: spec.subtitle,
    image,
  });
}

const project = defaultProject({
  ...campaign.studio,
  slides,
});

const presetId = project.presetId;
const p = PRESETS[presetId] ?? PRESETS.play_phone;
const titlePx = Math.round(p.width * (project.titleScale ?? 0.138));
const subPx = Math.round(p.width * (project.subtitleScale ?? 0.068));
console.log(
  `Compose: ${p.width}x${p.height} preset=${presetId} title target≈${titlePx}px subtitle target≈${subPx}px (auto-fit per slide; edit campaign.json + compose.sh)`,
);
for (let i = 0; i < slides.length; i++) {
  const canvas = createCanvas(1, 1);
  renderSlideToCanvas(canvas, i, project);
  const name = `morning-guard_${presetId}_slide-${String(i + 1).padStart(2, "0")}.png`;
  const outPath = join(outDir, name);
  writeFileSync(outPath, canvas.toBuffer("image/png"));
  console.log(`Wrote ${outPath}`);
}

const manifestPath = join(outDir, "manifest.json");
writeFileSync(
  manifestPath,
  JSON.stringify(
    {
      campaign: campaignPath,
      presetId,
      files: slides.map((_, i) => `morning-guard_${presetId}_slide-${String(i + 1).padStart(2, "0")}.png`),
    },
    null,
    2,
  ),
);
console.log(`Done — ${slides.length} store assets in ${outDir}`);
