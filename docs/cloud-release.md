# Cloud release (GitHub Actions)

Signed **full** flavor release AABs (`com.morningguard.app`) are built in GitHub Actions so you do not need Kenneth's Mac for `bundleRelease`. Versioning stays in `version.properties` (`versionCode = major×10000 + minor×100 + patch`); the workflow never bumps or commits it.

## Workflow map

| Workflow | When it runs | Purpose |
|----------|----------------|---------|
| [CI](../.github/workflows/ci.yml) | PR + push to `main` | Android `test`, `detekt`, `lint` |
| [iOS](../.github/workflows/ios.yml) | PR (iOS / `version.properties` paths) | Simulator build, `MorningGuardCore` tests |
| [Release](../.github/workflows/release.yml) | Tag `v*` or manual | Signed Android AAB (+ optional Play internal draft) |
| [iOS Release](../.github/workflows/ios-release.yml) | Tag `v*` or manual | Archive + TestFlight upload |

**Ship a version:** merge to `main`, wait for **CI** (and **iOS** on the PR) to pass, bump `version.properties` if needed, push `main`, then tag and push **only the tag** (or push `main` and tag in one go knowing you will get up to four runs). Prefer `git push origin main`, confirm green, then `git tag vX.Y.Z && git push origin vX.Y.Z` so store workflows are not stacked on top of redundant `main` checks you already saw on the PR.

## One-time: repository secrets

From a machine that already has a working `local.properties` and upload keystore:

```bash
./scripts/push-github-secrets.sh --dry-run   # names only; fails if required keys missing
./scripts/push-github-secrets.sh
./scripts/push-github-secrets.sh --play ~/path/to/play-service-account.json   # optional Play upload
```

The script reads values from `local.properties`, never prints them, and pipes each value to `gh secret set` on stdin (not as CLI arguments). It base64-encodes the keystore file referenced by `RELEASE_STORE_FILE` (relative paths are resolved from the repo root) into `ANDROID_KEYSTORE_B64`.

### Secret names

| Secret | Source | Purpose |
|--------|--------|---------|
| `ANDROID_KEYSTORE_B64` | `RELEASE_STORE_FILE` (file contents, base64) | Upload keystore on the CI runner |
| `RELEASE_STORE_PASSWORD` | `local.properties` | Keystore password |
| `RELEASE_KEY_ALIAS` | `local.properties` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | `local.properties` | Key password |
| `SUPABASE_URL` | `local.properties` | `BuildConfig` / auth |
| `SUPABASE_ANON_KEY` | `local.properties` | `BuildConfig` / auth |
| `GOOGLE_WEB_CLIENT_ID` | `local.properties` | Google Sign-In |
| `BIBLE_PROXY_BASE_URL` | `local.properties` | Bible chapter proxy base URL |
| `PLAY_CLOUD_PROJECT_NUMBER` | `local.properties` | Play Integrity (not a cryptographic secret) |
| `PRIVACY_POLICY_URL` | `local.properties` | Required for `bundleFullRelease` |
| `SENTRY_DSN` | `local.properties` | Optional crash reporting |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON key file (`--play`) | Optional Play Console upload |

Local Mac builds are unchanged: keep using `local.properties` and a filesystem path for `RELEASE_STORE_FILE`.

## Google Play service account (optional)

Used only to upload the AAB to the **internal testing** track as a **draft** release (never production, never completed rollout).

1. In [Google Cloud Console](https://console.cloud.google.com/), create a service account (or reuse one) for the Play Android Developer API.
2. Enable **Google Play Android Developer API** for the project.
3. Create a JSON key and download it.
4. In [Play Console](https://play.google.com/console) → **Users and permissions** → invite the service account email.
5. Grant access **only** to app **Morning Guard** (`com.morningguard.app`) with permission to **Release to testing tracks** (and view app information). Do not grant production release or all-apps access unless you intend to.
6. Push the JSON with `./scripts/push-github-secrets.sh --play /path/to/key.json`.

## Release notes (optional)

To send “what’s new” text with the Play draft, add localized files under:

```text
release-notes/
  en-US/
    default.txt
```

If `release-notes/` is absent, the Play upload still runs without custom notes.

## Triggering a release

Workflow file: [`.github/workflows/release.yml`](../.github/workflows/release.yml)

| Trigger | Behavior |
|---------|----------|
| **Tag push** `v*` (e.g. `v1.0.7`) | Build AAB, upload artifacts; Play draft upload runs if `PLAY_SERVICE_ACCOUNT_JSON` is set |
| **workflow_dispatch** | Same build; optional input **Upload to Play** (default on); skipped if the Play secret is missing |

Artifacts (per run):

- `morning-guard-full-release-<ref>` — `app-full-release.aab`
- `morning-guard-r8-mapping-<ref>` — ProGuard/R8 `mapping.txt` (if produced)
- `morning-guard-native-symbols-<ref>` — native debug symbols zip (if produced)

When `PLAY_SERVICE_ACCOUNT_JSON` is set, the Play upload step also sends R8 `mapping.txt` and `native-debug-symbols.zip` (when Gradle produces it) so Play Console can deobfuscate Java/Kotlin and native crashes.

Release builds set `ndk.debugSymbolLevel = FULL` and pin NDK **27.0.12077973** (installed in the Release workflow). Native symbols are only extractable if packaged `.so` files still contain debug metadata; many Maven AARs (including Sentry) ship stripped libraries, so the zip may be absent and Play may keep showing a native-symbols warning until a future upload includes symbols. That warning does not block internal testing; the next tagged release will pick up this pipeline automatically—no extra release is required for the Gradle/CI change alone.

Example tag release:

```bash
git tag v1.0.7
git push origin v1.0.7
```

Manual run: GitHub → **Actions** → **Release** → **Run workflow**.

## CI

Pull-request and `main` verification is [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) (`test`, `detekt`, `lint` only). Release APK/AAB artifacts come from the **Release** workflow on tags, not from CI.

## iOS TestFlight

See [docs/ios/testflight.md](ios/testflight.md). Tag `v*` also runs [`.github/workflows/ios-release.yml`](../.github/workflows/ios-release.yml) after App Store Connect API secrets are set via `./scripts/push-ios-github-secrets.sh`.
