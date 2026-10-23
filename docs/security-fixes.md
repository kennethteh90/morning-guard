# Morning Guard — security fix plan

Date: 17 October 2026. Companion to [security-audit.md](security-audit.md).

Every FAIL and PARTIAL from that audit is covered below. These diffs are the intended patch. They are not applied.

Must-fix items are what would block Play review or leave a live data-loss or account bug in the build you upload. Shared-device sync, passphrase hashing, and log redaction matter before a public launch, but they do not change the Play binary requirements. ProGuard tightening and OkHttp log level can wait.

## Must fix before Play submission

### 1. Stop cancelling calls and messages, and stop reposting bodies (F1)

Files: `core/domain/src/main/kotlin/com/morningguard/domain/notification/NotificationPolicy.kt`, `core/domain/src/test/kotlin/com/morningguard/domain/notification/NotificationPolicyTest.kt`, `app/src/main/kotlin/com/morningguard/app/notification/DigestNotificationManager.kt`, `app/src/main/res/values/strings.xml`

Hard-exempt these in policy, not only in the user bypass list (the user can turn a bypass off):

- categories `call` and `alarm`
- `com.android.dialer`, `com.google.android.dialer`, `com.samsung.android.dialer`, `com.android.server.telecom`, `com.google.android.apps.messaging`, `com.android.mms`, `com.samsung.android.messaging`

```diff
 object NotificationPolicy {
     const val ALARM_CATEGORY = "alarm"
+    const val CALL_CATEGORY = "call"
+
+    val neverInterceptCategories: Set<String> = setOf(ALARM_CATEGORY, CALL_CATEGORY)
+
+    val neverInterceptPackages: Set<String> = setOf(
+        "com.android.dialer",
+        "com.google.android.dialer",
+        "com.samsung.android.dialer",
+        "com.android.server.telecom",
+        "com.google.android.apps.messaging",
+        "com.android.mms",
+        "com.samsung.android.messaging",
+    )

     fun shouldIntercept(...): Boolean {
         if (packageName == appPackageName) return false
-        if (category == ALARM_CATEGORY) return false
+        if (category in neverInterceptCategories) return false
+        if (packageName in neverInterceptPackages) return false
         if (packageName in bypassPackages) return false
         ...
     }
 }
```

Digest notification: count and app name only. No title, no body, and nothing on the lock screen.

```diff
-        val content = if (summary.count == 1) {
-            "$latestApp — ${summary.latestTitle}: ${summary.latestText}"
-        } else {
-            "${summary.count} notifications held — latest from $latestApp"
-        }
-        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
+        val content = context.resources.getQuantityString(
+            R.plurals.digest_held_count, summary.count, summary.count, latestApp,
+        )
+        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
             ...
             .setContentText(content)
-            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
+            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
             .setContentIntent(pendingIntent)
```

```xml
<plurals name="digest_held_count">
    <item quantity="one">1 notification held from %2$s</item>
    <item quantity="other">%1$d notifications held — latest from %2$s</item>
</plurals>
```

Tests: extend `NotificationPolicyTest` so a locked gate still intercepts `com.social`, and does not intercept `category = "call"`, `com.google.android.dialer`, or `com.google.android.apps.messaging`.

Verify: during a locked window, post a dialer notification, an SMS, and one chat notification. Dialer and SMS stay in the shade. The Morning Guard notification shows a count and app name only, including on the lock screen. The in-app digest still shows the chat body after the goal is done.

### 2. Disclosure and a privacy policy the health screen can open (F3, F7)

Files: `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/com/morningguard/app/ui/onboarding/OnboardingFlowScreen.kt`, new `app/src/main/kotlin/com/morningguard/app/ui/privacy/PrivacyPolicyActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`

On the notification-access step, replace the current subtitle with a fixed disclosure and disable “Open Morning Guard access settings” until a checkbox is checked:

> Morning Guard reads the app name, title, and text of notifications it holds, stores them on this phone, and shows them in the digest after your goal. It does not upload that text. Calls and text messages are not held.

New `PrivacyPolicyActivity` shows that text plus `BuildConfig.PRIVACY_POLICY_URL`. Manifest: move the health rationale off `MainActivity` onto this activity, and add the Android 14 alias.

```xml
<activity
    android:name=".ui.privacy.PrivacyPolicyActivity"
    android:exported="true"
    android:label="@string/privacy_policy_title">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity>
<activity-alias
    android:name="ViewPermissionUsageActivity"
    android:exported="true"
    android:targetActivity=".ui.privacy.PrivacyPolicyActivity"
    android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
    <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```

Remove the `ACTION_SHOW_PERMISSIONS_RATIONALE` filter from `MainActivity`.

```kotlin
val privacyPolicyUrl = prop("PRIVACY_POLICY_URL")
buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
```

Fail `bundleRelease` when that URL is blank. Debug may stay blank and show the in-app text only.

Tests: a unit test is enough for the disclosure string resource existing. Activity launch is a manual check.

Verify: onboarding cannot open notification access until the checkbox is on. On Android 14+, Health Connect’s privacy link opens `PrivacyPolicyActivity`, not the home screen. The Play Console privacy URL is the same URL.

### 3. PKCE, exact auth URI, and do not forward token fragments (F2)

Files: `app/src/main/kotlin/com/morningguard/app/supabase/SupabaseClientProvider.kt`, `app/src/main/kotlin/com/morningguard/app/MainActivity.kt`, new `app/src/main/kotlin/com/morningguard/app/auth/AuthCallbackUri.kt`, `app/src/test/.../AuthCallbackUriTest.kt`, `site/auth-landing/index.html`

```kotlin
install(Auth) {
    flowType = FlowType.PKCE
    scheme = "morningguard"
    host = "auth"
}
```

```kotlin
object AuthCallbackUri {
    fun isAuthCallback(uri: Uri?): Boolean =
        uri?.scheme == "morningguard" && uri.host == "auth"
}
```

`MainActivity.handleAuthIntent` calls `handleDeepLink` only when `AuthCallbackUri.isAuthCallback` is true. `morningguard://auth.evil` and `morningguard://authentication` must not match.

Landing page: append `location.search` only. Drop `location.hash`, so an `access_token` fragment is not copied into the custom scheme.

```javascript
var deepLink = "morningguard://auth" + (window.location.search || "");
```

Deploy this page with the app change. Supabase redirect URL stays `morningguard://auth`.

Tests: `AuthCallbackUriTest` for host `auth`, host `auth.evil`, and a missing URI.

Verify: a magic link’s app URL contains `code` and no `access_token`. A page at `https://<landing>/?code=abc#access_token=secret` redirects to `morningguard://auth?code=abc` only.

### 4. Account deletion, sign-out wipe, and no cross-account push (F4 and the shared-device half of F2)

Files: `core/data/src/main/kotlin/com/morningguard/data/auth/AuthRepository.kt`, `app/src/main/kotlin/com/morningguard/app/auth/SupabaseAuthBackend.kt`, `app/src/main/kotlin/com/morningguard/app/ui/auth/AuthScreen.kt`, `app/src/main/kotlin/com/morningguard/app/ui/auth/AuthViewModel.kt`, `core/data/.../SettingsRepository.kt`, `core/data/.../BibleHighlightRepository.kt`, `core/data/.../NotificationCaptureRepository.kt`, `app/src/main/kotlin/com/morningguard/app/sync/UserDataSyncService.kt`, `site/auth-landing/index.html` (add a short “delete account” section that points at the in-app action)

Do not call `auth.admin.deleteUser`. In supabase-kt 3.0.3 that admin API is the service-role path. There is no service-role key in the app, and there must not be one.

Self-delete with the current user JWT:

```kotlin
suspend fun deleteCurrentUser(): Result<Unit> {
    val session = client.auth.currentSessionOrNull()
        ?: return Result.failure(IllegalStateException("Not signed in"))
    val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user"
    val response = okHttp.newCall(
        Request.Builder().url(url).delete()
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .build(),
    ).execute()
    if (!response.isSuccessful) {
        return Result.failure(IllegalStateException("Account deletion failed (${response.code})"))
    }
    client.auth.signOut()
    return Result.success(Unit)
}
```

GoTrue allows `DELETE /auth/v1/user` for the signed-in user. Confirm that once on the hosted project (evidence gap from the audit).

On sign-out and after a successful delete, clear:

- `captured_notifications`
- bible highlights
- `goal_progress.json` history, passphrase, and bypass timer
- the Supabase session

Keep morning-window settings if you want the device still usable offline. Do not keep highlights or the digest.

```kotlin
suspend fun syncHighlights(client: SupabaseClient) {
    val userId = client.auth.currentUserOrNull()?.id ?: return
    val bound = settingsRepository.boundUserId()
    if (bound != null && bound != userId) {
        bibleHighlightRepository.clearAll()
        settingsRepository.clearMorningHistory()
    }
    settingsRepository.setBoundUserId(userId)
    pullHighlights(client)
    // push only rows recorded while this userId was bound
}
```

`AuthScreen` signed-in card: “Delete account” with a confirm dialog, plus this copy instead of “sync settings”:

> Sign in to sync streak stats and Bible highlights. Notification text, settings, and the emergency passphrase stay on this phone.

Tests: `UserDataSyncService` is hard to unit-test without a fake client. Extract “should push local highlights” into a pure function, for example `SyncOwnership.shouldUpload(boundUserId, currentUserId)`, and test three cases: same user, null bound user, different user. Fake `AuthBackend.deleteAccount` on `UnconfiguredAuthBackend` so it returns failure.

Verify: create highlights as user A, sign out, confirm Room and the digest are empty. Sign in as B and confirm A’s excerpt is not in B’s `bible_highlights`. Delete B and confirm the auth user and both SQL tables are gone (`on delete cascade` already covers the tables).

Also add a delete-own RLS policy for `morning_day_records` in a new file under `supabase/migrations/`. Cascade already removes rows when the auth user is deleted; the policy lets the user delete history before that.

### 5. Ship a signed API-36 App Bundle with no Bible secrets (F5, F8)

Files: `app/build.gradle.kts`, `app/src/main/kotlin/com/morningguard/app/di/ApiBibleModule.kt`, `.github/workflows/release.yml`, `local.properties.example`

```kotlin
compileSdk = 36
targetSdk = 36
```

Remove `API_BIBLE_KEY` and `BIBLE_PROXY_TOKEN` from `defaultConfig`. The app already prefers the proxy and falls back to bible-api.com. Direct API.Bible from the phone should stay unavailable. AGP 8.7.3 may not accept platform 36; if `bundleRelease` cannot resolve `android-36`, bump the Android Gradle Plugin in `gradle/libs.versions.toml` to one that supports it, then re-run unit tests.

`release.yml`: stop writing those two secrets into `local.properties`. Build `bundleRelease`. Decode the keystore from a secret file to a runner path outside the repo. Do not `echo` passwords. Remove the `softprops/action-gh-release` APK upload. Play Console is the only place the AAB goes.

Tests: existing `./gradlew test` after the SDK bump. No new unit test for Gradle.

Verify: `aapt dump badging` on the bundle shows `targetSdkVersion:'36'`. `apkanalyzer` or `strings` on the bundle finds no API.Bible key. `gh release view` does not gain a new APK. Play Console accepts the bundle.

The public `v1.0.0.6` APK did not contain these secrets. Delete that unsigned asset anyway so sideload users are not stuck on it.

A shared `BIBLE_PROXY_TOKEN` inside the APK is not a secret. Do not replace the removed key with a client-embedded bearer token. Rate-limit the worker instead (item 6).

## Should fix before public launch

### 6. Worker quota without a client secret (F5 remainder)

Files: `workers/bible-cache/src/index.ts`, `workers/bible-cache/src/upstream.ts`

Leave the worker callable without `X-Morning-Guard-Token`, since that token cannot stay secret inside the app. Add a per-IP rate limit (Cloudflare Rate Limiting or a small KV counter). Keep `API_BIBLE_KEY` only in the worker secret. Cache public-domain translations. Do not cache NIV, NKJV, or NLT until the API.Bible plan says redistribution and caching are allowed. That license check is not a code change.

Tests: add a node test that a request for `translation=niv` does not call API.Bible until the license is confirmed, and that `translation=web` is served.

Verify: `curl` the worker for `translation=niv` and confirm it does not return licensed text. Burst `translation=web` and confirm the limit returns 429.

### 7. Slow passphrase hash and a lockout (F6, passphrase half)

Files: `core/data/src/main/kotlin/com/morningguard/data/security/PassphraseHasher.kt`, `core/data/.../EmergencyBypassRepository.kt`, `core/domain/.../EmergencyBypassPolicy.kt`, new hasher test

Store `pbkdf2-sha256$120000$<salt>$<hash>`. Verify with `SecretKeyFactory` PBKDF2WithHmacSHA256. Existing one-shot SHA-256 hashes will not match; show “Set a new emergency passphrase” rather than trying to migrate them. There is no plaintext to rehash.

Add `failedAttempts` and `lockedUntilEpochMs` on `GoalProgressStore`. Five failures lock for 15 minutes. `EmergencyBypassPolicy` can own the numbers so the test stays in `core/domain`.

Tests: policy test for the fifth failure and for a lock that is still active. Hasher test that the same passphrase and salt verify, and a wrong passphrase does not. Do not assert timing.

Verify: five wrong entries on device, then a correct passphrase during the lock is rejected. After the window, the correct passphrase works.

### 8. Encrypt the Supabase session (F6, session half)

Files: new `app/src/main/kotlin/com/morningguard/app/auth/KeystoreSessionManager.kt`, `SupabaseClientProvider.kt`

Implement supabase-kt `SessionManager` and store the session JSON in a file encrypted with an AES key from Android Keystore. Pass it into `install(Auth) { sessionManager = keystoreSessionManager }`. Do not use `security-crypto` 1.1.0-alpha06 for this. Remove that unused dependency from `core/data/build.gradle.kts` in the same change.

Tests: a Robolectric or instrumented test that `saveSession` then `loadSession` round-trips, and the file on disk does not contain `refresh_token` as plaintext.

Verify: after sign-in, `run-as` the app and confirm the session file is not JSON. Sign-out deletes it.

### 9. Narrow package visibility and release logs (F9, F10 logging half)

Files: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/morningguard/app/ui/bypass/BypassAppsViewModel.kt`, `app/src/main/kotlin/com/morningguard/app/di/AppModule.kt`, `core/data/.../DataModule.kt`

```xml
<intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent>
```

Query launcher activities in `BypassAppsViewModel` instead of `getInstalledApplications(GET_META_DATA)`.

```kotlin
fun provideAnalyticsTracker(): AnalyticsTracker =
    if (BuildConfig.DEBUG) LoggingAnalyticsTracker() else NoOpAnalyticsTracker()
```

```kotlin
if (BuildConfig.DEBUG) builder.addInterceptor(logging)
```

`BuildConfig` is not visible inside `:core:data` today. Pass a `@Named("httpLogging") Boolean` from `app` as `BuildConfig.DEBUG`, and add the interceptor only when that flag is true.

Tests: none for the manifest. Analytics binding can be checked by inspection of `AppModule`.

Verify: release logcat has no `MorningGuardAnalytics` and no OkHttp lines during a chapter fetch. The bypass list still shows installable apps and hides packages with no launcher.

### 10. Lock the digest screen (screenshot PARTIAL)

File: `app/src/main/kotlin/com/morningguard/app/ui/digest/DigestScreen.kt`

From `LocalContext`, set `WindowManager.LayoutParams.FLAG_SECURE` while the digest destination is visible, and clear it on dispose.

Verify: the digest route shows a black recents thumbnail and screenshots fail. Home and the Bible reader still capture normally.

### 11. Magic-link cooldown (rate-limit PARTIAL)

Files: `app/src/main/kotlin/com/morningguard/app/ui/auth/AuthViewModel.kt`, `AuthScreen.kt`

Ignore a second `sendMagicLink` for the same address for 60 seconds. This does not replace Supabase’s server limit. Hosted rate limits stay an evidence gap.

Tests: a ViewModel test with `kotlinx-coroutines-test` that the second call within 60s does not call the repository.

Verify: double-tap “Send magic link” and confirm one network call.

### 12. Verified App Link (residual F2)

Files: `app/src/main/AndroidManifest.xml`, `site/auth-landing/.well-known/assetlinks.json`, landing page

Add an `https` intent-filter for the real host with `android:autoVerify="true"`, and host `assetlinks.json` for the Play signing cert. Keep the custom scheme as a fallback only after PKCE is on.

Verify: `adb shell pm get-app-links` shows `verified` for that host. A magic link opens the app without a chooser.

## Can defer

| Item | Why it can wait | When you do it |
|---|---|---|
| F10 ProGuard `-keep class com.morningguard.** { *; }` | No secret should remain in the app after item 5. Obfuscation does not fix a secret that is still there. | After the Play build is clean, replace that keep with the Hilt, serialization, and Retrofit rules already below it. Rebuild and open the Bible screen, sign-in, and digest. |
| SQLCipher for `morning_guard.db` | Item 1 stops the shade leak. Bodies remain only in app-private storage with backup off. | If you keep message text after unlock, encrypt the database and add a retention delete when the gate opens. |
| Certificate pinning | Platform TLS is the right control for Supabase and bible-api.com. | Skip unless a later threat model requires it. |
| Dead Google sign-in | `AuthScreen` never calls `signInWithGoogle`. Not a Play failure if the store listing does not promise it. | Remove `GoogleSignInHelper` or add the button. Do not mention Google on the store page until the button exists. |
| Explicit `dataExtractionRules` | `allowBackup="false"` already disables backup. | Add `android:dataExtractionRules` and `fullBackupContent` pointing at empty rules if you want the manifest to say it twice. |
| Dependency CVE scan | No vulnerable package was confirmed. | Add a lockfile and a CI scanner. Do not block this release on an unscanned catalog. |
| `supabase/config.toml` password length | Local CLI only. The app has no password login. | Leave it. |

## Suggested order

1. Items 1 and 2, because they change what the listener does and what you tell the user.
2. Items 3 and 4 together, then deploy the landing page.
3. Item 5 last, so the first Play bundle already contains the other must-fix changes.

Items 6–12 can land in the same release or the one immediately after, before you send anyone the public listing.

## Finding coverage

| Audit result | Plan item |
|---|---|
| F1 FAIL — listener cancels calls/messages and reposts bodies | 1 (must). SQLCipher / retention deferred |
| F2 FAIL — implicit custom-scheme session | 3 (must), 4 (must), 12 (should) |
| F3 FAIL — no privacy policy or disclosure | 2 (must) |
| F4 FAIL — no account deletion or sign-out isolation | 4 (must) |
| F5 FAIL — client Bible secrets; open worker | 5 (must), 6 (should) |
| F6 FAIL — fast passphrase hash; plaintext session | 7 and 8 (should) |
| F7 FAIL — Health Connect rationale missing | 2 (must) |
| F8 FAIL — targetSdk 35, unsigned public APK | 5 (must) |
| F9 PARTIAL — broad package query; package names in logs | 9 (should) |
| F10 PARTIAL — release HTTP logs; blanket R8 keep | 9 logging half (should); ProGuard row (defer) |
| PARTIAL — digest screenshots / no `FLAG_SECURE` | 10 (should) |
| PARTIAL — no magic-link or worker rate limit | 11 (should), 6 (should) |
| PARTIAL — `startsWith("morningguard://auth")` | 3 (must) |
| FAIL — Account screen says settings sync | 4 (must) |
