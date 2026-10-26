# Morning Guard — security fix plan

Date: 26 October 2026. Companion to [security-audit-2026-10-26.md](security-audit-2026-10-26.md). Prior plans: [security-fixes-2026-10-22.md](security-fixes-2026-10-22.md), [security-fixes.md](security-fixes.md).

Items below are **open residuals** from the 26 October audit. October must-fix items (R1–R3, I1–I6) are already implemented in tree.

**Implemented in tree (26 Oct follow-up):** O2 Sentry sync scrubbing (Android + iOS), O4/O8 iOS PKCE + `AuthCallbackURL`, O5 Keychain lockout store, O6 Family Controls mirror for monitor, O7 Dependabot config. **Still operator:** O1 legacy Bible proxy token cutover; Universal Links for iOS magic links (longer term in §3).

## Should fix before broad public launch

### 1. Bible worker legacy token cutover (O1)

Operator-only. Set worker secret or env `BIBLE_PROXY_LEGACY_TOKEN_DISABLED=true` after Play and App Store release builds send Play Integrity / App Attest for NIV, NKJV, NLT.

Verify:

```bash
# Licensed chapter without attestation → 401
curl -sS -o /dev/null -w "%{http_code}\n" "$BIBLE_PROXY_BASE_URL/v1/chapter?translation=niv&book=john&chapter=1"
strings app/build/outputs/bundle/fullRelease/*.aab | grep -i BIBLE_PROXY_TOKEN || echo "OK: not in AAB"
```

### 2. Sentry sync failure scrubbing (O2)

Files: `app/src/main/kotlin/com/morningguard/app/sync/UserDataSyncService.kt`, optionally `AppInitializer.kt` `beforeSend`.

Stop sending `SyncLog.messageFor(error)` verbatim in `extras["detail"]`. Prefer `phase`, `error_type`, and HTTP status only. Optionally redact email-like patterns in shared `beforeSend` on Android; mirror on `SentryErrorReporter.swift` for iOS sync failures if similar extras exist.

### 3. iOS magic link hardening (O4, O8)

Files: `ios/MorningGuard/AppState.swift`, `ios/MorningGuard/Services/SupabaseAuthService.swift`, `ios/MorningGuard/Services/SupabaseConfig.swift`, entitlements + `Info.plist` Associated Domains.

- Change `handleDeepLink` to require `url.host?.lowercased() == "auth"` (drop `path.contains("auth")`).
- Explicitly configure Supabase auth `flowType: .pkce` when constructing `SupabaseClient` (parity with Android `SupabaseClientProvider`).
- Longer term: redirect URL `https://morning-guard.codingagents.cc/auth/callback` with Associated Domains and the same PKCE-only handling as Android.

### 4. iOS emergency lockout storage (O5)

Files: `ios/MorningGuard/Services/SettingsStore.swift`, `GoalProgressStore`, optional small Keychain helper.

Keep `failedAttempts` / `lockedUntilEpochMs` out of iCloud/backed-up plist JSON if the threat model treats offline passphrase guessing as in scope. Keychain with `ThisDeviceOnly` matches the passphrase hash model.

## Nice to have

### 5. Dependency scanning (O7)

Add Dependabot or OWASP dependency-check for Gradle; document SPM `resolved` audit for iOS in CI.

### 6. Monitor authorization mirror (O6)

If QA shows shields applying after Screen Time permission revoked, mirror `AuthorizationCenter.shared.authorizationStatus` into app group and pass real `isAuthorized` in `DeviceActivityMonitorExtension`.
