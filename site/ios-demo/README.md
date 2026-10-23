# Morning Guard — iOS interactive demo

Browser-based **navigable mockup** of the iPhone app for pitches, user interviews, and design reviews — no TestFlight or device setup required.

## Open locally

```bash
# From repo root (recommended — file uploads work if you extend this later)
python3 -m http.server 8766 --directory site/ios-demo
```

Then open [http://localhost:8766/](http://localhost:8766/)

Or double-click `index.html` (works for navigation; prefer a local server for sharing on your LAN).

## Presenting

- Click scenes in the left sidebar, or use **← / →** arrow keys.
- **Presenter mode** hides the sidebar and shows only the phone (shortcut: `P`).
- On narrow viewports, **Fullscreen demo** fills the screen (dark backdrop, larger phone, swipe ← → between scenes). **Exit** returns to the normal layout.
- Deep links: `#home-locked`, `#goal-bible`, `#picker`, `#peace`, `#highlights`, `#stats`, `#settings`, `#onboarding`

## What this is / isn’t

| This mockup | Real iOS app |
|-------------|----------------|
| Static HTML + CSS | SwiftUI + Screen Time APIs |
| Illustrates flows and copy | Live shields, HealthKit, proxy |
| Good for “what it feels like” | Required for engineering QA |

When you have Simulator or a device, capture PNGs and drop them into [`../screenshot-studio/`](../screenshot-studio/) for store-ready framed exports (see [`docs/store-screenshots.md`](../../docs/store-screenshots.md)).

## Android note

Play builds use **notification digest** during the morning gate. iOS uses **app shields** instead; the mockup calls that out on Home and Peace break.
