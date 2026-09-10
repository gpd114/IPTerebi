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
| Every channel when no category is given, as a real panel does | Searching all channels: search from **News** for "sport" and **Sport One**, in another category, has to turn up |
| Ids quoted in one record and bare in the next | The flexible serialisers |
| **Refused (connection limit)**, which answers 403 | The error wording, and Try again being reachable with a remote |
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
  connection closes; the player logs that as `ended (source closed the
  connection)`, which on a real line is usually the connection limit.
- **A connection limit.** Only channel 103 refuses; nothing counts connections.
- **Anything a real provider does that is not written down yet.** It only
  misbehaves in the ways already known. The first session on an actual line is
  still where the new ones will turn up — and each one found belongs here as
  well as in a test.
