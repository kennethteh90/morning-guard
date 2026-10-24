# Legacy plaintext highlight notes (optional operator check)

Morning Guard now stores `bible_highlights.note` as `mgenc1:` ciphertext. Clients re-encrypt any legacy plaintext on pull and on device migration.

To **count** rows that may still be plaintext in Postgres (read-only):

```sql
select count(*) as legacy_plaintext_notes
from public.bible_highlights
where note <> ''
  and note not like 'mgenc1:%';
```

Do not run destructive updates from the server. Let signed-in clients upsert encrypted notes on next sync.
