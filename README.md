# Patootie ✿

A super-kawaii Android app for running as many copies of the same app as you like, each with its own login and data.

- **No virtualization**: every clone runs the real, installed app.
- **No work profile, Shelter, Island or extra users**: everything happens in your main user.
- **No package renaming**: same package name, same signature, same Play Store updates.
- **Unlimited clones** of the same app.
- **Needs root** (Magisk, KernelSU or APatch).

## How it works: clones take turns

Android gives each installed package exactly one private data folder per user. Since clones can't get a second folder without renaming the package or virtualizing it, Patootie swaps what's inside the one folder the app already has:

```
/data/user/0/com.example.chat      ← the app's real data dir: whichever clone is "live"
/data/user_de/0/com.example.chat   ← device-encrypted data (moves with it)
/data/media/0/Android/data/com.…   ← the app's external files (move with it)

/data/adb/patootie/0/com.example.chat/
    ACTIVE                         ← which slot is live right now
    JOURNAL                        ← present only while a swap runs (crash-safe resume)
    slots/original/{ce,de,ext}/    ← your pre-Patootie data, parked
    slots/c1a2b3…/{ce,de,ext}/     ← every other clone, parked
```

Launching a clone:

1. force-stops the app,
2. moves the live data into its parked slot,
3. moves the chosen clone's data into the app's data dir,
4. fixes ownership and SELinux labels if the app's uid changed (after a reinstall), and
5. opens the app.

The store is on the same `/data` partition, so every move is a `rename()`: a swap is instant even for a 5 GB clone. A brand-new clone is just an empty slot, so the app starts logged out, as if freshly installed.

### What this means in practice
- **One clone per app is live at a time.** Only the live clone gets notifications and background sync. That's the price of following all the rules above.
- Updating the original from the Play Store updates every clone, because they're all the same install.
- **Original** is the data the app had before you made clones. You can always go back to it.
- Parked data lives in `/data/adb/patootie`, which only root can read. It survives uninstalling Patootie.
- Some apps bind a login to hardware-backed Keystore keys or Play Integrity. Keystore keys belong to the app's uid, not its data folder, so they can't be swapped. Those apps may ask you to log in again after a swap. Account-manager accounts (Settings → Accounts) are also shared by all clones.
- If you uninstall the original app, Android deletes its live data (the live clone's data is lost), but parked clones stay safe in the store and come back after a reinstall (use **Update** to re-fit them).

## Features
- **Make a clone**: pick an app, name it, choose an icon tint and a sticker, then tap **Clone!**
- **My clones**: grouped by app, with a live badge, data size, and **Launch**, **Rename**, **Update from original**, **Home icon** (pinned launcher shortcut) and **Delete**.
- **Original** button per app to swap your original data back in.
- Crash-safe swaps: an interrupted swap (battery died, process killed) shows a 🩹 banner and finishes with one tap. Files are never overwritten; anything in the way is set aside as `*.patootie-bak-*`.
- Kawaii UI: lavender and mint palette, rounded everything, Patootie the chibi mochi-bunny (blinks, bobs, and has moods for happy, thinking, excited and sad), drifting sparkles and hearts, a heart burst on success, bouncy squishy buttons, soft synthesized sounds (toggle with 🔔), and haptics.

## Tools and libraries, and why

| Choice | Why |
|---|---|
| **Kotlin** | Standard for Android; coroutines make root calls easy to keep off the main thread. |
| **Jetpack Compose + Material 3** | Spring physics (`spring(dampingRatio = 0.3f)`), `AnimatedContent` and Canvas drawing are what make the bounces, wiggles and pop-ins cheap to build. Material 3 is fully re-themed with the lavender and mint palette and big corner radii. |
| **Room** (via KSP) | Stores clone names, tints and stickers with a reactive `Flow`, so the list updates itself. KSP is faster than kapt. |
| **AndroidX Core `ShortcutManagerCompat`** | Pinned home-screen icons per clone (tinted, with its sticker), plus long-press shortcuts on Patootie's icon. |
| **Fredoka font** (bundled, SIL OFL) | Round, friendly letterforms for the kawaii look. Bundled rather than downloadable so it works offline. |
| **Our own `su` runner** (instead of libsu) | Each operation is one short script piped to `su` over stdin, so no persistent root shell is needed. libsu is great, but it's hosted on JitPack and would be our only non-Google/Maven Central dependency. |
| **Mascot drawn in Compose Canvas** (instead of Lottie) | No animation files to make or ship. Moods are just parameters (ear droop, eye shape, bounce height), so it reacts instantly to state. |
| **Sounds synthesized at runtime + `SoundPool`** | Soft sine and music-box tones are generated on first launch into tiny WAVs in the cache: no audio assets or licensing, and still low-latency, overlapping playback. |
| **POSIX `sh` scripts for the swap** | They run on Android's `mksh`, toybox and busybox alike. The generator validates every value it splices in (package name, slot id, uid, paths), so nothing user-controlled reaches the shell. |

No virtualization libraries, no hooking frameworks, no Xposed.

## Project layout

```
engine/   Pure Kotlin (no Android). The swap engine: script generator, su runner,
          CloneEngine. Fully unit-tested on a fake /data tree.
app/      The Android app.
  CloneRepository.kt      DB + engine + shortcuts, kept in step
  LaunchCloneActivity.kt  pop-up card: swap → celebrate → open the app
  ui/components/          Mascot, sparkles and hearts, bouncy buttons, dialogs
  ui/screens/             Home (clone list), Pick app, Create clone, dialogs
  sound/CuteSounds.kt     synthesized UI sounds
  icon/CloneIcon.kt       tint + sticker icon renderer (shared by UI and shortcuts)
```

## Building

The easiest way, on Linux, macOS or Windows (in WSL): one script downloads everything (a JDK 17 if needed, the Android SDK and the libraries) into `.local/` and builds the debug and release APKs:

```sh
./scripts/setup-and-build.sh
```

Or by hand:

```sh
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :engine:test                # swap-engine tests (uses /bin/sh)
./gradlew :engine:test -Ppatootie.shell=mksh      # under Android's shell
./gradlew :engine:test -Ppatootie.shell=busybox   # under busybox applets
```

Requires JDK 17+ and the Android SDK (platform 35). CI (`.github/workflows/build.yml`) runs the engine tests under `sh`, `mksh` and `busybox`, builds the APK, and attaches it to every run as `patootie-debug-apk`.

Install the APK, open Patootie, and grant root when your root manager asks.

## Release builds

`./gradlew :app:assembleRelease` builds a minified (R8) release APK in `app/build/outputs/apk/release/`. CI builds one on every push (artifact `patootie-release-apk`) and, when you push a tag like `v1.0`, also publishes it as a GitHub Release.

Android only installs an update if it is signed with the same key as the installed app, so set up your own key once:

```sh
keytool -genkeypair -v -keystore patootie.jks -alias patootie -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 patootie.jks   # paste the output into the secret below
```

Then add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `PATOOTIE_KEYSTORE_BASE64` | the base64 output above |
| `PATOOTIE_KEYSTORE_PASSWORD` | the keystore password |
| `PATOOTIE_KEY_ALIAS` | `patootie` |
| `PATOOTIE_KEY_PASSWORD` | the key password |

Keep `patootie.jks` and its passwords backed up somewhere safe; if you lose the key, you'll have to uninstall and reinstall to update. Without these secrets, the release APK is signed with a throwaway debug key and CI shows a warning.

Locally, set the same names as environment variables, using `PATOOTIE_KEYSTORE_FILE=/path/to/patootie.jks` instead of the base64 one.
