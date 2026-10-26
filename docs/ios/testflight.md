# iOS TestFlight (CI + local)

Automated uploads use **Fastlane** on GitHub Actions (`ios-release.yml`). Version numbers follow `version.properties` (same build number formula as Android).

## One-time: App Store Connect API key

1. [App Store Connect](https://appstoreconnect.apple.com) → **Users and Access** → **Keys** → **+** (Developer role must be Account Holder or Admin).
2. Name e.g. `morning-guard-ci`, access **App Manager**, download the `.p8` once.
3. Note **Key ID** and **Issuer ID** (Users and Access → Integrations).

Push secrets from your Mac (never commit the `.p8`):

```bash
export APP_STORE_CONNECT_API_KEY_ID=XXXXXXXXXX
export APP_STORE_CONNECT_API_ISSUER_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
export APP_STORE_CONNECT_API_KEY_PATH=~/Downloads/AuthKey_XXXXXXXXXX.p8
./scripts/push-ios-github-secrets.sh
```

`IOS_DEVELOPMENT_TEAM` is taken from `ios/Config/Local.xcconfig` unless you export it. Supabase/proxy secrets are copied from `local.properties` when present.

Validate:

```bash
./scripts/validate-ios-release-setup.sh
```

## Trigger a build

| Trigger | Result |
|---------|--------|
| Tag `v*` (e.g. `v1.0.8`) | Build + TestFlight upload |
| **Actions → iOS Release → Run workflow** | Same; uncheck **Upload** to archive only |

After upload, open App Store Connect → your app → **TestFlight** → add **Internal Testing** testers.

## Local Fastlane (optional)

Same env vars as CI, plus `ios/Config/Local.xcconfig` for signing:

```bash
cd ios
export IOS_DEVELOPMENT_TEAM=XXXXXXXXXX
export APP_STORE_CONNECT_API_KEY_ID=...
export APP_STORE_CONNECT_API_ISSUER_ID=...
export APP_STORE_CONNECT_API_KEY="$(base64 < /path/AuthKey.p8 | tr -d '\n')"
bundle install
bundle exec fastlane beta
```

## Blockers CI cannot fix

- **Family Controls distribution** entitlement must be approved before a store/TestFlight build can use shields. Request as Account Holder: [Family Controls entitlement](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement).
- **Bundle ID** `com.morningguard.app` and extensions must exist in the developer portal with App Group `group.com.morningguard.shared`.
- **Licensed Bible on device:** Cloudflare worker needs `APPLE_TEAM_ID` (see `workers/bible-cache/README.md`).

## Device testing without CI

See [ios/DEVICE-TESTING.md](../../ios/DEVICE-TESTING.md) and `./ios/scripts/open-for-device.sh`.
