# Morning Guard Bible cache (Cloudflare Worker + KV)

Edge proxy for Bible chapters. The Android app and iOS app call this worker when `BIBLE_PROXY_BASE_URL` is set. **`API_BIBLE_KEY` never ships in the app.**

## Access model (2026-10-22)

| Translation | `GET /v1/chapter` auth |
|-------------|-------------------------|
| **WEB, KJV,** and other open ids | None (IP rate limit only) |
| **NIV, NKJV, NLT** | Play Integrity (Android) or App Attest (iOS), **or** legacy `X-Morning-Guard-Token` during rollout |

Licensed requests use a one-time challenge:

1. Client `POST /v1/challenge` → `{ "nonce": "…" }` (KV TTL 2 minutes).
2. Client calls `GET /v1/chapter?translation=niv&…` with that nonce plus attestation headers.
3. Worker consumes the nonce on first successful licensed auth.

**Android (Play Integrity):** `X-Morning-Guard-Nonce`, `X-Morning-Guard-Play-Integrity`. App uses `PLAY_CLOUD_PROJECT_NUMBER` in `local.properties` / CI — not a secret.

**iOS (App Attest):** `X-Morning-Guard-Nonce`, `X-Morning-Guard-App-Attest-Key-Id`, `X-Morning-Guard-App-Attest-Assertion` (and attestation on first registration). Simulator cannot load licensed translations from the proxy.

**Legacy rollout:** Keep worker secret `BIBLE_PROXY_TOKEN` until store builds send attestation. When ready, set `BIBLE_PROXY_LEGACY_TOKEN_DISABLED=true` on the worker (or secret value `true`).

`GET /health` stays public.

**Rate limit:** 60 requests / minute / IP (`CF-Connecting-IP`), KV-backed → **429** when exceeded.

## One-time Cloudflare setup

1. `npm install` in this directory.
2. KV is bound in `wrangler.jsonc` (`BIBLE_CACHE`). Create if needed:

   ```bash
   npx wrangler kv namespace create BIBLE_CACHE
   ```

3. **Required secrets** (from `workers/bible-cache`):

   ```bash
   npx wrangler secret put API_BIBLE_KEY
   npx wrangler secret put API_BIBLE_LICENSED_ENABLED   # true
   ```

4. **Attestation (production licensed path):**

   ```bash
   npx wrangler secret put PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON   # Google SA JSON, playintegrity scope
   npx wrangler secret put PLAY_APP_SIGNING_CERT_SHA256          # Play App Signing cert SHA-256 (hex)
   npx wrangler secret put APPLE_TEAM_ID
   ```

   Optional vars (defaults shown in code): `PLAY_INTEGRITY_PACKAGE_NAME` (`com.morningguard.app`), `APPLE_APP_ATTEST_BUNDLE_ID` (`com.morningguard.app`).

5. **Rollout only** (remove after clients update):

   ```bash
   npx wrangler secret put BIBLE_PROXY_TOKEN
   ```

6. Optional bible_id overrides:

   ```bash
   npx wrangler secret put API_BIBLE_NIV_ID
   npx wrangler secret put API_BIBLE_NKJV_ID
   npx wrangler secret put API_BIBLE_NLT_ID
   ```

7. Deploy:

   ```bash
   npm run deploy
   ```

From repo root:

```bash
./workers/bible-cache/scripts/wrangler.sh deploy
```

## API

`POST /v1/challenge` → `{ "nonce": "…" }`

`GET /v1/chapter?translation=web&book=John&chapter=3` — no auth headers.

`GET /v1/chapter?translation=niv&book=Genesis&chapter=1` — licensed auth as above.

Response:

```json
{
  "reference": "Genesis 1",
  "body": "…plain fallback…",
  "blocks": [{ "type": "heading", "text": "…" }, { "type": "paragraph", "text": "[1] …" }]
}
```

`GET /health` → `{ "ok": true }`

## Caching policy

| Translations | Source | Workers KV | HTTP `Cache-Control` |
|--------------|--------|------------|----------------------|
| **WEB, KJV, …** (open) | bible-api.com | Yes, no TTL | `public, max-age=86400` |
| **NIV, NKJV, NLT** (licensed) | API.Bible | Yes, **30-day TTL** | `private, max-age=86400` |

Response header **`X-Cache: HIT` or `MISS`**.

## Local dev

```bash
npm run dev
```

`.dev.vars` (gitignored):

```
API_BIBLE_KEY=your-key
API_BIBLE_LICENSED_ENABLED=true
BIBLE_PROXY_TOKEN=dev-token
```

Use `wrangler dev --remote` for production KV.

## Android (`local.properties`)

```
BIBLE_PROXY_BASE_URL=https://morning-guard-bible-cache.<account>.workers.dev/
PLAY_CLOUD_PROJECT_NUMBER=<Google Play Cloud project number>
```

Licensed translations need Play Integrity (internal track or Play-signed builds). Debug sideload without listed debug cert: WEB/KJV via proxy still work; NIV needs internal track or worker debug-cert allowlist (`PLAY_ALLOW_UNRECOGNIZED_DEBUG_CERT` + cert SHA on worker).

## iOS (`ios/Config/Local.xcconfig`)

```
BIBLE_PROXY_BASE_URL = https://morning-guard-bible-cache.<account>.workers.dev
```

No proxy token in the app. Licensed translations require a **physical device** with App Attest.

## Tests

```bash
npm test
```

## Verify after deploy

```bash
# Open chapter — no headers
curl -sS "https://<worker>/v1/chapter?translation=web&book=John&chapter=3" | head

# Licensed without auth — 401
curl -sS -o /dev/null -w "%{http_code}\n" \
  "https://<worker>/v1/chapter?translation=niv&book=Genesis&chapter=1"
```
