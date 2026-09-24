# IPTerebi

Android IPTV player. A line on an Xtream Codes panel supplies the channels; the
app supplies nothing. **Phone and tablet first** — portrait for browsing and
landscape for playback. The same APK also runs on Android TV and works with a
D-pad remote, but TV is secondary: put phone and tablet first, and flag any
change that alters the tablet layout.

## This is the tv branch

**The TV app is built from here, not from main.** Its own app id,
`com.ipterebi.tv`, so it installs beside the phone app; leanback required, so
stores offer it to televisions only; releases tagged `tv-v*`. The pattern is the
owner's Debritsu, whose TV app lives the same way on its own `tv` branch.

- **Main is merged into tv, never the other way.** Panel fixes, `core/` and
  the shared player machinery arrive from main; TV-only code stays here. Keep
  the namespace `com.ipterebi.app` and keep TV screens in `ui/tv/` so those
  merges stay small.
- **The bar is TiviMate** — the Android TV IPTV player people call the gold
  standard: it opens on live TV, up/down changes channel, OK brings the channel
  list up over the picture, it has a real guide grid and catch-up. The plan, in
  phases, is: set-top-box live TV; the guide; catch-up; films, series and own
  lists; then recording if wanted. Multi-view is out on a one-connection line.
- **Where to beat it:** its users report constant 458s and buffering; ours are
  handled (see the connection-limit notes below), everything is free, and the
  phone and TV apps are one family.
- **Test on real Android TV**: the `googletv34` AVD (Google TV, Android 14).
  The owner's own box is a Xiaomi MiTV-AFKR0 (Android 11, 32-bit ARM, 1080p);
  "any Google TV box" is the aim. It has run the app against the owner's real
  line. Android 9–11 boxes do not announce network ADB, so find one by its IP
  (or a scan for port 5555) and `adb connect <ip>:5555`. Screenshots of it come
  out blank over video — the picture is on a hardware layer — so read the log.
  Its remote has a D-pad, OK and Back and nothing else a TV app can use — no
  number keys, no channel keys — so everything must work from those; digits
  and channel keys are extras for remotes that have them.

### Live TV (`ui/tv/TvLiveScreen.kt`)

The home screen once signed in: the channel left on last time (the newest
recent — recorded when a channel *plays*, not when it is chosen), full screen,
playing. Up/down zap through the group it was chosen in; OK opens the
channels with their guide (below); Left flips to the previous channel;
Right shows the banner; digits jump by number; Back twice leaves. Films, Series and Settings are still the phone's screens.

- **One ExoPlayer for the screen.** A change of channel stops the stream at
  once and asks for the next 300 ms later; another press inside that cancels
  it. The fake panel's log shows each close before the next open, and holding
  channel-up through ten channels opens one stream.
- **The whole line is loaded once** (`Lineup` in `core/`), decoded off the main
  thread — it is megabytes on a big line. Numbers are the provider's where each
  names one channel, else positions; see `Lineup`.
- **Waiting for the line** (`LineWait` in `core/`): refused with 456/458 before
  anything played, it asks again every 3 s for 30 s under "Waiting for your
  line to free up", then says the line is in use elsewhere and how to free
  it. This is what makes phone-to-TV work: Stop on the phone, and the TV rides
  out the fifteen seconds the panel keeps counting it. Not on 403 — that is
  also a refused user agent. The fake panel's **Line busy for 15 s** tests it.

### The channels, with their guide (`ui/tv/TvChannelGuide.kt`)

OK (or the Guide or Menu key) from full screen. **One screen, not a channel
list and a separate guide** — the owner's call: "you have a category and when
you click in to view the channels, this is where it will be a channel list as
well as an EPG". An earlier version had both, and a "TV guide" rail item and
Right-twice to reach the second; they are gone.

The group being zapped, its channels down the side, two hours across, the
focused programme described at the top, and the tuned channel still playing
top right — the *same* player, resized into a corner the screen leaves
unpainted, so it never opens a second stream. Up/down move between channels,
right looks ahead; **left from the programme on now slides the groups out**,
with the rail (Live TV, Films, Series, Settings) beyond them, and moving
through the groups changes the rows at once — OK or right goes back in. OK
watches the focused channel full screen; holding OK toggles it in
Favourites (acted on at key-up, so a first repeat can mean "held"); Back
closes.

The groups slide over the channels, and that is the owner's choice, made
twice: a version with the categories as a fixed column beside the channels
(PR #36, the grid narrowed to 90 minutes to fit) was built, tried on their
box, and put back to this on their word.

- **A cursor, not focusable cells.** Thousands of cells of different widths
  would put focus wherever geometry says. One focusable handles the keys and
  the rules are `GuideGrid` in `core/`, tested: left/right programme by
  programme, up/down keeping the point in time, empty stretches stepped in
  half-hours, the window following. Keys are worked out from the live cursor,
  not the last frame: bursts sent to the real box landed wrong until they were.
- **Left stops at now**, which is what makes it the way to the groups. When
  catch-up arrives the past needs another way in.
- **From the full guide on the device only.** Rows are loaded from
  `GuideStore` as they come near the cursor; a channel it does not cover is a
  row of empty half-hours, not a `get_short_epg` request per row.
- On the emulator, check its clock against the PC's first (`adb shell date`):
  one had drifted an hour and a half, and a right guide looked wrong.

## Layout

- `core/` — the panel client. URL normalisation, JSON models, HTTP, stream-URL
  building. Plain Kotlin JVM, no Android, and the only part with tests. Run them
  with `gradle :core:test`; it needs no SDK and no device, so there is no excuse
  for a change in here going untested.
- `app/` — Compose UI and the Media3 player. Nothing in here can be checked
  without a device.

Keep that line where it is. Anything that could be decided without an Android
class belongs in `core/`, because that is the half that can be proven.

## The look

**The structure came from Debritsu; the face no longer does.** The owner's
other app (`../Debritsu`, see its `ui/Theme.kt` and `ui/Components.kt`) is
where the way this app is built comes from — colours as roles, flat fills, a
panel per setting, a row of choices with the chosen one solid — and that part
stays. What was dropped is Debritsu's softness: its rounded face, its 22px
corners, its ExtraBold titles. It suits a library of books; against a
schedule it read as a toy. See the typeface note below.

Two themes, switched under Settings → Appearance: **Dark**, the default — the
TV guide's colours, near-black with its dark cells for cards and the soft blue
as the accent, because the owner asked for the whole app to look like the
guide — and **Light**, a pale page, white cards, the dark blue for what is
selected and the main button, kept as an option on the phone at their word.
The TV app has Dark alone (`Appearance.SWITCHABLE` is false there). The player
is dark in both (`OverVideo`); it frames video. On Dark, focus is the guide's
white ring, not the blue, which is lost on near-black.

Flat throughout: no gradients, glows, rims or sheen, and each role one colour.
Colours are roles in a `Palette` (`Light`, `Dark`), read through `Night.*`,
which holds the current palette as state — switching recolours everything that
read one. What you press is told apart by fill: quiet pills for choices not
chosen, the accent solid for the one chosen and for the main button, glass for
icon and secondary buttons. Pink, from the icon's smile, is for favourites. No
yellow in the app — its owner asked for it out; the icon keeps it. The choice
is kept by `Appearance` in plain preferences, read before the first frame; the
launch splash is the Dark page, so on Light it shows dark for that moment. The
choice moved to a new key when Dark became the default, so an old Light did
not hide the new look.

This was reached the hard way, and is worth not undoing. The app was
dark-only, on a navy page lit cobalt from the top, and every button on it read
as blue on blue — in navy, in lighter navy with an outline, in white, and once
flat on the navy — and the owner was not happy with any of them. What they
wanted was Debritsu's look: blue as the accent, never the page and the buttons
at once.

The palettes are in `ui/theme/Theme.kt`; the pieces — `SectionTopBar`,
`ScreenTopBar`, `Panel`, `PrimaryButton`, `SecondaryButton`,
`SquareIconButton`, `ChoiceRow`, `QuietPill`, `nightCard`, `fieldColours` —
are in `ui/NightParts.kt`, named after Debritsu's where they match. Use those
rather than new literals or Material's own buttons and chips, and never a
colour that only works in one theme.

The typeface is **Inter**, bundled in `res/font` as Latin-only cuts in four
weights, about 60 KB each. The app wore M PLUS Rounded 1c, which came over from
Debritsu along with the rest of that app's look, and the owner's verdict on it
here was that the Debritsu look does not fit — "as in fonts etc". It suits a
soft library app; over a guide grid the round terminals close up the counters
at cell sizes and the digits go pudgy, so a clock down the edge of a schedule
never quite lines up. Inter was drawn for interfaces at small sizes and is
deliberately characterless, which is what a page of channel names and times
wants. Four faces were put on the app's own screens before choosing (channel
list, guide grid, player overlay, at phone and ten-foot sizes) and this is the
one that was picked.

The cut is the same treatment as before — Latin only, because the full font
carries every script and costs megabytes, and a channel named in Japanese or
Arabic falls back to the system font on its own. The subset is Google Fonts'
own, fetched with the `text=` parameter, and the `tnum` feature survives it,
which matters: see below. Inter is under the SIL Open Font License, which
allows bundling and subsetting on condition the licence travels with it —
`assets/licenses/inter_OFL.txt`, and it must stay.

Three rules came in with it, and the point of each is that a schedule is not a
picture book:

- **Corners are roles, not numbers.** `Corners.panel` 12, `.card` 10,
  `.control` 8, `.tag` 6, in `ui/theme/Theme.kt`. They used to be 22 and 18
  with full-round pills for anything chosen, written as a literal at each call
  site. Nothing new should carry its own radius.
- **Weight carries less than it did.** ExtraBold is gone from the scale;
  titles are SemiBold, body is Regular, and Bold is for the one thing on a
  screen that matters. Hierarchy is size and colour first.
- **Digits that stack get `TextStyle.tabular()`.** A proportional 1 is
  narrower than a 0, so a clock ticking 19:11 → 19:12 shifts the line sideways
  and a column of times does not line up at all. It is on the guide's time
  header, programme times, the channel-number pill and the player's clock; put
  it on anything new whose text is mostly numbers.

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
  status code, and a real line answered 458 — to every reconnect for about
  fifteen seconds after a phone left Wi-Fi, until it stopped counting the
  dropped connection. `describeStreamHttpError` exists to turn these into
  sentences.

  Switching channel by swiping therefore happens *inside* the player screen,
  never by navigating to a new one: a new screen animates in while the old is
  still playing, and for that moment two players hold two connections. And the
  player is prepared in the effect that runs after the previous player's
  release, not where it is built — Compose builds the new one before disposing
  the old, so preparing in the builder opens the next connection first. The
  fake panel's log shows the old stream closing before the new one is asked for.

  So a player the user is not watching should not be holding the line. A
  *paused* ExoPlayer keeps its connection open; a *stopped* one releases it.
  `PlayerScreen` stops on `ON_STOP` for that reason.

  Picture-in-picture leans on exactly this. In the floating window the
  activity is *paused*, not stopped, so nothing fires and the video carries on;
  closing the window stops the activity and the same `ON_STOP` lets the
  connection go. Do not "pause the player on `ON_PAUSE`" — it would freeze the
  window, and still hold the line.

  The one `ON_STOP` that does not stop is the screen going off mid-stream
  (`PowerManager.isInteractive` is already false by then): that is the phone
  going into a pocket, and it plays on as sound. What keeps the process alive
  is `PlaybackService`, a Media3 `MediaSessionService` the player screen lends
  its session to — Media3 puts it in the foreground while the session plays
  and takes it out when it stops. The player stays in the screen; the service
  only holds the session. Something paused while away (lock screen, a
  headphone button, headphones pulled out) keeps the line for 30 seconds and
  is then stopped, which also ends the notification. A play key after that
  still works: Android lets a media key start a foreground service from the
  background, and the session's `LivePlayer` rejoins at the live edge.

  **Stopping that service is a race, and losing it kills the app.** Media3
  promotes it by calling `startForegroundService` and then posting the
  notification that calls `startForeground`; Android gives it five seconds to
  make the second call and kills the process if it does not come. `Attachment.
  hide()` used to `stopSelf()` the moment the last session was handed back, and
  a stop that landed between those two destroyed the service before the
  notification was posted — `RemoteServiceException: Context.
  startForegroundService() did not then call Service.startForeground()`, seen
  once on the box on 20 September at 20:47 and never reproduced in twenty
  attempts of starting and leaving playback on that same box. The stop is now
  posted to the main thread instead, so anything Media3 has already queued runs
  first, and the sessions are re-checked on the way through — switching channel
  attaches a new one in exactly that gap.

  Moving to another device — the phone to a TV — is the case the one
  connection makes awkward, so there are two ways to let go on purpose: Stop in
  the media notification (a session custom command) and "Free the line" in
  Settings, which then re-checks the line to show the connections in use. Both
  go through `ActivePlayback`, which the player screen registers with; it stops
  as "open in another player" does, and coming back shows "Stopped · Play here"
  rather than taking the line back from the TV.
- **Live streams drop, and a clean hang-up looks like the end of the stream.**
  A panel restarting a channel closes the connection normally, and ExoPlayer
  reports `ENDED` — for live, never true. A phone leaving Wi-Fi fails the read
  instead. Either way a channel that *has played* is reconnected with a
  back-off (`StreamReconnect` in `core/`: 1, 2, 4, 8, 15 s, then the error card);
  one that never played was refused, and says why at once. Films and episodes
  are reconnected by the same rules, carrying on at the second they reached
  rather than the live edge. Three things about how, each learned the hard
  way:

  ExoPlayer's own retry resumes a progressive stream at the byte it reached,
  with a Range request. A live panel answers from now with a 200, and the HTTP
  layer then *skips* every byte already watched — at the pace the panel sends,
  so after ten minutes, ten minutes of nothing. `StreamRetryPolicy` stops that
  for live. For a film the same resume is exactly right — a file has bytes to
  range over — so it is kept, and bridges a blip unseen.

  ExoPlayer also re-asks three times in as many seconds after a refusal — four
  requests per attempt against a panel that had just said no, and all of them
  inside the fifteen seconds a real line took to stop counting a dropped
  connection, so a film stopped on an error card. `StreamRetryPolicy` makes
  every refusal fail at once, for channels and films alike, and the back-off
  asks again.

  The back-off is waited out inside the data source (`DelayedOpenFactory`),
  while the player reads as *buffering*, and stop-seek-prepare happens inside
  the listener so the session never sees the player stopped. Media3 keeps the
  service in the foreground only while the session is buffering or ready, and
  once out, Android will not let it back in from the background — so a timer
  before `prepare()` would reconnect fine on screen and never in a pocket. With
  the screen off the fake panel's **Drops every 20 s** shows the service
  foreground throughout.
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
- **Live has no duration.** Media3 still draws a clock and a seek bar for it —
  "00:16 · 00:00" over an empty bar, which reads as a fault — so for a channel
  the player hides `exo_time` and `exo_progress`, along with the skip buttons,
  which could only ever be inert. Films and episodes keep all of them.
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
- **…and `get_short_epg`'s timestamps can be wrong too — by hours.** On the
  first real line Saturday Night Football ran 17:00–20:00 UK time (kick-off
  17:30, confirmed by the viewer; `xmltv.php` agreed), but `get_short_epg` sent
  it as `"18:00:00"` with a timestamp for 19:00: the panel's wall clock was two
  hours ahead of UTC, written into the timestamps as if it were UTC. So "now"
  showed nothing. A first fix read the strings in the device's zone, took the
  gap — an hour — and *looked* right, because a three-hour programme covers
  either start; it was still an hour out. The strings are in the panel's zone,
  which is unknown, and prove nothing. `GuideClock` now learns the error from
  evidence: exactly, from a programme found both there and in the full guide
  (same title, same length); or, lacking that, from what `get_short_epg`
  answers from — the programme the panel thinks is on now — once two channels
  agree. The debug log prints each programme's time against the device clock,
  which is how this was found. Test on the device's clock, too: an emulator
  that had drifted an hour and a half made a correct guide look wrong.
- **The full guide is `xmltv.php`, and it is the one to trust.** It is the
  whole schedule for every channel the provider has, as XMLTV, with each time's
  offset stated. On the first real line it was 76 MB, 228,709 programmes across
  8,350 channels — 1,569 of them the line's — about four days ahead, and it
  covered channels `get_short_epg` answered nothing for. It took a phone 13 s
  and the TV box 52 s. So it is fetched in the background, at most every twelve
  hours (on the phone only off metered networks), streamed through `readXmltv`
  a programme at a time — never held whole — and only the line's channels are
  kept, in `GuideStore` (17 MB on the phone). It is matched on each channel's
  `epg_channel_id`, which several streams can share. `Guide` answers "what is
  on" from it first and from `get_short_epg`, put right, only for channels it
  lacks. Because it is on the device, the channel list shows what is on under
  each channel and there is a TV guide (the grid button on Live TV,
  `ui/guide/GuideScreen.kt`) for whatever list was on screen — both local
  queries, never a request per row. The guide is dragged sideways through
  time with a fling, like any list; the grid's time rules are `GuideGrid` in
  `core/`. Until the phone has the full guide it says so, and offers to fetch
  it now over mobile data.
- **A missing guide is the normal case, not an error.** Lines carry no EPG,
  channels are missing from guides that exist, and some forks answer `false`.
  All of it arrives as an empty list.
- **Handing a stream to another player hands over the password.** The URL is
  the only thing a player can use, and the credentials are in its path. So
  "open in another player" is `ACTION_VIEW` through a chooser, to an app the
  user picks — never `ACTION_SEND`, which would offer messaging apps. It stops
  here *before* starting the other app, because of the connection limit, and
  on return shows "Play here" instead of re-preparing: VLC keeps playing in
  the background and would be the one refused. The `<queries>` in the
  manifest are what let it check a player exists first; without them Android
  11+ reports none, and the stream would be stopped for an empty chooser.
- **A stream id means nothing off its own panel.** It is the provider's private
  numbering, so id 4271 on one line and 4271 on another are unrelated channels.
  Anything stored against an id — favourites, recents — is therefore keyed on
  the line, via `lineKey`, and dropped when that line is signed out of. A shared
  list would show a favourite that plays something else entirely.
- **Providers rename channels constantly.** "BBC One" becomes "UK: BBC ONE HD"
  overnight. Stored lists match on `streamId` alone for that reason; matching a
  whole record would quietly empty someone's favourites the next time their
  provider tidied up.
- **The API has no search.** Finding a channel outside the loaded category
  means holding every channel, which is the large request everything else
  avoids. So the channel list fetches it once, the first time something is
  searched, and keeps it — with names normalised once into a `NameIndex`, since
  re-normalising tens of thousands of names per keystroke is the slow part. A
  failed fetch falls back to searching what is on screen, and says so.
- **A whole channel list can be tens of thousands of entries.** Asking
  `get_live_streams` with no category is several megabytes on a large line, so
  the UI loads one category at a time and the player is navigated to with a
  stream id alone — never the list, which would end up in a Bundle.

## The home screen

The app opens on Home, and the bottom bar has four tabs: Home, Live TV, Films,
Series. Home is one row per section — favourite channels (or recent ones,
switched under Settings → Home), then the films and the episodes you are
part-way through — and each heading opens that section's full list.

**Everything on it is already on the device.** Favourites and recents are
stored per line, what is on comes from the guide `xmltv.php` already left
behind, and the positions are a local list. Opening the app therefore costs no
request and the screen is full before the panel has answered anything. That is
the reason there is no "recently added films" row: it would mean pulling the
whole VOD list on every launch, which is several megabytes on a real line.

Where you got to is `WatchStore`, its own DataStore beside the channel lists
and keyed on the line the same way, holding the position with the name, the
poster and the container extension. Those three are kept because the list a
film came from is usually gone by the time someone taps the row — the player
is navigated to with an id and looks the rest up in a list that, on a cold
start straight to Home, was never loaded. `AppNav` publishes a one-item list
built from the stored record before navigating, so the overlay has a title.

The rules about what is worth keeping are `WatchProgress` in `core/`, tested:

- **Under 30 seconds in is a mis-tap, not a film.** On a line where things fail
  to play, the row would otherwise fill with things nobody watched.
- **Finished means within the smaller of two minutes and a twentieth of the
  length.** The flat two minutes came first and was wrong: the fake panel's
  test film is 90 seconds long, so everything past 30 seconds counted as
  finished and the row stayed empty through three runs on the emulator before
  the rule was the suspect. Short episodes have the same problem in miniature.
- The position is written every 15 seconds as well as on the way out, because
  a process killed from the task switcher never reaches `onDispose`.

One thing to know when testing this on the emulator: **playback there runs far
behind the clock** — a minute of wall time was ten seconds of film — so
watching something for a while will not pass the 30-second mark. Skip forward
with the player's own button instead.

### The home screen is the phone's, not the box's

The TV build keeps `TvLiveScreen` as its home — the channel list with its
guide, which is what the owner asked for and is why there is no separate
guide section here. The phone's Home screen, its four-tab bar and the house
icon are therefore *not* merged into this branch: `AppNav.kt` is resolved in
favour of this side every time main brings them across.

What does come over, and is worth having: `WatchStore` and the player's
resume. A film left half-watched on the box carries on where it stopped, the
same as on the phone. The rows that would show it are the phone's; the
position is the app's.
## Over the video: one panel, not a row of buttons

The button beside "open in another player" opens everything that can be
changed while something plays — the picture's shape, a sleep timer, the audio
track, the subtitles. One panel rather than a button each, because over a
picture every control is something in the way of the thing being watched.

- **Picture** — Fit, Fill, Stretch, mapped to Media3's `RESIZE_MODE_FIT`,
  `ZOOM` and `FILL`. A line is not all one shape: a 4:3 channel is pillarboxed
  on a 16:9 screen, and a panel that lies about a stream's aspect ratio is
  common enough that every set-top box has this button. Choosing one does
  *not* close the panel — the whole point is to see the difference, and
  closing each time would mean opening it three times to compare three shapes.
  Kept in plain preferences and applied in the view's factory as well as its
  update, so a 4:3 channel does not show its bars for a moment on the way in.
- **Sleep** — Off, and 15 to 90 minutes. Deliberately not stored and
  deliberately not a service: a timer that survived the app being killed would
  stop something hours later with no warning, which is the opposite of what it
  is for. When it goes off the player is *stopped*, not paused — a paused
  player keeps the line, and someone who has fallen asleep is not about to
  free it — and the screen offers Play here, as it does after the
  notification's Stop. It fires with the screen off too, because the effect
  watching it is tied to the composition rather than to the lifecycle, and
  that is the case it exists for.
- **Audio** and **Subtitles** appear only when the stream carries a choice.
  See `TrackChoice`: what is remembered is the language, not the track number.

The panel's height is a share of the screen rather than a fixed figure.
Anything is watched in landscape, where a phone is about 360dp tall, and a
460dp panel ran off the bottom.

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
- **Back is also a focus key.** Compose turns an unused Back into "leave this
  focus group": pressed on a row of the TV channel list, it moved focus out to
  the screen and was used up doing it, so the list stayed open and Back did
  nothing visible until pressed again. A `BackHandler` never sees that press.
  The TV live screen takes Back in `onPreviewKeyEvent`, before focus does.
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
- **The category shelf costs a remote a press per row.** Categories wrap in a
  `CategoryShelf` four rows tall that scrolls downwards — asked for, because a
  single sideways row was tedious by touch on a real line's dozens of
  categories. Down from a chip moves one row, so on a line with fifty
  categories the list under the shelf is up to a dozen presses from the top
  ones. Accepted: phone and tablet first. If it matters on a TV, give the chips
  a `focusProperties { down = … }` to the list and keep left and right for the
  shelf.
- **A TV needs `android.hardware.touchscreen` required="false"**, or it counts
  as unable to run the app, plus `LEANBACK_LAUNCHER` and a banner to appear on
  its home screen. Those three are in the manifest but have not been checked on
  a real Android TV image — the emulator used is a TV-shaped phone image, which
  tests the D-pad and the layout but not the TV launcher.

## Working notes

Measure before concluding. The panel quirks above are guesswork made concrete;
each one is pinned by a test in `core/src/test`, and when a provider turns up
that behaves differently the test is where the new truth goes.

**`app/` has run against one real line, and mostly on an emulator.** It has
been driven end to end on an API 34 emulator against the fake panel in
`tools/fakepanel` — one Java file serving generated test media and deliberately
malformed responses; its README says how to run it and what each fault tests —
covering sign-in, live playback with the guide, the background/return
behaviour, the 403 refusal, films in mp4 and mkv with seeking, series in each
`episodes` shape, and dropped channels. That proves the app does what the code
says. It does not prove the code is right about panels: the fake panel only
misbehaves in the ways already written down here.

The first real line was on a Pixel 10 (Android 17): sign-in, live at 720p,
an mkv film with seeking, series, picture-in-picture, playing on with the
screen off, and a reconnect from Wi-Fi to mobile data all worked, and it
turned up the 458 above. That is one provider. The next one will differ, and
the surprises will be in what it returns.

An emulator run is worth doing for any change to `app/` — the SDK on the dev
machine has an emulator and a `phone34` AVD, and it caught a real bug (see
`busy` in `LibraryUiState`) that no test could. Boot it headless with
`-no-window -gpu swiftshader_indirect`; on a cold boot the launcher and System
UI time out for a minute or two, and `settings put global hide_error_dialogs 1`
stops their dialogs eating input. From the emulator the host is `10.0.2.2`.
