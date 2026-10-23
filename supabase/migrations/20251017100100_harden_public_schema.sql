-- Hardening defaults for Morning Guard (aligns with dashboard: RLS on, no auto-expose)

-- Ensure RLS stays on if tables already existed
alter table if exists public.morning_day_records force row level security;
alter table if exists public.bible_highlights force row level security;

-- PostgREST: only authenticated JWT can touch user tables (policies above gate rows).
-- No policies for anon → no row access even if grants existed.

-- Optional: restrict API max rows (mirror dashboard / config.toml max_rows = 1000)
