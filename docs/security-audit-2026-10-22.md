# Morning Guard — security audit

Date: 22 October 2026. Baseline: [security-audit.md](security-audit.md) (17 October) and the patches that landed from [security-fixes.md](security-fixes.md). Scope: this tree, Android and iOS. No hosted Supabase, Cloudflare, Sentry, or App Store Connect project was re-checked. No code was changed for this audit.

## 1. Release verdict

**Android: not the October blocker set.** Calls are hard-exempt, the digest shade no longer repeats a message body, auth defaults to PKCE, account deletion and sign-out wipe exist, `targetSdk` is 36, CI builds a signed App Bundle instead of a public APK, the Health Connect privacy activity exists, the session is Keystore-encrypted, and the emergency passphrase is PBKDF2 with a lockout.

Three residuals should land before a public Play build that turns on accounts and the Bible proxy:

- SMS and messaging packages are still cancelled during a locked window.
- The app still imports an `access_token` fragment if one arrives on `morningguard://auth` or on any URL of `morning-guard.codingagents.cc`.
- `BIBLE_PROXY_TOKEN` is still compiled into the client, and `API_BIBLE_LICENSED_ENABLED` is already on. NIV, NKJV, and NLT are live behind a string anyone can read out of the app. Guard that path with Play Integrity on Android and App Attest on iOS. Leave public-domain translations on the proxy open, with the IP rate limit. iOS does not call the worker yet; the parity work should send an App Attest assertion only when it requests a licensed translation.

**iOS: not store-ready.** Screen Time is the right API (individual Family Controls, opaque tokens, no notification access). The monitor does not enforce the gate on its own: it trusts a boolean the app last wrote, and it clears shields when the scheduled window ends even if a peace session should stay locked. The passphrase hash is stored in the Keychain and then copied into UserDefaults and the app group, which is what iOS puts in backups. There is no privacy manifest, no in-app privacy-policy link, and the shield button opens the app through private API on a custom URL scheme.

## 2. October findings

| ID | 17 Oct | 22 Oct | What changed |
|---|---|---|---|
| F1 | FAIL | **PARTIAL** | `call` and `alarm`, plus the three dialer packages, are hard-exempt. The digest notification is a count and app name with `VISIBILITY_SECRET`. Messaging packages from the fix plan were not added. Title and body are still plaintext in Room until dismiss. |
| F2 | FAIL | **PARTIAL** | `flowType = PKCE`. `AuthCallbackUri` requires host `auth`, not a prefix. The landing page appends `location.search` only. `hasAuthPayload` still returns true for a fragment containing `access_token=`, and `SupabaseAuthBackend.handleDeepLink` then calls `handleDeeplinks`. The verified App Link is the whole host, with no path. |
| F3 | FAIL | **CLOSED in tree** | Onboarding disclosure checkbox, `PrivacyPolicyActivity`, `PRIVACY_POLICY_URL` required for `bundleRelease`. Hosted policy and Play Data safety were not re-checked. |
| F4 | FAIL | **CLOSED in tree** | `DELETE /auth/v1/user` with the user JWT, local wipe on sign-out and delete, `SyncOwnership` blocks a different bound user, `morning_day_records` delete-own migration exists. Production RLS was not re-checked. First sign-in (`boundUserId == null`) still uploads existing local highlights. |
| F5 | FAIL | **PARTIAL** | `API_BIBLE_KEY` is worker-only. Worker returns 503 when `BIBLE_PROXY_TOKEN` is unset, 401 on mismatch, and rate-limits per IP. The operator reports `API_BIBLE_LICENSED_ENABLED` is already on, so NIV/NKJV/NLT are served. The same proxy token is still a `BuildConfig` field and is written into CI `local.properties`. |
| F6 | FAIL | **CLOSED on Android** | PBKDF2-SHA256, 120000 iterations, five failures, 15-minute lockout, `KeystoreSessionManager`. `security-crypto` is unused. iOS has the same KDF but also copies the hash out of the Keychain (I2). |
| F7 | FAIL | **CLOSED in tree** | Rationale activity and `ViewPermissionUsageActivity` alias. Play health form was not re-checked. |
| F8 | FAIL | **CLOSED in tree** | `targetSdk` 36. `release.yml` runs `bundleRelease` and uploads an Actions artifact, not a GitHub Release APK. |
| F9 | PARTIAL | **CLOSED in tree** | `<queries>` is `MAIN` / `LAUNCHER`. Release analytics is `NoOpAnalyticsTracker` unless `AppDebugFlags.verboseDiagnostics`. |
| F10 | PARTIAL | **PARTIAL** | OkHttp `BASIC` logging is behind `@Named("httpLogging")`. `proguard-rules.pro` still has `-keep class com.morningguard.** { *; }`. |
| Digest screenshots | PARTIAL | **CLOSED** | `FLAG_SECURE` while `DigestScreen` is shown. |
| Magic-link cooldown | PARTIAL | **CLOSED** | `MagicLinkRateLimitStore` and `AuthViewModelTest`. |
| Account copy | FAIL | **CLOSED in tree** | Auth screen no longer says settings sync. Delete account is on the signed-in card. |

Also landed since 17 October, and in good shape: optional highlight-note AES-GCM (`mgenc1:`) before sync when a note key is unlocked; encrypted local backup; screenshot mode is a separate `applicationIdSuffix` and is `false` on the release build type; iOS reads HealthKit step count only and does not request write or background delivery; iOS Family Controls uses `.individual`.

## 3. Residual and new findings

| ID | Severity | Surface | Title | Evidence | Impact | Fix |
|---|---|---|---|---|---|---|
| R1 | High | Android safety | Messaging apps are still cancelled | `NotificationPolicy.neverInterceptPackages` is dialers only. `MorningNotificationListenerService` still persists title/text and `cancelNotification`. Default bypass is Spotify. | A locked window removes SMS, RCS, and one-time codes from Google Messages and Samsung Messages. The shade no longer repeats the body. The text stays in `morning_guard.db` until dismiss. | Hard-exempt the messaging packages from the October plan. Do not exempt every `msg` category; chat apps are the product. |
| R2 | High | Android auth | Implicit session import is still accepted | `AuthCallbackUri.hasAuthPayload` treats a fragment containing `access_token=` as a callback. `SupabaseAuthBackend.handleDeepLink` calls `client.handleDeeplinks(intent)` when there is no `code`. Manifest App Link is `https://morning-guard.codingagents.cc` with `autoVerify` and no path. | `https://morning-guard.codingagents.cc/#access_token=…&refresh_token=…` opens this app and imports the session. Local highlights can then be pushed as that user. A PKCE `code` without this device’s verifier does not have the same effect. | Accept only a `code` query parameter. Do not call `handleDeeplinks` for a fragment. Keep the landing page on `search` only, and do not treat `access_token` in the query as success. |
| R3 | High | Secrets | Licensed chapters are gated by a token in the app | `app/build.gradle.kts` `BIBLE_PROXY_TOKEN`. `release.yml` writes it into `local.properties`. Worker `proxyAuthFailureResponse` requires `X-Morning-Guard-Token`. Rate limit is 60/minute/IP. Operator: `API_BIBLE_LICENSED_ENABLED` is already on. iOS `BibleClient` still calls `bible-api.com` and does not attest. | One extracted string lets any client spend the API.Bible quota and read cached NIV, NKJV, and NLT. | Delete the shared token. Require a one-time Play Integrity token (package `com.morningguard.app`, Play signing cert, `PLAY_RECOGNIZED`, `MEETS_DEVICE_INTEGRITY`) or an App Attest assertion (bundle `com.morningguard.app`, increasing sign count) only for NIV, NKJV, and NLT. Public-domain translations on `/v1/chapter` stay unauthenticated, behind the IP rate limit, so sideloaded debug builds and the Simulator can keep using the proxy. Keep licensed translations on. During rollout, accept the old token for licensed requests until store builds send attestation, then reject it. |
| I1 | High | iOS shield | Monitor does not enforce the gate | `DeviceActivityMonitorExtension` applies shields only when app-group `shieldsActive` is true. `shouldClearShieldsOnIntervalEnd` returns true always. `intervalDidEnd` calls `clearAllSettings` and does not re-read the gate. Peace sessions set `LOCKED` outside the morning window (`MorningGateEvaluator`). | If the app is not running at window start, and the last mirrored flag is false, shields never go on. If a peace session outlives the schedule, interval end drops shields and nothing puts them back until the app opens. | The extension should run `MorningGateEvaluator` on mirrored window, progress, bypass expiry, and peace expiry. Clear only when that evaluation is not `LOCKED`. |
| I2 | Medium | iOS secrets | Passphrase hash leaves the Keychain | `PassphraseStore` uses `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. `SettingsStore.setEmergencyPassphraseHash` and `persistGoalProgress` also write that string into standard UserDefaults and `group.com.morningguard.shared` (`goalProgressJSON` and `emergencyPassphraseHash`). | iOS backups include the app container and the app group. The Keychain item does not. An 8-character passphrase can be attacked offline against the copied PBKDF2 string. Lockout counters in the same JSON can be reset from that backup. | Store the hash only in the Keychain. Extensions do not need it. |
| I3 | High for review | iOS distribution | Private API and a shared URL scheme | `ShieldActionExtension` calls `UIApplication.value(forKey: "sharedApplication")` and opens `morningguard://goal`. `Info.plist` registers the `morningguard` scheme with no host. No associated domains. | App Review rejects that private call. Another app can register the same scheme, so the shield button may open the wrong app. The URL carries no token. | Remove the private call. The shield copy should tell the user to open Morning Guard. Do not put auth tokens on this scheme. |
| I4 | High for review | iOS privacy | No privacy manifest or in-app policy | No `PrivacyInfo.xcprivacy`. UserDefaults is a required-reason API. Settings and onboarding do not link a privacy policy. Usage strings for Family Controls, HealthKit, and notifications exist and match the code. | App Store submission warns or rejects for a missing required-reason declaration. Health and Screen Time listings need the same policy URL as Android. | Add the manifest (`CA92.1` for UserDefaults). Link `PRIVACY_POLICY_URL` from Settings. Set `ITSAppUsesNonExemptEncryption` to false. |
| I5 | Medium | iOS control | “Clear shields now” survives a force-quit | `SettingsView` calls `revokeShieldsImmediately`, which clears Managed Settings and sets `shieldsActive` false. The 30-second refresh reapplies only while the process lives. The monitor will not reapply while the flag is false. | During a locked gate, one tap plus a force-quit leaves selected apps unshielded for the rest of the window. The emergency passphrase is not required. | While the gate is locked, require the passphrase, or remove the button and point at Settings → Screen Time. |
| I6 | Low | iOS crypto | KDF failure can store an empty digest | `PassphraseStore.pbkdf2SHA256` returns empty `Data` when `CCKeyDerivationPBKDF` fails. `SecRandomCopyBytes`’s status is ignored. | A stored hash of `pbkdf2-sha256$120000$<salt>$` verifies any passphrase that also fails the KDF. Unlikely on device. A failed RNG stores a zero salt. | Fail the save if either call fails. Do not persist an empty digest. |
| R4 | Low | Android privacy | Sentry has no scrubber | `AppInitializer` sets `tracesSampleRate` 0.1 in production and does not set `beforeSend`. `SentryErrorReporter` sends `SyncLog.messageFor(error)` as an extra, up to 2000 characters. | A sync exception message can include an email or a server body. Note text is not intentionally attached. | `sendDefaultPii = false`, a `beforeSend` that drops `Authorization`, email, and `note`, and truncate breadcrumb messages. |
| R5 | Low | Android privacy | Notes sync in plaintext unless encryption is on | `NoteEncryptionRepository.encryptForStorage` returns the plaintext when `dek` is null. `UserDataSyncService` always pushes `excerpt` as stored and `note` through that function. | Signed-in users who never set a note password upload highlight notes in the clear. Excerpts are scripture selections. | Sync `note` only when encryption is on, or keep notes local until it is. Say that on the account screen. |

**Accepted, not reopened.** The morning gate is still only on the device. First sign-in uploads local highlights when `boundUserId` is null; a different bound user does not. Room notification bodies stay until dismiss, with backup off and the shade redacted. Google sign-in remains in `AuthViewModel` and is not on `AuthScreen`. `allowBackup="false"` without `dataExtractionRules`. Certificate pinning stays out of scope. Debug iOS builds skip Screen Time authorization; Release on a device does not (`MGPlatform.relaxesShieldOnboarding`).

**iOS auth.** There is no Supabase client on iOS. Do not add magic-link tokens to `morningguard://`.

## 4. Evidence gaps

- Hosted Supabase: RLS, redirect allow-list, email rate limits, and whether `DELETE /auth/v1/user` is enabled. Repo migrations are not production proof.
- Cloudflare: this audit did not read the worker config. The operator reports `API_BIBLE_LICENSED_ENABLED` is already on, so KV may already hold NIV, NKJV, and NLT chapters.
- Play Integrity: Cloud project linked to the Play app, and the Play app-signing certificate SHA-256 that the worker will pin.
- App Attest: the capability on the `com.morningguard.app` App ID, and the Apple team ID the worker will pin.
- Sentry project settings for PII and attachments.
- Play Console Data safety, exact-alarm, full-screen intent, health, and the privacy URL.
- Apple Family Controls entitlement approval for the distribution profile. The plist key is not approval.
- On-device check that a Messages notification is cancelled, and that a peace session stays shielded after the Device Activity interval ends.

## 5. Top actions

1. Hard-exempt SMS packages. Confirm on a device that Messages stays in the shade and a chat app does not.
2. Reject auth callbacks that are not a PKCE `code`.
3. Guard NIV, NKJV, and NLT with Play Integrity and App Attest. Leave public-domain proxy requests open under the rate limit. Accept the old token for licensed requests only until the store builds send attestation.
4. Make the iOS monitor compute the gate. Keep the passphrase hash in the Keychain only.
5. Add the iOS privacy manifest and an in-app policy link. Remove the private shield open.
6. Fill Play and App Store privacy forms from this list. Family Controls still needs Apple’s entitlement approval before TestFlight of a build that shields apps.
