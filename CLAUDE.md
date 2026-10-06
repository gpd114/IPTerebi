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
Right shows the banner; digits jump by number; Back goes to Home. Home and
Settings are the phone's screens; Films and Series have their own below.

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
with the rail beyond them — Home, Live TV, Films, Series, Recordings, in that
order because that is the order every other screen's rail is in, and Settings
set apart at the foot — and moving
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
- **A tap of left stops at now**, which is what makes it the way to the groups.
  **Holding it goes back** through what has already been on, as far as the
  provider keeps a recording of that channel — TiviMate's convention, and the
  only one left on a remote whose every key is spoken for. OK on a finished
  programme plays the recording; where there is none the hint line says so
  rather than offering it. The rule is `guideLeft` in `core/`, tested, because
  a hold there is a boolean. Rows for a channel with an archive are loaded
  from as far back as it keeps, rather than the hour behind that a channel
  without one gets.

  **A hold is timed, not counted, and that is the whole reason it works.** It
  was read from the key event's repeat count, which is what Android gives a
  held key — and on the owner's box the arrow keys *never* repeat: every press
  of left arrives as one event with `repeatCount=0` however long it is held,
  measured in the log on the box itself. So the hold was invisible and left
  opened the groups every time, on every channel, which read as catch-up being
  broken. Its **OK** key does repeat, which is why holding OK for a favourite
  has always worked there and why the fault looked like anything but a key.

  So the key is timed: the moment it goes down is recorded, the press is acted
  on when it comes up, and anything over 400 ms is a hold. OK, left and right
  all go through that one place, and a repeat, where a remote sends one, is
  still acted on the moment it arrives. Time is the one thing every remote
  has.

  **It works on the box, on the real line, with the owner's own remote** —
  27 September. Holding left walked back fourteen hours to a programme from
  the evening before, and OK on it opened
  `/timeshift/***/***/10/2026-09-26:16-10/497001.ts`, which came back at
  1280x720 and played. That is the whole path the box could not reach at all
  a day earlier.

  It also made the thing testable. `adb shell input keyevent --longpress`
  never registered against the repeat count, and against a timed key it lands
  as a hold — so the whole path can be driven on the `googletv34` emulator,
  and on the first run it caught a real bug: a channel with **no** recording
  walked into the past anyway, because the hour of guide loaded behind it for
  context was being used as the cursor's limit as well. How much is loaded and
  how far the cursor may go are two different numbers now.
  Coming back is a hold of right, which returns to now in one go rather than
  a press per programme walked back; ahead of now a hold still steps, because
  that is how tonight is browsed.
  Which channels keep one is marked on the row, the same replay mark the
  phone puts on a channel row: on a real line the channel above keeps seven
  days and the one below keeps nothing, and without the mark holding left
  works on one row and not the next with nothing on screen to say why. When
  there is nothing to go back to it now says which reason — no recording at
  all, or older than what is kept. That was silent, and silence reads as a
  fault.
- **From the full guide on the device only.** Rows are loaded from
  `GuideStore` as they come near the cursor; a channel it does not cover is a
  row of empty half-hours, not a `get_short_epg` request per row.
- On the emulator, check its clock against the PC's first (`adb shell date`):
  one had drifted an hour and a half, and a right guide looked wrong.

### Films and series, for a sofa (`ui/tv/TvLibraryScreen.kt`)

The phone's library is a search box, a shelf of categories and a grid of
112dp tiles. On a television that is the wrong shape twice over: a tile that
size is a postage stamp across a room, and the name under it, clipped to two
lines, says nothing on a real line — the titles there are
`4K-OSN+ - The Last of Us (2023) (US)`, where everything that identifies the
thing is at the end.

So the TV build draws them differently and shares everything else. The
`LibraryViewModel` is the same, `filmsSource` and `seriesSource` are the
same, one category at a time is the same, the viewer's own lists are the
same. Only the drawing differs:

- **What the cursor is on gets the top of the screen**: its poster, its name
  at headline size over two lines, its rating, and a series' plot. Films have
  no plot in `get_vod_streams` and asking for one is a request per film, so
  that line is empty for them rather than fetched.
- **With nothing focused yet the panel says where you are** — the category or
  list and how many are in it — rather than standing empty, which is what it
  did on the first run.
- **168dp of height**, not the guide's 213: that strip carries a programme,
  its times and a player in the corner, this one a poster and a name. On a
  540dp television the difference is a whole row of covers, and at the
  guide's height the first row was clipped by the bottom edge — the same
  mistake the wrapped category shelf made.
- **Chips in one sideways row**, the viewer's lists first and in pink, then
  the provider's categories.
- **Posters at 118dp**, six across a 1080p screen, with the name under each.
  They were 170dp, which comes out as four, and four was too big: one row
  filled the screen under the description strip, so the grid showed four
  films and no sign there were more. At six a row and the top of the next
  one fit.
- **The release that ends a hold must not do the next thing.** Holding OK on
  a poster opened the list panel and, where the viewer already had a list,
  put the thing in it and closed again — so the panel looked like it did
  nothing but add, and there was no way to reach "new list". tv-material
  fires its long click while the key is still down and then acts on the
  centre key's *release* without caring whether it saw the press, so the
  release lands on whatever has focus by then, which is the panel's first
  row. With no lists it looked fine, because a text field is not a button.

  **A timer cannot catch this, and the first attempt was one.** 350 ms of
  deafness after the panel opens works only if the viewer lets go inside
  350 ms; a hold of about a second on the box sailed past it. How long a key
  is held is the viewer's choice and nothing here gets to decide it. What is
  certain is the shape of the event — an up with no press before it — so the
  panel now ignores centre keys until it sees a key down of its own.

  **And "of its own" means a repeat count of zero.** The box's OK key
  repeats, so a hold is a press, a stream of repeats and then a release, and
  those repeats land on the panel once it is open. Arming on any key down
  would arm on them and the release would choose the row underneath. This is
  the same reading of `repeatCount` the guide does for its own holds.

  The guide hit this first with `okHeld`; this is the second instance, so
  expect a third. The panel logs every centre key it sees under
  `IPTerebiPlay` in a debug build, because the emulator cannot reproduce a
  real hold — `input keyevent --longpress` releases within milliseconds, so
  focus has not moved yet and the release never reaches the panel at all.
- **The panel keeps focus while it is up.** Down from its first row was
  landing on a poster behind it, and from there nothing moved at all: the
  grid is still composed underneath, and the poster below the row starts a
  few pixels nearer than the name field does, which is all Compose's
  distance scoring needs. The panel cancels its focus exit, so the search
  stays inside it and down goes row, field, button. Deactivating the grid
  instead does not work: `focusGroup()` is itself `canFocus = false`, and a
  deactivated group is exactly one whose children are still reachable.
- **Hold OK puts something in a list**, and inside a list it takes it out —
  the same gesture as the phone's long press and the channel list's
  favourite. The panel it opens is over the grid, not a sheet up from the
  bottom: a sheet at the foot of a television is one a remote has to walk the
  length of the screen to reach. It takes focus while it is up and Back
  closes it, as the guide's groups panel does.

Reaching it is the rail's usual pattern: the rail, right into the content,
down into the posters. The view models are keyed `tv-films` and `tv-series`
apart from the phone screens' — same class, same source, and a shared key
would hand one screen the other's state on the one build where both exist.

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
is dark in both (`OverVideo`); it frames video.

**Focus is a fill, and the fill is the accent.** It was a two-pixel white ring
on the phone screens and a white pill on the TV ones, and the owner's verdict
was that the white is ugly — and that where a component already marks focus by
changing its own background, a ring on top of it says the same thing twice. So
`Palette.focus` is a block of colour behind whatever has the remote: cobalt on
Dark, where the near-white ink reads on it, and a pale blue on Light, where the
dark ink does. `focusFill()` paints it over the element's own background and
under its content, so a card that is all picture — a poster, a channel's logo
tile — carries three device-independent pixels of padding inside the fill, and
focus shows as a frame around the picture rather than not at all. One place is
still a line: a text field cannot be filled behind its own well, so
`DpadTextField` outlines in the same colour.

**Chosen is not focus, and they must not be the same colour.** `Palette.chosen`
is what is picked among peers — the section the rail is on, the chip you are
browsing — with `onChosen` on it. On Light it is the same dark blue as
`cobalt`, which is what it always was. On Dark it is the pale accent, because
there `cobalt` is *also* `focus`: the rail drew the section you were in and the
item the remote was on as two identical blue blocks, and the owner could not
tell which was which. So on a television the section you are in is the pale
blue with near-black on it, and the remote is the cobalt fill. The TV screens
say the same thing in their own colours — `TvAccent` on `TvOnAccent` for
chosen, `TvFocusFill` for focus, in `ui/tv/TvParts.kt`.

One thing to watch when a component paints its own background: it covers the
focus fill underneath. The library chips did, so the chip the remote was on
looked like any other; they are transparent while focused now. Anything that
fills itself and sits inside a `TvRow` needs the same.

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



**The release build is minified, and that was never true until now.** R8 had
never run over this code: CI built debug, the owner's devices ran debug, and
`isMinifyEnabled` was false. What R8 breaks here is parsing, and it breaks it
silently — the panel's answers are decoded through serializers the plugin
generates and nothing refers to by name, so R8 sees dead code and removes
them. A release would then sign in, ask for the channel list and fail to read
the reply, which looks exactly like the provider's fault.

`app/proguard-rules.pro` keeps them, along with the hand-written
`FlexibleIntSerializer` and `FlexibleStringSerializer`, which are reached only
from annotations — the one shape R8 cannot see through. Media3 is kept whole
because its session service is resolved through the manifest.

CI builds release on every change for this reason. It is the only place the
rules are checked, and a broken rule costs nothing to find here and a bad
report to find anywhere else.

It is worth doing for its own sake too: **4.4 MB against 23.9 MB**, which is
most of a fifth, and the box has had as little as 235 MB free.

**Still unsigned, deliberately.** A release key is the owner's to make and
keep. The build was proved by signing it with the local debug key by hand and
driving it on the `googletv34` emulator: it signed in, fetched and parsed
`get_live_streams`, downloaded `xmltv.php`, opened a stream and asked for
`get_short_epg` — the whole decode path, under R8, with the debug logging
gone. That is the test that matters; distribution is a separate question and
nothing is tagged yet.

**CI builds both branches, and tries every change against the TV one.** A
change can be green on main and break the box: they share `core/` and nearly
all of `app/`, and `tv` adds screens main knows nothing about. That happened
when `refreshIfStale` began taking channels instead of their guide ids — the
phone's caller passes nothing and picks them up inside, so main never noticed,
and the TV's caller only failed when it was built by hand afterwards. So on
every pull request a second job merges the change into `tv` and builds that
too. Only a conflict in `app/` or `core/` fails it, because only those are
built: conflicts elsewhere are routine and expected — both branches add their
own test channels to the same lines of the fake panel, both keep their own
notes in this file, and `tv` has had its own name in that workflow's push list
since it was created. Those are taken the TV branch's way and the build goes
ahead. That rule was learnt immediately: the first version failed on any
conflict outside `tools/`, and the pull request that introduced it failed on
its own workflow file.

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
- **A provider that has fallen over must say so in seconds, not a minute.** The
  connect timeout is per *route*, and a host behind a CDN resolves to several
  addresses, so a 15-second connect timeout with a retry on top left the
  sign-in screen sitting for about a minute — long enough that the app looks
  hung rather than the provider looking down. The short calls (sign-in, and
  "check the line") are therefore bounded as a whole by `QUICK_CALL_SECONDS`,
  an OkHttp `callTimeout` that covers DNS, every route and the retry; connect
  is 8 s. The long ones are deliberately *not* bounded: a channel list on cheap
  hosting really does take half a minute, and `xmltv.php` is 76 MB.

  And the message matters as much as the wait. `describeNetworkFailure` turns
  each failure into the thing to try — a name that will not resolve asks about
  a typo *and* about the device being online, because a phone with no signal
  fails identically; a refused connection points at the port; a TLS failure
  suggests plain http. No figure is quoted for a timeout, because whichever of
  the two budgets ran out first is not knowable from the exception: on the
  emulator against an unroutable address it gave up at 11 seconds, and a
  message promising 15 would have been a small lie.
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
  "00:16 · 00:00" over an empty bar, which reads as a fault — so the player
  hides `exo_time`, `exo_progress` and the skip buttons, which could only ever
  be inert. **Which streams those are is asked of the stream, not of its
  type**: `isCurrentMediaItemSeekable` and a duration, read when the player is
  ready and applied in the view's update as well as its factory. A film is
  always seekable and a channel never is, so neither changed — but a catch-up
  depends on the panel, and this one serves a recording as a finite body, so
  it gets the bar and the skip. See the catch-up section.
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
  8,350 channels — 1,569 of them the line's — and it
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

  **How far it reaches is a property of the provider, and it moves.** The same
  line measured 228,709 programmes in September and 211,689 a few days later;
  the first reading here said "about four days ahead", and a later one, printed
  by the refresh itself, said **from 24 hours ago to 41 hours ahead**. So do not
  rely on a figure in this file — the log line says what today's download
  actually covered, and that is the number that matters.

  It matters because of catch-up: that line keeps **seven days** of recordings
  and publishes **one day** of past guide, so six of those days have nothing to
  point at. `GuideStore.commit` therefore carries the past forward instead of
  dropping it — the programmes falling before the new download's earliest entry
  are moved into the new generation rather than deleted, for the channels that
  keep a recording, up to `RETAIN_PAST_SECONDS`. The guide's past then grows a
  day at a time until it matches the archive. Only for those channels, because
  keeping it for the whole line would be a day of extra programmes per refresh,
  most of them for channels that can play none of it back.
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
- **A poster is whatever shape the provider uploaded.** The tiles are a fixed
  2:3, which is what a film cover usually is and what every poster the fake
  panel served used to be — so the artwork was cropped to fill and nobody
  noticed until a real line served a square one and a landscape one. A square
  cover lost its top and bottom, a wide one lost most of its width. Artwork
  is therefore *fitted* everywhere it is shown — the library grids, the Home
  rows, a series' header and its episode stills — and the tile's own colour
  carries whatever is left over. The cost is visible: a 16:9 still in a 2:3
  tile is mostly background. The owner's call, and the right one — a cover
  you cannot see the edges of is worse than a tile with space in it. The fake
  panel now serves one square poster and one landscape one so this cannot
  quietly come back.
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

## Your own lists

Beside the provider's categories, a handful of lists the viewer names and
fills: "Saturday", "For the kids", whatever they like. A real line's own
filing is dozens of categories called `EN - 2020 & OLD`, which is the
provider's idea of order rather than anyone else's.

**Films and series only.** Channels already have Favourites, and two ideas
for the same act — starring a channel, adding a film — would be one too many.
`ListedItem` would hold a channel without changing if that turns out to be
wanted.

- **Holding a poster is how something goes in or comes out.** Not a button on
  every tile: the grid is mostly picture, and a corner control on each would
  be a hundred small targets nobody asked for. On a remote a hold is already
  what adds a favourite on the channel list, so it means the same thing in
  both places. Held in the panel's grid it offers every list and a field to
  name a new one; held inside a list it offers to take the thing out again.
- **The chips are the lists**, in the same shelf as the categories and pink
  like Favourites, because they are a different kind of thing from the
  provider's filing. A list holding nothing of this kind is not shown on this
  screen — a films-only list does not clutter Series — but the "add to" sheet
  offers every list, including empty ones.
- **A list holds what it needs to draw and open its entries**, not just ids:
  the name, the poster, and a film's container extension. The same bargain
  `WatchStore` makes, for the same reason — the list a film came from is
  usually gone by the time someone opens it again, so an entry that carried
  only an id would be a blank card that could not be played. It is also what
  lets a list open with the panel not yet spoken to.
- **Per line, like everything keyed on a panel's ids** (`ListStore`, its own
  DataStore). Film 4271 on one provider is not film 4271 on another, so a list
  carried across lines would play something else entirely.
  **And per app**: the phone and the box each keep their own, because a
  DataStore is the app's and there is nowhere between them to sync to without
  a server. The owner was asked and chose to leave it there. So a list made on
  the sofa is not on the phone, and that is the design rather than a gap to
  close.

**They are rows on Home too**, under the rows the app decides on: what
someone filed themselves belongs there, but below what they were in the
middle of. A list with nothing in it is left out — a row explaining its own
emptiness on the screen you see most is a row not worth its height — and a
list heading has no "All" beside it, because the row *is* the list and there
is nowhere further to go. Nothing on those rows costs a request: an entry
was stored with its poster and its name for exactly this.

The rules are `core/OwnLists.kt`, tested: what a name may be (trimmed, spaces
collapsed, cut to 40, never blank), that the same name twice is one list
whatever the case, that adding the same thing twice keeps the first — the
second may come from a screen with no poster to hand — that entries stay in
the order they were added because a curated list that reorders itself is one
nobody can point at, that emptying a list is not deleting it, and that a film
and a series may share an id without being the same thing.

## Hiding channels

The other half of making a provider's list usable. Favourites says what to
keep close; hiding says what to stop showing at all, and it is the half that
shrinks a line of 21,077 channels filed into categories called
`EN - 2020 & OLD`.

Long-press a channel on Live TV. It asks first, because a long press is easy
to make by accident on a list being scrolled and a channel that silently
disappears is a fault report rather than a feature — and the sheet says where
to undo it, which is Settings.

**Hidden means hidden everywhere**, not just on the list screen: the filter is
applied to `visibleChannels` in `ChannelsViewModel`, which is what the list,
the search results, the published repository, the TV guide and the player's
zap keys all read, and again to the Home rows, which are drawn from the stored
copies rather than from that list. A channel still turning up in search is one
that has not been hidden, whatever the list says.

**Stored as whole records, not ids, and that was a mistake first.** The
original design kept ids alone, reasoning that a hidden channel is never drawn
so there is nothing to remember but which one it is. That is wrong in a way
that would have shipped: hiding has to be undoable, the screen that undoes it
has to draw a name, and by then the channel is out of every list the name
could have been read from. So it carries its name, the same bargain
favourites and `WatchStore` make. Filtering still matches on `streamId` alone,
so a provider renaming a channel overnight does not un-hide it.

The rules are `core/HiddenChannels.kt`, tested: that filtering an empty set
returns the same list rather than copying a line's worth of channels, that
hiding twice is showing again, that the newest hidden is first because it is
the likeliest mistake, that a renamed channel stays hidden, and that ids the
provider has dropped stop being kept — a set that grew for ever might one day
hide something new that was given an old id.

## Putting favourites in order

Starring appends, so a favourites list arrives in the order it was built.
The order is most of its value — the point is that the four channels actually
watched are the first four — so it can be changed: the Favourites shelf
carries a **Reorder** button, and while it is on each row swaps its star for
a pair of arrows.

**A mode with arrows rather than a drag, and that is a decision rather than a
shortcut.** There was no room for a drag handle — the row already carries a
star, a number and a catch-up mark, and the note on `ChannelRow` says a
fourth target is one too many on a phone — and no spare gesture to start a
drag with, because a long press is how a channel is hidden. Arrows also work
under a remote, which the TV build inherits from here and could not have
driven a drag with at all. The honest cost is that dragging is what a phone
user expects; `withFavouriteMoved` takes a from and a to, so a drag could be
added later without changing anything underneath.

The rule is `withFavouriteMoved` in `core/ChannelLists.kt`, tested: that a
move keeps every channel and duplicates none, and that an index off either
end leaves the list alone rather than throwing — a drag or a fast double tap
can ask for one, and a reorder that crashes is far worse than one that
declines.

Reordering writes the whole list back through `setFavourites`, so the stored
order is the order, and everything reading it follows: the shelf, and the
Live TV row on Home.

## Finding a match across the channels carrying it

A line usually allows one stream, so when a feed buffers or dies the only
move left is another channel showing the same thing. Finding that by browsing
a line of 21,077 channels filed into categories called `EN - 2020 & OLD` is
hopeless. The guide already on the device knows the answer, so it answers:

- **Searching finds what is *on*, not only what a channel is *called*.** A
  team name matches no channel name — nothing is called Croatia — so the
  search box now reports both: events above, channels matched by name below.
- **A failing stream offers the other feeds.** The error card lists them and
  switching happens inside the player screen, by changing which channel it is
  on, never by navigating — which would animate a second player in over the
  top and hold two connections against a line that allows one.

The rules are `core/WhatsOn.kt`, tested, and every one of them was settled by
measuring one real line's `xmltv.php` — 100,192 programmes across 1,366
channels, read off the box — rather than guessed. Each measurement killed a
simpler design:

- **The description matters as much as the title.** Croatia v England was on
  seven feeds and five of them titled it only "Nations League", with the teams
  in the description. Searching titles alone found two of the seven.
- **Grouping by identical title is wrong**, which was the first design. The
  same fixture arrived titled three ways — "UEFA Nations League: Croatia v
  England", "Nations League", "Kick Off - Croatia v England" — so exact titles
  split one event into three. And in the other direction `Live: College
  Football` was on **127 channels**, a generic slot name covering *different*
  games on different affiliates, so exact titles also glue unrelated matches
  into one. An event is therefore a close start, an overlap, and the shorter
  title's real words appearing in the longer one.
- **The longest title wins, not the commonest.** Five channels said "Nations
  League" and two gave the fixture, so a majority picks the vague one.
- **Noise is ranked down, not filtered out.** Searching "England" also finds
  darts, two cricket ODIs, "7 News Today in New England" and "Out of England".
  No word-boundary rule helps — England is a whole word in "New England" — and
  this app is not going to carry a football database. What separates them is
  how many channels carry it: the match is on seven, the news programme on
  one. So events are ordered by that and the viewer picks from a short list,
  rather than the app pretending to know. Measured against the real guide, the
  actual match ranks first with the other 33 England-mentioning channels below
  it.
- **`epg_channel_id` arrives in inconsistent case.** One line sent both
  `SkySport3.nz` and `skysport3.nz`, and `SkySportsCricket.uk` beside
  `skysportscricket.uk`. Folded, or the same channel is offered twice.
- **The channel being watched is kept in the event, and only its failed
  *stream* is dropped.** A provider carries ITV1 as the HD cut, the FHD cut
  and a backup under one guide id, so when a feed dies the best thing to
  switch to is very often another stream on the *same* guide channel. Taking
  the guide channel out would hide exactly those. Hence `showingOf`, which
  keeps it, beside `alsoShowing`, which does not.

**It is all local, and that is the point.** `GuideStore.inWindow` reads the
window from the database on the device and `WhatsOnIndex` folds the text once
— 12,584 programmes and 1.8 MB on a real line, far too much to re-fold per
keystroke, which is the lesson `NameIndex` already learnt for channel names.
The panel refusing things is usually why there is an error card at all, so
working around it must not need the panel's help. `LineChannels` holds the
whole channel list that maps guide ids to things that can be pressed, so the
channel search and this share one several-megabyte request instead of making
it each.


**It ground to a halt on a real line, and only a real line could show it.**
Shipped, then measured on the owner's box against their own provider: the
lookup never returned. No error, no card, no crash — the log said it had
started and then nothing, for as long as anyone cared to wait. Two faults,
both now numbers rather than opinions:

- **The window was the playing programme's whole span.** Strictly Come Dancing
  runs two and a half hours, and asking for everything overlapping it returned
  **3,163 rows across 160 start times**; a band of a quarter of an hour either
  side of its start holds **168**. Two programmes can only be one event when
  their starts are close, so the rule discarded nearly all of it anyway. Hence
  `GuideStore.startingNear`, which asks by `start` rather than by overlap — a
  different question from `inWindow`, which is why both exist.
- **The grouping was quadratic with a normaliser inside the comparison.** Each
  programme was compared against every event gathered so far, folding both
  titles with `normaliseForSearch` every time: a quarter of a million
  comparisons and half a million NFD normalisations for one window. It now
  folds each title once and walks events in start order, dropping those too
  far behind to match again. Same answers, **9 ms** where it had never come
  back.

This is the `NameIndex` lesson — re-folding thousands of strings is the slow
part — met again one layer down. It was applied to the matching when this was
written and missed in the grouping, which is worth remembering: the index only
helps the half it covers. `WhatsOnTest` now builds a real line's shape, 4,000
programmes across 200 slots, as a guard against it returning quietly.

Writing that test turned up a quirk worth knowing: words of one or two letters
are skipped when titles are compared, so **two titles differing only by a
number read as one event** — "Match Day 3" and "Match Day 4" group. Left that
way deliberately, because the same rule is what merges "Nations League" into
"UEFA Nations League: Croatia v England", and the worst case is a channel
offered that is showing the next fixture rather than this one. There is a test
saying so, so it is a decision and not an accident.

**Two things limit it, both the provider's.** It only knows what the guide
says, and how far the guide reaches moves: the same line published 41 hours
ahead one day and **17 the next**, so this answers "on now and tonight"
dependably and "this weekend" often not at all. And a channel being listed is
not a promise it plays — it is a shortlist, not a guarantee.

One bug worth remembering, because it hid the whole feature: the channel list
replaced its results with an empty panel whenever no channel *name* matched,
which is precisely the search this exists for. "0 channels match" drew over
the match it had just found. The empty panel now needs both kinds to be
empty, and the line above the results counts what is on.

The fake panel serves the case in miniature: channel 103 refuses with a 403
and carries `bigmatch.a`, which `Big match (backup feed)` shares, with
`BigMatch.B` spelling it with capitals and titling the slot generically, and
`Something else entirely` on at the same moment to catch a rule that groups
by time alone.

**On the box it is the error card**, in `TvLiveScreen`: the other feeds listed
as buttons under Try again, and OK on one tunes the same screen's single
player. No request is involved — the guide is in the box's database and the
whole line is already in the `Lineup` — which is the point, because the panel
refusing things is usually why the card is there. It logs what it found under
`IPTerebiPlay`, because the box's screenshots come out blank over video and
the log is the only way to see it.

The TV guide has no search box yet, so the team search is the phone's for now.

**Not yet seen working on a real line, and the reason is good news.** The
owner's line refused nothing on 3 October: MPEG-TS played, HLS played (this
panel really does serve `m3u8`, which is per-panel and worth knowing), the
app's own honest user agent was accepted, and eight channels in a row came up
first time. There was no failure to put a card on. What was measured on that
line is the data the feature stands on: in the next twelve hours, **465 of
6,238 slots were carried on two or more channels and 131 on four or more**.
The widest are US affiliates sharing syndicated output under generic names
— `Live: College Football` on 127 channels — while a pay-channel fixture
sits on one guide channel, which may still be several playable streams under
it. The log line is in place, so the first real failure will say what it
offered.

One thing to be careful of when reading these numbers: a first attempt at
counting them grouped by `(start, title)` and then labelled each row with the
longest title at that *start*, which pasted the wrong name onto real counts.
Label a group with its own title.

## A second guide, or several

Everything built on the guide is limited by what the provider publishes, and
on a real line that is thin: 40 hours ahead one day and 17 the next, 1,366
channels of 21,077 covered at all, and a fixture carried by seven feeds named
on one of them. So Settings takes extra XMLTV addresses and they are fetched
with the provider's own.

**Several, not one, and that is the owner's requirement rather than a
flourish.** They watch the Premier League, so a UK sports guide is the obvious
source — and the three o'clock Saturday kick-offs are not broadcast in the UK
at all, so the feeds carrying them are foreign and so are their listings. One
country's guide cannot answer both.

**`MAX_GUIDE_SOURCES` is eight, and it was four for about a day.** Four was
meant as two for the above plus room for another country, and the arithmetic
was simply wrong: asked what they actually watch, the owner named the UK and
Ireland *and* the United States, Canada and Australia, which is five addresses
before anyone has thought about it. Eight is headroom rather than a second
guess. The real cost is the download — each source is a few megabytes gzipped
and about ten seconds, fetched together after the provider's own guide, so
eight is roughly a minute on the box, twice a day at most.

The rules are `core/ExtraGuide.kt`, tested. Two hard parts, and neither is the
fetching:

- **Which of their channels is which of ours.** The id first, case-folded,
  because plenty of publishers copy their ids from the same few public
  sources. Failing that the name with the decoration taken off — `guideKey`
  drops a country or language prefix before a colon, maps `&` to "and"
  (this provider writes `ENGLISH: AND FLIX` where a public guide writes
  `&flix`), and drops the words that say how a channel is delivered rather
  than which channel it is: 4k, uhd, fhd, hd, hevc, backup and the rest. What
  it will *not* do is guess. A name that reduces to something two of the
  line's channels share is dropped rather than attached to one of them at
  random, because a guide on the wrong channel is worse than no guide: it is
  wrong with confidence, on a screen built to be trusted. The line really
  does carry ITV1 London twice.
- **And each of ours is claimed once**, which is a different question and was
  found on the owner's own line. A public guide carries the same channel
  twice — the Irish source lists "Sky Sports Premier League" beside "Sky
  Sports Premier League HD" — and both reduced to one name, so both were
  matched to our single channel and both schedules were written: the box drew
  every programme on it twice. That is **deduplicated rather than refused**,
  and the difference is the whole point. Two of *ours* sharing a name is a
  question nobody can answer, so it is dropped. Two of *theirs* is not
  ambiguous at all — it is one channel listed twice, carrying the same
  programmes — so the second claimant is ignored. Ids are matched in a pass of
  their own before names, so a channel both sides agree on is not decided by
  document order.
- **What to keep.** Only where the provider said nothing: a channel it has no
  guide for at all, or a stretch beyond where its guide reaches — `GuideSpan`
  per channel, and `worthKeepingBeyond`. Never a hole inside the provider's
  own stretch. It is closer to what it is actually broadcasting, a gap there
  is usually a junction rather than a mistake, and two sources interleaved
  across one evening is a guide nobody can read. A source is judged against
  what the earlier ones added as well, which is why its spans are merged in
  only once it has finished: within one document a channel's programmes need
  not arrive in order, and widening the span mid-stream would start refusing a
  source its own earlier entries.

Three things about the fetching itself:

- **It goes into the same writer, and so the same generation.** The extra
  sources are part of that download, not a second one, and nothing is
  committed until they have all had their turn. A source that fails is logged
  and skipped — the provider's guide is already in the writer by then and is
  worth having on its own.
- **It is sniffed for gzip, not asked.** Nearly every public source is served
  as `.xml.gz`, because a week of every channel is a hundred megabytes of
  text, and OkHttp only unwraps gzip it negotiated itself. Neither the file
  name nor the content type can be trusted — publishers serve `.gz` typed
  `text/xml`, and the fake panel does exactly that on purpose — so
  `maybeGunzip` reads the two magic bytes, which an XMLTV document cannot
  begin with.
- **A source is logged by `guideSourceLabel`, never by its URL.** Some
  publishers put a subscriber token in the query, and a token in a log is the
  same mistake as a stream URL in a log.

**Not keyed on the line**, unlike favourites, hidden channels and the viewer's
own lists. Those hold a provider's stream ids, which mean nothing on another
panel; a public guide describes channels, so it survives changing provider —
the one time somebody is least likely to want to set this up again.
`ExtraGuideStore`, plain preferences.

Settings carries a **Fetch now** beside Save, which is not a convenience: a
refresh runs at most every twelve hours, so without it somebody who had just
pasted two addresses would see nothing change until tomorrow and conclude they
had typed them wrong. Saving also puts the tidied list back in the field, so a
line that was not an address visibly goes at once rather than being dropped
silently in a background refresh hours later where nobody would see it. And a
guide source defaults to **https** where a panel defaults to http, which looks
like an inconsistency and is the opposite: panels are plain HTTP on high ports,
public guides are ordinary websites.

Proved on the `phone34` emulator against the fake panel's two extra sources,
one of them gzipped: `UK: FILM FOUR HD`, which the provider's `xmltv.php` says
nothing whatever about, gained three programmes — two from the UK source and
the later one from the foreign one — and the channel list drew "The Saturday
Film" under it where it had been blank. `news.test` gained a programme past
where the provider stops and not the one inside it; `sport.test` gained the
kick-off four hours out, which is the three o'clock case; the two ITV1 London
cuts gained nothing, because the name is ambiguous; and the foreign source's
programme overlapping what the UK one had just added was dropped while the
later one was kept.


## Several channels at once

Asked for, cancelled, and asked for again — "I want to have the multi screen
even if I cant test it". The rules are `core/MultiView.kt`, tested; the screen
is the TV branch's, because a set-top box is where anyone wants this and
`main` is phone first.

**The limit is the line, not the box, and that is the whole design.** Every
pane is its own player and so its own connection, and a line usually allows
one. Everything else in this app is built around that: the TV screen reuses
one player and stops the old stream before asking for the next, switching
channel happens inside the player screen rather than by navigating, and a
paused player is stopped because a paused one keeps the connection. Multiview
is the single feature that asks for more than one on purpose, so on a
one-connection line it will get a refusal for every pane after the first.

That is not a reason to refuse to build it. A line that allows three is a line
where this is lovely, and the viewer knows what they are paying for better
than the app does. It is the reason every rule here is about **failing well**:

- **Panes open one at a time, `PANE_OPEN_GAP_MS` apart.** Measured behaviour
  rather than politeness: a real line answered 458 to every reconnect for
  about fifteen seconds after a phone dropped off Wi-Fi, and the ordinary case
  of leaving one channel for another is refused when it happens too quickly.
  Asking for four streams in the same instant is the worst version of that,
  and a pane refused in a burst would take the ones behind it down too.
- **A refused pane blames the right thing.** `describePaneRefusal` says "your
  line will not give this a second stream: 2 panes are already using it" when
  others are playing, and falls back to the ordinary wording when none is —
  because then nothing of ours is holding the line. A 404 stays a missing
  channel: saying "close a pane" about it sends somebody chasing the wrong
  fault.
- **Exactly one pane has sound**, never none and never all. Four commentaries
  is not a feature anyone has wanted, and silence everywhere reads as four
  broken streams rather than as a choice.
- **The same channel cannot be opened twice.** Two connections for one picture
  is the most expensive mistake available on a line that allows two.
- **`MAX_PANES` is four, and the ceiling is the box.** A 1080p box decoding
  four streams is near the end of its hardware decoder, and the owner's is an
  Android 11 Xiaomi that has been as low as 235 MB free. The line's own limit
  will usually bite long before this does.

The grid is decided by the screen being wide: two go side by side rather than
stacked, because stacked leaves half a television empty; three is a 2x2 with a
cell spare rather than three columns, because a third of a 16:9 screen is a
letterbox slot nobody can follow a ball across.

**Untestable here, and knowingly so.** The owner's line is the only real one
available and it is the case this cannot work on, so what has been proven is
the arithmetic and the wording, not the experience. The fake panel can serve
several streams at once, which tests the plumbing and tests nothing about a
panel's patience.
## Your team

The thing this was asked for, in the owner's words: *"find out which
channels my team is playing and have them listed so I can easily switch
between them if connection goes bad."* A name typed once in Settings, and
after that a row at the top of Home saying where they are on — the match,
when it is, and every channel carrying it, each one a card that tunes it.

**A list, not a search, and the difference is the whole point.** The moment
the list is wanted is the moment a feed has just died, mid-match, with a
remote in hand. That is the worst imaginable time to be typing a team name
letter by letter on a D-pad keyboard. The name does not change from week to
week, so all of the work can be done in advance, and is. A search box was
built first and taken back out; it is in the history if it is ever wanted.

- **`core/YourTeam.kt`**, tested: which match to show — on now beats what is
  coming, because that is the one a dying feed is interrupting; failing that
  the soonest to come, so the row says something useful for the six days a
  week the team is not playing. Among several at once the widest-carried
  wins, which is the same ranking the rest of this uses and for the same
  reason: a fixture is on many channels and a programme merely naming the
  team is not. And what a name may be: trimmed, spaces collapsed, cut to 40.
  Blank is how the row is turned off, so there is no second switch to fall
  out of step with the name.
- **The name is not keyed on the line**, unlike favourites, hidden channels
  and the viewer's own lists. Those hold a provider's stream ids, which mean
  nothing on another panel; a team's name means the same everywhere, so it
  survives changing provider — the one time someone is least likely to want
  to set it up again. `TeamStore`, plain preferences.
- **It is the one thing on Home that can cost a request**, and that is a
  deliberate exception rather than a change of mind. Turning the guide's
  channel ids into things that can be pressed needs the channel list, which
  is megabytes on a real line — the reason there is no "recently added
  films" row. The difference is that nothing happens unless a team has been
  named: setting one is asking for exactly this, in advance. Everyone else
  pays nothing.
- **It looks again when the guide arrives.** Found by testing: on a first
  run Home is built before the full guide has downloaded, so the first
  answer is always "nothing for them" — and without `Guide.version` in the
  combine it stayed that way until something else rebuilt the screen.
- **Four days ahead**, not the one a window around a programme uses. A row
  that says "nothing" the moment a match ends is a row nobody trusts. The
  guide reaches only as far as the provider publishes, so this is a ceiling
  and usually not reached.
- **The day is named, always** (`matchWhenLabel`, tested). The row showed the
  clock alone for anything happening today and added a date only for another
  day, on the reasoning that today needs no saying. Against a fixture it does:
  the owner looked at "11:00 – 13:00" over a Champions League tie and said it
  could confuse somebody, and they were right — the only thing placing that
  time was the ON NOW tag beside the team's name, which is an inference, made
  at a glance, by someone who has just lost a picture. Near days get a word
  ("Today", "Tomorrow") because that is how people say them and a word reads
  quicker across a room than "Tue 6 Oct"; further off gets the weekday as well
  as the date, because "11 Oct" does not answer "is that the weekend".
  Yesterday is in there for one real case — a match that kicked off at 23:30
  and is still on at half past midnight is on now, and started yesterday.
- **A repeat ranks below a real fixture**, and that was found on the box
  rather than reasoned out. Arsenal were not playing at all that day, and the
  row announced **"Napoli vs. Arsenal · ON NOW"** — a first-matchday Champions
  League tie from three weeks before, being replayed at eleven in the morning
  on `tntsport.cl`, the *Chilean* TNT Sports, which the line carries as
  `CL: TNT SPORT`. The owner's verdict was the right one: "we already played
  napoli ages ago, it must be way off."

  **Nothing in the title can tell a replay from a live match**, because a
  broadcaster lists it under the title the live match had. The same fixture
  was in that guide four times that day at four unrelated times — 10:00
  Chilean, 10:40 Swiss, 12:00 Serbian, 15:00 Croatian — which is the shape of
  a repeat doing the rounds, where a live match is on many channels at *one*
  time. Only one of them said so in words, and in Serbian: `snimak`.

  XMLTV's `previously-shown` is the field that states it, and `readXmltv` used
  to throw it away with everything else that is not a title, a description or
  a time. It is now read, stored (`programme.repeat`, schema 2 — added as a
  column rather than rebuilt, because 0 is what every row already there means
  by it), carried on `Showing` and ranked on: a real fixture still to come
  beats a repeat happening now, because somebody whose team plays tonight
  wants tonight rather than a rerun of September. A repeat is still shown when
  it is all there is — it is the team, after all — but the row says **Repeat**
  rather than **On now**.

  **`Showing.repeat` is all of its channels, not any.** A fixture replayed on
  one channel and shown live on another is being played somewhere, and that is
  the one worth switching to.

  **How much it helps depends entirely on the guide.** Measured across the
  owner's five public sources: US2 marks 68,623 programmes and CA2 53,802,
  while UK1, IE1 and AU1 mark none at all. So `false` means "not stated", never
  "live", and on a guide that says nothing this changes nothing. The entry that
  caused all this came from the provider's own guide, which is exactly the one
  that cannot be measured from here.

Type the name the way the guide writes it. "Man Utd" and "Manchester
United" are not the same thing to a provider, and nothing here second-
guesses that on the viewer's behalf.

Proved on the `googletv34` emulator against the fake panel: the name set in
Settings, and Home then showing **Croatia / ON NOW / "UEFA Nations League:
Croatia v England" / 23:21–01:21 · on 4 channels**, with the four feeds
listed — including the backup sharing the failed channel's own guide id, and
not the unrelated programme on at the same moment.
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

**The groups panel can fail to take focus, and that used to kill the remote.**
It asks for focus one frame after scrolling its list to the group you are in.
On the fake panel's handful of groups that always worked; on a real line, with
hundreds of groups and a slow box, the item often was not composed yet,
`requestFocus` threw into a `runCatching` that swallowed it, and nothing held
focus at all. The grid had already stopped taking keys — `if (groupsOpen)
return false` — so **every key after that went nowhere**, and the only way out
was to close the guide and open it again. It looked for all the world like
Left was ignored.

It now tries for ten frames and, if focus still will not land, closes the
panel rather than stranding the remote; the grid takes focus back when it
goes. Found by driving the box against a real line — twenty-odd channels of
the fake panel could never have shown it.

### The options panel on a remote

Hold **OK**, or press **Menu** on a remote that has one, for the phone's
picture-and-sleep panel: picture shape, sleep timer, and the audio and
subtitle tracks when the stream carries a choice. **Back** closes it and the
remote goes back to changing channel.

It is reached by a key rather than by a button because of the rule above —
nothing focusable may *wait* over the video, or the first press of OK goes to
it instead of to the channel list. A panel that is only there when asked for
is a different thing: while it is up it takes the remote deliberately, which
is what `covered` already means for the channel list, and focus is handed back
when it closes.

Holding OK is the way in that matters. Most remotes have no Menu key — the
box's certainly does not — and OK is the one button every remote has. The hold
is read from the key event's repeat count, and `okHeld` carries the fact
between the repeat and the release, so the release that ends a hold does not
also open the channel list.

### The box opens on Home too

The TV build used to open on `TvLiveScreen`, on the grounds that the channel
list with its guide is what the owner asked for. They then asked for Home here
as well, so the shared `HomeScreen` is the TV build's start, `HOME` is first in
the rail, and `AppNav.kt` on this side routes a channel card to
`tv/live?channel={id}` — the TV's own screen, tuned to that channel, never the
phone's player. There is still no separate guide section: Live TV is the guide.

Getting a remote into the rows took an explicit hand-off; the D-pad note about
full-width headings says why. Walking Down on the Google TV emulator now goes
cog → Live TV → the first channel card → Films → Series, and Up retraces it.
OK on a card tunes the TV's own screen: the log shows `tv open channel 106`,
which is the stream behind the card numbered 8.

**Back takes one press now.** It used to take two — "Press Back again to
leave" — because live television was the bottom of the stack and Back ended
the evening. Home is under it now, so Back goes there, and a warning about
leaving would have been a warning about nothing.

What came over from main before any of it, and is worth having on its own:
`WatchStore` and the player's resume. A film left half-watched on the box
carries on where it stopped.

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

## Catch-up

Some providers keep a recording of a channel. Two fields say so —
`tv_archive` and `tv_archive_duration` — and `hasCatchUp` wants both, because
panels send archive on with a duration of zero, which is a promise of nothing.
The channel-list log line says what a line offers ("1 with catch-up up to 4
days", or "none with catch-up"); nothing else reveals it.

There is **no API call for a past programme**. The recording is addressed by
when it was on: `/timeshift/user/pass/{minutes}/{yyyy-MM-dd:HH-mm}/{id}.{ext}`.
Three things follow, and `core/CatchUp.kt` holds all three with tests:

- **The time in that URL is the panel's wall clock**, like everything else a
  panel says about time. So the shift is applied *in reverse* when building
  it. Get this wrong and nothing fails — it plays the wrong hour, which is
  much harder to notice than an error, and it is exactly what happened on the
  box: every recording played two hours early while looking perfectly healthy.

  **Ask the panel what time it thinks it is; do not only infer it.**
  `server_info` carries `time_now`, the panel's wall clock, beside
  `timestamp_now`, the same instant in unix seconds. The gap between them is
  the offset, stated rather than worked out, and it arrives on the first call
  the app makes. `panelClockShift` reads it, `Guide.learnPanelClock` asks for
  it once per line per launch, and `GuideClock` keeps it under the evidence
  from the full guide and above everything else.

  That was learnt the hard way. `GuideClock` could already work the error out
  from a programme found in both `get_short_epg` and `xmltv.php` — but only
  where the short answer answers. On the box it answers *nothing* for the
  channels the TV screen tunes: nine empty answers in one sitting, so nothing
  was ever learned, the shift stayed 0, and catch-up asked a UTC+2 panel for
  UTC. On the phone the same line worked, because the channels browsed there
  did answer. A fault that follows the device rather than the line is a fault
  in what the device happened to see.
- **The programme must have finished.** A panel will serve a catch-up that
  runs into the future, and what comes back stops dead at the live edge with
  the player waiting for more.
- **A programme half out of the window starts at the window's edge.** A
  three-hour film that began four days and an hour ago still has two hours
  kept; asking from its own start gets silence.
- **What comes back may begin before what was asked for, by several minutes,
  and that is the provider.** On the first real line a recording asked for at
  its scheduled start played five to ten minutes of the previous programme
  first. Checked in the log rather than assumed: the app asked for 90 minutes
  from `2026-09-26:23-45`, the panel's name for the exact second the guide
  gives that programme, and the cursor it came from lines up with it. Two
  ordinary reasons, both the panel's: an archive is stored in chunks, so a
  requested start is rounded down to a segment boundary, and a broadcast
  drifts against its published schedule — junctions and trailers do not read
  the guide. Nothing here can know either, and asking a minute or two later to
  compensate would clip the start on a panel that is accurate. Left alone
  deliberately.

A catch-up plays as a *channel* rather than as a film: it takes the channel's
retry policy, the channel's reconnect — rejoin, never resume at a byte — and
the channel's error wording, the connection limit included. `Playable.CatchUp`
says so, and `isBroadcast` is what the player asks.

**But it can be skipped in, because this panel serves a recording as a file.**
That was written here as settled — "a stream with no length and nothing to
seek in" — and it was a guess. Measured on the box: a recording asked for as
45 minutes came back with `duration=2401700 ms, seekable=true, live=false`.
So the controls follow the stream instead: the seek bar, the clock and the
skip buttons appear when the player says the item is seekable and has a
length. On a panel that streams a recording endlessly, as live television is
streamed, they stay hidden and nothing else changes. The fake panel serves
`/timeshift/` from a file with Range support for the same reason.

**Coming back follows the same answer.** A recording that drops, or that was
left while the app went away, is re-prepared at the position it reached
rather than rejoined at a "live edge" it does not have — rejoining a file at
its edge means starting the programme again, which is what it used to do.
A channel is still rejoined, because a position in live television is a
position in a window that has moved on. Verified on the emulator for the
coming-back case: away and back resumed at 00:47 rather than 00:00. Not
verified for a mid-stream drop — the fake panel's ten-minute recording
buffers whole in a few seconds, so killing the panel mid-play interrupts
nothing.

What still follows the *type* is `StreamRetryPolicy`, built with the player
before anything is known about the stream: a catch-up gets the channel's
policy, so ExoPlayer's own retry will not issue a Range request behind our
back. That is the conservative way round. Undoing it needs the same measured
answer the controls now use, and a panel that streams a recording endlessly
would punish getting it wrong — the HTTP layer skipping every byte already
watched, at the pace the panel sends them.

**The guide is the way in**, so the guide had to change: it only ever scrolled
back two hours, which was right when it answered "what is on" and useless for
finding something that has been on. On a line with an archive it now reaches
back as far as the recording does. It goes no further than the guide itself
knows, though, and that is usually less: `xmltv.php` generally starts at about
now, so the past is only as deep as what that download happened to include.

**A provider carries the same channel twice, and only one copy keeps a
recording.** On the first real line, `get_live_streams` for the whole line
answered 21,077 channels of which **365 kept seven days** — and the category
anyone would actually go to, `### UK GENERAL HEVC/HD ###`, had **none at
all**. So the BBC One you find by browsing is the BBC One that cannot be
caught up, and nothing on any screen said so: the only place an archive
showed was the button on a finished programme, which you would have to find
first. Hence the mark on the channel row — a small replay icon in the accent,
on the row and so on every search result — and it is the only way to tell the
two copies apart. The guide's reach into the past follows the same field, so
on a channel with no archive it still stops at two hours; that is what "the
guide only goes back two and a half hours on BBC" turned out to be.

The fake panel serves all of it: channel 101 keeps four days, channel 102
claims an archive for zero days, `/timeshift/` streams what is inside the
window and 404s what is outside, and the log prints how long ago each request
was for — which is the check that catches a wrong clock.

**Its clock is two hours ahead of UTC, as the first real line's was**, stated
in `server_info` and used when reading a `/timeshift/` path back. So a client
that sends UTC lands two hours early and the panel's log says so, in the
"(N h ago)" it prints beside every request. That is the fault the box had,
made reproducible.

## Recording

The last TV phase. The viewer keeps their own copy of a programme: the app
opens the channel and writes the bytes to a file. Not to be confused with
catch-up, which plays back the *provider's* recording — see above.

The rules are `core/Recordings.kt`, tested, and three measured facts shape
all of them.

- **One connection is one recorder.** There is no second tuner, so two
  recordings that overlap at all cannot both happen whatever channels they
  are on, and a recording in progress owns the line: the service stops
  playback through `ActivePlayback`, waits for the panel to notice, and then
  opens the channel itself. That is why "record what you are watching" stops
  the picture — bytes cannot be teed out of ExoPlayer, so the honest
  behaviour is the one the screen describes before you press it. Two
  overlapping recordings on the *same* channel are the exception and merge,
  which is also what stops an evening of one channel clashing with itself.
- **A broadcast does not keep to its published time**, as catch-up already
  measured. So a recording starts a minute early and ends three late, and the
  padding is part of the window rather than something a screen adds. The id
  is built from the programme's own start, so widening the padding later does
  not orphan what is already booked.
- **A USB stick is usually FAT32**, which refuses `\ / : * ? " < > |` in a
  name — provider channels are called `UK: BBC ONE HD` — and cannot hold a
  file of 4 GB, which at this bitrate is a little over an hour. Names are
  cleaned by replacement rather than deletion, or two channels collide on one
  file.

**Android TV has no file picker, and that decided where recordings go.** The
box resolves `OPEN_DOCUMENT_TREE` to nothing at all, so launching it throws;
the Google TV emulator resolves it to a framework stub that does nothing.
Both measured before anything was built. So the Storage Access Framework is
out and the viewer picks a *volume* rather than a folder:
`getExternalFilesDirs` gives the app a directory on each mounted volume, with
no permission and no picker, and Settings lists them with the free space on
each — which also answers "is the stick in?" without anyone reading a path.
It matters because **the box has very little room, and less each time**: 737 MB
free when recording was built, and **235 MB at 95% full** on 3 October, where a
24 MB debug APK would not install at all. `pm trim-caches 800M` took it back to
444 MB, which is what Android does for itself under pressure and the first thing
to try. Its own guide database is 52 MB of that and the right thing not to
delete: `GuideStore.commit` carries past programmes forward for catch-up
channels, and the provider publishes only about a day of past, so clearing it
throws away guide that cannot be fetched again. 737 MB was twelve to
twenty-four minutes; there is a test in core saying an hour does not fit and
neither does a quarter of an hour.

**Two ways in.** Hold OK while a channel plays and choose Record, for what is
on now. Or, in the guide, OK on a cell that has not been on yet — that cell
was the one place in the grid where OK did nothing anyone wanted, so the
gesture was free, and a remote with no spare keys needs the free ones. OK
again takes it off, and the footer says which it will do.

**One alarm at a time**, for the next recording due, re-armed when the list
changes, when a recording ends, when the app starts and after a reboot. A
reboot loses every alarm Android holds, so `BOOT_COMPLETED` is not a nicety.
It is exact and `USE_EXACT_ALARM` is declared rather than asked for: an
inexact alarm may be held back to batch it, and the usual way to get
permission is a Settings screen a television may not have — the file picker
having already taught that lesson.

`Recordings` in the rail lists what is booked, what is recording and what is
kept, with the reason beside anything that failed: "the stick is not in" is
worth more than silence. OK watches a finished one, cancels a booked one or
stops one in progress; holding OK deletes it, file and all, because a stick
holding recordings the app no longer lists is a stick that fills up for
reasons nobody can see.

A recording plays as a file, not as the channel it came from — `Playable.
Recorded`, seekable, with an end. Two things that cost time: the player's
media source is built on the HTTP data source, which is handed a `file://`
uri and throws `FileURLConnection cannot be cast to HttpURLConnection`,
reported as "Source error" and saying nothing about the real reason, so a
recording gets `DefaultDataSource` instead; and `ActivePlayback.stop()` goes
through the ExoPlayer the screen owns, which Media3 insists is the main
thread, while the whole service runs on IO.

**The clock decides when a recording is over, never the socket.** This was a
known gap and is not one now. A panel closing the connection made the read
return -1, and the recorder took that for the end: the file was closed and
the row said Done over whatever few minutes had arrived. It is the oldest
mistake in this app, made twice — a clean hang-up from a live panel is never
the end, which is the whole reason playback has a back-off and rejoins.

So the file is opened once and `record()` loops over connections, the same
`StreamReconnect` the player uses driving the back-off (1, 2, 4, 8, 15 s,
then give up), with the bytes from each going into the same file. The delays
matter as much as the retrying: a line that allows one stream counts the
connection it just lost for a few seconds, so an instant reconnect is
refused. One connection's worth of copying ends in a `Chunk` saying which of
three things happened — the end time arrived, the stream went, or something
retrying cannot fix — because only the first of those is a finish.

Refused **before a single byte** is still reported at once, because that is
the panel saying no — wrong format, dead channel, the connection limit — and
the reason is worth showing. Refused *after* bytes have arrived is only a
failed attempt at getting back in, which is what the back-off is for.

**Out of attempts keeps the file and says so.** A row claiming success over
a short file is worth less than nothing, so it is Failed, with how many
minutes were still to go and the promise that what was recorded is kept —
and that promise forced a second change: OK on such a row used to *forget*
it. `Recording.watchable` in `core/` is the rule now, tested: bytes and a
file behind them, in a state that is over (Done or Failed). Anything still
going is not watchable, because opening it would be the app competing with
itself for the one connection.

Driven on the `googletv34` emulator against the fake panel's **Drops every
20 s** channel, which hangs up after 20 s and answers 456 to a reconnect
inside 2.5 s. The log is the proof: `dropped at 0 MB; asking again in
1000 ms`, then 4000, then 8000, then 15000 — the 2000 ms step going on the
456 that the 1 s attempt earned — one file of 4,148,000 bytes from four
connections where the old code would have kept one 20-second connection and
called it Done, and the row then played that part-recording back at 640x360.
It gives up on about the fifth drop, which is the player's policy and
correct: `STEADY_MS` forgives attempts after 30 s of steady bytes, so only a
channel dropping faster than that ever runs out.

Proved end to end on the `googletv34` emulator against the fake panel: the
volume chosen, hold OK and Record, the picture stops with "Your line is free
for another device", 24,888,380 bytes arrive starting `0x47`; a cell booked
from the guide, the log saying "waking in 1 min", the alarm firing, the
service starting and the alarm re-arming itself; and a finished recording
played back at 640x360. The refusal path too — recording the fake panel's
"Line busy for 15 s" channel fails with the connection-limit sentence rather
than an empty file.

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
- **A panel a hold opens must ignore the release that opened it.** It has
  been needed four times now — the guide's favourite (`okHeld`), the list
  panel, the player's options panel, and anything else a hold will ever
  open — so it is `Modifier.deafUntilPressed()` in `ui/Dpad.kt` rather than
  a fourth copy. tv-material fires a long click while the key is still down
  and then acts on the centre key's release without caring whether it saw
  the press, so the release lands on whatever has focus by then, which is
  the panel that just appeared. In the options panel it was invisible until
  a hold put something that mattered at the top: the first row used to be
  "Fit", and pressing it set the picture to what it already was. Adding
  Record above it turned a harmless bug into one that recorded.

  **A fresh press means a repeat count of zero**, because the box's OK key
  repeats and those repeats land on the panel once it is up. Arming on any
  key down would arm on them.
- **A key that opens a window must act on the release, not the press.**
  `DpadTextField` started editing on the key *down*, so the keyboard window
  opened while OK was still held, and the release landed outside the
  app's composition. On the box and on the Google TV emulator that threw the
  whole screen away: the list panel went, then the screen, then the back
  stack was down to Home, with no Back ever dispatched and no activity
  restart — measured, not guessed, with lifecycle logs either side of the
  press. It now starts editing on the key up and swallows the press that
  goes with it. This is the third time the same rule has been needed; see
  `okHeld` in the guide and the list panel's arming.
  A phone's keyboard has not been seen to do it, which is why it went
  unnoticed on main. Anything else that opens a window from a key should do
  the same.
- **Decide input mode when focus is decided, never at composition.** The mode
  flips on the input itself, before recomposition, so a captured value is one
  input stale exactly when it matters. Capturing it broke a tablet with a
  keyboard: tap a field, type, and the first key made the field unfocusable.
  Read `LocalInputModeManager` inside `focusProperties`.
- **Material's focus indication is invisible from a sofa.** Anything selectable
  gets `focusFill()`, placed before `clickable` so it sees that element's focus
  and after its own `background` so it is the one seen.
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
- **The rail was centred, which strands it on a tall screen.** It had a
  weighted spacer above and below, so its items sat in the middle of the
  column. On a 540dp-tall television that reads as deliberate, because four
  items nearly fill it. On an 800x1280dp tablet in portrait it put Home,
  Live TV, Films and Series two thirds of the way down the left edge, a long
  way from the content and from where a thumb rests — measured as a rail
  column of `[0,137][160,2440]` holding its items between y 1028 and 1548.
  The top spacer is now there only below `SHORT_SCREEN_HEIGHT`, which is the
  same 560dp line the category shelf uses to decide whether to wrap, and for
  the same reason: a short screen is a television or a phone sideways, and
  wants the opposite of what a tall one wants.

  **This is the first time the app has been looked at on a tablet at all**,
  despite "phone and tablet first" being the first thing this file says. It
  was found by setting an emulator to `wm size 1600x2560` and
  `wm density 320`, which is cheaper than a tablet AVD and gives the same
  800x1280dp. Worth doing again after anything that touches layout.

  One thing that looked like a second bug and was not: the poster grid
  appeared to leave a fifth of the width empty. It does not — the node
  bounds show five columns at a 286px pitch across the full 1440px content
  area, and the fake panel's films category simply holds four films. Dump
  the bounds before believing a screenshot.
- **A bottom bar is unreachable on a TV.** It sits past the end of the list, so
  800 channels puts it 800 presses away. At 600dp and wider the sections are a
  `NavigationRail`, one press of left from anywhere. The `NavHost` must stay the
  same call in the same place whichever bar is showing, or it is recreated and
  every screen's state goes with it.
- **A wrapped shelf eats a television.** Categories wrap in a `CategoryShelf`
  four rows tall — asked for, because a single sideways row was tedious by
  touch on a real line's dozens of categories. On a 1080p box, which is 540dp
  tall, the top bar, the search field and those four rows left about a third
  of the height for the posters: the first row of covers was cut off by the
  bottom edge, and it *stayed* cut off when the remote moved into it, because
  the shelf is pinned and only the grid scrolls. The owner reported it as the
  posters not being fully visible, and looking for a cropping bug found a real
  one — but not that one.

  So the shelf is wrapped only where there is height for it: under
  `WRAP_MIN_HEIGHT` (560dp) it is a single row that scrolls sideways, which is
  a television and a phone held sideways. A phone upright is around 800dp and
  a tablet more, so neither changes. On the box a full row of posters and
  their titles now fits, with the next row showing beneath. Down from a chip
  lands in the grid, because there is no second row of chips to catch it.
- **Right at the end of a row leaves the row.** There is no card past the
  last one, and Compose does not stop there: it looks for the nearest
  focusable anywhere to the right and takes it. On a television with one
  favourite channel that was the settings cog in the opposite corner — one
  press of Right and the remote was most of the screen away in the top bar,
  measured as a jump from `[192,208][356,434]` to `[1798,10][1894,106]`, with
  a second press doing nothing because there is no further right. Every
  Home row cancels a focus exit to the right now. Only Right: Left is how a
  remote gets back to the rail, and up and down are how the rows are walked.
- **A heading the width of the screen beats the small card under it.** Down
  from a Home row heading went to the *next heading*, never into the row. The
  cards were focusable all along and were candidates in the search: they
  simply never won it. Compose picks the next focus by Android's old
  weighting, which counts the sideways distance between the two centres as
  well as the distance in the direction pressed — and a full-width heading's
  centre is most of the screen away from a 76dp card's, while the next
  heading's is directly below. The two scores came out close, and the heading
  won. So each heading names its own row with `focusProperties { down = … }`
  and the row is a `focusGroup()`, which passes the request on to its first
  card. Anything shaped like a row of cards under a wide heading needs the
  same.

  Two things about finding it, both of which cost time here. The box returns
  **blank screenshots**, so `uiautomator dump` and element bounds are the only
  view of it, and a focused rectangle at `[0,676][160,800]` was taken for a
  card when it was the navigation rail. And a **stale APK looks exactly like a
  fix that did not work** — the build had failed on a bad `JAVA_HOME` and
  installed the morning's APK without complaint. Three attempts on the box
  proved nothing; the Google TV emulator, where screenshots work, showed it in
  one pass. Check the APK's timestamp before believing a device.
- **A TV needs `android.hardware.touchscreen` required="false"**, or it counts
  as unable to run the app, plus `LEANBACK_LAUNCHER` and a banner to appear on
  its home screen. All three are in the manifest, and the app has now been
  looked at on the owner's own box — a Xiaomi running Google TV — rather than
  only on the TV-shaped phone image the emulator provides. It is there, once,
  under **Your apps**, drawing its own icon.

  **The banner is not what that launcher uses.** Google TV's launcher draws a
  round app icon in a row, the way a phone launcher does; the wide banner is
  the older Android TV leanback launcher's idea, and nothing on this box asks
  for it. It stays in the manifest because a box running that older launcher
  would want it, and because its absence there is a blank tile rather than a
  fallback — but do not go looking for it on a Google TV and conclude it is
  broken.

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
turned up the 458 above.

**Catch-up has now played on that line too**, which until 26 September it
never had. A channel in `#### GENERAL HD/4K ####` — 191 of its 199 keep
three days — a programme picked out of the night before, and
`/timeshift/u/p/50/2026-09-26:03-25/162115.ts` came back at 1920x1080. The
time in that path is the point: the guide had the programme at 01:25 UTC, the
panel is two hours ahead of UTC, and 03:25 is what it was therefore asked for.
That is `GuideClock`'s learned shift being undone, and it is the one part of
catch-up that fails silently rather than loudly — so it was checked the only
way it can be: the viewer confirmed that what played was the programme they
had tapped, not the one an hour either side of it.

That is one provider. The next one will differ, and
the surprises will be in what it returns.


**A recording and a reminder both survive a real reboot, and that had never
been tried.** It was written down as unverified for good reason:
`BOOT_COMPLETED` is a protected broadcast that cannot be faked from `adb
shell`, so an emulator proves nothing about it. Driven on the owner's box on
6 October: a recording set from the guide (*Escape to the Country*, 15:00) put
an exact `RECORDING_DUE` alarm at 14:59:00; the box was rebooted; and with the
app never launched its process started on its own and re-armed the identical
alarm, same instant to the millisecond. The same reboot fired
`ReminderAlarmReceiver` too.

**Both receivers are `android:exported="false"`, and that does not stop it.**
Worth writing down because it looks like a bug and was nearly "fixed" as one:
the reasoning that a system broadcast cannot reach a non-exported receiver is
wrong here, and the box says so. Leave them unexported — exporting them would
let any app on the device send `RECORDING_DUE` and `REMINDER_DUE`.

**The reminder lands five minutes early**, as `REMINDER_LEAD_SECONDS` says:
a 21:00 kick-off armed `REMINDER_DUE` for 20:55:00. Pressing the pill again
cancelled it, and the alarm went with it.

**The one thing the box has that the emulator has not is a full disk.** The
recordings store held a failure from 2 October — 548 MB of *The Celebrity
Traitors: Uncloaked* written and then "The disk is full. What was recorded up
to then is kept." That is the right behaviour, and it is also a warning: the
box had about 1 GB free, which is a fraction of one programme.
An emulator run is worth doing for any change to `app/` — the SDK on the dev
machine has an emulator and a `phone34` AVD, and it caught a real bug (see
`busy` in `LibraryUiState`) that no test could. Boot it headless with
`-no-window -gpu swiftshader_indirect`; on a cold boot the launcher and System
UI time out for a minute or two, and `settings put global hide_error_dialogs 1`
stops their dialogs eating input. From the emulator the host is `10.0.2.2`.
