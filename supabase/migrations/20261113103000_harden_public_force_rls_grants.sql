-- Re-affirm FORCE RLS and anon/public revokes on all user tables (idempotent).
-- Apply via Supabase migration deploy before relying on these settings in production.

alter table if exists public.morning_day_records force row level security;
alter table if exists public.bible_highlights force row level security;
alter table if exists public.user_note_crypto force row level security;

revoke all on table public.morning_day_records from anon, public;
revoke all on table public.bible_highlights from anon, public;
revoke all on table public.user_note_crypto from anon, public;

grant select, insert, update, delete on table public.morning_day_records to authenticated;
grant select, insert, update, delete on table public.bible_highlights to authenticated;
grant select, insert, update, delete on table public.user_note_crypto to authenticated;

grant usage on sequence public.bible_highlights_id_seq to authenticated;
