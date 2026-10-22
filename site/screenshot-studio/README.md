# Screenshot Studio

Local, browser-based composer for **Morning Guard** App Store and Google Play listing images. Inspired by [AppScreens](https://appscreens.com/) (templates, device frames, multi-size export) but runs entirely offline from this folder.

## Automated pipeline

See [`docs/store-screenshots.md`](../../docs/store-screenshots.md) — `scripts/store-screenshots/orchestrate.sh` plans copy, captures via the `screenshot` app build, and composes PNGs with `studio-core.js`.

## Run

Open `index.html` in a desktop browser (Chrome or Safari recommended):

```bash
open site/screenshot-studio/index.html
```

Or serve the repo root if your browser blocks file uploads from `file://`:

```bash
python3 -m http.server 8765 --directory site/screenshot-studio
# http://localhost:8765/
```

## Presets

| Preset | Size | Use |
|--------|------|-----|
| App Store iPhone 6.7" | 1290×2796 | Primary iOS phone screenshots |
| App Store iPhone 6.5" | 1284×2778 | Older large iPhones |
| Google Play phone | 1080×1920 | Phone screenshots (9:16) |
| Google Play feature graphic | 1024×500 | Store feature banner |

Verify current requirements in [Apple’s screenshot specs](https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications) and [Play’s graphic assets](https://support.google.com/googleplay/android-developer/answer/9866151) before submission.

## What we did not build (vs AppScreens)

- Cloud projects, AI captions, 80+ locale overrides, RTL editor
- Direct upload to App Store Connect / Play Console
- 150+ third-party templates, 3D/clay device libraries

Those are product differentiators on AppScreens; this tool focuses on **fast, branded PNG export** for our app only.
