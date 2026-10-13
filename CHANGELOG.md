# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-13

### Added

- Morning gate with verifiable goals (Bible chapter, timed focus, step count).
- Notification listener that aggregates non-alarm notifications into a single digest.
- Per-app bypass list and emergency passphrase bypass.
- Optional morning alarm with boot reschedule.
- Supabase magic link / Google ID token auth (when configured).
- Sentry error reporting and logging analytics abstraction.
- CI (test, lint, detekt) and CD release workflow on `main`.
