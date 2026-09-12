# Fake panel

A stand-in Xtream Codes panel, for running IPTerebi without a real line. One
Java file, no dependencies, nothing to build.

It is not a well-behaved panel. It sends the data real panels send — ids as
numbers and as strings, channels with no id, a category with a blank id, base64
guide titles, a channel that refuses with 403, series in every shape
`get_series_info` comes in — because a panel that only sends clean data proves
nothing. Running the app against it for the first time found bugs no test had.

## Running it

You need JDK 17 (already needed to build the app) and ffmpeg.

```bash
cd tools/fakepanel
./make-media.sh            # once: generates ./media, about 45 MB of test patterns
java FakePanel.java        # serves on port 8080
```

On Windows, run `make-media.sh` from Git Bash. `java FakePanel.java [mediaDir]
[port]` if you want either somewhere else.

Then sign in to the app with:

| From | Server address | Username | Password |
|---|---|---|---|
| An Android emulator | `10.0.2.2:8080` | `demo` | `demo` |
| A phone on the same Wi-Fi | `<this computer's IP>:8080` | `demo` | `demo` |

`10.0.2.2` is how an emulator reaches the computer running it. For a real phone,
Windows may ask whether Java can accept connections the first time — it has to,
for this to work. Every request is logged, which is the easiest way to see what
the app actually asked for.

## What it gets wrong on purpose

Each of these is a quirk written down in `CLAUDE.md` and handled somewhere in
the app. If the app misbehaves here, that is the place to look.

| The panel sends | What it exercises |
|---|---|
| Two categories that are a blank id and a repeat | `usableCategories()` — both must vanish, not crash the category row |
| Two channels with no `stream_id` | `playableChannels()` — they would share list key 0 and take the list down |
| 35 more live categories, empty, named the way providers name them — `UK \| Sports HD`, `24/7 \| Cartoons`, and some in Greek, Arabic, Japanese and Korean | The category shelf at a real line's size: four rows, the next peeking under the fade, scrolling, and the open one scrolled into view on return. Also the empty-category message |
| Every channel when no category is given, as a real panel does | Searching all channels: search from **News** for "sport" and **Sport One**, in another category, has to turn up |
| Ids quoted in one record and bare in the next | The flexible serialisers |
| **Refused (connection limit)**, which answers 403 | The error wording, and Try again being reachable with a remote |
| **Drops every 20 s**: hangs up cleanly after 20 s of playing, and answers 456 to a reconnect within 2.5 s of that, as a panel still counting the old connection does | Reconnecting a dropped channel, the "Reconnecting…" label, and — with the screen off — the app staying in the foreground through it. Dropping this often, it runs out of attempts on about the sixth drop, which is correct |
| **Drops, then off air**: hangs up after 20 s, then answers 503 for 45 s | The back-off (one request per attempt, 1, 2, 4, 8, 15 s apart), giving up after about half a minute with Try again, and Try again working once it is back |
| **Line busy for 15 s**: the first ask after a quiet minute starts 15 s of 458 — what a real line answered for about that long after a phone dropped off it — then plays | The TV's "Waiting for your line": it should ask about every 3 s, say what it is doing, and get in once the line frees, with no error card |
| Film **Drops mid-film (MP4)**: sent at the pace it plays, its connection goes silent 40% of the way in, then it answers 458 for 35 s — 15 s of refusals once the player notices, as a real line gave | A film reconnecting at the second it reached rather than stopping on an error — the player's 20 s read timeout notices the silence, the refusals are waited out, and it carries on. Drops at most once every two minutes |
| A guide for **Test News HD** only: base64 titles, one timestamp quoted and one bare, and `start`/`end` strings that are deliberately wrong | `decodeEpgText`, and the guide reading timestamps rather than strings. Every other channel has no guide, the normal case |
| A film in mp4 and one in mkv | Film URLs using the film's own extension, not the live stream format; the mkv needs a ranged read of its index before it starts |
| **Test Horses**: episodes keyed by season and out of order, specials in season 0, an empty `seasons`, an episode with `info: []`, a repeated and a blank episode id, titles with a `Show - S01E01 - ` prefix | `parseSeriesDetail` — the order, the names, the damage limitation, and the title clean-up |
| **Bars and Tones**: episodes as an array of arrays and `info: []` | The other `episodes` shape, and falling back to the name from the list |
| The password echoed back in the sign-in response | Real panels do this; it must never reach the log |
| Anything else answered with the literal `false` | How forks refuse actions they don't implement |
| A wrong password answered 200 with `auth: 0` | Real panels never send a 401 |

## What it does not do

- **HLS.** Only MPEG-TS is served, so switching the Stream format setting to HLS
  gets a 404 — a realistic answer, but not a test of HLS playback.
- **Live that never ends.** The "live" stream is ten minutes long and then the
  connection closes, which the app treats as a drop and reconnects — so the
  test pattern starts again from 0.
- **A connection limit.** Only channels 103 and 104 refuse, each on its own
  rule; nothing counts connections across channels.
- **Anything a real provider does that is not written down yet.** It only
  misbehaves in the ways already known. The first session on an actual line is
  still where the new ones will turn up — and each one found belongs here as
  well as in a test.
