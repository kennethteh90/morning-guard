# App Store Connect privacy labels (iOS)

Use this checklist when answering **App Privacy** for Morning Guard. Values reflect the app when optional services are enabled.

## Data collection

| Data type | Collected? | Linked to user? | Used for tracking? | Purpose |
|-----------|------------|-----------------|--------------------|---------|
| Email address | Only if user signs in (magic link) | Yes | No | App functionality (cloud sync) |
| Health & fitness (steps) | Only if user picks a step goal | No | No | App functionality (goal verification) |
| Crash data | Only if `SENTRY_DSN` is set in the build | No | No | Analytics / stability |
| User content (highlights, notes) | Stored on device; ciphertext may sync if user enables cloud sync + recovery password | Yes when synced | No | App functionality |

## Data not collected on iOS

- Other apps’ notification content (no notification listener on iOS).
- Precise location.
- Contacts, photos, or browsing history.

## Required-reason APIs (`PrivacyInfo.xcprivacy`)

- **UserDefaults** — `CA92.1` (app functionality: settings, streaks, gate state).

## Kenneth: verify before submit

- [ ] Confirm Sentry is disabled in App Store build if you leave `SENTRY_DSN` empty in Release xcconfig.
- [ ] If Sentry is enabled, declare crash data as above and complete Sentry’s data-processing agreement.
- [ ] HealthKit usage string matches step-goal-only behavior.
- [ ] Family Controls / Screen Time: no separate “collected data” row; shields are on-device policy only.
