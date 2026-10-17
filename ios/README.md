# Morning Guard — iOS

SwiftUI app and Screen Time extensions for Morning Guard. The Xcode project is generated from [`project.yml`](project.yml) via [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## Requirements

- macOS with **Xcode 15+** (iOS 17 SDK)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`
- **Physical iPhone** for real Family Controls / shield behavior (Simulator builds compile but Screen Time APIs are limited)

## Generate and open

```bash
./ios/scripts/generate-xcodeproj.sh
open ios/MorningGuard.xcodeproj
```

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
| MorningGuardShieldAction | `com.morningguard.app.shieldaction` | Opens `morningguard://goal` from the shield |

Shared logic lives in the local Swift package **MorningGuardCore** (`ios/MorningGuardCore`).

## Family Controls distribution

TestFlight and App Store builds need Apple’s Family Controls entitlement for distribution. The Account Holder must request it:

[Requesting the Family Controls entitlement](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement)

Development signing often works before approval; allow several weeks for production.

## Tests

```bash
cd ios/MorningGuardCore && swift test
```

## Privacy

See [docs/ios/PRIVACY-SCREEN-TIME.md](../docs/ios/PRIVACY-SCREEN-TIME.md).
