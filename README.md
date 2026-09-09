# IPTerebi

An Android IPTV player for Xtream Codes lines. Sign in with a server address, a
username and a password; browse the live channels your provider gives you; play
one.

**IPTerebi ships no channels, no playlists and no sources of its own.** It is an
empty client. What it can see is entirely determined by the line you sign in
with, which is yours to supply.

## Status

Early. The live-television path is written end to end — sign in, categories,
channel list, search, playback — and it builds, but it has not yet been run
against a real panel, so treat "works" as unproven. Not there yet:

- Video on demand and series, which use the same API with different actions
- Favourites, recently watched, and resuming the last channel
- Android TV and D-pad navigation — this is a phone and tablet build

The guide is in, as far as `get_short_epg` goes: what is on now and next appears
over the player, with a progress bar through the current programme. There is no
guide on the channel list and there is not meant to be — the only call that
would fill one is `xmltv.php`, which returns the entire schedule for every
channel on the line. A line with no EPG shows the channel name alone.

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
