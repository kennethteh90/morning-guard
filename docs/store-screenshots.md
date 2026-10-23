# Store screenshot pipeline

Automates **plan → capture → frame** for Play/App Store assets, modeled on [AppScreens](https://appscreens.com/) but entirely local.

## Overview

| Step | Tool | Output |
|------|------|--------|
| 1. Features + ASO copy | `scripts/store-screenshots/generate-plan.mjs` | `campaign.json` |
| 2. Raw app screens | `screenshot` Gradle variant + `capture-device.sh` | `scripts/store-screenshots/raw/*.png` |
| 3. Framed listing PNGs | `compose.mjs` → `site/screenshot-studio/studio-core.js` | `scripts/store-screenshots/out/*.png` |

**Do not commit** `campaign.json` (if generated), `raw/`, `out/`, or `node_modules/` under `scripts/store-screenshots/` (gitignored).

## When to recapture vs compose only

| Change | Command |
|--------|---------|
| Marketing **title/subtitle**, preset, template, **typography** | `./scripts/store-screenshots/compose.sh` only |
| **App UI** (screens, Bible layout, screenshot-mode behavior) | `capture-device.sh` or full `orchestrate.sh`, then compose |
| New slide scenes | Update campaign + capture + compose |

Copy and headlines are **not** part of the device capture—they are drawn in step 3 from `campaign.json`.

```bash
./scripts/store-screenshots/compose.sh
# equivalent: ./scripts/store-screenshots/orchestrate.sh --compose-only
```

## One command (full pipeline)

Connect an emulator or device, then:

```bash
./scripts/store-screenshots/orchestrate.sh        # default copy
./scripts/store-screenshots/orchestrate.sh --ai   # OPENAI_API_KEY refines copy
```

Optional env: `OPENAI_MODEL` (default `gpt-4o-mini`).

## Manual steps

```bash
node scripts/store-screenshots/generate-plan.mjs [--ai]
./scripts/store-screenshots/capture-device.sh
./scripts/store-screenshots/compose.sh
```

## `campaign.json`

- `slides[]`: `scene` (`home` | `digest` | `goal` | `stats`), `title`, `subtitle`, `rawFile`
- `studio.presetId`: `play_phone` (default), `ios_67`, `play_feature`, …
- `studio.templateId`: e.g. `forest_dawn`
- Optional typography (applied in `studio-core.js`, auto-fit per slide):
  - `titleScale` — default ~`0.138` (fraction of canvas width)
  - `subtitleScale` — default ~`0.068`
  - `marketingBandRatio` — default ~`0.3` (top fraction of canvas for headline block)

**Title rules:** one line only; font shrinks until the full title fits (min ~44px). Subtitle may wrap up to 3 lines; block shrinks if it would overflow the marketing band.

## Marketing typography (compose)

- Rendered in `site/screenshot-studio/studio-core.js` (shared with `site/screenshot-studio/index.html`).
- Node compose must use explicit fonts (`Helvetica Neue`, `Helvetica`, `Arial`)—`system-ui` often ignores requested sizes in `canvas`.
- `compose.mjs` logs target title/subtitle px; actual size may be smaller after auto-fit.
- Tuning: edit scales in `campaign.json` → `compose.sh` (no recapture).

## Screenshot build (`SCREENSHOT_MODE`)

- Gradle variant: **`screenshot`** (`./gradlew :app:installScreenshot`)
- `applicationId`: `com.morningguard.app.screenshot`
- **Debuggable** for `adb` / `run-as`, but **no debug panels** (`AppDebugFlags.showDebugPanels` is false).
- `ScreenshotDemoSeeder` seeds digest notifications, morning window, Bible goal (Psalm 23), stats history.
- Deep link: `adb shell am start -n com.morningguard.app.screenshot/com.morningguard.app.MainActivity -e screenshot_scene home|digest|goal|stats`
- Ready file: `files/screenshot_scene_ready` (polled by `capture-device.sh`).

### Screenshot-only app behavior (for clean captures)

- **Digest:** `FLAG_SECURE` is disabled in screenshot mode (otherwise `adb screencap` is black). Digest list is shown in screenshot mode when demo notifications exist (`DigestViewModel` + `SCREENSHOT_MODE`).
- **Bible verses:** Open Bible loads numbered verses from bible-api.com (`verses` array, not flat `text`). If the Bible proxy returns paragraphs without `1.` / `[1]` markers, repository falls back to open Bible so verse numbers render in `BiblePassageText`.

## Device capture (`capture-device.sh`)

Prerequisites: JDK 17, Android SDK, `adb` device, Node for plan/compose.

**Emulator tips**

- Prefer `-gpu host` (macOS); `swiftshader` can trigger **System UI isn’t responding** ANR dialogs.
- Allow ~45s after boot before capture; script dismisses ANR/dialogs when possible.
- Grant `POST_NOTIFICATIONS` after `pm clear` so permission UI doesn’t block later scenes.

**Script details**

- Loads slide list into a bash **array** before calling `adb` (bash 3.2 on macOS: `while read; do adb…` consumes stdin and only captures the first slide).
- Uses `am start -S` for a clean activity per scene.
- Longer settle for `digest` / `goal` scenes before screencap.

Recapture a single scene without full orchestrate (example: goal only):

```bash
# after installScreenshot; adjust scene and rawFile
adb shell am start -S -n com.morningguard.app.screenshot/com.morningguard.app.MainActivity -e screenshot_scene goal
# wait for files/screenshot_scene_ready == goal, then adb exec-out screencap -p > scripts/store-screenshots/raw/goal.png
./scripts/store-screenshots/compose.sh
```

## Compose dependencies

First run may need:

```bash
cd scripts/store-screenshots && npm install
npm approve-scripts canvas   # if native canvas install is blocked
```

## Interactive studio

Same renderer as CLI: open `site/screenshot-studio/index.html` and drop files from `raw/`.

## Output naming

`scripts/store-screenshots/out/morning-guard_{presetId}_slide-NN.png` plus `manifest.json`.

## Notification-listener policy demo video

Play’s **notification listener** declaration often asks for a short video showing disclosure, how access is granted, and how held notifications work. Listing screenshots alone are not enough for that form.

| Artifact | Command | Output |
|----------|---------|--------|
| Screen recording (recommended) | `./scripts/store-screenshots/record-notification-demo.sh` | `scripts/store-screenshots/out/notification-listener-demo.mp4` |

**Flow** (`notification-demo.json` **steps**): disclosure → tap open access settings → system toggle (grant via adb) → back → finish onboarding → tap **Start morning goal** → digest (warm nav) → home. Edit `hold` / `tap_text` steps to tune pacing.

**Build:** same `screenshot` variant as store captures (`com.morningguard.app.screenshot`). Scene `onboarding_notification` jumps to the notification-access onboarding step with the disclosure checkbox on.

**Emulator:** prefer `-gpu host` (see device capture tips). Allow the device to finish boot before recording.

**Upload:** Play Console → App content → Sensitive app permissions → Notification listener (file or unlisted YouTube URL).

To change timing or order, edit `notification-demo.json` and re-run the script (no app rebuild unless scenes change).

**Why does the app seem to “restart”?** The demo only **cold-starts** once (opening onboarding). Steps that leave for **Android Settings** are expected. Older scripts used `cold: true` on every scene (force-stop + splash), which looks like a crash; current `notification-demo.json` uses **warm** `launch_scene` for home/digest so navigation stays in one session. Set `"cold": true` on a step only if you need a full reset.
