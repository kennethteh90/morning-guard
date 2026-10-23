# Sentry setup — Morning Guard (Android)

Organization: **[kenneth-t3](https://sentry.io/organizations/kenneth-t3/)**

The app already ships the Sentry Android SDK and reports:

- Uncaught crashes
- Non-fatal Supabase sync failures (`feature=supabase_sync`)
- Breadcrumbs for sync runs

Monitoring is **off** until `SENTRY_DSN` is set at build time. Sync errors are **not** shown in the UI.

---

## Option A — Sentry web UI (fastest)

1. Open **[Create project](https://sentry.io/organizations/kenneth-t3/projects/new/)** (sign in if needed).
2. Platform: **Android**.
3. Project name: **Morning Guard** → slug **`morning-guard`** (or accept the auto slug).
4. Skip SDK install steps in the wizard (this repo is already configured).
5. Go to **Settings → Client Keys (DSN)** and copy the **DSN**.

If Sentry shows a **run code** (e.g. for AI-assisted setup), enter it in **their** browser wizard only — it pairs that session to your org. You still need the **DSN** from the project settings above.

---

## Option B — Script (after you have an auth token)

1. Create an auth token:  
   [User auth tokens](https://sentry.io/settings/account/api/auth-tokens/) or an org **Internal Integration** with `project:write` and `org:read`.

2. Run:

```bash
export SENTRY_AUTH_TOKEN="sntrys_…"
export SENTRY_ORG="kenneth-t3"          # default in script
export SENTRY_PROJECT_SLUG="morning-guard"  # optional
./scripts/sentry-bootstrap.sh
```

The script creates the Android project (if missing), prints the **DSN**, and shows `gh secret set` / `local.properties` lines.

---

## Wire the DSN into builds

### Local debug

Add to `local.properties` (never commit):

```properties
SENTRY_DSN=https://…@o….ingest….sentry.io/…
```

Rebuild:

```bash
./gradlew :app:installDebug
```

### GitHub Actions (CI / release)

```bash
gh secret set SENTRY_DSN --repo kennethteh90/morning-guard
```

Workflows already pass `SENTRY_DSN` into `local.properties` when the secret exists (`.github/workflows/ci.yml`, `release.yml`).

---

## Verify

1. Install a build **with** DSN set; open the app once.
2. In Sentry → **Issues**, filter **environment:debug** (debug builds) or **production** (release).
3. Optional: trigger a sync failure (wrong Supabase URL while signed in) and look for tag **`feature:supabase_sync`**.
4. Logcat: `UserDataSync` for local detail.

---

## Cursor MCP (optional)

Sentry hosts a remote MCP server (OAuth — no auth token in the repo). This project includes:

```json
"sentry": {
  "type": "http",
  "url": "https://mcp.sentry.dev/mcp/kenneth-t3/morning-guard"
}
```

in `.cursor/mcp.json` (scoped to org **kenneth-t3**, project **morning-guard**). If your project slug differs, edit the URL.

1. **Cursor → Settings → MCP** — confirm **sentry** appears (or reload MCP).
2. Complete the **browser OAuth** prompt when Cursor asks you to sign in to Sentry.
3. Test in chat: *“What are the latest unresolved issues in this Sentry project?”*

Docs: [Sentry MCP](https://docs.sentry.io/ai/mcp/)

---

## Optional repo defaults

Copy `.sentryclirc.example` → `.sentryclirc` and add your token under `[auth]` for `sentry-cli` (source maps, releases). Do **not** commit `.sentryclirc` with a token.

---

## Sentry project settings (recommended)

| Setting | Suggestion |
|--------|------------|
| **Alerts** | Notify on new issues or spikes in `supabase_sync` |
| **Security & Privacy** | Align default PII / IP collection with your privacy policy |
| **Release health** | Later: add Sentry Gradle plugin + upload ProGuard mappings for release stacks |
