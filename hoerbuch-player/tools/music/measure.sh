#!/bin/bash
# Measures every candidate piece: downloads it into a fresh temp dir, records size, SHA-256,
# stream format and simple audio features (features.py), then deletes the audio again.
# Needs curl, ffmpeg/ffprobe, python3 with numpy. Output: analysis.jsonl next to this script.
set -u
T=$(cd "$(dirname "$0")" && pwd)
OUT=$T/analysis.jsonl
: > "$OUT"
while read -r id url; do
  d=$(mktemp -d)
  if curl -sS -f -o "$d/a.ogg" "$url" < /dev/null; then
    size=$(stat -c %s "$d/a.ogg"); sha=$(sha256sum "$d/a.ogg" | cut -d' ' -f1)
    info=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels -of csv=p=0 "$d/a.ogg" < /dev/null)
    ffmpeg -nostdin -v error -i "$d/a.ogg" -ac 1 -ar 22050 -f f32le "$d/a.raw"
    feat=$(python3 -I "$T/features.py" "$d/a.raw")
    echo "{\"id\":\"$id\",\"url\":\"$url\",\"size\":$size,\"sha256\":\"$sha\",\"stream\":\"$info\",\"f\":$feat}" >> "$OUT"
  else
    echo "{\"id\":\"$id\",\"error\":true}" >> "$OUT"
  fi
  rm -rf "$d"
done < "$T/candidates.txt"
