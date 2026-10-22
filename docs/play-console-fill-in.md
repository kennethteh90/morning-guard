# Google Play Console — Morning Guard fill-in guide

App: **Morning Guard** · `com.morningguard.app` · contact **kenneth@codingagents.cc** · privacy **https://morning-guard.codingagents.cc/privacy/**

Use this when completing Play forms. Wording in Console changes; map sections by topic.

---

## Install the internal test build

1. **Publish the internal release** (if not already): **Test and release** → **Testing** → **Internal testing** → open the release with version **10000** → **Save** → **Review release** → **Start rollout to Internal testing**.

2. **Add yourself as a tester**: same page → **Testers** tab → create an email list (e.g. `internal`) → add **kenneth@codingagents.cc** (and any other Gmail accounts you use on test phones) → **Save**.

3. **Copy the opt-in link**: **Testers** tab → **How testers join your test** → copy **Copy link** (looks like `https://play.google.com/apps/internaltest/…`).

4. **On the Android phone** (must be signed into Play Store with a **listed** Google account):
   - Open the opt-in link in Chrome → **Become a tester** / **Accept**.
   - Tap **Download it on Google Play** (or search **Morning Guard** in Play Store — you should see **Internal test** badge).

Direct Console page (internal track):

`https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/tracks/internal-testing`

**Not working?** Release still “Processing” (wait) · wrong Google account on phone · release not rolled out · app not available in your country (check **Countries/regions** on the track).

---

## Store listing (main)

| Field | Suggested text |
|-------|----------------|
| **App name** | Morning Guard |
| **Short description** (80 chars) | Hold notifications until you finish a calm morning goal—then see one digest. |
| **Full description** | See block below |
| **Website** | https://morning-guard.codingagents.cc |
| **Email** | kenneth@codingagents.cc |
| **Category** | Productivity (or Health & fitness if you emphasize steps) |
| **Tags** | morning routine, notifications, focus, Bible, digital wellbeing |

### Full description (paste & trim to 4000 chars)

```
Morning Guard helps you protect the first part of your day. During your morning window, non-essential notifications are held out of your status bar while you finish a simple goal—read a Bible chapter, take a quiet timed break, or walk enough steps. When you are done, held alerts appear in one in-app digest (Android cannot restore them to the shade).

WHAT YOU GET
• Morning gate — choose your window and goal
• Notification digest — see what was held, on your phone only
• Bypass list — allow specific apps (e.g. music) through the gate
• Optional morning alarm
• Optional cloud sync for streak stats and Bible highlights, or stay local-only

TRANSPARENCY
Before notification access, the app explains that it reads app name, title, and text for held notifications, stores them on device, and does not upload that text. Phone calls are not held.

Optional sign-in uses email magic links (Supabase). Step goals use Health Connect when you enable them.

Privacy policy: https://morning-guard.codingagents.cc/privacy/
```

Screenshots / feature graphic: `docs/store-screenshots.md` → `scripts/store-screenshots/out/`.

---

## Data safety

**Overview**

| Question | Answer |
|----------|--------|
| Does your app collect or share user data? | **Yes** (at least crash/diagnostics if Sentry enabled; email if user signs in) |
| Is all data encrypted in transit? | **Yes** (HTTPS) |
| Can users request data deletion? | **Yes** — in-app **Account → Delete account**; local data cleared on uninstall |
| Committed to Play Families policy? | **No** (unless you target children — default **No**) |

**Data types to declare** (adjust if you ship a build with `SENTRY_DSN` blank — still declare if any production track uses Sentry):

| Type | Collected? | Shared? | Required or optional | Processed ephemeral? | Purpose |
|------|------------|---------|----------------------|----------------------|---------|
| **Email address** | Yes, if user signs in | With **Supabase** (processor/host) | **Optional** | No | Account management, cloud sync |
| **User IDs** | Yes, if signed in | With Supabase | Optional | No | Account management |
| **Health info** (steps) | Yes, if user picks step goal + grants Health Connect | **No** (on-device only) | Optional | No | App functionality |
| **App interactions** (e.g. goal/streak metadata) | Yes | With Supabase if cloud sync | Optional | No | App functionality, analytics (streaks) |
| **Crash logs / diagnostics** | Yes, if Sentry DSN in build | With **Sentry** | Not required for core use | No | Analytics, crash reporting |
| **Other user-generated content** (Bible highlight notes) | Yes, if user creates highlights | With Supabase if cloud sync | Optional | No | App functionality |
| **Notifications** (content read by listener) | **Yes** — stored **on device** | **No** upload to your servers | Required for core feature | No | App functionality |

For **notifications**: Play’s form may use “Messages” or “Other”; describe honestly: notification title/body read for digest, **not** synced to backend.

**Not collected for Play purposes:** precise location, contacts, photos, financial info, SMS upload to server.

---

## Sensitive permissions & App content

### Notification listener

- **Core purpose:** Hold non-alarm notifications during the morning gate; show digest after goal.
- **User control:** System notification access toggle; in-app disclosure checkbox before opening settings.
- **Video:** `./scripts/store-screenshots/record-notification-demo.sh` → upload MP4 under **App content → Sensitive app permissions**.

Suggested declaration text:

```
Morning Guard uses the notification listener only after the user enables it in Android settings and confirms an in-app disclosure. While the morning gate is active, the app removes non-essential notifications from the status bar (except alarms, phone calls, and apps the user adds to the bypass list), stores title and text on device, and shows them in an in-app digest after the user completes their morning goal. Notification text is not uploaded to our servers.
```

### Installed apps (package visibility — no `QUERY_ALL_PACKAGES`)

The bypass picker uses Android **package visibility** (`<queries>` for `MAIN` / `LAUNCHER` only). It lists launcher apps plus packages that have already posted a notification while notification access is enabled. **Do not declare `QUERY_ALL_PACKAGES`** in Play Console.

### Exact alarms / Full-screen intent

```
Optional wake-up alarm the user configures in Settings. Exact alarms and full-screen intent are requested when the user enables the morning alarm feature.
```

### Health Connect (READ_STEPS)

```
Optional step-based morning goals. Step counts are read via Health Connect on device to verify goal completion and are not uploaded for cloud sync.
```

---

## Content rating (IARC)

Typical answers for Morning Guard (verify each question):

- No violence, sex, gambling, drugs, user-generated public content, or unrestricted web access.
- Utility / productivity / lifestyle; optional Bible text is **scripture content**, not user chat.
- No login required for core gate (optional account for sync).
- Result is usually **Everyone** / low age; submit questionnaire for official label.

---

## Privacy policy

URL: **https://morning-guard.codingagents.cc/privacy/**

---

## Key management (App Links)

`https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/keymanagement`

Use **App signing key certificate** → SHA-256 for `assetlinks.json` (done in repo).
