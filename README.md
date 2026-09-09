# IPTerebi

An Android IPTV player for Xtream Codes lines. Sign in with a server address, a
username and a password; browse the live channels your provider gives you; play
one.

**IPTerebi ships no channels, no playlists and no sources of its own.** It is an
empty client. What it can see is entirely determined by the line you sign in
with, which is yours to supply.

## Status

Early. Live television works end to end — sign in, categories, channel list,
search, playback. Not there yet:

- Video on demand and series, which use the same API with different actions
- The EPG (`get_short_epg` / `xmltv.php`)
- Favourites, recently watched, and resuming the last channel
- Settings beyond what the login screen asks for
- Android TV and D-pad navigation — this is a phone and tablet build

## Building

There is no Gradle wrapper, deliberately; CI provisions Gradle 8.9 and so
should you. You need Gradle 8.9, JDK 17 and an Android SDK with
`platforms;android-34` and `build-tools;34.0.0`.

```
gradle assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The panel client is a plain Kotlin module with no Android in it, so its tests
run anywhere without an SDK or a device:

```
gradle :core:test
```

## Layout

- `core/` — everything that talks to an Xtream panel: URL normalisation, the
  JSON models, the HTTP calls, stream-URL building. No Android. Tested.
- `app/` — the Android application: Compose UI and the Media3 player.

## Licence

GPL-3.0, as `LICENSE`.
