#!/bin/bash
# Measures every candidate piece: downloads it into a fresh temp dir, records size, SHA-256,
# stream format, file tags (composer, title …) and simple audio features (features.py), then
# deletes the audio again. Needs curl, ffmpeg/ffprobe, python3 with numpy.
# usage: measure.sh [candidates.txt] [analysis.jsonl]
set -u
T=$(cd "$(dirname "$0")" && pwd)
IN=${1:-$T/candidates.txt}
OUT=${2:-$T/analysis.jsonl}
: > "$OUT"
while read -r id url; do
  [ -z "$id" ] && continue
  d=$(mktemp -d)
  if curl -sS -f -L -o "$d/a" "$url" < /dev/null; then
    size=$(stat -c %s "$d/a"); sha=$(sha256sum "$d/a" | cut -d' ' -f1)
    info=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels -of csv=p=0 "$d/a" < /dev/null)
    tags=$(ffprobe -v error -show_entries format_tags:stream_tags -of json "$d/a" < /dev/null | python3 -I -c "
import json,sys
d=json.load(sys.stdin); t={}
t.update(d.get('format',{}).get('tags',{}))
for s in d.get('streams',[]): t.update(s.get('tags',{}))
print(json.dumps({k.lower(): v for k, v in t.items()}, ensure_ascii=False))")
    ffmpeg -nostdin -v error -i "$d/a" -ac 1 -ar 22050 -f f32le "$d/a.raw"
    feat=$(python3 -I "$T/features.py" "$d/a.raw")
    echo "{\"id\":\"$id\",\"url\":\"$url\",\"size\":$size,\"sha256\":\"$sha\",\"stream\":\"$info\",\"tags\":$tags,\"f\":$feat}" >> "$OUT"
  else
    echo "{\"id\":\"$id\",\"url\":\"$url\",\"error\":true}" >> "$OUT"
  fi
  rm -rf "$d"
done < "$IN"
