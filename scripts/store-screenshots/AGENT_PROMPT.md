# Agent prompt: Morning Guard store screenshot pipeline

Copy everything below the line into a **new agent** session.

---

You are running the **Morning Guard** store screenshot pipeline end-to-end in repo:

`/Users/kennethteh/workspace/morning-guard`

Goal: produce **store-ready framed PNGs** under `scripts/store-screenshots/out/` for Google Play / App Store listing (phone preset by default).

## What this pipeline does

1. **Plan** — `generate-plan.mjs` writes `campaign.json` (features, ASO titles/subtitles, scene → raw filename mapping, studio preset).
2. **Capture** — `screenshot` Android build (`SCREENSHOT_MODE`) seeds demo data, opens each scene, `adb screencap` → `scripts/store-screenshots/raw/`.
3. **Compose** — `compose.mjs` uses `site/screenshot-studio/studio-core.js` to add device frames + copy → `scripts/store-screenshots/out/`.

Full reference: `docs/store-screenshots.md`

## Prerequisites (verify before starting)

- **JDK 17**, Android SDK, repo builds: `./gradlew :app:compileScreenshotKotlin`
- **adb device or emulator** connected: `adb devices` shows one device in `device` state
- **Node.js** (18+) for plan + compose scripts
- Optional AI copy: `OPENAI_API_KEY` set (and optionally `OPENAI_MODEL`, default `gpt-4o-mini`)
- Emulator tip: use a **phone** profile (e.g. Pixel 6) for consistent captures; unlock screen before capture

Do **not** commit `campaign.json`, `raw/`, `out/`, or `node_modules/` (gitignored under `scripts/store-screenshots/`).

## Your task

1. Read `docs/store-screenshots.md` and skim `scripts/store-screenshots/default-campaign.json` so you know expected scenes: `home`, `digest`, `goal`, `stats`.
2. Run the orchestrator from repo root:
   - Default copy: `./scripts/store-screenshots/orchestrate.sh`
   - Or with AI-refined copy if `OPENAI_API_KEY` is available: `./scripts/store-screenshots/orchestrate.sh --ai`
3. If orchestration fails, **diagnose and fix** (do not give up after one error):
   - **Gradle / variant**: `./gradlew :app:installScreenshot` must succeed (`screenshot` build type, package `com.morningguard.app.screenshot`).
   - **adb**: install, `pm clear`, `am start -n com.morningguard.app.screenshot/com.morningguard.app.MainActivity -e screenshot_scene <scene>`.
   - **Ready file**: `adb shell run-as com.morningguard.app.screenshot cat files/screenshot_scene_ready` should match the scene after launch.
   - **compose**: `cd scripts/store-screenshots && npm install && node compose.mjs`
4. Confirm outputs:
   - `scripts/store-screenshots/raw/` has one PNG per slide in `campaign.json`
   - `scripts/store-screenshots/out/` has `morning-guard_play_phone_slide-*.png` (or preset from `campaign.json` → `studio.presetId`)
   - `scripts/store-screenshots/out/manifest.json` lists files
5. Report back with:
   - Command(s) run
   - Preset used (`studio.presetId`)
   - List of output PNG paths
   - Any manual tweaks needed (e.g. re-run compose only after editing `campaign.json`)
   - Whether AI planning was used

## Partial re-runs (if user only needs one step)

| Step | Command |
|------|---------|
| Plan only | `node scripts/store-screenshots/generate-plan.mjs [--ai] --out scripts/store-screenshots/campaign.json` |
| Capture only | `CAMPAIGN=scripts/store-screenshots/campaign.json ./scripts/store-screenshots/capture-device.sh` |
| Compose only | `cd scripts/store-screenshots && npm install && node compose.mjs` |

To export **App Store** sizes, edit `campaign.json` → `"studio": { "presetId": "ios_67", ... }` and re-run compose (no recapture required unless copy changed).

## Constraints

- Do **not** create git commits unless the user explicitly asks.
- Do **not** push to remote unless asked.
- Prefer fixing local env (device, adb, npm) over changing product code unless the pipeline is genuinely broken.
- Use shell yourself; do not only instruct the user to run commands.

## Notification-listener policy video (optional)

Separate from listing screenshots — for Play **Sensitive app permissions**:

```bash
./scripts/store-screenshots/record-notification-demo.sh
```

Output: `scripts/store-screenshots/out/notification-listener-demo.mp4`. Requires adb device; edit timing in `notification-demo.json`.

## Success criteria

Pipeline succeeded when `scripts/store-screenshots/out/` contains framed PNGs for every slide in `campaign.json` and captures look like the release app (no debug settings card, no step debug overlay).
