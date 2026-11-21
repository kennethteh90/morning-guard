-- PostgREST upsert (on_conflict=user_id,client_id) requires a non-partial unique
-- constraint/index on exactly those columns. Migration 20261101120000 added a
-- partial unique index (WHERE client_id IS NOT NULL), which Postgres does not
-- match for ON CONFLICT (user_id, client_id) — see Sentry MORNING-GUARD-Q.

-- Keep the newest row per (user_id, client_id) when client_id is set.
delete from public.bible_highlights as keeper
    using public.bible_highlights as dup
where keeper.user_id = dup.user_id
  and keeper.client_id is not null
  and keeper.client_id = dup.client_id
  and keeper.id < dup.id;

drop index if exists public.bible_highlights_user_client_id_uidx;

alter table public.bible_highlights
    drop constraint if exists bible_highlights_user_client_id_key;

alter table public.bible_highlights
    add constraint bible_highlights_user_client_id_key
    unique (user_id, client_id);

comment on constraint bible_highlights_user_client_id_key on public.bible_highlights is
    'Enables PostgREST merge-duplicates upsert on user_id + client_id for first push.';