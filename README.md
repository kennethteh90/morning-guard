# Morning Guard

Open-source Android app that helps you protect your morning: hold non-alarm notifications in a single digest until you complete a verifiable goal, with optional alarm, emergency bypass, and per-app exceptions.

Architecture is split into `:core:domain` (pure Kotlin, iOS-ready business logic) and `:core:data` / `:app` (Android).

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
4. Open in Android Studio or run:

```bash
./gradlew test lint detekt assembleDebug
```

Grant **Notification access** in system settings for the listener service.

## CI / CD

- **CI** (`.github/workflows/ci.yml`): `test`, `detekt`, `lint` on PRs and pushes; `assembleRelease` on `main`.
- **Release** (`.github/workflows/release.yml`): GitHub Release with APK and `CHANGELOG.md` on merge to `main`.

## Versioning

Semantic version in `version.properties` → `versionName` / `versionCode`.

## License

Apache-2.0 — see [LICENSE](LICENSE).
