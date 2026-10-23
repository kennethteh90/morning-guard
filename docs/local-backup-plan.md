# Local-only mode and encrypted backup plan

Morning Guard already stores gate settings, digest captures, Bible highlights, streaks, and related data in **Room** on device. Supabase is **optional cloud sync** when the user signs in. This document describes how to support users who **do not want to log in** and how to offer **local backup/restore** without relying on your cloud.

## Goals

| Goal | Description |
|------|-------------|
| Local-first | Full app value without an account |
| Optional cloud | Sign-in = sync across devices, not a hard requirement |
| User-owned backup | Export/import a file the user controls (Drive, files app, etc.) |
| Privacy | Minimize sensitive data in backups by default |

## Phase 0 — Product decisions

| Decision | Recommendation |
|----------|----------------|
| Default mode | **Local-first**; sign-in labeled optional (“Sync across devices”) |
| Backup contents | User settings, morning day records, Bible highlights, bypass package list, emergency bypass **metadata** (hashed passphrase, not plaintext) |
| Exclude by default | Raw notification bodies (size + privacy); optional opt-in later |
| Format | Versioned JSON (or CBOR) envelope + **AES** encryption |
| Encryption key | User-chosen export password, or Android Keystore–wrapped key with password for portability |

## Phase 1 — Explicit “local only” path

1. **Onboarding / Settings:** “Continue without account” vs “Sign in to sync”.
2. Persist `dataMode: LocalOnly | CloudSync` in `UserSettings` (DataStore/Room).
3. **Gate Supabase:** All `UserDataSyncService` and auth-gated UI already skip when unsigned; audit screens that imply sign-in is required.
4. **Copy:** Local mode = data stays on this phone unless the user exports a backup.

**Touchpoints (existing code):**

- `AppInitializer` — sync on sign-in only (`syncAllIfSignedIn`).
- `AppRouteViewModel` — onboarding does not need auth today; align messaging.
- `AuthScreen` / Settings — present local-only as first-class.

## Phase 2 — Export / import backup file

### 2.1 Backup module (`core/data`)

- DTO: `MorningGuardBackup(schemaVersion, exportedAt, deviceLabel?, payload)`.
- Payload sections: settings, `morning_day_records`, `bible_highlights`, bypass sets, emergency bypass config (no plaintext secrets).
- `LocalBackupExporter`: read from repositories/DAOs → serialize → encrypt.
- `LocalBackupImporter`: decrypt → validate `schemaVersion` → transactional **replace** or **merge** (user choice).

### 2.2 UI (Settings)

- **Back up to file** — Storage Access Framework `CREATE_DOCUMENT` → write `.mgbackup` (or similar).
- **Restore from file** — `OPEN_DOCUMENT` → confirm dialog → import.
- Show schema version mismatch errors with clear upgrade path.

### 2.3 Tests

- Fixture DB → export → import into empty DB → assert row/count parity.
- Merge tests: highlights + settings conflicts (prefer newer `updatedAt` or last-write-wins per entity).

## Phase 3 — Optional convenience

- **Scheduled backup** to a user-selected folder (WorkManager, weekly).
- **Share backup** intent with security warning (file is sensitive).
- **First sign-in:** “Upload local data to cloud?” one-time merge using existing sync upsert paths.

## Phase 4 — Hardening

- Threat model doc: backup file = sensitive; recommend strong export password.
- `android:allowBackup="false"` remains; Android auto-backup is not a substitute.
- Migration when `schemaVersion` increments (importers for N-1).

## Implementation status

- **Done:** `DataStorageMode` on `UserSettings`, onboarding **Your data** step, `LocalBackupRepository` (AES-GCM + PBKDF2, `.mgbackup`), Settings export/restore.
- **Not yet:** client-side encrypt notes before Supabase sync; merge-on-import; scheduled backups.

## Suggested implementation order

1. ~~`dataStorageMode` + UX copy~~ **done**
2. ~~Backup DTO + export/import MVP~~ **done (replace restore only)**
3. Merge rules + unit tests (**1–2 days**)
4. Auto-backup + cloud upload prompt (**later**)

## Out of scope (for v1)

- Cross-platform backup format (iOS) unless schemas are shared deliberately
- Server-side backup without user account
- Including full notification digest text without explicit opt-in

## Related files (today)

| Area | Path |
|------|------|
| Sync (signed-in only) | `app/.../sync/UserDataSyncService.kt` |
| Settings | `core/data/.../settings/SettingsRepository.kt` |
| Local DB | `core/data/.../local/MorningGuardDatabase.kt` |
| Auth | `core/data/.../auth/AuthRepository.kt`, `app/.../auth/SupabaseAuthBackend.kt` |
| App bootstrap | `app/.../di/AppInitializer.kt` |
