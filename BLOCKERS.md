# Morning Guard — Blockers

| ID | Topic | Status | Notes |
|----|--------|--------|-------|
| B1 | Google OAuth client IDs | Open | Requires `google-services.json` / Web client ID in Supabase dashboard for production sign-in. App supports magic link without Google until configured. |
| B2 | Sentry DSN | Open | Set `SENTRY_DSN` in CI secrets / `local.properties` for release error reporting. Debug builds use no-op reporter. |
| B3 | Supabase project | Open | Set `SUPABASE_URL` and `SUPABASE_ANON_KEY` via `local.properties` or CI env. Auth screens degrade gracefully when unset. |
| B4 | Notification listener in CI | Accepted | Full listener behavior requires device/emulator; covered by unit tests + Robolectric where feasible. |
| B5 | Remote repo host | Open | User requested open source + CD on `main`; will create GitHub public repo when scaffold is committed (needs `gh` auth). |
