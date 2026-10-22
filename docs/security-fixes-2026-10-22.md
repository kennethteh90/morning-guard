# Morning Guard — security fix plan

Date: 22 October 2026. Companion to [security-audit-2026-10-22.md](security-audit-2026-10-22.md). The 17 October plan is [security-fixes.md](security-fixes.md). Items below are what that plan did not close, plus iOS.

These diffs are the intended patch. They are not applied.

Must-fix Android items are what still mis-handles messages or account sessions in a build you upload. The Bible worker already serves NIV, NKJV, and NLT (`API_BIBLE_LICENSED_ENABLED` is on) and must stop trusting a shared token for that path. Public-domain chapters on the same proxy stay open. iOS must-fix items are what would make Screen Time fail open or fail App Review. The iOS proxy client belongs to the parity work; it must send App Attest for licensed translations, not a copied `BIBLE_PROXY_TOKEN`. Sentry scrubbing, plaintext notes, and the ProGuard keep can follow in the next release.

## Must fix before the next Play bundle

### 1. Hard-exempt messaging apps (R1)

Files: `core/domain/src/main/kotlin/com/morningguard/domain/notification/NotificationPolicy.kt`, `core/domain/src/test/kotlin/com/morningguard/domain/notification/NotificationPolicyTest.kt`

Calls and alarms are already exempt. Add the SMS packages from the October plan. Do not exempt category `msg`: WhatsApp and the other chat apps are supposed to be held unless the user bypasses them.

```diff
     val neverInterceptPackages: Set<String> = setOf(
         "com.android.dialer",
         "com.google.android.dialer",
         "com.samsung.android.dialer",
         "com.android.server.telecom",
+        "com.google.android.apps.messaging",
+        "com.android.mms",
+        "com.android.messaging",
+        "com.samsung.android.messaging",
     )
```

Tests: a locked gate still intercepts `com.whatsapp` and `com.social`. It does not intercept `com.google.android.apps.messaging` or `com.samsung.android.messaging`, including when that package is absent from the user’s bypass list.

Verify: during a locked window, an SMS stays in the shade, a WhatsApp notification is held, and the Morning Guard shade text is still only the count and app name.

### 2. Accept only a PKCE code (R2)

Files: `app/src/main/kotlin/com/morningguard/app/auth/AuthCallbackUri.kt`, `app/src/main/kotlin/com/morningguard/app/auth/SupabaseAuthBackend.kt`, `app/src/test/.../AuthCallbackUriTest.kt`, `site/auth-landing/index.html`

`isAuthCallback` is true only when the query contains a non-empty `code`. A fragment or query `access_token` is not a callback.

```kotlin
fun isAuthCallback(uri: Uri?): Boolean {
    if (uri == null) return false
    val code = uri.getQueryParameter("code")
    if (code.isNullOrBlank()) return false
    val custom = uri.scheme == "morningguard" && uri.host == "auth"
    val appLink = uri.scheme == "https" && uri.host == AUTH_LANDING_HOST
    return custom || appLink
}
```

`handleDeepLink` exchanges that code and returns. Delete the `handleDeeplinks` branch.

Landing page: keep `morningguard://auth` + `location.search`. If `search` contains `access_token=`, show the expired-link message and do not redirect.

Tests: host `auth` with `?code=abc` matches. `morningguard://auth#access_token=secret`, `morningguard://auth?access_token=secret`, `morningguard://auth.evil?code=abc`, and a missing URI do not. `https://morning-guard.codingagents.cc/#access_token=secret` does not.

Verify: a magic link’s app URL contains `code` and no `access_token`. Opening `https://morning-guard.codingagents.cc/#access_token=secret&refresh_token=secret` does not create a session.

### 3. Play Integrity and App Attest on the Bible proxy (R3)

Files: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/kotlin/com/morningguard/app/di/ApiBibleModule.kt`, `core/data/.../BibleProxyAuthInterceptor.kt`, `core/data/.../BibleProxyConfig.kt`, new Android integrity caller under `app/src/main/kotlin/com/morningguard/app/bible/`, `.github/workflows/release.yml`, `.github/workflows/ci.yml`, `workers/bible-cache/src/auth.ts`, `workers/bible-cache/src/index.ts`, worker tests. iOS, in the parity work: `ios/MorningGuard/Services/BibleClient.swift` and a new App Attest helper. Do not add `BIBLE_PROXY_TOKEN` to an xcconfig.

`API_BIBLE_KEY` stays a worker secret. Delete `BIBLE_PROXY_TOKEN` from `defaultConfig`, from both workflows’ `local.properties` blocks, and from the request header. `PLAY_CLOUD_PROJECT_NUMBER` may be a `BuildConfig` field. It is not a secret. The Play app-signing certificate SHA-256 and the Apple team ID are worker config. The Google service account used to decode integrity tokens is a worker secret and must not be in the app.

`API_BIBLE_LICENSED_ENABLED` is already on. This change does not turn licensed text off. It replaces who is allowed to request it.

Attestation applies only to `translation=niv`, `nkjv`, and `nlt`. Those are the calls that spend `API_BIBLE_KEY`. Public-domain translations (`web`, `kjv`, and the other `open_bible` ids) stay available on `GET /v1/chapter` with no Play Integrity token, no App Attest assertion, and no `X-Morning-Guard-Token`. The IP rate limit applies to both. A sideloaded debug build and the iOS Simulator can keep calling the proxy for WEB and KJV.

Licensed requests require a fresh attestation. Existing Play installs only know the shared token, so until those store builds send attestation the worker also accepts `X-Morning-Guard-Token` on licensed requests. Then delete that check. Shipping attestation-only first would take NIV, NKJV, and NLT away from current users. The free path does not use that header at all.

Challenge, licensed requests only:

- `POST /v1/challenge` writes a random nonce to KV with a two-minute TTL and returns it once.
- The licensed chapter request presents that nonce. The worker deletes it on first use. A second use is 401.

Android, Play Integrity, licensed requests only:

- `IntegrityManager.requestIntegrityToken` with that nonce.
- Worker calls Google’s `decodeIntegrityToken`.
- Store and internal-track installs: accept when `requestPackageName` is `com.morningguard.app`, `appRecognitionVerdict` is `PLAY_RECOGNIZED`, `certificateSha256Digest` contains the Play app-signing cert, `deviceRecognitionVerdict` contains `MEETS_DEVICE_INTEGRITY`, and the nonce matches. Do not require `LICENSED`.
- A sideloaded debug build cannot meet that rule. It still loads WEB and KJV from the proxy. To load NIV from a desk phone, install an internal-track build, or sign the debug APK with a private debug keystore whose SHA-256 is listed on the worker and accept `UNRECOGNIZED` only for that digest. The usual Android debug keystore is public. Production must not allow it. There is no bypass header.

iOS, App Attest, when the parity client requests a licensed translation:

- On device, `DCAppAttestService` attests a key against a challenge. The worker verifies the attestation against Apple’s App Attest root, RP ID `com.morningguard.app`, and the team ID, then stores key id, public key, and sign count.
- Each licensed chapter request sends `generateAssertion` over the challenge. The worker checks the signature, the bundle id, and that the sign count increased.
- The Simulator cannot attest. It still calls the proxy for WEB and KJV. NIV on the Simulator is unavailable. An Xcode build on a device uses Apple’s development App Attest environment for NIV. TestFlight and the App Store use production. The worker accepts both environments for this team ID and bundle id.

Tests: a worker test double stands in for Google and Apple. `translation=web` with no attestation and no token is 200. `translation=niv` with neither is 401. With the legacy token still accepted, a matching `X-Morning-Guard-Token` is 200 for `niv` and a wrong one is 401. After the cutover flag, a reused nonce is 401 and a passing Android verdict or iOS assertion reaches the API.Bible upstream for `niv`. An Android test shows a WEB request sends neither header, and an NIV request sends the integrity token and does not send `X-Morning-Guard-Token`.

Verify: `strings` on the AAB finds no `BIBLE_PROXY_TOKEN` value. `curl` of `translation=web` with no headers returns a chapter, and a burst returns 429. `curl` of `translation=niv` with no attestation is 401. Before cutover, a current install still loads NIV with the old header. A Play internal-track build loads NIV with Play Integrity, and a copied integrity token does not. A sideloaded debug build and the Simulator load WEB from the proxy. After cutover, `curl` of `translation=niv` with only the old header is 401. A device build from Xcode loads NIV with a development App Attest assertion.

## Must fix before TestFlight

### 4. Extension evaluates the gate (I1)

Files: `ios/MorningGuardCore/Sources/MorningGuardCore/ShieldSessionPolicy.swift`, `ios/MorningGuardMonitor/SharedShieldSupport.swift`, `ios/MorningGuardMonitor/DeviceActivityMonitorExtension.swift`, `ios/MorningGuard/Services/SettingsStore.swift`, `ios/MorningGuardCore/Tests/.../ShieldSessionPolicyTests.swift`

Mirror the inputs `MorningGateEvaluator` already takes: morning window, goal progress, `emergencyBypassUntilEpochMs`, `peaceSessionUntilEpochMs`. Do not mirror the passphrase hash (item 5).

`intervalDidStart`, `intervalDidEnd`, and `eventDidReachThreshold` all call the same function: evaluate, apply when `LOCKED` and a selection exists, otherwise clear.

```swift
public static func shouldClearShields(gateState: GateState) -> Bool {
    gateState != .locked
}
```

Delete `shouldClearShieldsOnIntervalEnd`’s always-true behavior. `shieldsActive` can stay as a UI hint. The extension must not use it as the decision.

Tests: peace session still `LOCKED` after the morning interval ends, so shields stay. Goal complete or bypass active, so shields clear. Locked window with a selection applies even when the old boolean is false.

Verify: set a one-hour peace session, let the Device Activity interval end with the app swiped away, confirm the shield is still up. Open the app the next morning without having left it running; shields are up inside the window and down after the goal.

### 5. Keep the passphrase hash in the Keychain (I2, I6)

Files: `ios/MorningGuard/Services/PassphraseStore.swift`, `ios/MorningGuard/Services/SettingsStore.swift`, `ios/MorningGuard/AppState.swift`, `ios/MorningGuard/AppGroupKeys.swift`

Stop writing `emergencyPassphraseHash` into UserDefaults and the app group. `GoalProgressStore` in the app group keeps progress, bypass expiry, peace expiry, and lockout counters. It does not keep the hash.

`attemptEmergencyBypass` reads the hash only from `PassphraseStore`. If the Keychain item is missing, the passphrase is not configured, even if an old JSON copy exists. On launch, if a legacy JSON hash is present, leave it unused and clear those keys.

`hash` throws if `SecRandomCopyBytes` or `CCKeyDerivationPBKDF` is not success. Never store a digest that is empty.

Verify: after saving a passphrase, the app-group plist has no `pbkdf2-sha256$` string. Five wrong attempts still lock for 15 minutes. The correct passphrase works after the lock.

### 6. Privacy manifest, policy link, no private API (I3, I4)

Files: new `ios/MorningGuard/PrivacyInfo.xcprivacy`, `ios/MorningGuard/Info.plist`, `ios/MorningGuard/UI/SettingsView.swift`, `ios/MorningGuardShieldAction/ShieldActionExtension.swift`, `ios/MorningGuardShieldConfig/ShieldConfigurationExtension.swift`

`PrivacyInfo.xcprivacy`: `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1`. Tracking false. No tracking domains.

`Info.plist`: `ITSAppUsesNonExemptEncryption` = false.

Settings: a row that opens the same privacy policy URL as Android (`PRIVACY_POLICY_URL` / the hosted page). Onboarding can keep the Screen Time explanation it already has.

Shield action: delete `openContainingApp` and the `sharedApplication` lookup. Primary button handler calls `completionHandler(.close)`. Shield subtitle says to open Morning Guard from the Home Screen. Leave the `morningguard` scheme for `handleDeepLink` of `goal` only. Do not add auth parameters to it.

Verify: the extension target no longer references `sharedApplication`. A Release archive’s privacy report lists UserDefaults. The Settings row opens the hosted policy.

### 7. No passphrase-free shield clear while locked (I5)

Files: `ios/MorningGuard/UI/SettingsView.swift`, `ios/MorningGuard/AppState.swift`

While `gateService.evaluation.state == .locked`, hide “Clear shields now”. The existing Screen Time sentence stays, so the user can still revoke access in Settings. Outside a locked gate, the button can remain.

Verify: during a locked window the button is gone and shields stay up after a force-quit. After the goal, the button clears shields.

## Should fix before public launch

### 8. Scrub Sentry (R4)

Files: `app/src/main/kotlin/com/morningguard/app/di/AppInitializer.kt`, `app/src/main/kotlin/com/morningguard/app/monitoring/SentryErrorReporter.kt`

```kotlin
options.isSendDefaultPii = false
options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
    event.request?.let { request ->
        request.headers?.remove("Authorization")
        request.headers?.remove("apikey")
    }
    event
}
```

Truncate breadcrumb messages the same way extras are truncated. Do not put email or note text in `SyncLog.messageFor`.

Verify: a failed sync in a release build does not show the user’s email or a highlight note in the Sentry event.

### 9. Do not upload plaintext notes (R5)

Files: `app/src/main/kotlin/com/morningguard/app/sync/UserDataSyncService.kt`, account-screen copy

When `noteCipher.isEncryptionEnabled()` is false, push highlights with an empty `note`. Local notes stay on device. Excerpts stay as they are (the selected verse). When encryption is on, keep the current `encryptForStorage` path.

Account copy: “Bible highlights sync. Notes sync only if you turn on note encryption in Settings. Notification text stays on this phone.”

Verify: sign in without setting a note password, create a note, and confirm `bible_highlights.note` is empty. Unlock encryption, edit the note, and confirm the row is `mgenc1:`.

### 10. Narrow the R8 keep

File: `app/proguard-rules.pro`

Do this after item 3 so `BIBLE_PROXY_TOKEN` is already gone from `BuildConfig`.

Remove `-keep class com.morningguard.** { *; }`. Leave the Hilt, kotlinx.serialization, and Retrofit rules already in that file. Keep the Play Integrity classes those rules already need if R8 strips them.

Verify: release Bible chapter, magic-link sign-in, digest, and account deletion still run. `strings` on the AAB does not show the proxy token.

## Can defer

| Item | Why it can wait |
|---|---|
| SQLCipher and a digest TTL | Shade body leak is closed. Bodies stay in app-private storage with backup off. Add a delete when the gate opens if you keep message text after unlock. |
| Explicit `dataExtractionRules` | `allowBackup="false"` already disables backup. |
| Certificate pinning | Platform TLS for Supabase and bible-api.com. |
| Dead Google sign-in | `AuthScreen` never calls `signInWithGoogle`. Remove it when you next touch auth. Do not mention Google on the store page. |
| Worker KV counter race | 60/minute stays the quota guard for open public-domain requests and for attested licensed requests. |
| iOS book-name allowlist | Constrain the book slug in the proxy client when the parity work adds it. |
| Family Controls entitlement | Apple has to approve the capability on the distribution profile. The plist key does not do that. |

## Suggested order

1. Items 1 and 2, then the Android half of item 3. The next Play bundle sends Play Integrity only for NIV, NKJV, and NLT, and does not contain the proxy token. Public-domain proxy calls stay unauthenticated. Deploy the worker so licensed requests accept attestation or the old token. Ship the App Attest check in that same deploy. The iOS client sends an assertion only for licensed translations. Remove the old token check only after current store installs have updated.
2. Items 4 and 5 together. The extension must not gain a copy of the hash while you are moving gate inputs into the app group.
3. Items 6 and 7 before the first TestFlight that requests Screen Time.
4. Items 8–10 in the release after that, before you send anyone the public listing.

## Finding coverage

| Audit result | Plan item |
|---|---|
| R1 messaging still cancelled | 1 (must, Play) |
| R2 implicit fragment still imported | 2 (must, Play) |
| R3 shared proxy token; expensive path needs Play Integrity and App Attest | 3 (must, Play + iOS proxy) |
| I1 monitor trusts `shieldsActive` and always clears | 4 (must, iOS) |
| I2 hash copied into UserDefaults and the app group | 5 (must, iOS) |
| I6 empty PBKDF2 digest | 5 (must, iOS) |
| I3 private API and custom scheme | 6 (must, iOS) |
| I4 no privacy manifest or policy link | 6 (must, iOS) |
| I5 clear-shields bypass | 7 (must, iOS) |
| R4 Sentry scrubber | 8 (should) |
| R5 plaintext note sync | 9 (should) |
| F10 ProGuard keep, still open | 10 (should, after 3) |
| F1 Room bodies, F6 Android, F3, F4, F7, F8, F9 | Closed in tree. Not in this plan. |
