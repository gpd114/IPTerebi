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
