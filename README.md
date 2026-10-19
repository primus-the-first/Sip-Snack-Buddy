# Sip & Snack Buddy

A private Android app that helps her drink 8 cups of water a day and keep up with meals. She snaps a photo of her bottle or plate, the app recognises it on the phone, and meme clips celebrate the wins (and side-eye the delays).

- PRD: https://claude.ai/code/artifact/aed3fa20-418c-4ae6-843d-3a6471e32b31
- Design (Midnight Blue): https://claude.ai/artifact/9syNrXyzHXvaDBr2RKbxvD

## What V1 does

- Today screen: progress ring toward her goal (default 8 cups), "On pace" / "Behind" / "Goal hit", and today's log. Tap a line to remove it.
- Snap a sip: one photo, labelled on the phone by ML Kit (offline), then she confirms water / other drink / meal / snack and the size. Photos are never saved.
- +1 cup: logs a cup without a photo.
- Memes: a quick "Let's have it!" sound per cup; video pop-ups at halfway and goal hit.
- Nudges (8 am to 10 pm, at most 3 a day): the side-eye after 2 hours without water or 2+ cups behind pace; Akrobeto if she ignores it for 30 minutes; lunch and dinner reminders at 2 pm and 8 pm. Tapping a nudge opens its video.
- Settings: goal, memes on/off, nudges on/off, meme previews, delete all data.
- Live sharing (0.2.0): on first open each phone picks who it belongs to. Her phone asks once, "Let Walking Selfie Stick cheer you on?"; one tap and her buddy's phone shows her ring, last sip, meal times (if she allows) and her week, live. She can pick the level, pause or stop (which deletes the shared copy) any time; a "Sharing with ..." label always shows on her Today screen. He can send preset cheers and gets a notification when she hits her goal. Read-only for him.
- Steps (0.4.0): the phone's step-counter sensor, read every 30 seconds in the app and every 15 minutes by the worker; today's count survives the midnight rollover and phone restarts (`rules/Steps.kt`). Default goal 5,000. Her steps join her shared day unless she turns "Share my steps" off.
- Two of us (0.3.0): the buddy phone has "Her day" and "My day" tabs, so he tracks his own water and meals too, and a "Log for her" card adds sips and meals to her day (marked as his, removable, and switchable off on her Sharing page). His entries wait in `pairs/<PAIR_ID>/added` until her phone takes them in.
- One-tap updates (0.2.2): the app checks this repo's public GitHub Releases when it opens; a newer version shows an Update card, downloads, and Android asks to install. Releases must keep the same signing key.
- Without sharing, everything stays on her phone. No accounts: each phone signs in anonymously only for sharing.

## Firebase (for live sharing)

1. Firebase console: project `sip-snack-buddy`, Android app `com.sipsnack.buddy`. Put its `google-services.json` in `app/` (kept out of Git; keep a copy safe). Without it the app builds with sharing switched off.
2. Authentication > Sign-in method > Anonymous: enable.
3. Firestore Database: create in production mode, then paste `firestore.rules` into the Rules tab and Publish.

Data: one document `pairs/<PAIR_ID>` (her seat, his seat, sharing level, paused, latest cheer) and `pairs/<PAIR_ID>/days/<yyyy-mm-dd>` daily summaries (cups, goal, last sip, and times/kinds of logs at the "with meals" level). No labels or photos leave her phone.

## Build and install

```bash
./gradlew :app:testDebugUnitTest      # rules tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (platform 35).

## Layout

- `app/src/main/java/com/sipsnack/buddy/rules/` - pure Kotlin rules (pace, moments, nudges, meals, label mapping). No Android imports, unit-tested in `app/src/test`.
- `sync/` Firestore pairing and sharing. `data/` Room log table and settings. `memes/` which clip plays when. `reminders/` notification channels and the 15-minute nudge check. `ui/` Compose screens and the Midnight Blue theme.
- `app/src/main/res/raw/` meme clips, named `<moment>_sound.mp3` and `<moment>_video.mp4` (Android ignores extensions here, so names must differ).

## Notes

- Meme clips are other people's content, fine for this private app; swap them for your own recordings before sharing it wider.
- Fonts: Sora and DM Sans, SIL Open Font License (see `licenses/`).
- Numbers (2 hours, 30 minutes, 3 nudges, meal times) are starting values in `rules/Rules.kt`.

## Releasing a new version

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push `main`.
2. Build, then sign with the same key as every earlier release (keep the key outside the repo):
   `./gradlew :app:assembleRelease`, then `zipalign -p 4` and `apksigner sign --ks <key>` to make `SipSnack-<version>.apk`.
3. Add the APK (and optional `notes/<version>.md`) to the `releases` branch and push it.
4. Push a tag `v<version>` on `main`, or open Actions > Release > Run workflow and type the tag. The workflow publishes a GitHub Release with the APK attached.
