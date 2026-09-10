# IPTerebi

An Android IPTV player for Xtream Codes lines. Sign in with a server address, a
username and a password; browse the live channels your provider gives you; play
one.

**IPTerebi ships no channels, no playlists and no sources of its own.** It is an
empty client. What it can see is entirely determined by the line you sign in
with, which is yours to supply.

## Status

Early. Live television, films and series are written end to end, and have been
run on an Android emulator against a fake panel built to misbehave the way real
ones do — but not yet against a real provider, so treat "works" as unproven
there.

Android TV and remotes work: the same APK installs on a TV, every screen can be
driven with a D-pad, and on a wide screen the sections move to a rail down the
left edge. Text fields are click-to-edit under a remote, so moving around never
throws a keyboard over the screen. The TV home-screen banner is declared but has
not yet been seen on a real Android TV.

Series are in: a Series tab, a poster grid of programmes, and each one opening
onto its seasons and episodes. Specials are listed after the numbered seasons
rather than before them, and episode titles lose the "Show Name - S01E03 - "
prefix panels tend to repeat on every one.

Films are in: a Films tab beside Live TV, browsed one category at a time as a
poster grid, played with a seek bar and skip buttons, and paused rather than
restarted when you come back to the app. Nothing is fetched until the tab is
first opened, so a line with no films costs nothing.

Search covers every channel on the line, not just the category on screen.
Xtream panels offer no search of their own, so the first search fetches the
full channel list once and keeps it; browsing never does.

Starring a channel keeps it on a Favourites shelf, watching one puts it on a
Recent shelf, and the last thing watched is offered back as a "carry on
watching" row. Both shelves are stored per line and cost no request. Resuming is
one tap rather than automatic: opening straight into playback would spend the
line's single connection before the user had said what they wanted.

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
