-- Password-wrapped DEK for highlight note encryption (client-side AES).
create table if not exists public.user_note_crypto (
    user_id uuid primary key references auth.users (id) on delete cascade,
    password_wrapped_dek text not null,
    salt_base64 text not null,
    wrap_iv_base64 text not null,
    kdf_iterations integer not null default 120000,
    updated_at timestamptz not null default now()
);

alter table public.user_note_crypto enable row level security;

create policy "Users read own note crypto"
    on public.user_note_crypto for select
    using (auth.uid() = user_id);

create policy "Users insert own note crypto"
    on public.user_note_crypto for insert
    with check (auth.uid() = user_id);

create policy "Users update own note crypto"
    on public.user_note_crypto for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Users delete own note crypto"
    on public.user_note_crypto for delete
    using (auth.uid() = user_id);
