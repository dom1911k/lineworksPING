# LINE WORKS Ping

A tiny Android app that plays a sound **only when a message actually matters** —
a direct message (1:1 chat) or an **@mention of your name** — so you can relax on
the couch and still catch the important pings without staring at your phone.

It works by reading the notifications that LINE WORKS (or any app you choose)
already posts, deciding whether each one is important, and playing your chosen
alert sound when it is. It does **not** read your messages from a server or log
in to LINE WORKS — it only reacts to on-device notifications.

## How it decides what's important

For each notification from a watched app:

- **Direct message** — detected via Android's MessagingStyle group flag
  (`EXTRA_IS_GROUP_CONVERSATION`) on Android 9+, with a heuristic fallback on
  older versions.
- **@mention** — any of your configured keywords (e.g. your name) appears in the
  notification text. Optionally require the `@` prefix.

Group-summary notifications and the app's own notifications are ignored, and a
short cooldown prevents rapid-fire repeats.

## Building

This is a standard Gradle/Kotlin Android project. The easiest path:

1. Open the project folder in **Android Studio** (Hedgehog or newer).
2. Let it sync, then **Run** onto your phone, or **Build > Build APK(s)** and
   sideload the APK.

Command line (with an Android SDK installed and `local.properties` pointing at
it via `sdk.dir=...`):

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

- Module: `app`  ·  package `com.dominic.lineworksping`
- `minSdk` 26 (Android 8.0) · `targetSdk`/`compileSdk` 34 · Kotlin, AGP 8.5.2

## First-run setup (on the phone)

1. Open **LINE WORKS Ping**.
2. **Notification access** → tap *Open access settings* and enable
   “LINE WORKS Ping”. (Required so the app can see notifications.)
3. **Apps to watch** → *Choose apps* → tick **LINE WORKS**.
4. **What counts as important** → keep *Direct messages* on, and type your
   name(s)/keywords for *@mentions* (comma- or newline-separated).
5. **Alert sound** → *Choose sound*, then *Test* to preview.

That's it — leave the app's master switch **Enabled** and you'll only hear a
ping for DMs and mentions of you.

## Notes & tuning

- **Battery optimization:** if pings stop after the phone sits idle, exclude the
  app from battery optimization so the listener service isn't killed.
- **Do Not Disturb:** the alert is played as a notification-usage sound, so DND
  rules still apply. Allow the app (or its sound) through DND if you want pings
  during focus time.
- **Mention matching is text-based.** If LINE WORKS shows mentions as
  `@YourName`, turn on *Only match keywords written as “@keyword”* to avoid
  false pings when your name appears in normal conversation.
