# IPTerebi

Android IPTV player. A line on an Xtream Codes panel supplies the channels; the
app supplies nothing. Phone and tablet, portrait for browsing and landscape for
playback.

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

## Working notes

Measure before concluding. The panel quirks above are guesswork made concrete;
each one is pinned by a test in `core/src/test`, and when a provider turns up
that behaves differently the test is where the new truth goes.

**`app/` compiles but has never been run.** CI builds it green and produces an
installable APK, and `core/`'s tests pass — but nothing here has ever been
pointed at a real panel or run on a phone. Everything the UI does is inference
from the API shapes, so the first session on an actual line is where the real
answers are. Expect the surprises to be in what panels return, not in whether
the code builds.
