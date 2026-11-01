-- Idempotent highlight upsert for devices that push before a server id is assigned locally.
create unique index if not exists bible_highlights_user_client_id_uidx
    on public.bible_highlights (user_id, client_id)
    where client_id is not null;

comment on index public.bible_highlights_user_client_id_uidx is
    'One cloud row per local Room id; enables upsert without server id on first push.';
