-- Verse-level highlights (YouVersion-style); color + optional note
alter table public.bible_highlights
    add column if not exists verse integer,
    add column if not exists color_key text not null default 'yellow',
    add column if not exists note text not null default '';

comment on column public.bible_highlights.verse is 'Chapter verse number; null for legacy partial-text highlights.';
comment on column public.bible_highlights.color_key is 'Highlight swatch key (yellow, green, blue, pink, purple).';
