-- One highlight row may cover multiple verses (comma-separated list + optional range).
alter table public.bible_highlights
    add column if not exists verse_end integer,
    add column if not exists verse_numbers text;

comment on column public.bible_highlights.verse_end is 'Last verse in range; use verse_numbers for exact multi-verse sets.';
comment on column public.bible_highlights.verse_numbers is 'Comma-separated verse numbers, e.g. 2,3,4.';
