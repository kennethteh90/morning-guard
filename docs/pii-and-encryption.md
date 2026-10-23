# PII, notes, and encryption

Morning Guard stores **email** (via Supabase Auth), **highlight notes**, **notification digest text**, and **emergency passphrase hashes** on device and (when the user opts into cloud sync) in Supabase Postgres.

## What Supabase provides by default

| Layer | What you get |
|-------|----------------|
| **Transport** | HTTPS/TLS between app and Supabase APIs. |
| **At rest (platform)** | Postgres and Auth data encrypted at rest on Supabase infrastructure (see [Supabase security](https://supabase.com/security)). |
| **Access control** | Row Level Security (RLS) so each `auth.uid()` only reads/writes their rows. |
| **Auth email** | Stored in `auth.users` (GoTrue). Required for magic link; not encrypted per-field by you. |

Supabase does **not** automatically apply **application-level field encryption** for your `note` column or digest text. Anyone with a valid user JWT and your table schema can read plaintext column values. The **service role** bypasses RLS and must never ship in the app.

There is no Dashboard toggle for “symmetric encrypt all user notes.” That is an **app design** choice.

## Patterns for sensitive fields

### 1. Local-only (no cloud PII)

User chooses **local storage mode**: highlights, streaks, and settings stay on device; email is never required. Backups are **encrypted files** the user controls (see `docs/local-backup-plan.md`).

**Best for:** users who do not want account email or cloud copies of notes.

### 2. Client-side encryption before sync (recommended if cloud + private notes)

- Derive a key from a user secret (export password or device-bound key + recovery).
- Encrypt `note` (and optionally `excerpt`) with **AES-GCM** in the app **before** `upsert` to `bible_highlights`.
- Store only ciphertext + nonce (and optional key id) in Postgres.
- Supabase still sees ciphertext, not plaintext notes.

**Trade-offs:** key recovery is hard; search in SQL on note text is impossible unless you index hashes/tags; you must version the encryption scheme.

#### Where does the AES key live? (reinstall & safety)

| Strategy | Survives app reinstall? | Safe if phone is stolen? |
|----------|-------------------------|---------------------------|
| **Password-derived only** (like local `.mgbackup`) | Yes, if user remembers password | Yes, if password is strong |
| **Android Keystore only** | Often **no** (key tied to install) | Better against casual backup extraction |
| **Keystore + user password** (wrap DEK) | Password can unlock after reinstall | Strongest practical mobile pattern |

Nothing in the APK is secret: a rooted device or backup dump can be attacked offline. **Keystore** protects keys from other apps and casual file copy, not a determined attacker with the unlocked phone.

**Reinstall:** Keystore keys are usually wiped with uninstall. For cloud-encrypted notes you need either (a) the user re-enters a **recovery passphrase** to derive the same key, or (b) accept that old ciphertext in Supabase is unreadable until they restore from an encrypted local backup.

**Not the same as emergency bypass:** that flow stores only a **hash** of the passphrase locally, not the note encryption key.

### 3. Postgres / Supabase Vault (server-side)

- [Supabase Vault](https://supabase.com/docs/guides/database/vault) and extensions like **pgsodium** can encrypt at the database layer with keys managed in the project.
- Usually requires **Edge Functions or server-side logic** to encrypt/decrypt; the mobile app should not hold the master key.
- Good when a trusted backend performs crypto; not a one-click mobile feature.

### 4. Minimize what you sync

- Sync **settings + streak metadata**; keep **notification bodies** local only (already a good default for backups).
- Do not log email or note text to Sentry (scrub breadcrumbs).

## Morning Guard today

| Data | Local | Cloud (signed in) | Encrypted E2E |
|------|-------|-------------------|---------------|
| Email | Session only | `auth.users` | No (Auth) |
| Highlight notes | Room (`mgenc1:` when enabled) | `bible_highlights.note` ciphertext | **Yes** (optional, Settings) |
| Note DEK wrap | `note_crypto.json` + Keystore | `user_note_crypto` password wrap | Password + Keystore |
| Digest notifications | Room | Not synced | N/A |
| Emergency passphrase | Salted hash | Not synced | Hash only |

**Local backup files** use password-based AES-GCM (`LocalBackupRepository`).

## Practical roadmap

1. **Shipped:** `DataStorageMode.LOCAL_ONLY` + encrypted local backup export/import.
2. **Next:** optional `EncryptNotesBeforeSync` with key in Android Keystore + recovery phrase.
3. **Later:** custom SMTP + minimal PII in Auth (still need email for magic link unless you add anonymous → link flow).

## References

- [Supabase Auth rate limits](https://supabase.com/docs/guides/auth/rate-limits)
- [Auth SMTP](https://supabase.com/docs/guides/auth/auth-smtp)
- [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Vault](https://supabase.com/docs/guides/database/vault)
