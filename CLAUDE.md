# IPTerebi

Android IPTV player. A line on an Xtream Codes panel supplies the channels; the
app supplies nothing. Phone, tablet and Android TV from one APK: portrait for
browsing on a phone, landscape for playback everywhere, and fully usable with a
D-pad remote.

## Layout

- `core/` — the panel client. URL normalisation, JSON models, HTTP, stream-URL
  building. Plain Kotlin JVM, no Android, and the only part with tests. Run them
  with `gradle :core:test`; it needs no SDK and no device, so there is no excuse
  for a change in here going untested.
- `app/` — Compose UI and the Media3 player. Nothing in here can be checked
  without a device.

Keep that line where it is. Anything that could be decided without an Android
class belongs in `core/`, because that is the half that can be proven.

## Building

**There is no Gradle wrapper, deliberately.** CI provisions Gradle 8.9 and runs
`gradle assembleDebug`. Locally you need Gradle 8.9, JDK 17 and an Android SDK
with `platforms;android-34` and `build-tools;34.0.0`.

```
gradle assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Versions move together: Kotlin, the Compose compiler plugin and the
serialization plugin are all 2.1.21 and must stay equal, because the Compose
compiler plugin is versioned with Kotlin rather than with Compose.

## Debugging on device

Debug builds log under two tags, both wrapped in `BuildConfig.DEBUG` and free in
release:

- `IPTerebiApi` — one line per panel request with the credentials stripped, what
  the response contained (category and channel counts, the first channel's name
  and id), the line's status and connection count on sign-in, and the opening of
  any body that failed to parse
- `IPTerebiPlay` — the format and user agent a stream was opened with, the URL's
  *shape* with the credentials starred out, every playback state transition, the
  video size once frames arrive, and the mapped failure on an error

```
adb logcat -G 16M
adb logcat -s IPTerebiApi:D IPTerebiPlay:D
```

**Two things must never reach the log.**

A stream URL: the credentials sit in its *path*, not in a header, so a logged
URL is a logged password. Only its shape is printed.

A raw sign-in response body: panels echo the username **and the password in
clear** back inside `user_info`. `String.withoutCredentialValues()` strips both
and is what the unparseable-body log goes through. There is a test pinning it;
if you add a new place that logs a body, route it through the same function.

## Changing settings without reinstalling

The settings screen (cog on the channel list) changes the two things most likely
to be wrong, live:

- **Stream format** — MPEG-TS or HLS, saved immediately. The player keys its
  ExoPlayer instance on the account, so reopening a channel picks up the change.
- **User agent** — presets for VLC, ffmpeg, ExoPlayer and an honest one, or type
  your own.

There is also a "check the line" button that re-runs the sign-in call and shows
what the panel says: status, expiry, connections in use, and which output
formats it admits to. That last one is worth reading before concluding the app
is at fault — a panel that does not list `m3u8` will not serve it.

## Things that are true about Xtream panels

- **"Xtream Codes" is not one implementation.** The original panel was abandoned
  in 2019 and what providers run now is a spread of forks that agree on the URL
  shape and little else. Assume every field is optional and every type is
  negotiable.
- **The same field arrives as a number from one panel and a string from the
  next.** `stream_id`, `category_id`, `num`, `max_connections` — all of them.
  This is what `FlexibleIntSerializer` and `FlexibleStringSerializer` are for.
  Strict parsing gives you an app that works against the one panel it was
  written against.
- **A rejected login is HTTP 200 with `auth: 0` in the body.** Never a 401. The
  status code tells you nothing; the body is the whole answer.
- **A suspended panel answers with an HTML page and a 200.** Anything decoding a
  response has to notice a leading `<` and say so, or the user gets
  "Unexpected JSON token at offset 0" and no idea what to do about it.
- **Some forks answer `false` to an action they do not implement.** Not `[]`,
  not an error — the literal `false`.
- **An id is not guaranteed and not guaranteed unique.** `stream_id` can be
  missing, and the flexible serialisers answer `0` when it is; `category_id` can
  be blank. Both are used as Compose list keys, and a repeated key is not a
  missing row — it is `Key "0" was already used` and the whole list is gone.
  `playableChannels()` and `usableCategories()` in `core/` are what make that
  invariant true before a list reaches a screen; nothing in `app/` should be
  keyed on a panel-supplied id that has not been through them.
- **Nobody pastes a base URL.** They paste `host:port`, or a `player_api.php`
  link, or a `get.php?...&type=m3u_plus` playlist link with the username and
  password sitting in the query string. `XtreamUrl.parse` takes all of those and
  lifts the credentials out of the last two.
- **Default the scheme to http, not https.** Panels are overwhelmingly plain
  HTTP on a high port. Assuming https fails at the handshake and produces an
  error that names TLS, which sends you looking in entirely the wrong place.
- **A default user agent gets refused by a meaningful share of panels.** Every
  mainstream IPTV client identifies as VLC or ffmpeg for exactly this reason.
  The tell is a 403 on the *stream* while every API call succeeds, which reads
  as "wrong password" and is nothing of the kind.
- **`android:usesCleartextTraffic` is not optional.** From API 28 Android blocks
  cleartext by default, and nearly every panel is plain HTTP. The symptom is
  every channel failing instantly.
- **The connection limit is the most common playback failure.** A line usually
  allows one stream at a time, and the panel takes a moment to notice the
  previous connection has gone — so leaving a channel and immediately opening
  another can refuse. Some forks report it as HTTP 456, which is not a real
  status code. `describeStreamHttpError` exists to turn these into sentences.

  So a player the user is not watching should not be holding the line. A
  *paused* ExoPlayer keeps its connection open; a *stopped* one releases it.
  `PlayerScreen` stops on `ON_STOP` for that reason.
- **`prepare()` does nothing unless the player is idle.** `ExoPlayerImpl`
  returns early for any other state, and the first `prepare()` leaves the
  player buffering synchronously — so a second call, or a `prepare()` after
  `pause()`, is a silent no-op. Anything meant to reconnect has to `stop()`
  first. This was got wrong once already: an earlier version paused on the way
  into the background and "re-prepared" on the way back, which did nothing, and
  live television resumed from a buffer minutes behind the broadcast. Read the
  Media3 source before reasoning about what a player call does.
- **`.ts` and `.m3u8` are both offered and only one may work.** Which one is a
  property of the panel, not the channel, and `allowed_output_formats` is
  aspirational — panels advertise `m3u8` and then serve a playlist that 404s.
  MPEG-TS is the safer default.
- **`get_series_info` is structurally unreliable, not just loosely typed.**
  `episodes` is documented as an object of season number → episodes, and also
  arrives as an array of arrays (seasons by position) or one flat array
  (grouped by each episode's `season`). `seasons` is often empty while
  `episodes` is full, so it is used for names only. `parseSeriesDetail` reads
  the JSON tree by hand for this reason — annotations cannot express "one of
  three shapes" — and skips an unreadable episode rather than losing the season.
- **PHP spells an empty object `[]`.** Most panels are PHP, and `json_encode` of
  an empty associative array is `[]`, so `info: []` and `episodes: []` are what
  "nothing here" looks like. Anything expecting an object must treat an array
  as empty rather than throw.
- **An episode's id is a string, and its URL uses it, not the series id.**
  `/series/u/p/{episode_id}.{ext}`. It stays a string end to end — parsed
  nowhere, encoded into routes and URLs — so a panel that uses something
  non-numeric still plays.
- **A film is a file, not a stream.** Its URL is `/movie/u/p/{id}.{ext}` where
  the extension is the film's own `container_extension` — `mp4`, `mkv` and `avi`
  all turn up on one line. The Stream format setting (TS or HLS) has nothing to
  do with films and must not follow them: `12345.ts` 404s against a panel
  holding `12345.mkv`. Error wording is separate for the same reason —
  `describeFilmHttpError` never suggests changing the format.
- **Live has no duration.** The seek bar stays empty and the position never
  moves. That is correct, not a bug, and the transport buttons are hidden
  because they could only ever be inert.
- **EPG titles and descriptions are base64.** Documented, not a fork quirk — but
  forks that send plain text exist, and `News` is itself valid base64 that
  decodes to three bytes of noise, so "did it decode" does not answer the
  question. `decodeEpgText` decodes and then *reads* the result, keeping it only
  if it is valid UTF-8 without control characters.
- **A programme's `start` and `end` are in no stated timezone.** They are the
  panel's local wall clock, which is not the viewer's and is written down
  nowhere. Rendering them puts a programme an arbitrary number of hours out.
  `start_timestamp`/`stop_timestamp` are unix seconds and are what everything
  here reads; the strings are kept for logs only. Note these want a Long, not an
  Int — a guide is the one thing routinely asked about the future, and unix
  seconds stop fitting in an Int in 2038.
- **There is no guide call for a list.** `get_short_epg` takes one `stream_id`,
  and the only alternative is `xmltv.php`, which is the whole schedule for every
  channel on the line as XML. So the guide is a per-channel request made when a
  channel is opened, and the channel list deliberately has none.
- **A missing guide is the normal case, not an error.** Lines carry no EPG,
  channels are missing from guides that exist, and some forks answer `false`.
  All of it arrives as an empty list.
- **A stream id means nothing off its own panel.** It is the provider's private
  numbering, so id 4271 on one line and 4271 on another are unrelated channels.
  Anything stored against an id — favourites, recents — is therefore keyed on
  the line, via `lineKey`, and dropped when that line is signed out of. A shared
  list would show a favourite that plays something else entirely.
- **Providers rename channels constantly.** "BBC One" becomes "UK: BBC ONE HD"
  overnight. Stored lists match on `streamId` alone for that reason; matching a
  whole record would quietly empty someone's favourites the next time their
  provider tidied up.
- **A whole channel list can be tens of thousands of entries.** Asking
  `get_live_streams` with no category is several megabytes on a large line, so
  the UI loads one category at a time and the player is navigated to with a
  stream id alone — never the list, which would end up in a Bundle.

## Things that are true about a D-pad

Every one of these was found by driving the app on an emulator with key events,
not by reading code. None of them shows up under touch, which is why they
survived until something was pressed.

- **A text field traps the arrow keys**, keyboard open or not — focus goes in
  and never comes out. **And focus arriving is enough to summon the keyboard**,
  so a search box above a list throws a keyboard over the screen on every trip
  up the list. `DpadTextField` fixes both with click-to-edit: under a remote the
  field is passed over, centre starts editing, and up or down always leave.
  Every text field in the app goes through it; a new one must too.
- **Decide input mode when focus is decided, never at composition.** The mode
  flips on the input itself, before recomposition, so a captured value is one
  input stale exactly when it matters. Capturing it broke a tablet with a
  keyboard: tap a field, type, and the first key made the field unfocusable.
  Read `LocalInputModeManager` inside `focusProperties`.
- **Material's focus indication is invisible from a sofa.** Anything selectable
  gets `focusRing()`, placed before `clickable` so it sees that element's focus.
- **Anything focusable over the video steals OK.** The overlay back button was
  the first focusable a remote reached, so the first press of OK — the one
  everyone uses to pause — left the film. It is `canFocus = false`; the remote
  has a Back key.
- **Media3's controls only hear the remote while `PlayerView` holds focus**, and
  lose it every time they auto-hide: the play/pause button that had focus goes
  with them and focus falls to Compose, where nothing takes keys. The screen
  hands focus back to the player whenever the controls hide.
- **On an error Media3 raises its controls, and they take focus** — OK went to
  the settings gear, not Try again. The controls are switched off while an
  error is up, and back on with the retry.
- **A bottom bar is unreachable on a TV.** It sits past the end of the list, so
  800 channels puts it 800 presses away. At 600dp and wider the sections are a
  `NavigationRail`, one press of left from anywhere. The `NavHost` must stay the
  same call in the same place whichever bar is showing, or it is recreated and
  every screen's state goes with it.
- **A TV needs `android.hardware.touchscreen` required="false"**, or it counts
  as unable to run the app, plus `LEANBACK_LAUNCHER` and a banner to appear on
  its home screen. Those three are in the manifest but have not been checked on
  a real Android TV image — the emulator used is a TV-shaped phone image, which
  tests the D-pad and the layout but not the TV launcher.

## Working notes

Measure before concluding. The panel quirks above are guesswork made concrete;
each one is pinned by a test in `core/src/test`, and when a provider turns up
that behaves differently the test is where the new truth goes.

**`app/` has run on an emulator, never against a real panel.** It has been
driven end to end on an API 34 emulator against a fake panel — a small local
server serving generated test media and deliberately malformed responses —
covering sign-in, live playback with the guide, the background/return
behaviour, the 403 refusal, films in mp4 and mkv with seeking, and series in
each `episodes` shape. That proves the app does what the code says. It does not
prove the code is right about panels: the fake panel only misbehaves in the
ways already written down here. The first session on an actual line is still
where the real answers are, and the surprises will be in what panels return.

An emulator run is worth doing for any change to `app/` — the SDK on the dev
machine has an emulator and a `phone34` AVD, and it caught a real bug (see
`busy` in `LibraryUiState`) that no test could. Boot it headless with
`-no-window -gpu swiftshader_indirect`; on a cold boot the launcher and System
UI time out for a minute or two, and `settings put global hide_error_dialogs 1`
stops their dialogs eating input. From the emulator the host is `10.0.2.2`.
