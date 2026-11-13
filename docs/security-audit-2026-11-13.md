# Morning Guard — security audit (13 November 2026)

Full-repo review for production readiness. Prior audits: [2026-10-26](security-audit-2026-10-26.md), [2026-10-22](security-audit-2026-10-22.md). UI screens were not modified (parallel UX agents). No live Supabase dashboard, worker deploy, or secret values were accessed.

## Executive summary

| Area | Verdict |
|------|---------|
| Supabase RLS / grants (repo migrations) | **Good** with follow-up deploy for latest hardening migration |
| Android / iOS auth (PKCE, deep links, token storage) | **Good**; Universal Links for magic link remain a product improvement |
| Bible worker (attestation, sessions, rate limits) | **Good after worker deploy** of rate-limit + timeout changes |
| Android manifest / backup / exported components | **Good** (`allowBackup=false`, listener gated by platform permission) |
| iOS entitlements / ATS | **Good** (HTTPS-only clients; Family Controls entitlement present) |
| Sentry / PII | **Good** after sync breadcrumb hardening PR |
| Secrets in git | **None found** (patterns + manual review) |
| GitHub Actions | **Improved** (least-privilege `permissions`, pinned `checkout`) |

## Findings by severity

### Critical

| ID | Title | Status | Notes |
|----|-------|--------|-------|
| — | — | — | No critical issues found in repository code/config reviewed |

### High

| ID | Title | Status | PR / action |
|----|-------|--------|-------------|
| H1 | Legacy `BIBLE_PROXY_TOKEN` may still authorize licensed chapters | **NOT DONE (operator)** | Set worker secret `BIBLE_PROXY_LEGACY_TOKEN_DISABLED=true` after store builds use attestation. Verify with `curl` and `strings` on release AAB/IPA. |
| H2 | Hosted Supabase RLS/grants not verified live | **NOT DONE (manual)** | Run `supabase db push` / dashboard migration for `20261113103000_harden_public_force_rls_grants.sql` and confirm `20261102143000_harden_user_table_grants.sql` applied on project `otabcjkhqcpbstssinxe`. |

### Medium

| ID | Title | Status | PR / action |
|----|-------|--------|-------------|
| M1 | `/v1/challenge` and `/v1/session` had no per-IP rate limits (KV abuse) | **DONE** | Bible worker PR — deploy worker |
| M2 | Play Integrity Google API could hang until CF **504** | **DONE** | Worker fetch timeouts → **503** path; deploy worker |
| M3 | Android sync completion breadcrumbs could include PostgREST bodies via `summary()` | **DONE** | Sentry breadcrumb PR (`sentrySummary()`) |
| M4 | Magic link uses custom scheme on iOS (no Associated Domains) | **NOT DONE (owner)** | Prefer Universal Link to `morning-guard.codingagents.cc` with dedicated auth path; Android already has App Links for HTTPS host |
| M5 | Notification title/body stored in Room until dismiss | **ACCEPTED** | Documented threat model; digest uses `VISIBILITY_SECRET` |
| M6 | Lockout counters in iOS `UserDefaults` / app group (backup-eligible) | **NOT DONE (owner)** | Passphrase hash is Keychain-only; decide whether lockout must survive restore |
| M7 | `npm audit` reports high issues in worker devDependencies | **NOT DONE** | Run `npm audit` in `workers/bible-cache`; address or document accepted Wrangler transitive risk |

### Low

| ID | Title | Status | PR / action |
|----|-------|--------|-------------|
| L1 | `auto_expose_new_tables` unset in `config.toml` | **DONE** | Set `false` in config PR (local/cli alignment) |
| L2 | CI workflows lacked explicit `permissions` and pinned checkout | **DONE** | CI hardening PR |
| L3 | ProGuard keeps entire `io.github.jan.supabase.**` | **ACCEPTED** | Needed for R8 stability; no secrets in those classes |
| L4 | CORS `Access-Control-Allow-Origin: *` on Bible worker | **ACCEPTED** | Licensed content still requires attestation/session; WEB chapters are public cache |
| L5 | Dependabot covers Gradle + Actions only (no npm worker ecosystem) | **NOT DONE** | Optional `dependabot.yml` entry for `workers/bible-cache` |

## Supabase (migrations in repo)

| Table | RLS | FORCE RLS | Policies | `anon` | `authenticated` grants |
|-------|-----|-----------|----------|--------|-------------------------|
| `morning_day_records` | ON | ON | select/insert/update/delete own | REVOKE ALL | SELECT, INSERT, UPDATE, DELETE |
| `bible_highlights` | ON | ON | select/insert/update/delete own | REVOKE ALL | SELECT, INSERT, UPDATE, DELETE |
| `user_note_crypto` | ON | ON | select/insert/update/delete own | REVOKE ALL | SELECT, INSERT, UPDATE, DELETE |

No `SECURITY DEFINER` functions or extra RPCs in migrations. Auth uses standard Supabase Auth (Google, magic link); account deletion via client `deleteCurrentUser()` (no service role in app).

## Auth & session storage

- **Android:** PKCE deep link via `morningguard://auth` and HTTPS App Link host `morning-guard.codingagents.cc`; `AuthCallbackUri` rejects implicit tokens. Supabase session blob in `KeystoreSessionManager` (AES-GCM + Keystore). Sign-out / delete clears local data via `UserLocalDataCleaner`.
- **iOS:** `AuthSignInCallbackURL` requires `host == auth` and `?code=` (O8 closed). Session via supabase-swift storage; passphrase hash Keychain-only. Sign-out clears highlights and sensitive goal data.

## Bible worker

- Licensed path: legacy token (optional) → session HMAC → fresh Play Integrity / App Attest + one-time nonce.
- Rate limits: 60/min chapter, 30/min challenge, 20/min session per `CF-Connecting-IP`.
- Fail closed: missing cert allowlist, invalid attestation, missing session secret.
- **401 / 504 in Sentry (~13 users):** Clients retry 401/502/503/504 for session exchange; worker now bounds Google calls to reduce 504. Persistent 401 often indicates cert/project mismatch (`PLAY_APP_SIGNING_CERT_SHA256`, `PLAY_CLOUD_PROJECT_NUMBER`) — operator guide in `docs/security-operator-guide.html`.

## Manual deploy / verification checklist

1. **Cloudflare:** `wrangler deploy` for `morning-guard-bible-cache`; confirm secrets; set `BIBLE_PROXY_LEGACY_TOKEN_DISABLED=true` when ready.
2. **Supabase:** Apply pending migrations on `otabcjkhqcpbstssinxe`; spot-check policies in SQL editor.
3. **Sentry:** Confirm licensed-auth noise filtering and no sync body extras in new events after app release.
4. **Store:** Data safety / privacy labels match [pii-and-encryption.md](pii-and-encryption.md).
