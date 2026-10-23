# Morning Guard — Blockers

| ID | Topic | Status | Notes |
|----|--------|--------|-------|
| B1 | Google OAuth client IDs | Open | Set `GOOGLE_WEB_CLIENT_ID` in `local.properties` / CI secrets; configure the same Web client in Supabase Auth → Google provider. |
| B2 | Sentry DSN | Open | Set `SENTRY_DSN` in CI secrets / `local.properties` for release error reporting. Debug builds skip init when empty. |
| B3 | Supabase project | Open | Set `SUPABASE_URL` and `SUPABASE_ANON_KEY` via `local.properties` or CI env. Auth screens show “unconfigured” until set. |
| B4 | Notification listener in CI | Accepted | Full listener behavior requires device/emulator; covered by unit tests + manual QA. |
| B5 | Remote repo host | Resolved | Public GitHub repo: https://github.com/kennethteh90/morning-guard |
| B6 | Play Store listing | Open | Signing + AAB build wired; see `site/play-store-launch/index.html` for Console, policy, and upload steps. |
