# Morning Guard iOS — Screen Time & privacy

Morning Guard uses Apple’s **Screen Time API** (Family Controls, Managed Settings, Device Activity) so you can **shield apps you choose** during your morning routine until you complete your goal.

## What Morning Guard can see

- **Opaque tokens** for apps and categories you select in Apple’s system picker. Morning Guard does not receive a list of all installed apps or bundle IDs from Apple.
- **Goal progress** (time on goal screen, step counts from HealthKit if you enable the step goal) stored on your device.
- **Settings** you configure (morning window, goal type) stored on your device and in an app group shared only with Morning Guard’s own extensions.

## What Morning Guard cannot do on iOS

- Read, block, or replay **notifications from other apps**.
- Turn on **Focus** or Do Not Disturb for you without your action in Settings.
- Shield apps without your **explicit authorization** in Settings → Screen Time → Morning Guard (revocable anytime).

For **Downtime** and **Focus** setup (with screenshots), see https://morning-guard.codingagents.cc/iphone/ (repo copies in [DOWNTIME-AND-FOCUS.md](DOWNTIME-AND-FOCUS.md)).

## Data sharing

- v1 iOS build is **local-first**. Optional cloud sign-in (when enabled) follows the same Supabase policies as Android; see the main privacy policy on the website.
- We do not sell Screen Time data. Shield selections are not uploaded to our servers in v1.

## Children

Morning Guard for iOS is designed for **self-control on your own device** (individual authorization). It is not marketed as parental monitoring software.

## Contact

Questions: use the support channel listed on [morning-guard](https://github.com/kennethteh90/morning-guard) repository issues or your distribution channel’s support email.
