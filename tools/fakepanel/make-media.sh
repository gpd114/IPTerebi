#!/usr/bin/env bash
# Generates the test media the fake panel serves: two films, a ten-minute
# "live" stream and two posters, all synthetic test patterns.
#
# Generated rather than committed: together they are about 45 MB, and ffmpeg
# makes them in under a minute. Needs ffmpeg on the PATH (on Windows, run this
# from Git Bash).
#
#     ./make-media.sh            # writes ./media next to this script
set -euo pipefail

cd "$(dirname "$0")"
command -v ffmpeg >/dev/null || { echo "ffmpeg is not on the PATH" >&2; exit 1; }
mkdir -p media
cd media

ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }

# A film in mp4. faststart puts the index at the front, which is how most
# providers encode theirs; the burned-in clock makes a seek checkable by eye.
echo "film.mp4 (90 s)"
ff -f lavfi -i "testsrc2=size=640x360:rate=25:duration=90" -f lavfi -i "sine=frequency=440:duration=90" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 64k -movflags +faststart film.mp4

# A film in mkv, whose index sits at the end of the file. The player has to
# read it with a ranged request before it can start, which is worth having.
echo "film.mkv (60 s)"
ff -f lavfi -i "smptebars=size=640x360:rate=25:duration=60" -f lavfi -i "sine=frequency=660:duration=60" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 64k film.mkv

# "Live": ten minutes of MPEG-TS, sent with no length. A keyframe every two
# seconds, so a player joining it starts promptly.
echo "live.ts (10 min)"
ff -f lavfi -i "testsrc=size=640x360:rate=25:duration=600" -f lavfi -i "sine=frequency=330:duration=600" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -g 50 -c:a aac -b:a 64k -f mpegts live.ts

echo "posters"
ff -f lavfi -i "testsrc2=size=400x600:duration=1" -frames:v 1 poster501.jpg
ff -f lavfi -i "smptebars=size=400x600:duration=1" -frames:v 1 poster502.jpg

echo "done: $(pwd)"

# Streams that carry a choice, which is what the audio-and-subtitles panel
# exists for. Providers send these constantly — a match with the home and the
# away commentary, a film with its original language beside a dub — and there
# was nothing here to point the picker at.
#
# The language goes in the ISO 639 descriptor in the PMT (and in the track
# header in Matroska), which is where a player reads it from. Without it the
# tracks are "Audio 1" and "Audio 2" and the panel cannot say which is which,
# so it is worth having both cases: the channel names its languages.
echo "multitrack.ts (10 min, English and Italian audio)"
ff -f lavfi -i "testsrc=size=640x360:rate=25:duration=600" \
    -f lavfi -i "sine=frequency=440:duration=600" \
    -f lavfi -i "sine=frequency=880:duration=600" \
    -map 0:v -map 1:a -map 2:a \
    -c:v libx264 -preset ultrafast -crf 32 -pix_fmt yuv420p -g 50 -c:a aac -b:a 64k \
    -metadata:s:a:0 language=eng -metadata:s:a:1 language=ita \
    -f mpegts multitrack.ts

# Subtitles live on the film rather than the channel, and that is not a
# shortcut: a live TS carries DVB subtitles, which are bitmaps, and ffmpeg
# cannot make them out of text — "Subtitle encoding currently only possible
# from text to text or bitmap to bitmap". Matroska takes SubRip as it is, and
# VOD is where subtitles mostly turn up anyway.
echo "film_tracks.mkv (2 min, English and French audio, English subtitles)"
cat > subs.srt <<'SRT'
1
00:00:02,000 --> 00:00:12,000
Subtitles are on.

2
00:00:14,000 --> 00:00:24,000
If you can read this, the text track was selected.

3
00:00:26,000 --> 00:01:59,000
Still on.
SRT
ff -f lavfi -i "smptebars=size=640x360:rate=25:duration=120" \
    -f lavfi -i "sine=frequency=660:duration=120" \
    -f lavfi -i "sine=frequency=990:duration=120" \
    -i subs.srt \
    -map 0:v -map 1:a -map 2:a -map 3:s \
    -c:v libx264 -preset ultrafast -crf 32 -pix_fmt yuv420p -c:a aac -b:a 64k -c:s srt \
    -metadata:s:a:0 language=eng -metadata:s:a:1 language=fra -metadata:s:s:0 language=eng \
    film_tracks.mkv
rm -f subs.srt
