# Grok bot handoff — Morning Guard Google Play setup

Copy **everything below the second `---`** into Grok (or any agent) as the system/task prompt. The human (Kenneth) is logged into Play Console in the browser; the bot guides clicks, copy-paste text, and local file paths. The bot does **not** have Play API access unless the user provides screenshots of each form.

---

## PROMPT START (copy from here)

You are completing **Google Play Console** setup for **Morning Guard**, an Android app. Work from the repo and docs below. Kenneth uses browser Play Console; you supply exact navigation, field text, and which files to upload from disk.

### Repo

```
/Users/kennethteh/workspace/morning-guard
```

### App identity

| Field | Value |
|-------|--------|
| App name | Morning Guard |
| Package | `com.morningguard.app` |
| Developer contact | kenneth@codingagents.cc |
| Website | https://morning-guard.codingagents.cc |
| Privacy policy URL | https://morning-guard.codingagents.cc/privacy/ |
| Current internal release | versionCode **10000**, versionName **1.0.0** — **Available to internal testers** (released Oct 22, 2026) |

### Play Console deep links (Kenneth’s account)

Replace nothing unless Kenneth gives new IDs.

| Page | URL |
|------|-----|
| App dashboard | https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/app-dashboard |
| Internal testing | https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/tracks/internal-testing |
| App signing / SHA-256 | https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/keymanagement |
| Publishing overview | Use left nav **Publishing overview** on the app |

### Already done (do not redo)

- Play developer account; app created in Console
- Signed AAB uploaded; internal track release **10000** live for internal testers
- Public site + privacy policy deployed (`site/auth-landing`)
- `assetlinks.json` updated with **App signing key** SHA-256 and deployed
- `PRIVACY_POLICY_URL` in GitHub Actions secrets
- Contact email on website: kenneth@codingagents.cc

### Primary doc to follow for form text

Read and use verbatim where possible:

```
docs/play-console-fill-in.md
```

Supporting docs:

| Doc | Purpose |
|-----|---------|
| `docs/play-console-fill-in.md` | Store copy, data safety table, permission declarations, internal install steps |
| `docs/pii-and-encryption.md` | Data safety accuracy (what syncs vs local-only) |
| `docs/store-screenshots.md` | Screenshot/video pipeline |
| `site/play-store-launch/index.html` | Launch checklist (markdown), outstanding items |
| `site/auth-landing/README.md` | Website, privacy URL, keymanagement link |
| `docs/supabase/SETUP.md` | Prod auth URLs (if Kenneth asks about backend) |
| `docs/security-audit.md` | Sensitive permission honesty (notification content on device) |

Screenshot-only agent prompt (regenerate assets):

```
scripts/store-screenshots/AGENT_PROMPT.md
```

---

## Your mission (ordered)

### Phase A — Fix “Item not found” for internal testers

Internal release is already **Available to internal testers**. If Play Store still shows **Item not found**:

1. **Grow → Store presence → Main store listing** — upload minimum assets (see **Asset paths** below): **512×512 icon**, **≥2 phone screenshots**, short + full description from `docs/play-console-fill-in.md`. Save.
2. **Internal testing → Testers** — confirm `kenneth@codingagents.cc` (and phone’s Gmail) on the list.
3. Use **only** the **internal opt-in link** from Testers tab (`https://play.google.com/apps/internaltest/…`), not the public `details?id=` URL until listing is complete.
4. Wait 15–30 minutes after saving store listing; retry opt-in → Install.

Report which step was blocking.

### Phase B — Store listing (Grow → Main store listing)

Paste from `docs/play-console-fill-in.md`:

- Short description (80 chars)
- Full description
- Website, email, category (Productivity suggested)

**Upload graphics from disk** (absolute paths):

| Play field | File(s) |
|------------|---------|
| Phone screenshots (4 recommended) | `/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/morning-guard_play_phone_slide-01.png` through `slide-04.png` |
| Hi-res icon (512×512) | Export from app: `/Users/kennethteh/workspace/morning-guard/app/src/main/res/` (mipmap/drawable). If no 512 PNG exists, generate 512×512 from `ic_launcher` assets or ask Kenneth to export from Android Studio **Image Asset**; temporary fallback: upscale best mipmap PNG. |
| Feature graphic (1024×500) | **Not pre-generated in repo.** Option 1: open `/Users/kennethteh/workspace/morning-guard/site/screenshot-studio/index.html` in browser, use preset `play_feature`, export. Option 2: edit `scripts/store-screenshots/campaign.json` → `"presetId": "play_feature"`, run `./scripts/store-screenshots/compose.sh` and use output from `out/`. |

Manifest of current screenshots:

```
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/manifest.json
```

If framed PNGs are rejected by Play, use raw device captures:

```
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/raw/home.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/raw/digest.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/raw/goal.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/raw/stats.png
```

Regenerate pipeline (only if assets missing or stale):

```bash
cd /Users/kennethteh/workspace/morning-guard
./scripts/store-screenshots/orchestrate.sh
# or compose-only after editing campaign.json:
./scripts/store-screenshots/compose.sh
```

### Phase C — App content (policy forms)

Use `docs/play-console-fill-in.md` sections:

1. **Privacy policy** → `https://morning-guard.codingagents.cc/privacy/`
2. **Data safety** — follow table in doc; cross-check `docs/pii-and-encryption.md`
3. **Content rating** — IARC questionnaire; utility app, no UGC/social; Bible is static text
4. **Sensitive app permissions → Notification listener**
   - Paste declaration text from doc
   - Upload video:

```
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/notification-listener-demo.mp4
```

   Re-record if missing or outdated:

```bash
cd /Users/kennethteh/workspace/morning-guard
./scripts/store-screenshots/record-notification-demo.sh
```

   Choreography config: `scripts/store-screenshots/notification-demo.json`

5. Declare **exact alarms**, **full-screen intent**, **Health Connect / steps** using blurbs in `docs/play-console-fill-in.md` (bypass picker uses launcher `<queries>` only — **no** `QUERY_ALL_PACKAGES`)

### Phase D — Verify internal install

After Phase A–B:

1. Internal test opt-in on physical device
2. Install from Play (internal test badge)
3. Smoke test: onboarding, notification disclosure, gate, digest, optional sign-in
4. Optional: `adb shell pm get-app-links com.morningguard.app` → `morning-guard.codingagents.cc` verified

### Phase E — Publishing overview

Walk Kenneth through **Publishing overview** until no blockers for the tracks he wants (internal → closed → production). Do **not** promote to production until data safety + content rating + sensitive permissions + listing are complete.

---

## Asset quick reference (absolute paths)

```
# Framed Play phone listing (preferred marketing)
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/morning-guard_play_phone_slide-01.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/morning-guard_play_phone_slide-02.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/morning-guard_play_phone_slide-03.png
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/morning-guard_play_phone_slide-04.png

# Notification listener policy demo (Play App content)
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/out/notification-listener-demo.mp4

# Raw in-app pixels (fallback for Play screenshot rules)
/Users/kennethteh/workspace/morning-guard/scripts/store-screenshots/raw/*.png

# Interactive studio (feature graphic / other presets)
/Users/kennethteh/workspace/morning-guard/site/screenshot-studio/index.html

# Release AAB (if new upload needed)
/Users/kennethteh/workspace/morning-guard/app/build/outputs/bundle/release/app-release.aab
# Build: cd repo && ./gradlew bundleRelease  (requires local.properties secrets)
```

---

## Rules for the bot

- Do **not** commit git changes or push unless Kenneth explicitly asks.
- Do **not** ask for or paste keystore passwords, `local.properties` secrets, or Supabase keys.
- Play Console UI labels change; map by **topic** (Data safety, Store listing, Internal testing) not exact menu names.
- When unsure about a Data safety toggle, prefer **disclosed in privacy policy** + **optional** for sign-in/sync features.
- Notification **text** is processed **on device only** — not uploaded to Morning Guard servers (state clearly in forms).

---

## Success criteria

Kenneth can:

1. Install **Morning Guard** via **internal test** link on his phone
2. **Publishing overview** shows no blockers for intended test track
3. Store listing saved with icon + screenshots + descriptions
4. Data safety, privacy URL, content rating, and notification-listener video submitted
5. (Stretch) App Links verified for magic-link HTTPS host

Report a checklist with ✅/❌ and screenshots of any Play errors Kenneth should fix manually.

## PROMPT END

---

## For Kenneth

- **Grok:** paste from **PROMPT START** through **PROMPT END** into a new Grok chat; keep Play Console open; send Grok screenshots when menu labels don’t match.
- **Cursor:** same file path `docs/grok-play-console-handoff.md` for any agent.
