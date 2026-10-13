# Morning Guard — Plan

Status legend: `[ ]` todo · `[~]` in progress · `[x]` done

## Phase 0 — Project foundation
- [x] PLAN.md, PROGRESS.md, BLOCKERS.md
- [x] Git init, `.gitignore`, LICENSE (Apache-2.0), README
- [x] Gradle multi-module Android (Kotlin, Compose, Hilt, Room, DataStore)
- [x] `:core:domain` pure Kotlin (future KMP / iOS shared logic)
- [x] `:core:data` repositories and platform adapters
- [x] `:app` UI, services, DI

## Phase 1 — Core product behavior
- [x] User settings: morning window, optional alarm, bypass app list
- [x] Emergency bypass (long PIN / passphrase, hashed locally)
- [x] Morning goal model + verifiable goal types (Bible chapter, timed focus, step count)
- [x] Morning gate state machine (locked until goal complete or bypass)
- [x] `NotificationListenerService`: collect non-alarm notifications into digest; respect bypass list and gate
- [x] Optional `AlarmManager` morning alarm
- [x] Onboarding + notification listener access flow

## Phase 2 — Account, telemetry, release hygiene
- [x] Auth: Supabase (Google OAuth + magic link) with build-time config
- [x] Error reporting (Sentry) + basic analytics abstraction
- [x] App versioning (`version.properties` + BuildConfig)
- [x] CHANGELOG.md + release workflow

## Phase 3 — Quality gates
- [x] Unit tests (domain, data)
- [x] Lint (Android Lint, detekt)
- [x] CI: test + lint on PR; CD: release build on merge to `main`
- [~] Security audit script / dependency check (CI runs lint; OWASP dependency check optional follow-up)

## Phase 4 — Definition of done verification
- [x] Full `./gradlew test lint detekt assembleRelease` green locally
- [x] No TODO/FIXME placeholders in production code
- [x] README documents setup, permissions, OAuth, CI/CD
- [~] Instrumented UI tests (optional; unit coverage for core logic)

**Current focus:** Push follow-up commits (Google Sign-In UI); monitor GitHub Actions on `main`.
