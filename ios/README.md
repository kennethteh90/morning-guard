# Morning Guard — iOS

SwiftUI app and Screen Time extensions for Morning Guard. The Xcode project is generated from [`project.yml`](project.yml) via [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## Requirements

- macOS with **Xcode 15+** (iOS 17 SDK)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`
- **Physical iPhone** for real Family Controls / shield behavior (Simulator builds compile but Screen Time APIs are limited)

## Demo without a device

For pitches and reviews before TestFlight, use the **interactive HTML mockup** (no install):

```bash
python3 -m http.server 8766 --directory site/ios-demo
# http://localhost:8766/ — arrow keys, presenter mode (P)
```

See [site/ios-demo/README.md](../site/ios-demo/README.md).

## Generate and open

**Device testing:** see [DEVICE-TESTING.md](DEVICE-TESTING.md). **TestFlight / CI:** see [docs/ios/testflight.md](../docs/ios/testflight.md).

```bash
./ios/scripts/run-simulator.sh          # build + install + launch (no Xcode UI needed)
./ios/scripts/open-for-device.sh        # physical iPhone
# or: open ios/MorningGuard.xcodeproj
```

After changing SPM dependencies in [`project.yml`](project.yml), open the project in Xcode (or run `xcodebuild -resolvePackageDependencies -project MorningGuard.xcodeproj -scheme MorningGuard`) and commit the updated lockfile at `MorningGuard.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`. **Xcode Cloud** requires this file when automatic package resolution is disabled; GitHub Actions resolves packages at build time without it.

**⌘R only works when Xcode is the frontmost app** (not Cursor/Terminal). Click the Xcode window, then Run (▶) or ⌘R.

In Xcode:

1. Select the **Morning Guard** scheme and your **Team** for signing.
2. Enable **Family Controls** and **App Groups** (`group.com.morningguard.shared`) on the app and all three extensions (entitlements files are already in the repo).
3. Run on a **device** to authorize Screen Time and test shields.

## Targets

| Target | Bundle ID | Role |
|--------|-----------|------|
| MorningGuard | `com.morningguard.app` | Main SwiftUI app |
| MorningGuardMonitor | `com.morningguard.app.monitor` | Device Activity Monitor — applies/clears shields from app group state |
| MorningGuardShieldConfig | `com.morningguard.app.shieldconfig` | Branded shield UI |
| MorningGuardShieldAction | `com.morningguard.app.shieldaction` | Primary **Open goal** returns `.defer` to foreground the app (Apple does not pass a custom URL from the extension). Deep link `morningguard://goal` works when opened from elsewhere (e.g. magic link flows). |

Shared logic lives in the local Swift package **MorningGuardCore** (`ios/MorningGuardCore`).

## Family Controls distribution

TestFlight and App Store builds need Apple’s Family Controls entitlement for distribution. The Account Holder must request it:

[Requesting the Family Controls entitlement](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement)

Development signing often works before approval; allow several weeks for production.

## Tests

```bash
cd ios/MorningGuardCore && swift test
```

## Bible proxy (optional)

Set `BIBLE_PROXY_BASE_URL` in `ios/Config/Local.xcconfig` (see `Local.xcconfig.example`). Licensed translations (NIV/NKJV/NLT) use App Attest on a **device**; WEB/KJV work on Simulator without attestation. See [`workers/bible-cache/README.md`](../workers/bible-cache/README.md).

## Privacy

`PrivacyInfo.xcprivacy` declares UserDefaults (`CA92.1`), optional crash data when Sentry is configured, HealthKit steps, and email when cloud sync is used. App Store answers: [`docs/app-store-privacy-labels.md`](../docs/app-store-privacy-labels.md). Settings includes an in-app privacy policy plus the hosted URL. Screen Time copy: [docs/ios/PRIVACY-SCREEN-TIME.md](../docs/ios/PRIVACY-SCREEN-TIME.md).

## Screenshot / store capture mode

- Launch argument: `-MGScreenshotMode` (Xcode scheme **MorningGuard-Screenshots**), or set `MORNING_GUARD_SCREENSHOT_MODE` = `YES` in Info.plist for a dedicated build.
- Seeds Psalm 23, streak history, and a sample highlight (`ScreenshotDemoSeeder`).
