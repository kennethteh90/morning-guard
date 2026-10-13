# Morning Guard — Plan

Status legend: `[ ]` todo · `[~]` in progress · `[x]` done

## Phase 0 — Project foundation
- [~] PLAN.md, PROGRESS.md, BLOCKERS.md
- [ ] Git init, `.gitignore`, LICENSE (Apache-2.0), README
- [ ] Gradle multi-module Android (Kotlin, Compose, Hilt, Room, DataStore)
- [ ] `:core:domain` pure Kotlin (future KMP / iOS shared logic)
- [ ] `:core:data` repositories and platform adapters
- [ ] `:app` UI, services, DI

## Phase 1 — Core product behavior
- [ ] User settings: morning window, optional alarm, bypass app list
- [ ] Emergency bypass (long PIN / passphrase, Keystore-backed verification)
- [ ] Morning goal model + verifiable goal types (Bible chapter, timed focus, step count)
- [ ] Morning gate state machine (locked until goal complete or bypass)
- [ ] `NotificationListenerService`: collect non-alarm notifications into digest; respect bypass list and gate
- [ ] Optional `AlarmManager` morning alarm
- [ ] Onboarding + notification listener access flow

## Phase 2 — Account, telemetry, release hygiene
- [ ] Auth: Supabase (Google OAuth + magic link) with build-time config
- [ ] Error reporting (Sentry) + basic analytics abstraction
- [ ] App versioning (`version.properties` + BuildConfig)
- [ ] CHANGELOG.md + release workflow

## Phase 3 — Quality gates
- [ ] Unit tests (domain, data, viewmodels)
- [ ] Lint (Android Lint, ktlint/detekt)
- [ ] CI: test + lint on PR; CD: release build on merge to `main`
- [ ] Security audit script / dependency check

## Phase 4 — Definition of done verification
- [ ] Full `./gradlew test lint detekt assembleRelease` green
- [ ] No TODO/FIXME placeholders in production code
- [ ] README documents setup, permissions, OAuth, CI/CD

**Current focus:** Phase 0 — scaffold repo and Gradle modules.
