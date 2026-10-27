# CI workflows security audit (2026-10-27)

Public repo: `kennethteh90/morning-guard`. Scope: all GitHub Actions workflows, secret usage, artifacts, and supply chain.

## Workflows

| Workflow | Trigger | Secrets | Signing / upload | Notes |
|----------|---------|---------|------------------|-------|
| `ci.yml` | PR + `main` | Client config only (`SUPABASE_*`, Sentry, Google web client, proxy URL, Play Integrity project number). **No** keystore or Play/iOS signing secrets. | None (`test`, `detekt`, `lint`) | Fork PRs do not receive secrets (GitHub default). |
| `ios.yml` | PR (iOS paths) | None | `CODE_SIGNING_ALLOWED=NO` simulator build | Safe for untrusted PR code. |
| `release.yml` | `main` + `version.properties` or manual | Keystore via `ANDROID_KEYSTORE_B64` (decoded to `$RUNNER_TEMP`), passwords, Play JSON optional, client config | Signed AAB; optional Play internal via `r0adkll/upload-google-play@v1.1.3` | `permissions: contents: read`. Artifacts retention 7 days. |
| `ios-release.yml` | Tag `v*` or manual | ASC API key (file on runner), distribution P12, client xcconfig secrets | TestFlight via Fastlane; ephemeral CI keychain | `permissions: contents: read`. IPA artifact on success only, 7-day retention. ASC base64 env **not** passed to Fastlane when `.p8` path is set. |

## GitHub secret inventory (by consumer)

**Android Release (`release.yml`):** `ANDROID_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `PRIVACY_POLICY_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID`, `BIBLE_PROXY_BASE_URL`, `PLAY_CLOUD_PROJECT_NUMBER`, optional `SENTRY_DSN`, optional `PLAY_SERVICE_ACCOUNT_JSON`.

**iOS Release (`ios-release.yml`):** `IOS_DEVELOPMENT_TEAM`, `APP_STORE_CONNECT_API_KEY_*`, `IOS_DISTRIBUTION_CERTIFICATE_P12`, `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD`, shared `SUPABASE_*`, `BIBLE_PROXY_BASE_URL`, optional `SENTRY_DSN`.

**CI only:** Same client-config names as above (no signing secrets).

**Not used by any workflow (worker / local only):** `API_BIBLE_KEY`, `BIBLE_PROXY_TOKEN` — keep in GitHub only if other automation uses them; they are **not** injected into Android/iOS CI builds.

**Removed 2026-10-27:** `RELEASE_KEYSTORE_BASE64` (superseded by `ANDROID_KEYSTORE_B64`), `RELEASE_STORE_FILE` (obsolete path-string approach).

## Expected client-visible values

Release Android AAB and iOS IPA embed **Supabase URL**, **anon key**, **Bible proxy base URL**, **Sentry DSN**, **Google web client ID**, and **privacy policy URL** — same as a store build. Protection is RLS, proxy auth (App Attest on iOS), and Play Integrity on Android — not hiding the anon key.

## Supply chain

Repository Actions setting: **selected** actions — GitHub-owned, Marketplace verified publishers, plus allow patterns `maxim-lobanov/*` and `r0adkll/*` (Xcode setup and Play upload).

Third-party actions in use: `actions/checkout@v4`, `actions/upload-artifact@v4`, `ruby/setup-ruby@v1`, `maxim-lobanov/setup-xcode@v1`, `r0adkll/upload-google-play@v1.1.3`.

## Validation scripts

```bash
./scripts/validate-android-release-setup.sh
./scripts/validate-ios-release-setup.sh
```

## Residual risks (accepted or follow-up)

- **Public repo artifacts:** Anyone with read access can download workflow artifacts for 7 days. They contain release binaries (client keys only), not upload keystores or ASC `.p8`.
- **Tag / workflow_dispatch:** Collaborators with write access can ship to TestFlight or trigger Android release; no GitHub Environment approval gate (declined for now).
- **Legacy doc:** `docs/security-audit.md` § signing still describes pre-`ANDROID_KEYSTORE_B64` behavior; treat this file as the CI signing source of truth.
