# Morning Guard — iOS production plan

Status legend: `[ ]` todo · `[~]` in progress · `[x]` done

**Last updated:** 2026-03-21  
**Decision record:** Swift-native v1 (no KMP in v1). Android `:core:domain` remains source of truth; iOS `MorningGuardCore` mirrors behavior and is kept aligned via shared unit-test scenarios (ported assertions).

---

## 1. Product contract

### What we promise on iOS

| Promise | Mechanism |
|---------|-----------|
| Protect the first minutes after waking | User-selected **app shields** during the **morning window** until a **verifiable goal** completes |
| Alarms and critical comms still reachable | User excludes Phone/Messages from picker; copy explains limits |
| Emergency exit | Local passphrase → **1h bypass** (same policy as Android `EmergencyBypassPolicy`) |
| Honest platform limits | **No** cross-app notification digest; onboarding states this explicitly |

### What we do not claim on iOS

- Holding, canceling, or replaying other apps’ notifications (no public API; see [Apple Forums — ANCS](https://developer.apple.com/forums/thread/758584)).
- Forcing Focus/DND on programmatically ([no Focus toggle API](https://stackoverflow.com/questions/69786874/ios-api-for-enable-disable-focus-mode)).
- Blocking more than Apple’s shield limits (~50 apps per `ManagedSettings` store).

### Optional nudge (v1.1)

- Local notification before window: “Turn on your Morning Focus” + link to Settings (user action required).

---

## 2. Architecture

```
ios/
├── project.yml                 # XcodeGen — single source of project structure
├── Config/
│   ├── Debug.xcconfig
│   ├── Release.xcconfig
│   └── MorningGuard-Shared.xcconfig
├── MorningGuardCore/           # Swift Package — domain + policies (testable)
├── MorningGuard/               # SwiftUI app target
├── MorningGuardMonitor/        # Device Activity Monitor extension
├── MorningGuardShieldConfig/   # Shield Configuration extension
├── MorningGuardShieldAction/   # Shield Action extension (open app)
├── scripts/
│   └── generate-xcodeproj.sh
└── README.md
```

### Module responsibilities

| Module | Responsibility |
|--------|----------------|
| **MorningGuardCore** | `MorningGateEvaluator`, `GoalCompletionVerifier`, `EmergencyBypassPolicy`, `UserSettings` models, `ShieldSessionPolicy` (when to apply/clear shields) |
| **MorningGuard** | SwiftUI, `AuthorizationCenter`, `FamilyActivityPicker`, goal UI, settings, Keychain passphrase, `DeviceActivityCenter` scheduling, app group persistence |
| **MorningGuardMonitor** | `intervalDidStart` / `intervalDidEnd` → read shared app-group state → apply/clear `ManagedSettingsStore` |
| **MorningGuardShieldConfig** | Branded shield copy + icon |
| **MorningGuardShieldAction** | Primary button → deep link `morningguard://goal` |

### Shared state (App Group)

`group.com.morningguard.shared`

| Key | Type | Purpose |
|-----|------|---------|
| `gateEvaluationJSON` | Data | Latest `GateEvaluation` for extensions |
| `shieldSelectionData` | Data | Encoded `FamilyActivitySelection` (PropertyList) |
| `shieldsActive` | Bool | Extension-safe mirror of “should shield now” |
| `morningWindowJSON` | Data | Window for monitor alignment |

Extensions **must not** depend on the main app process; they read app group and apply `ManagedSettingsStore(named:)` consistent with the main app.

### Gate + shield coupling

```text
LOCKED   → shields ON  (if authorized + selection non-empty)
UNLOCKED → shields OFF
INACTIVE → shields OFF (outside window; monitor may still fire — policy clears)
```

Goal completion and emergency bypass run in the app → persist state → call `ShieldController.refresh()` → clear `ManagedSettings`.

---

## 3. Feature parity matrix

| Android | iOS v1 |
|---------|--------|
| Notification listener + digest | App shields + in-app status (“N apps shielded”) |
| Bypass packages | Apps **not** included in `FamilyActivityPicker` selection |
| Morning window 1:00–10:00 default | Same defaults in `UserSettings` |
| Bible / focus / steps goals | Same thresholds (`60s` read, configurable focus/steps) |
| Health Connect steps | HealthKit `HKQuantityTypeIdentifierStepCount` (permission in goal flow) |
| Supabase auth | v1: **optional** — scaffold `AuthConfiguration` + README; ship local-first |
| Morning alarm | `UNUserNotificationCenter` repeating calendar trigger |
| Onboarding | Auth N/A → Family Controls auth → picker → goal → window |

---

## 4. Entitlements & App Store

| Entitlement | Targets |
|-------------|---------|
| `com.apple.developer.family-controls` | App + all three extensions |
| App Groups | App + Monitor (minimum); all extensions if they read state |
| HealthKit | App only (step goal) |

**Distribution:** Request [Family Controls distribution](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement) before TestFlight. Account Holder submits; allow 1–4 weeks.

**Review narrative:** Personal morning wellbeing; user chooses apps to shield; individual authorization (iOS 16+); no child surveillance.

**Privacy:** `docs/ios/PRIVACY-SCREEN-TIME.md` — data stays on device; opaque tokens; no app list uploaded.

---

## 5. Build & CI

### Local

1. Install Xcode 15+ and XcodeGen: `brew install xcodegen`
2. `./ios/scripts/generate-xcodeproj.sh`
3. Open `ios/MorningGuard.xcodeproj`
4. Set Team + enable Family Controls capability (development works before distribution approval)
5. Run on **physical device** (Screen Time APIs are limited on Simulator)

### GitHub Actions (`.github/workflows/ios.yml`)

- `runs-on: macos-14`
- `brew install xcodegen`
- Generate project, `xcodebuild -scheme MorningGuard -destination 'platform=iOS Simulator,name=iPhone 16' build`
- `swift test` in `MorningGuardCore` package path

---

## 6. Security notes (aligned with Android audit)

- Passphrase: PBKDF2 or CryptoKit SHA256 with random salt in Keychain (match Android roadmap for KDF).
- Rate limit: `EmergencyBypassPolicy` lockout after 5 failures / 15 minutes.
- No notification bodies stored on iOS v1.

---

## 7. Implementation checklist

### MorningGuardCore
- [x] Port gate evaluator + goal verifier + emergency policy
- [x] Unit tests (XCTest) mirroring Kotlin test cases
- [x] `ShieldSessionPolicy`

### App
- [x] SwiftUI shell: Home, Goal, Settings, Onboarding, Emergency
- [x] `ShieldController` + `DeviceActivitySchedule`
- [x] `SettingsStore` (UserDefaults + app group)
- [x] Bible fetch client (URLSession → bible-api.com / proxy from Info.plist)
- [x] HealthKit step reader

### Extensions
- [x] Device Activity Monitor
- [x] Shield Configuration + Action

### Repo
- [x] `ios/README.md`
- [x] `docs/ios/PRIVACY-SCREEN-TIME.md`
- [x] CI workflow
- [x] Root README link to iOS

---

## 8. Open questions (post-v1)

- [ ] Supabase Swift SDK + magic link parity with Android
- [ ] KMP extract `:core:domain` for single source of truth
- [ ] Widget / Live Activity for gate status
- [ ] `DeviceActivityReport` for weekly stats

---

## References

- [Screen Time API](https://developer.apple.com/documentation/ScreenTimeAPIDocumentation)
- [Configuring Family Controls](https://developer.apple.com/documentation/xcode/configuring-family-controls)
- [ManagedSettings blockedApplications](https://developer.apple.com/documentation/managedsettings/applicationsettings/blockedapplications-swift.property)
- [WWDC22 — Screen Time for individuals](https://developer.apple.com/videos/play/wwdc2022/110336/)
