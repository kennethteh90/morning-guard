# Supabase setup (Morning Guard)

Project ref for Morning Guard: **`otabcjkhqcpbstssinxe`** (`https://otabcjkhqcpbstssinxe.supabase.co`).

## 1. Cursor Supabase MCP

1. Copy [`.cursor/mcp.json.example`](../../.cursor/mcp.json.example) to **`.cursor/mcp.json`** (repo root).
2. Project ref is already set in `.cursor/mcp.json.example` as `otabcjkhqcpbstssinxe` (copy to `.cursor/mcp.json` if needed).
3. In Cursor: **Settings → Tools & MCP** → enable **supabase** → complete **OAuth** (pick the org that owns the project).
4. Restart Cursor if tools do not appear.

**Safer MCP URL (explore schema only):**

```text
https://mcp.supabase.com/mcp?project_ref=otabcjkhqcpbstssinxe&read_only=true&features=database,docs
```

Use **without** `read_only` when you want the agent to run `apply_migration` / `execute_sql`. Keep **manual approval** on each tool call.

Official docs: [Supabase MCP](https://supabase.com/docs/guides/ai-tools/mcp).

### CLI (optional)

```bash
npx supabase login
npx supabase link --project-ref otabcjkhqcpbstssinxe
npx supabase db push
```

`link` prompts for the **database password** you saved when creating the project (not the anon key).

`SUPABASE_URL` in `local.properties` should be `https://otabcjkhqcpbstssinxe.supabase.co`.

## 2. Run migrations (pick one)

### A. Supabase CLI (recommended)

```bash
cd /path/to/morning-guard
npx supabase login
npx supabase link --project-ref otabcjkhqcpbstssinxe
npx supabase db push
```

### B. SQL Editor (Dashboard)

Run in order:

1. [`supabase/migrations/20251017100000_morning_guard_user_data.sql`](../../supabase/migrations/20251017100000_morning_guard_user_data.sql)
2. [`supabase/migrations/20251017100100_harden_public_schema.sql`](../../supabase/migrations/20251017100100_harden_public_schema.sql)

### C. Supabase MCP

After MCP is connected, ask the agent to **apply_migration** or paste the migration files (with your approval on each SQL call).

## 3. Dashboard security checklist

These match how you created the project (Data API on, auto-expose off, auto RLS on):

| Area | Setting |
|------|---------|
| **Database → Tables** | `morning_day_records`, `bible_highlights` show **RLS enabled** |
| **Authentication → Providers** | Email on for magic link; Google off until you need it |
| **Authentication → URL configuration** | `morningguard://auth` in Redirect URLs; Site URL = your HTTPS landing page if deployed |
| **Authentication → Email Templates → Magic link** | Subject/body name **Morning Guard** (see below) |
| **Authentication → Rate limits** | Default SMTP is ~**2 emails/hour** per project; with custom SMTP you can raise **Email sent** (e.g. 30/hr). The app also enforces a **60s** resend cooldown per address. |
| **API → Data API** | Do **not** enable “automatically expose new tables” |
| **Project Settings → API keys** | App uses **publishable/anon** only; never ship **secret** / `service_role` in the APK |
| **Network** | Optional: restrict Database IP if you only use Dashboard + PostgREST (not direct Postgres from home) |

## 4. Branded magic-link email

Default Supabase mail looks generic. Users should see **Morning Guard** in the subject and body.

### Free tier + Supabase’s built-in mail

**You cannot edit email templates** on the free plan while using the default email provider. The Management API and Dashboard return an error like *“Email template modification is not available for free tier projects…”*.

What still helps without paying Supabase:

- **Site URL** is your landing page (`https://morning-guard.codingagents.cc`) — after they tap the link in the generic email, they see Morning Guard branding before the app opens.
- The app’s **Account** screen explains that the message is for Morning Guard sync (streaks / highlights).

To customize the email itself, pick one:

| Path | Cost | What you get |
|------|------|----------------|
| **Custom SMTP** on the same free project | Often free/low from Resend, SendGrid, etc. | Unlocks template editing; set **Sender name** to `Morning Guard` and use [`supabase/templates/magic_link.html`](../../supabase/templates/magic_link.html). [Supabase: custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp) |
| **Upgrade** Supabase plan | Paid | Template editing with Supabase’s mail (if your plan includes it) |

### After custom SMTP (or upgrade)

**Dashboard:** [Authentication → Email Templates → Magic link](https://supabase.com/dashboard/project/otabcjkhqcpbstssinxe/auth/templates) — subject `Sign in to Morning Guard`, body from [`supabase/templates/magic_link.html`](../../supabase/templates/magic_link.html).

**Script** (same PAT as auth URLs):

```bash
export SUPABASE_ACCESS_TOKEN=sbp_...
./scripts/configure-supabase-email-templates.sh
```

Keep `{{ .ConfirmationURL }}` and `{{ .Email }}` in the HTML. Send a new magic link to verify.

## 5. App integration

When signed in (magic link), the app syncs via PostgREST:

- `morning_day_records` ↔ `GoalProgressStore.morningHistory` (on launch, goal complete, emergency bypass)
- `bible_highlights` ↔ Room (on launch, after saving a highlight)

RLS limits rows to `auth.uid()`. Sync is best-effort (failures are ignored locally; data stays on device).

## 6. GitHub / CI

Do **not** commit database passwords or service role keys. For MCP in CI, use a **scoped PAT** and `read_only=true` per [Supabase MCP manual auth](https://supabase.com/docs/guides/ai-tools/mcp#ci-environment).
