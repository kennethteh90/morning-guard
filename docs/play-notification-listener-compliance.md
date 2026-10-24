# Google Play — notification listener compliance

Morning Guard on **Android** uses a `NotificationListenerService` to hold non-alarm notifications during the morning gate and show them in an in-app digest after the goal completes.

## What the app already does (code)

| Requirement | Implementation |
|-------------|----------------|
| Prominent disclosure before access | Onboarding step **Allow notification access** shows `notification_access_disclosure` and requires checkbox `notification_access_disclosure_checkbox` before opening system settings (`OnboardingFlowScreen.kt`). |
| In-app privacy policy | `PrivacyPolicyActivity` repeats the same disclosure plus Health Connect / steps note; linked from Settings. |
| Purpose limitation | Digest shows app name, title, and text only for held notifications; not uploaded (`strings.xml` disclosure). |
| User control | User grants/revokes access in Android settings; can skip listener (gate still works with shields on Android 13+ where applicable). |

## Play Console — Kenneth must complete

- [ ] **Sensitive permissions / App content → Notification listener**: declare that the app reads notifications to provide the morning digest feature.
- [ ] Paste or paraphrase the in-app disclosure: notifications are stored on device, shown after the goal, not uploaded; calls/SMS not held.
- [ ] Link privacy policy URL: `https://morning-guard.codingagents.cc/privacy/` (or your deployed URL).
- [ ] If Google requests a video: use screenshot build onboarding on the notification-access step (`ScreenshotDemoSeeder.seedOnboardingNotificationDemo()`).

## Gaps / follow-ups

- No separate “notification listener” section on the public web policy unless you add a paragraph mirroring `notification_access_disclosure` (recommended for store review).
- iOS does **not** use a notification listener; do not reuse this declaration for App Store.
