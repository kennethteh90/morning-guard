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
| **Database → Tables** | `morning_day_records`, `bible_highlights`, and `user_note_crypto` show **RLS enabled** (and **forced** after hardening migrations) |
| **Authentication → Providers** | Email on for magic link; Google off until you need it |
| **Authentication → URL configuration** | `morningguard://auth` in Redirect URLs; Site URL = your HTTPS landing page if deployed |
| **Authentication → Email Templates → Magic link** | Subject/body name **Morning Guard** (see below) |
| **Authentication → Rate limits** | Default SMTP is ~**2 emails/hour** per project; with custom SMTP you can raise **Email sent** (e.g. 30/hr). The app also enforces a **60s** resend cooldown per address. |
| **API → Data API** | Do **not** enable “automatically expose new tables” |
| **Project Settings → API keys** | App uses **publishable/anon** only; never ship **secret** / `service_role` in the APK |
| **Network** | Optional: restrict Database IP if you only use Dashboard + PostgREST (not direct Postgres from home) |

### Production RLS verification (read-only SQL)

Run in **SQL Editor** before or after `db push` (no writes). Expected for Morning Guard user tables:

- `rls_enabled` and `rls_forced` both **true**
- Policies on each table: **select / insert / update** (and **delete** where the app deletes rows), all scoped with `auth.uid() = user_id` (or equivalent) and role **`authenticated`**
- **`anon`** should have **no** table privileges on these tables; **`authenticated`** should have only the privileges matching the policies (including **`DELETE`** on `morning_day_records` and `bible_highlights` after migration `20261102143000_harden_user_table_grants.sql`)

**1. RLS flags**

```sql
select
    n.nspname as schema_name,
    c.relname as table_name,
    c.relrowsecurity as rls_enabled,
    c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in (
      'morning_day_records',
      'bible_highlights',
      'user_note_crypto'
  )
order by c.relname;
```

**2. Policies**

```sql
select
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual as using_expression,
    with_check
from pg_policies
where schemaname = 'public'
  and tablename in (
      'morning_day_records',
      'bible_highlights',
      'user_note_crypto'
  )
order by tablename, policyname;
```

**3. Table grants (anon must be empty)**

```sql
select
    grantee,
    table_name,
    string_agg(privilege_type, ', ' order by privilege_type) as privileges
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in (
      'morning_day_records',
      'bible_highlights',
      'user_note_crypto'
  )
  and grantee in ('anon', 'authenticated', 'public')
group by grantee, table_name
order by table_name, grantee;
```

After applying pending migrations, re-run query **3** and confirm `authenticated` includes **`DELETE`** on `morning_day_records` and full CRUD on `user_note_crypto`, and that **`anon`** has no rows.

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
