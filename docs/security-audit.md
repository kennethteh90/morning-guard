# Morning Guard — pre-Play security and privacy audit

Date: 17 October 2026. Scope: this repository plus the public GitHub release asset `v1.0.0.6`. No code was changed for this audit.

## 1. Release verdict

**BLOCKER.** Morning Guard is a morning lock that removes other apps’ notifications until a local goal is finished. That design is allowed only with a narrow exemption list, a real disclosure, and a privacy policy. The current listener cancels everything except alarms, this app, and Spotify, stores title and body in plaintext Room, and for a single held alert reposts the full body on the shade. Account sign-in uses supabase-kt’s default **implicit** flow over the hijackable `morningguard://auth` scheme, then uploads local highlights and history to whatever session the link imported. There is no privacy policy and no account-deletion path. `targetSdk` is 35; as of 31 August 2026 Google Play requires API 36 for a new app. The public GitHub release `v1.0.0.6` is an unsigned APK, not an App Bundle.

## Phase 1 — Security model

**Purpose.** Hold non-alarm notifications in one digest during a morning window until the user finishes a Bible chapter, a timed focus session, or a step goal. Optional local emergency passphrase, per-app bypass, and optional exact alarm. Supabase is optional and, in the UI, only syncs streak history and Bible highlights.

### Entry points

| Component | Export | Notes |
|---|---|---|
| `MainActivity` | `exported=true` | Launcher, `morningguard://auth`, and `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (`app/src/main/AndroidManifest.xml` lines 37–54) |
| `MorningNotificationListenerService` | `exported=true` | Guarded by `BIND_NOTIFICATION_LISTENER_SERVICE` (lines 56–69). Correct pattern. |
| `MorningAlarmReceiver` | `exported=false` | Explicit immutable `PendingIntent` only |
| `BootCompletedReceiver` | `exported=false` | `BOOT_COMPLETED` only |
| Content providers, WebView, FCM, App Links | none | No `https` intent-filter |

The auth bridge is `site/auth-landing/index.html`. It copies `location.search` and `location.hash` onto `morningguard://auth` and calls `location.replace`.

### Sensitive data

- Notification title, big text, and inbox lines → Room table `captured_notifications` (`NotificationExtrasReader.kt` lines 8–20, `CapturedNotificationEntity.kt` lines 8–14). Not uploaded.
- Emergency passphrase → one-shot SHA-256 in DataStore file `goal_progress.json` (`PassphraseHasher.kt` lines 18–21, `SettingsRepository.kt` lines 34–45).
- Supabase session → supabase-kt `SettingsSessionManager`, key suffix `-session`, via russhwolf `Settings()` (unencrypted SharedPreferences). Confirmed in `auth-kt` 3.0.3 bytecode. Default `flowType` is `IMPLICIT`.
- Bible highlight excerpts and morning outcomes → Supabase tables `bible_highlights` and `morning_day_records` when signed in.
- Step totals stay on device (`HealthConnectStepReader.kt`). They are not in `UserDataSyncService`.

**Trust boundaries.** Device storage is trusted for the gate (entirely client-side). The network is Supabase, `https://bible-api.com/`, `https://api.scripture.api.bible/`, and the optional Cloudflare worker. Deep links and the auth landing page are untrusted. Notification extras are data from other apps, read under a powerful system role.

### Permissions

| Permission | Needed for a visible feature? |
|---|---|
| `POST_NOTIFICATIONS` | Yes, digest and alarm. Requested in `MainActivity.onCreate` before onboarding explains it. |
| `RECEIVE_BOOT_COMPLETED` | Yes, reschedule the optional alarm. |
| `SCHEDULE_EXACT_ALARM` | Yes, optional wake alarm. If the user has not allowed exact alarms, `MorningAlarmScheduler.schedule` returns without telling them (lines 34–36). Play Console still requires the exact-alarm declaration. |
| `ACTIVITY_RECOGNITION` | Yes, hardware step counter. Requested when the user picks the step goal. |
| `health.READ_STEPS` | Yes, Health Connect fallback. Manifest is incomplete (see F7). |
| `INTERNET` | Yes, Bible text, auth, Sentry. |
| Not requested | Location, camera, mic, contacts, SMS, storage, advertising ID, `QUERY_ALL_PACKAGES`. |

Package visibility for the bypass picker is limited to `MAIN` / `LAUNCHER` (`AndroidManifest.xml` `<queries>`), plus packages already seen via the notification listener (`BypassAppsViewModel.kt`).

### Third parties

| SDK | What it can receive | Endpoints in code |
|---|---|---|
| supabase-kt 3.0.3 auth + postgrest | Email, session tokens, highlight text, daily outcome | `BuildConfig.SUPABASE_URL` |
| Sentry Android 7.19.0 | Crash data if `SENTRY_DSN` is non-empty. No `beforeSend` scrubber (`AppInitializer.kt` lines 29–34) | DSN host |
| API.Bible via OkHttp | Chapter requests, `api-key` header | `https://api.scripture.api.bible/v1/` |
| bible-api.com | Chapter reference | `https://bible-api.com/` |
| Cloudflare worker `morning-guard-bible-cache` | Chapter query, optional `X-Morning-Guard-Token` | `BIBLE_PROXY_BASE_URL` |
| Credential Manager / Google Identity | Google ID token, only if `signInWithGoogle` is called. **No UI calls it** (`AuthScreen.kt` has magic link only) | Google |
| Health Connect 1.1.0-alpha10 | Step aggregates, on device | System |
| No ads, Firebase, or billing SDK | | |

`androidx.security:security-crypto:1.1.0-alpha06` is on the `:core:data` classpath and has **no call sites**.

## Phase 2 — Control results

### Platform

- Exported components: **PARTIAL**. Listener and receivers are correct. `MainActivity` must be exported, but the browsable custom scheme is the problem in F2.
- Pending intents: **PASS**. `FLAG_IMMUTABLE` in `DigestNotificationManager.kt` lines 46–50 and `MorningAlarmScheduler.kt` lines 22–26.
- Deep links: **FAIL**. F2.
- WebView: **N/A**. No WebView.
- Local storage: **FAIL** for notification bodies and the passphrase (F1, F6). Room queries are bound parameters (`CapturedNotificationDao.kt`).
- Keystore / EncryptedSharedPreferences: **FAIL**. Not used for the session or passphrase (F6).
- Backups: **PASS**. `android:allowBackup="false"` (`AndroidManifest.xml` line 25). No `dataExtractionRules` file; with `allowBackup=false`, Auto Backup is off.
- Clipboard: **PASS**. No clipboard reads or writes.
- Screenshots: **PARTIAL**. Digest text is shown with no `FLAG_SECURE`.
- Cleartext / pinning: **PASS** for the app. No `usesCleartextTraffic`, no network security config that permits cleartext, app URLs are HTTPS. Pinning is **N/A** for this threat model. The public `v1.0.0.6` APK also contains dependency strings `http://10.0.2.2:8969/stream` and `http://localhost:8969/stream`; nothing in this repo references them. **Needs verification** that no release path dials them. Default cleartext blocking should refuse them.
- Root/emulator detection: **N/A**. Not a financial or DRM app.

### Auth

- Token storage and implicit flow: **FAIL**. F2, F6.
- Cross-user API access: **PASS in repo migrations, unverified in production**. `supabase/migrations/20251017100000_morning_guard_user_data.sql` enables RLS, `FORCE ROW LEVEL SECURITY` is in the follow-up migration, policies require `auth.uid() = user_id`, and `anon` is revoked. The client `select()` has no user filter and depends entirely on those policies (`UserDataSyncService.kt` lines 70–74 and 125–128).
- Privileged actions: the morning gate is **only client-side**. That matches an offline self-control app; it is not an authorization bypass of someone else’s account.
- Password reset: **N/A**. UI is magic link only. `supabase/config.toml` password settings are local CLI defaults, not evidence of production.
- Account deletion and logout isolation: **FAIL**. F3, F4.

### Input

- SQL/path/shell/JS: **PASS**. No raw SQL, no `Runtime.exec`, no WebView, no dynamic dex. Bible book names are normalized before chapter fetch.
- Worker cache key: **PASS** as a filesystem issue. `bookKeySegment` only rewrites spaces; the key is a KV name, not a path (`workers/bible-cache/src/usfm.ts` lines 83–85).
- Notification payload: **FAIL** for over-collection and re-display (F1), not for intent injection. `StatusBarNotification.packageName` is the posting app, not attacker-controlled text.
- Push: **N/A**. No FCM.

### Network

- TLS: **PASS** for app endpoints, with the dependency-string caveat above.
- Secrets in URLs: **PASS** for API keys (they are headers). **FAIL** for session tokens in the implicit redirect fragment (F2).
- `HttpLoggingInterceptor.Level.BASIC` is installed on every OkHttp client, including release (`DataModule.kt` lines 55–65). BASIC logs the URL line, not headers.
- Rate limits: **PARTIAL**. No client or worker throttle. Hosted Supabase email limits were not reviewed.

### Secrets and supply chain

- Tracked source: **PASS**. No live `eyJ`, private key, or `service_role` in the repo. `local.properties` is gitignored. Anon key is the intended public client key **if** RLS is deployed.
- Client-embedded secrets: **FAIL** by design of `app/build.gradle.kts` lines 60–69 (`API_BIBLE_KEY`, `BIBLE_PROXY_TOKEN`, DSN). F5.
- Public release `v1.0.0.6` asset `app-release-unsigned.apk`: **no** `supabase.co` host, **no** `eyJ` JWT, **no** `ingest.sentry.io`, **no** `api.scripture.api.bible`. This build did not bake those secrets. The workflow will bake them into the next public APK once the GitHub secrets are set (`.github/workflows/release.yml` lines 18–45 and 56–62).
- Signing: **FAIL** for release integrity. The workflow writes `RELEASE_STORE_FILE` as a path string and never decodes a keystore, so CI produced `app-release-unsigned.apk` on a **public** repo (`https://github.com/kennethteh90/morning-guard`).
- R8: minify is on for release, but `app/proguard-rules.pro` line 3 is `-keep class com.morningguard.** { *; }`, so app code is not obfuscated. `BuildConfig` strings would be readable anyway.
- Debug UI: **PASS**. Settings debug presets and step debug are behind `BuildConfig.DEBUG` (`SettingsScreen.kt` line 117, `MorningGoalScreen.kt` line 136).
- Dependencies: **Needs verification**. No Gradle lockfile and no vulnerability scan was run. `security-crypto` alpha and Health Connect alpha are present. No CVE is claimed here.

### Privacy and Play

- Data safety vs code: **FAIL**. No privacy policy in `strings.xml`, the app, or `site/`. Actual collection is email, notification contents, installed-app inventory, health steps, crash reports, and cloud highlights. F3.
- Account deletion: **FAIL**. F4.
- Health permission rationale: **FAIL**. F7.
- Children / Families: **N/A** in code. No kids UI. Confirm the Play target age is not under 13.
- Payments: **N/A**. No Play Billing and no other checkout.
- Misleading copy: **FAIL**. `AuthScreen.kt` line 40 says sign-in syncs settings. `UserDataSyncService` syncs only `morning_day_records` and `bible_highlights`. Settings, passphrase, bypass list, and the digest stay local. Google sign-in is implemented and not shown.

## 2. Prioritized findings

| ID | Severity | Category | Title | Evidence | Impact | Exploit / failure | Fix | Verification | Play |
|---|---|---|---|---|---|---|---|---|---|
| F1 | Critical | Privacy / safety | Listener cancels calls and messages, and reposts a single body | `NotificationPolicy.kt` 13–25 exempts only `category == "alarm"`, this package, and bypass packages. Default bypass is Spotify only (`DefaultBypassPackages.kt` 5–7). `MorningNotificationListenerService.kt` 75–92 persists title/text then `cancelNotification`. `strings.xml` line 6 says removed alerts cannot be restored. `DigestNotificationManager.kt` 54–67, when `count == 1`, sets content to `"$latestApp — ${title}: ${text}"` with no `setVisibility(VISIBILITY_SECRET)` and no public redacted version. In-app digest is hidden while the gate is locked (`DigestScreen.kt` 43–64); the status-bar copy is not. | Missed calls and one-time codes. A single OTP or message is shown on the shade/lock screen while the in-app gate still says to finish the goal. Bodies sit in `morning_guard.db` until the user dismisses them. No TTL. | During the locked window, a banking or SMS notification is removed. If it is the only one held, its text is the Morning Guard notification. Anyone who can unlock the shade, or who later opens the digest, reads it. Another app on the device cannot read the DB. | Exempt `call`, `alarm`, ongoing calls, and known phone/SMS packages by default. Do not put message bodies in the digest notification. Store a package name and count, or encrypt and expire rows when the gate opens. | Post a call, an SMS, and an OTP during a locked window. Confirm the originals stay. Confirm the digest notification has no body. Inspect the DB after dismiss. | **Blocker** |
| F2 | High | Auth | Implicit magic-link tokens on a custom scheme, then silent sync | `SupabaseClientProvider.kt` 14–19 installs Auth with no `flowType`. supabase-kt 3.0.3 `AuthConfigDefaults` sets `FlowType.IMPLICIT`. `handleDeeplinks` imports the URL fragment (`parseFragmentAndImportSession`). `site/auth-landing/index.html` appends `search` and `hash` to `morningguard://auth` and redirects. `MainActivity.kt` 68–75 accepts any data starting with `morningguard://auth` and calls `handleDeepLink`, then `syncAllIfSignedIn`. `startsWith("morningguard://auth")` is wider than the manifest host match. | Another app that registers `morningguard://auth` can receive `access_token` and `refresh_token`. A link can also sign this app into an attacker session; local highlights and history are then upserted as that user (`UserDataSyncService.kt` 80–92, 100–118). | Victim taps a link to `morningguard://auth#access_token=…&refresh_token=…` (directly, or via the HTTPS landing page, which forwards the fragment). The app imports it with no confirmation and pushes local rows. | Set `flowType = PKCE`. Use a verified `https` App Link. Confirm the account email before the first sync. On sign-in, do not upload pre-existing local rows until the user agrees. | Capture a magic link and confirm the URL has `code`, not `access_token`. Install a second handler and confirm the token is not a bearer token. Sign in as user B on a device that already has user A’s highlights and confirm A’s rows are not created under B. | **Blocker** if accounts ship |
| F3 | High | Play policy | No privacy policy for notification, health, email, and crash data | No “privacy” string in `app/src/main/res`. Onboarding (`OnboardingFlowScreen.kt` 234–238) says Android needs access “so we can hold other apps’ alerts” and does not say title, body, and package are stored. Sentry starts in `AppInitializer` before any consent. Bypass UI lists installed apps. | Play User Data policy requires a policy and a prominent disclosure before sensitive access. Data safety cannot match the code until this exists. | Play review rejects or removes the app for notification access and Health Connect without the same policy shown in the app and on the store listing. | Publish a policy that lists notification content, installed apps, steps, email, highlights, and Sentry. Show that disclosure on the notification-access step before the settings button. Link it in-app and in Play Console. Add a crash-reporting opt-out. | Onboarding cannot continue to notification access without the disclosure. The health privacy link and the store listing show the same policy. | **Blocker** |
| F4 | High | Play policy | Accounts can be created; nothing deletes them or isolates the device | `AuthScreen.kt` 103–105 is sign-out only. `SupabaseAuthBackend.signOut` (lines 61–68) clears the Supabase session only. No `deleteUser` / account-deletion URL. `morning_day_records` has no user `DELETE` policy (history rows survive until the auth user row is deleted). `ON DELETE CASCADE` exists, but nothing calls it. | Play requires in-app and web account deletion for apps that let users create accounts. Sign-out leaves the previous person’s digest and highlights on the phone; the next sign-in uploads them (F2). | User A signs out. User B signs in on the same phone. B’s sync pushes A’s local highlights. A cannot delete the cloud account from the app. | Add in-app deletion that calls a Supabase user-delete path, wipes Room and both DataStores, and provide the same action on the website. Give `morning_day_records` a delete-own policy. | Delete an account, confirm `auth.users` and both tables are gone, reopen the app, and confirm the digest and highlights are empty. | **Blocker** if `SUPABASE_URL` is in the Play build |
| F5 | High | Secrets | Bible API key and proxy token are compiled into the client; the worker is open if the token is unset | `app/build.gradle.kts` 64–69. `ApiBibleAuthInterceptor.kt` 17 sends `api-key`. `BibleProxyAuthInterceptor.kt` 17 sends `X-Morning-Guard-Token`. Worker `checkAuth` (`workers/bible-cache/src/index.ts` 69–76) allows every request when `BIBLE_PROXY_TOKEN` is empty. CORS is `*` (lines 91–96). Comparison is `!==`. KV id `b962ddd033954d75957cb66da13d1a01` is in `wrangler.jsonc` (not a credential). Public `v1.0.0.6` did **not** contain a JWT or `api.scripture.api.bible`. | Anyone with the APK can spend the API.Bible quota and read cached chapters. NIV/NKJV/NLT text is cached with `Cache-Control: public`. Redistribution rights were not in the repo. | After secrets are added, download the GitHub Release APK (the workflow uploads it) and read `BuildConfig`. Call the worker with the extracted token, or with no token if the secret was never set. | Keep `API_BIBLE_KEY` only on the worker. Do not put `BIBLE_PROXY_TOKEN` in the APK; require a Play Integrity or signed-user check, or accept that a public scripture proxy needs rate limits and no shared secret. Fail closed when the token is missing. Stop publishing release APKs that contain BuildConfig secrets. Confirm the API.Bible plan allows this cache. | **Should fix** before the first build that contains the key. **Blocker** if that APK is what you ship. |
| F6 | Medium | Authn storage | Passphrase and refresh token are fast-hash / plaintext | `PassphraseHasher.hash` is one SHA-256 of `salt:passphrase` (lines 18–21). No attempt counter (`EmergencyBypassViewModel.attemptBypass`). Hash and salt are JSON in `goal_progress.json`. Session JSON is the SDK default Settings store, not Keystore. `security-crypto` is unused. | Someone with a backup, root, or `run-as` can crack an 8-character passphrase offline and can replay the refresh token. The gate is local, so this does not cross accounts by itself. | Copy `goal_progress.json` and brute-force the hash. Copy the Settings session and call PostgREST. | Use a real KDF (PBKDF2/Argon2) and a short lockout. Store the Supabase session with an encrypted settings implementation or the Keystore. | Confirm the session file is not plaintext JSON and that 20 bad passphrase attempts back off. | **Should fix** |
| F7 | High | Play / health | Health Connect permission without a privacy-policy activity | Manifest lines 51–53 put `ACTION_SHOW_PERMISSIONS_RATIONALE` on `MainActivity`, which opens the home UI, not a policy. There is no `ViewPermissionUsageActivity` alias with `START_VIEW_PERMISSION_USAGE` and `HEALTH_PERMISSIONS`. `READ_STEPS` is declared. Steps are only aggregated locally (`HealthConnectStepReader.kt` 35–47). | On Android 14+, the Health Connect privacy link does not show a policy. Play’s health declaration expects that activity to show the same policy as the store listing. | Request step access, open the Health Connect privacy link, and land in the main UI with no policy. | Add the rationale activity and the Android 14 alias from the Health Connect guide. Point both at the policy screen. Declare Steps only, collected, not shared, for the step goal. | On API 34+, the privacy link shows the policy. Play health form matches `READ_STEPS` only. | **Blocker** while `READ_STEPS` stays in the manifest |
| F8 | High | Play release | `targetSdk` 35 and no App Bundle | `app/build.gradle.kts` lines 50 and 55: `compileSdk`/`targetSdk` 35. `release.yml` runs `assembleRelease` and uploads `*.apk`. [Play’s target API policy](https://support.google.com/googleplay/android-developer/answer/11926878) requires API 36 for new apps and updates since 31 August 2026. New apps must be AABs. | Play Console will refuse the upload. | Upload this release build. | `targetSdk`/`compileSdk` 36, `bundleRelease`, sign with the upload key inside CI (decode the keystore; do not echo it). Do not attach that AAB to a public GitHub release. | Console accepts the bundle. `aapt dump badging` shows `targetSdkVersion:'36'` and a signing cert. | **Blocker** |
| F9 | Medium | Disclosure | Installed-app inventory and thin notification disclosure | `BypassAppsViewModel.kt` 32–38. Manifest `<queries>` is every `MAIN` activity, not launcher-only. Analytics logs `notification_captured` with the package name on all builds (`AnalyticsTracker.kt` 11–14, `AppModule.kt` 21, listener line 85–88). | Data safety must include installed apps and app interactions if you treat the inventory as collected. Logcat on a debuggable phone shows which apps notified the user. Other apps cannot read that log on current Android. | Open the bypass screen (full inventory in memory). Capture a notification and read logcat `MorningGuardAnalytics`. | Query `MAIN`/`LAUNCHER` only. Declare installed apps in Data safety as on-device, not shared. Use `NoOpAnalyticsTracker` in release, or log counts without package names. | Release logcat has no package names. Data safety matches. | **Should fix** |
| F10 | Low | Hardening | Release OkHttp logs URL lines; R8 keeps the whole app | `DataModule.kt` 58–64. `proguard-rules.pro` line 3. | Chapter URLs can appear in logcat. Decompiling the app is trivial once a secret is in `BuildConfig`. | `adb logcat` during a Bible fetch on a userdebug device. | Drop the logging interceptor unless `BuildConfig.DEBUG`. Replace the blanket `-keep` with the specific rules you already have for Hilt, Kotlin serialization, and Retrofit. | Release logcat shows no OkHttp lines. | **Optional** |

**Client vs server.** F2’s token theft and F6’s session file are client bugs. Cross-account PostgREST access is blocked only if the hosted project matches the migrations. F5’s worker is open only if `BIBLE_PROXY_TOKEN` is unset in Cloudflare. That production value was not checked.

## 3. Top 10 pre-launch actions

1. Stop treating this as a Play upload until `targetSdk` is 36 and CI produces a **signed AAB**, not a public unsigned APK.
2. Change the listener so calls, SMS, and ongoing system alerts are never cancelled, and stop copying message bodies into your notification.
3. Add a privacy policy and an in-app disclosure that states notification title, body, and app name are read and stored, before the user opens notification access.
4. Ship auth only after PKCE, a verified HTTPS App Link, account deletion, and a local wipe on sign-out and account switch.
5. Remove `API_BIBLE_KEY` and `BIBLE_PROXY_TOKEN` from `BuildConfig`. Make the worker fail closed. Confirm the API.Bible license covers caching NIV/NKJV/NLT.
6. Add the Health Connect privacy activity and alias, or drop `READ_STEPS` until the policy screen exists.
7. Encrypt or stop persisting notification bodies; hash the emergency passphrase with a slow KDF and a lockout.
8. Put the Supabase session in encrypted storage. Apply the RLS migrations to the hosted project and confirm with a second user that `select()` returns only one user’s rows.
9. Fix the Account screen so it does not say settings sync, and either expose Google sign-in or remove it.
10. Fill Play Console Data safety, notification-access, exact-alarm, and health-steps declarations from the lists above. Set the target age to not under 13 unless you add a Families program.

## 4. Evidence gaps

- Hosted Supabase: RLS actually applied, redirect URL allow-list, email rate limits, and whether `delete user` is enabled. Repo SQL is not production proof.
- Cloudflare: whether `BIBLE_PROXY_TOKEN` and `API_BIBLE_KEY` are set, and whether the KV namespace already holds copyrighted chapters.
- API.Bible contract for caching and redistributing NIV, NKJV, and NLT.
- Sentry project settings (PII, logcat attachments). The app does not disable them.
- Play Console forms, store listing, and privacy URL. None are in the repo.
- Whether `http://10.0.2.2:8969/stream` in the public APK is dead library text. It is not referenced by this repo.
- Dependency CVEs. No lockfile scan was run.
- On-device test that a `CATEGORY_CALL` notification is actually cancelled. The code path says yes; OEM call UIs can differ.
- `v1.0.0.6` did not contain a Supabase JWT or API.Bible host. Later CI builds will if those secrets are set.

## 5. Suggested CI and release gates

- Secret scanning on the repo and on the release AAB (`gitleaks` or `trufflehog`, plus a check that `BuildConfig` does not contain `API_BIBLE_KEY` / `BIBLE_PROXY_TOKEN` / a Sentry DSN).
- Fail the release job if `aapt dump badging` shows `targetSdkVersion` below 36, `debuggable`, or a missing signing certificate.
- Upload **only** a signed `bundleRelease` to Play. Do not attach it to the public GitHub release.
- Decode the keystore from a GitHub Actions secret in memory or a masked file. Never `echo` keystore passwords. The current script writes them into `local.properties` on the runner; GitHub masks known secrets, but the file must not be uploaded.
- `./gradlew lint` with `NotificationPermission` / exported-component checks, plus a manifest unit test: no component `exported=true` except `MainActivity` and the notification listener; listener must keep `BIND_NOTIFICATION_LISTENER_SERVICE`.
- Dependency scanning (`gradle/dependency-lock` or OWASP dependency-check) on `libs.versions.toml`.
- A SQL test or `supabase db lint` that anon has no grants and both tables have forced RLS.
- A worker test that missing `BIBLE_PROXY_TOKEN` returns 401, not 200.
- Manual checklist: notification disclosure, account deletion, Health Connect policy link, Data safety, exact-alarm declaration, and a second-account RLS test.
