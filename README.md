# Morning Guard

Open-source morning routine app for **Android** and **iOS**. Android holds non-alarm notifications in a single digest until you complete a verifiable goal; iOS shields apps you choose via Screen Time APIs (see platform notes below). Both support optional alarm, emergency bypass, and morning goals (Bible, focus, steps).

Architecture: Android `:core:domain` (pure Kotlin) + `:core:data` / `:app`; iOS `MorningGuardCore` Swift package mirrors domain rules — see [`docs/PLAN-IOS.md`](docs/PLAN-IOS.md).

## iOS

SwiftUI app and Screen Time extensions live under [`ios/`](ios/). See [`ios/README.md`](ios/README.md) for XcodeGen, signing, and device testing.

## Features

- Notification listener aggregates notifications (excluding alarms) into one ongoing digest during the morning gate
- Morning goals: Bible chapter (via [bible-api.com](https://bible-api.com)), timed focus, or step count
- Emergency passphrase bypass (hashed locally)
- Per-app bypass list
- Optional exact morning alarm (rescheduled on boot)
- Supabase auth (magic link; Google ID token when configured)
- Sentry error reporting and structured analytics hooks

## Setup

1. Install Android SDK 35 and JDK 17.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
3. Optional: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SENTRY_DSN`, `GOOGLE_WEB_CLIENT_ID`.
4. **Bible chapters:** deploy [`workers/bible-cache`](workers/bible-cache/README.md), set worker secrets (`API_BIBLE_KEY`, Play Integrity / App Attest config, optional legacy `BIBLE_PROXY_TOKEN` during rollout). In `local.properties` set `BIBLE_PROXY_BASE_URL` and `PLAY_CLOUD_PROJECT_NUMBER` (Android). The app never embeds `API_BIBLE_KEY`. Open translations (WEB/KJV) use the proxy without client secrets; NIV/NKJV/NLT require Play Integrity (Android) or App Attest on device (iOS). Without the proxy, only free [bible-api.com](https://bible-api.com) translations work from the phone.

   **Verse numbering gaps:** Some licensed translations (e.g. NIV) omit verses that are not in their base text (John 5:4 is a common example). API.Bible supplies a verse marker with no body; the app skips empty verses, so chapter view may jump (e.g. 3 → 5). That matches the translation, not a sync bug. Open translations such as WEB/KJV may still show those verses.
5. Open in Android Studio or run:

```bash
./gradlew test lint detekt assembleFullDebug

**Demo presenter app** (separate package `com.morningguard.app.demo`, scenario picker): `./gradlew :app:assembleDemoDebug` then `./scripts/serve-phone-apk.sh --demo` to install over Wi‑Fi.
```

Grant **Notification access** in system settings for the listener service.

## Release signing (install on your phone)

1. Create a keystore once (pick your own passwords and remember them):

```bash
keytool -genkey -v \
  -keystore "$HOME/morning-guard-release.keystore" \
  -alias morning-guard \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Morning Guard, OU=Dev, O=You, L=City, ST=State, C=US"
```

2. Add to `local.properties` (see `local.properties.example`): `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

3. Build a signed release APK:

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (signed when the four `RELEASE_*` properties are set).

Install: `adb install -r app/build/outputs/apk/release/app-release.apk`, or copy the APK to the phone and allow installs from that source.

**Back up the keystore and passwords.** You need the same key for Play Store updates later.

## Supabase magic link

1. In Supabase **Authentication → URL configuration**:
   - **Redirect URLs:** `morningguard://auth` (required; matches the app manifest).
   - **Site URL:** optional web fallback, e.g. `https://morning-guard.codingagents.cc` after deploying [`site/auth-landing`](site/auth-landing/README.md).
2. Put **Project URL** and **publishable** (or legacy anon) key in `local.properties` as `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
3. Rebuild and install. Request a link from **Account → Send magic link**, then open the email **on the phone** with Morning Guard installed.

The app requests OTP with `redirectUrl = morningguard://auth` so links prefer opening the app directly.

Cloud schema (stats/highlights sync): see [`docs/supabase/SETUP.md`](docs/supabase/SETUP.md) and [`supabase/migrations/`](supabase/migrations/).

## Google Play

Launch checklist: [`site/play-store-launch/index.html`](site/play-store-launch/index.html) (markdown). Play Console fill-in: [`docs/play-console-fill-in.md`](docs/play-console-fill-in.md). **Agent handoff (Grok):** [`docs/grok-play-console-handoff.md`](docs/grok-play-console-handoff.md). Public site + privacy policy: deploy [`site/auth-landing`](site/auth-landing/README.md) to `https://morning-guard.codingagents.cc` (`PRIVACY_POLICY_URL=https://morning-guard.codingagents.cc/privacy/`).

Store screenshots (plan → `screenshot` build capture → [`screenshot-studio`](site/screenshot-studio/)): [`docs/store-screenshots.md`](docs/store-screenshots.md) — run `./scripts/store-screenshots/orchestrate.sh` with a device connected (`--ai` optional).

Upload **AAB** from `app/build/outputs/bundle/fullRelease/` (see checklist). Store releases are built via the Release workflow (see below).

## CI / CD

- **CI** (`.github/workflows/ci.yml`): `test`, `detekt`, `lint` on PRs and pushes; `assembleFullRelease` on `main`.
- **Release** (`.github/workflows/release.yml`): signed `bundleFullRelease` on version tags (`v*`) or manual dispatch — see [`docs/cloud-release.md`](docs/cloud-release.md).

### GitHub Actions secrets

Push secrets from your Mac `local.properties` (never commit values):

```bash
./scripts/push-github-secrets.sh --dry-run
./scripts/push-github-secrets.sh
```

Full secret list, Play service account setup, and triggers: [`docs/cloud-release.md`](docs/cloud-release.md).

CI builds work without secrets (Supabase shows as unconfigured in those APKs). Add secrets before you need signed release bundles or Play upload in Actions.

Magic-link browser fallback: deploy [`site/auth-landing`](site/auth-landing/README.md) with `./site/auth-landing/deploy.sh` (Cloudflare Pages).

## Versioning

Semantic version in `version.properties` → `versionName` / `versionCode`.

## License

Apache-2.0 — see [LICENSE](LICENSE).
