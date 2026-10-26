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

## Local TestFlight (recommended while CI signing is tuned)

From repo root (uses `Local.xcconfig` + `~/Keys/morning-guard/AuthKey_*.p8` if present):

```bash
export APP_STORE_CONNECT_API_ISSUER_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
./scripts/ios-testflight-local.sh
```

Archive only (no upload): `SKIP_UPLOAD=1 ./scripts/ios-testflight-local.sh`

Issuer ID: App Store Connect → **Users and Access** → **Integrations** (top of the API keys page). Key ID is in the `AuthKey_<ID>.p8` filename.

Sign in to Xcode with the same Apple ID as your developer team before the first run. Local scripts use your **Xcode login** and `-allowProvisioningUpdates`. Runners have no Apple ID session: that flag cloud-signs and returns `FORBIDDEN`, and `xcodebuild -exportArchive` without it waits forever for a login dialog. CI imports the Apple Distribution `.p12` into its own keychain and exports with manual App Store profiles.

## Blockers CI cannot fix

- **Family Controls distribution** entitlement must be approved before a store/TestFlight build can use shields. Request as Account Holder: [Family Controls entitlement](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement).
- **Bundle ID** `com.morningguard.app` and extensions must exist in the developer portal with App Group `group.com.morningguard.shared`.
- **Licensed Bible on device:** Cloudflare worker needs `APPLE_TEAM_ID` (see `workers/bible-cache/README.md`).
- **Cloud-managed distribution certificates:** If export fails with `FORBIDDEN_ERROR` / “cloud-managed distribution certificates”, the API key’s user needs that access in App Store Connect (Account Holder or Admin), or upload from a Mac with `./scripts/ios-testflight-local.sh`.
- **CI distribution cert:** GitHub Actions cannot use your Mac login keychain. Export **only** **Apple Distribution** (not Apple Development) from Keychain Access → My Certificates → expand **Apple Distribution** → select certificate + private key → File → Export Items → `.p12`. Then:

  ```bash
  IOS_DISTRIBUTION_CERTIFICATE_P12_PATH=~/Downloads/YourExport.p12 \
  IOS_DISTRIBUTION_CERTIFICATE_PASSWORD='p12-export-password' \
  ./scripts/push-ios-distribution-cert-secret.sh
  ```

  If CI fails with “maximum number of Distribution certificates”, revoke unused **Apple Distribution** certs at [developer.apple.com](https://developer.apple.com/account/resources/certificates/list) (orphans from failed CI runs), then re-run **iOS Release**.

## Device testing without CI

See [ios/DEVICE-TESTING.md](../../ios/DEVICE-TESTING.md) and `./ios/scripts/open-for-device.sh`.
