# Morning Guard — security audit

Date: 26 October 2026. Baseline: [security-audit-2026-10-22.md](security-audit-2026-10-22.md) and fixes landed since. Scope: this tree, Android and iOS. No hosted Supabase, Cloudflare worker secrets, Play Console, App Store Connect, or Sentry project settings were re-checked. No application code was changed for this audit.

## 1. Release verdict

**Android: suitable for a signed Play upload** from a client-security standpoint, assuming production backend matches repo migrations and the Bible worker has disabled the legacy proxy token after store builds ship attestation.

October blockers R1 (messaging hard-exempt), R2 (PKCE-only callbacks), and R3 (no `BIBLE_PROXY_TOKEN` in the app; Play Integrity for licensed chapters) are **closed in tree**. F6 session and passphrase storage, digest redaction, account deletion, `targetSdk` 36, AAB CI, and highlight note sync policy remain in good shape.

Residuals before a broad public launch: confirm worker `BIBLE_PROXY_LEGACY_TOKEN_DISABLED`, trim Sentry sync error bodies, and accept that notification title/body stay in Room until dismiss (documented).

**iOS: much closer to TestFlight**, but not fully “hands off” for App Review. Screen Time enforcement in the monitor extension now evaluates `MorningGateEvaluator` from app-group state (I1 closed). Privacy manifest, in-app policy URL, encryption export flag, shield action without private API, passphrase hash in Keychain only, App Attest for licensed Bible proxy, and Supabase magic-link sync are present.

Remaining product risks: custom-scheme magic-link redirect (`morningguard://auth`) without Associated Domains (scheme collision / wrong app opened), emergency lockout counters mirrored in backed-up `UserDefaults`/app group, and Apple Family Controls distribution entitlement approval (operational, not provable from git).

## 2. October findings — status

| ID | 22 Oct | 26 Oct | Notes |
|---|---|---|---|
| R1 | High open | **CLOSED** | `NotificationPolicy.neverInterceptPackages` includes Google/Samsung messaging and MMS packages. |
| R2 | High open | **CLOSED (Android)** | `AuthCallbackUri` requires `code` query; `SupabaseAuthBackend.handleDeepLink` calls `exchangeCodeForSession` only. Tests reject fragment/query `access_token`. Landing page auto-redirects only when `?code=` is present; `access_token` in query shows an error, not redirect. |
| R3 | High open | **CLOSED in app** | `app/build.gradle.kts` has `BIBLE_PROXY_BASE_URL` and `PLAY_CLOUD_PROJECT_NUMBER` only. `PlayIntegrityBibleProxyLicensedAuth` + worker `licensed-auth.ts` (Integrity / App Attest / session token). iOS `BibleProxyClient` uses App Attest on device. **Worker** may still accept legacy `BIBLE_PROXY_TOKEN` until cutover — operator action. |
| I1 | High open | **CLOSED** | `DeviceActivityMonitorExtension.refreshShields()` uses `SharedShieldStateReader` + `ShieldSessionPolicy`; interval end does not clear while `gateState == .locked`. |
| I2 | Medium open | **CLOSED** | `PassphraseStore` writes Keychain only; `GoalProgressStore` encode omits hash; `clearLegacyPassphraseHashFromSharedStorage()` on `SettingsStore` init strips legacy copies. |
| I3 | High (review) open | **CLOSED** | `ShieldActionExtension` uses `.defer` only; no `sharedApplication` / private URL open. |
| I4 | High (review) open | **CLOSED** | `PrivacyInfo.xcprivacy` (UserDefaults `CA92.1`, collected data types). `PRIVACY_POLICY_URL` in `Info.plist` and Settings. `ITSAppUsesNonExemptEncryption` false. |
| I5 | Medium open | **CLOSED** | “Clear shields” button hidden when `gateService.evaluation.state == .locked`. |
| I6 | Low open | **CLOSED** | `PassphraseStore` throws `cryptoFailure` on KDF/RNG failure; verify rejects empty derived key. |
| R4 | Low open | **PARTIAL** | `sendDefaultPii = false` and `beforeSend` strips `Authorization` / `apikey` on Android and iOS. `UserDataSyncService.recordSyncFailure` still attaches `SyncLog.messageFor(error)` (up to 500 chars of REST body) to Sentry extras. |
| R5 | Low open | **CLOSED** | `HighlightSyncNotePolicy` on both platforms; `NoteEncryptionRepository.encryptForStorage` fails closed when DEK locked; sync skips or preserves ciphertext. |
| F10 ProGuard | Partial | **IMPROVED** | Broad `-keep class com.morningguard.**` removed; targeted keeps for Hilt, Room, Supabase, Play Integrity, Sentry remain. |
| security-crypto | Unused dep | **CLOSED** | Not on `fullReleaseRuntimeClasspath`. |

**New since 22 Oct (in scope):** iOS **Supabase** auth and `UserDataSyncService` (magic link, account delete, highlight sync). Default **supabase-swift** `flowType` is PKCE (`Defaults.defaultFlowType = .pkce`), so `session(from:)` does not accept implicit `access_token` URLs unless misconfigured.

## 3. Residual and new findings

| ID | Severity | Surface | Title | Evidence | Impact | Fix |
|---|---|---|---|---|---|---|
| O1 | High (ops) | Bible worker | Legacy proxy token may still work | `workers/bible-cache/src/licensed-auth.ts` `legacyTokenMatches` until `BIBLE_PROXY_LEGACY_TOKEN_DISABLED`. README documents cutover. | Old builds or anyone with the worker secret can still read licensed chapters until cutover. | Set `BIBLE_PROXY_LEGACY_TOKEN_DISABLED=true` after Play/App Store builds use attestation; verify with `curl` and `strings` on release AAB/IPA. |
| O2 | Medium | Android privacy | Sentry may receive sync API bodies | `UserDataSyncService.recordSyncFailure` → `extras["detail"]` = `SyncLog.messageFor(error)` (`RestException` body `.take(500)`). | Unlikely to include highlight notes; could include email or auth error text from PostgREST. | Send phase + status code only, or hash/truncate and strip email-like substrings in `beforeSend`. |
| O3 | Medium | Android privacy | Notification plaintext in Room | `MorningNotificationListenerService` still persists title/text and `cancelNotification` during lock. Digest uses `VISIBILITY_SECRET`; messaging packages exempt. | Backup off (`allowBackup="false"`); shade redacted. Forensic access to DB still sees bodies until dismiss. | Accepted for threat model; document in Data safety / privacy policy. |
| O4 | Medium | iOS auth | Magic link uses custom URL scheme only | `SupabaseConfig.magicLinkRedirect = "morningguard://auth"`. No Associated Domains in project. `CFBundleURLSchemes` = `morningguard`. | Another app could register the same scheme; user may complete sign-in in the wrong app. PKCE `code` is one-time but phishing/confusion risk remains. | Prefer Universal Link to `morning-guard.codingagents.cc` with path reserved for auth; validate host + `code` before `session(from:)`. |
| O5 | Medium | iOS control | Lockout state in backup-eligible storage | `GoalProgressStore.failedAttempts` / `lockedUntilEpochMs` in standard + app group `UserDefaults` (`persistGoalProgress`). Passphrase hash is Keychain-only. | Restoring an older backup can reset lockout counters (not the hash). | Move lockout fields to Keychain or NSUbiquitousKeyValueStore excluded from backup; or document as best-effort local gate. |
| O6 | Low | iOS monitor | Extension assumes Family Controls authorized | `DeviceActivityMonitorExtension` passes `isAuthorized: true` into `ShieldSessionPolicy.shouldApplyShields`. | If authorization revoked while gate locked, behavior depends on Managed Settings APIs; unlikely data leak. | Mirror authorization flag from app group if extension must distinguish. |
| O7 | Low | Both | Dependency / CVE scan not automated | No Gradle lockfile or CI OSV scan noted in repo. | Unknown vulnerable transitive deps. | Add `dependencyCheck` or GitHub Dependabot + periodic `./gradlew dependencyUpdates` / `xcodebuild` SPM audit. |
| O8 | Low | iOS auth | Deep link matcher is broad | `AppState.handleDeepLink`: `url.path.contains("auth")` in addition to host `auth`. | Odd URLs on the custom scheme might trigger auth handling (PKCE still required by SDK). | Match host `auth` only, same as Android `AuthCallbackUri`. |

**Accepted, not reopened.** Local morning gate; first sign-in may upload local highlights when `boundUserId` is null; certificate pinning out of scope; demo/screenshot flavors; debug Screen Time relaxation (`MGPlatform` / `MGDebugFlags`).

## 4. Evidence gaps

- Hosted Supabase: RLS, redirect allow-list, rate limits, `DELETE /auth/v1/user` enabled in production.
- Cloudflare worker: secrets set, licensed cache populated, legacy token disabled, Play Integrity project + app-signing cert SHA-256, App Attest team ID.
- Play Console Data safety, health, notification listener, and privacy URL match deployed policy.
- App Store: Family Controls entitlement on distribution profile; privacy nutrition labels match `PrivacyInfo.xcprivacy`.
- Sentry: project-level PII defaults and inbound filters.
- On-device: Messages notification survives locked gate; peace session stays shielded after Device Activity interval ends; licensed NIV on release Android (Integrity) and release iOS device (App Attest).

## 5. Top actions

1. **Operator:** Disable Bible proxy legacy token after attestation builds are live; run `strings` on release AAB/IPA for `BIBLE_PROXY_TOKEN` / `API_BIBLE_KEY`.
2. **Android:** Reduce Sentry sync failure extras to non-sensitive metadata.
3. **iOS:** Tighten `handleDeepLink` to host `auth` only; plan Universal Links for magic link return.
4. **iOS:** Decide whether lockout counters must survive backup restore; if yes, document; if no, move off `UserDefaults`.
5. **Both:** Re-run Play / App Store privacy forms against this doc and [pii-and-encryption.md](pii-and-encryption.md).
