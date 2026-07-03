# LINE WORKS Ping

A tiny Android app that gives you an **on-screen pop-up and a sound only when a
message actually matters** — a direct message (1:1 chat) or an **@mention of
your name** — so you can relax on the couch and still catch the important pings
without staring at your phone.

It works by reading the notifications that LINE WORKS (or any app you choose)
already posts, deciding whether each one is important, and then posting its own
**high-priority heads-up notification** (pop-up + sound + vibration) that can
optionally pierce **Do Not Disturb**. It does not read your messages from a
server or log in to LINE WORKS — it only reacts to on-device notifications.

## How it decides what's important

For each notification from a watched app:

- **Direct message** — detected via Android's MessagingStyle group flag
  (`EXTRA_IS_GROUP_CONVERSATION`) on Android 9+, with a heuristic fallback on
  older versions.
- **@mention** — any of your configured keywords (e.g. your name) appears in the
  notification text. Optionally require the `@` prefix.

Group-summary notifications and the app's own notifications are ignored.

## Why a pop-up (and how DND works)

Earlier the app only *played a sound*. On Do Not Disturb that kind of sound is
suppressed by the system, and there was nothing on screen — so you'd see and
hear nothing. Now the app posts a real **IMPORTANCE_HIGH** notification, which:

- shows a **heads-up banner** (pop-up) on screen,
- plays your chosen **sound** and **vibrates**,
- can **bypass Do Not Disturb** if you grant "Do Not Disturb access".

## Diagnosing "no pings"

Open **Recent activity** in the app to see the last notifications it saw and how
each was classified (a 🔔 means it fired a ping). If LINE WORKS doesn't show up,
turn on **Troubleshoot: log every app's notifications** to discover its exact
package and confirm the listener is receiving events.

## Building

Standard Gradle/Kotlin Android project. Open in **Android Studio** and Run, or:

```bash
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
```

- Module: `app` · package `com.dominic.lineworksping`
- `minSdk` 26 · `targetSdk`/`compileSdk` 34 · Kotlin, AGP 8.5.2

## Getting the APK without Android Studio (GitHub Actions)

Every push builds the APK on GitHub's runners and:

1. uploads it as a workflow **artifact**, and
2. publishes it as a **GitHub Release** asset (named
   `lineworks-ping-<version>-<versionCode>.apk`).

Grab the latest from the repo's **Releases** page and sideload it.

## In-app updates (no reinstall from a file)

The app can update itself from **GitHub Releases**:

1. Open the app → **Updates** → **Check for updates**.
2. If a newer version exists, it downloads the APK and launches the installer;
   tap **Update**. (You'll be asked once to allow installing from this app.)

> **This requires the repository to be PUBLIC.** An Android app can't download
> release assets from a private repo without embedding credentials. Flip it in
> **GitHub → repo → Settings → General → Danger Zone → Change visibility →
> Public**. There are no secrets in this repository. The updater points at
> `dom1911k/lineworksPING`'s latest release and compares its `versionCode`
> against the installed build.

CI stamps each build's `versionCode`/`versionName` from the workflow run number,
so newer pushes always look "newer" to the updater.

## First-run setup (on the phone)

1. Open **LINE WORKS Ping**.
2. **Notification access** → *Open access settings* → enable "LINE WORKS Ping".
3. Make sure **pop-up notifications are allowed** (the app requests this; you can
   double-check via *Notification settings*).
4. **Apps to watch** → *Choose apps* → tick **LINE WORKS**.
5. **What counts as important** → keep *Direct messages* on and type your
   name(s)/keywords for *@mentions*.
6. **Alert sound** → *Choose sound*, then *Test ping* to preview the full pop-up.
7. **Do Not Disturb** → turn on *Let important pings through* and *Grant Do Not
   Disturb access* if you want pings during DND.

## Notes & tuning

- **Battery optimization:** if pings stop after the phone sits idle, exclude the
  app from battery optimization so the listener service isn't killed.
- **Mention matching is text-based.** If LINE WORKS shows mentions as
  `@YourName`, enable *Only match keywords written as "@keyword"* to avoid false
  pings when your name appears in normal conversation.
