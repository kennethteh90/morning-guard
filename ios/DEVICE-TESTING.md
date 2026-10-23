# Install Morning Guard on your iPhone (dev)

## Simulator (free, no Developer Program)

```bash
./ios/scripts/run-simulator.sh
```

Or in Xcode: scheme **MorningGuard** → destination **iPhone 17** (or any simulator) → **Run** (⌘R). No Team/signing needed for Simulator.

**Limits:** Screen Time shields and Family Activity Picker do not behave like a real device. Use Simulator for UI, onboarding copy, Bible/focus goals, and settings—not for shield testing.

### Debug mode (Debug configuration only)

- Orange **Debug build** banner on Home.
- **Settings → Debug:** window presets (active now / full day / default), reset today's goal, reset onboarding.
- Matches Android `AppDebugFlags.showDebugPanels` (hidden in **Release** and when `MORNING_GUARD_SCREENSHOT_MODE` is `true` in Info.plist).

---

## Before you start (physical device)

- **Mac:** Xcode 15+ from the App Store (not Command Line Tools only).
- **iPhone:** iOS **17+**, USB or Wi‑Fi debugging enabled.
- **Apple Developer Program** ($99/year) — required for **Family Controls** on a real device.
- **Branch:** `ios/scaffold-ci` (or `main` once merged).

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
brew install xcodegen
```

## One-time signing setup

1. Copy local team config:
   ```bash
   cp ios/Config/Local.xcconfig.example ios/Config/Local.xcconfig
   ```
2. Edit `ios/Config/Local.xcconfig` and set `DEVELOPMENT_TEAM` to your 10-character Team ID.

3. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list):

   - **App Group:** `group.com.morningguard.shared`
   - **App IDs** (enable **Family Controls** + **App Groups** on each):
     - `com.morningguard.app` (+ HealthKit if you test steps)
     - `com.morningguard.app.monitor`
     - `com.morningguard.app.shieldconfig`
     - `com.morningguard.app.shieldaction`

   Xcode automatic signing can create these if your account has permission.

## Open and run

```bash
./ios/scripts/open-for-device.sh
```

In Xcode:

1. Scheme **MorningGuard** → destination **your iPhone**.
2. If signing errors appear, select the **MorningGuard** target → **Signing & Capabilities** → choose your Team; repeat for the three **extension** targets in the project navigator.
3. **Run** (⌘R). On the phone: **Settings → General → VPN & Device Management** → trust your developer certificate.

## First-run on the phone

1. Approve **Screen Time** access (individual / this device).
2. Pick **one or two apps** to shield (easy to verify).
3. Set **morning window** so **now** is inside the range (Settings in app if you skipped in onboarding).
4. Choose **Timed focus** (1 min) for the fastest goal test.
5. Open a shielded app → shield UI → **Open Morning Guard** → complete goal → app unlocks.

## Quick debug

| Issue | Check |
|-------|--------|
| Install fails on entitlements | Family Controls on all 4 bundle IDs; App Group on all 4 targets |
| No shield | Physical device; gate **Locked**; apps selected in picker; Screen Time auth **Approved** |
| Gate **Inactive** | Widen morning window to include current time |
| Bible empty | Wi‑Fi/cellular; bible-api.com reachable |

## Distribution later

TestFlight/App Store needs Apple’s [Family Controls distribution entitlement](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement) (separate from local dev).
