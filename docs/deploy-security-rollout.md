# Security rollout checklist (2026-10-22)

Companion to [security-fixes-2026-10-22.md](security-fixes-2026-10-22.md). Use this after merging the implementation.

## 1. Cloudflare bible-cache worker

```bash
cd workers/bible-cache
npm test
npm run deploy
```

Ensure secrets exist (see [workers/bible-cache/README.md](../workers/bible-cache/README.md)):

- `API_BIBLE_KEY`, `API_BIBLE_LICENSED_ENABLED=true`
- `PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON`, `PLAY_APP_SIGNING_CERT_SHA256`, `APPLE_TEAM_ID`
- Keep `BIBLE_PROXY_TOKEN` until old Android builds are rare

Smoke:

```bash
WORKER=https://your-worker.workers.dev
curl -sS "$WORKER/v1/chapter?translation=web&book=John&chapter=3" | head -c 200
curl -sS -o /dev/null -w "%{http_code}\n" "$WORKER/v1/chapter?translation=niv&book=Genesis&chapter=1"
# expect 200 then 401
```

## 2. GitHub Actions

```bash
./scripts/push-github-secrets.sh
```

Confirm `PLAY_CLOUD_PROJECT_NUMBER` is set; remove obsolete `BIBLE_PROXY_TOKEN` secret from the repo if still present.

## 3. Auth landing (Cloudflare Pages)

Redeploy `site/auth-landing` so magic links reject `access_token` in the query string.

## 4. Android internal track

- Release build with `PLAY_CLOUD_PROJECT_NUMBER` + proxy URL
- `strings` on AAB: no `BIBLE_PROXY_TOKEN`
- Manual: SMS not held, WhatsApp held, magic link uses `code` only, NIV loads on internal track

## 5. iOS TestFlight

- Archive → Privacy Report includes UserDefaults reason
- Peace session keeps shields after interval ends with app killed
- NIV on device (not Simulator)

## 6. Cutover

When attestation-only clients dominate:

```bash
npx wrangler secret put BIBLE_PROXY_LEGACY_TOKEN_DISABLED
# value: true
```

Optionally delete `BIBLE_PROXY_TOKEN` from the worker.
