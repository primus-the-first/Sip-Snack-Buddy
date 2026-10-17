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
- Everything stays on her phone: no account, no internet permission.

## Build and install

```bash
./gradlew :app:testDebugUnitTest      # rules tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (platform 35).

## Layout

- `app/src/main/java/com/sipsnack/buddy/rules/` - pure Kotlin rules (pace, moments, nudges, meals, label mapping). No Android imports, unit-tested in `app/src/test`.
- `data/` Room log table and settings. `memes/` which clip plays when. `reminders/` notification channels and the 15-minute nudge check. `ui/` Compose screens and the Midnight Blue theme.
- `app/src/main/res/raw/` meme clips, named `<moment>_sound.mp3` and `<moment>_video.mp4` (Android ignores extensions here, so names must differ).

## Notes

- Meme clips are other people's content, fine for this private app; swap them for your own recordings before sharing it wider.
- Fonts: Sora and DM Sans, SIL Open Font License (see `licenses/`).
- Numbers (2 hours, 30 minutes, 3 nudges, meal times) are starting values in `rules/Rules.kt`.
