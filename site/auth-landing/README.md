# Morning Guard public site (Cloudflare Pages)

Host this folder on **`https://morning-guard.codingagents.cc`** — same domain used for Supabase **Site URL**, Play **website**, magic-link fallback, and **privacy policy**.

## Pages

| Path | File | Use |
|------|------|-----|
| `/` | `index.html` | Marketing landing; auto-redirects to `morningguard://auth` when `?code=` is present |
| `/privacy/` | `privacy/index.html` | Public privacy policy (`PRIVACY_POLICY_URL`) |
| `/.well-known/assetlinks.json` | App Links for `com.morningguard.app` |

Shared styles: `styles/site.css`. Landing screenshots: `assets/screenshots/` (copy from `scripts/store-screenshots/out/morning-guard_play_phone_slide-*.png` after re-running compose).

## Build config

**Contact:** Play / privacy contact **`kenneth@codingagents.cc`** (footer + privacy policy).

**App Links (`assetlinks.json`):** use the **SHA-256 certificate fingerprint** of the cert that signs installs — this is **not** a PEM public key or your upload keystore password.

1. **Play Store / internal testing (required for real users):** Play Console → **Protected with Play** → **key management** (direct link for Morning Guard):
   `https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/keymanagement`
   Under **App signing key certificate**, copy **SHA-256 certificate fingerprint** (not the upload key).
2. **Upload keystore only (local sideload):** `./scripts/print-android-signing-sha256.sh`

Then:

```bash
./scripts/set-assetlinks-sha256.sh 'AA:BB:CC:…:ZZ'
./site/auth-landing/deploy.sh
```

Verify on device: `adb shell pm get-app-links com.morningguard.app`

## Supabase dashboard

1. **Authentication → URL configuration**
   - **Site URL:** `https://morning-guard.codingagents.cc`
   - **Redirect URLs:** keep `morningguard://auth`, and add:
     - `https://morning-guard.codingagents.cc`
     - `https://morning-guard.codingagents.cc/**` (if wildcards are enabled)

2. The Android app sends `redirectUrl = morningguard://auth` when requesting a magic link, so many flows open the app directly. This site is a fallback when the link opens in Chrome first.

Script helper: `./scripts/configure-supabase-auth-urls.sh`

## Deploy on Cloudflare Pages

### Git-connected (recommended)

1. Cloudflare dashboard → **Workers & Pages** → **Create** → **Pages** → Connect to GitHub `morning-guard`.
2. **Build settings:** Framework preset **None**; build command empty; **Output directory:** `site/auth-landing`.
3. **Custom domain:** `morning-guard.codingagents.cc` (DNS CNAME to Pages as Cloudflare instructs).

### Wrangler (from this repo)

```bash
./site/auth-landing/deploy.sh
./scripts/attach-pages-domain.sh   # if the custom domain is not attached yet
```

No build step — static HTML/CSS only.

## App build secret

After deploy, set in `local.properties` and GitHub Actions:

```properties
PRIVACY_POLICY_URL=https://morning-guard.codingagents.cc/privacy/
```

Then `./scripts/push-github-secrets.sh` (or `gh secret set PRIVACY_POLICY_URL`).

## Local preview

```bash
cd site/auth-landing && python3 -m http.server 8787
```

Open `http://127.0.0.1:8787/` and `http://127.0.0.1:8787/privacy/`. Magic-link redirect can be tested with `/?code=test` (app must handle the code).
