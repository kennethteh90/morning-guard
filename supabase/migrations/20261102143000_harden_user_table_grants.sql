-- Harden grants and RLS on user_note_crypto; enable morning_day_records DELETE for own-row policy.
-- Safe to re-run. Do not apply to production until reviewed and deployed via Supabase migration flow.

-- user_note_crypto was missing FORCE RLS, explicit revokes, and authenticated-only grants/policies.
alter table if exists public.user_note_crypto force row level security;

revoke all on table public.user_note_crypto from anon, public;
grant select, insert, update, delete on table public.user_note_crypto to authenticated;

drop policy if exists "Users read own note crypto" on public.user_note_crypto;
create policy "Users read own note crypto"
    on public.user_note_crypto
    for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "Users insert own note crypto" on public.user_note_crypto;
create policy "Users insert own note crypto"
    on public.user_note_crypto
    for insert
    to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "Users update own note crypto" on public.user_note_crypto;
create policy "Users update own note crypto"
    on public.user_note_crypto
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "Users delete own note crypto" on public.user_note_crypto;
create policy "Users delete own note crypto"
    on public.user_note_crypto
    for delete
    to authenticated
    using (auth.uid() = user_id);

-- Delete-own policy exists but original grants omitted DELETE on morning_day_records.
grant delete on table public.morning_day_records to authenticated;
