'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const V = require('../extension/src/common/media.js');

test('classify: Videodateien über MIME-Typ', () => {
  assert.equal(V.classify('https://cdn.example.com/a/movie', 'video/mp4').kind, 'video');
  assert.equal(V.classify('https://cdn.example.com/a/movie.webm', 'video/webm; codecs=vp9').kind, 'video');
  assert.equal(V.classify('https://cdn.example.com/song.mp3', 'audio/mpeg').kind, 'audio');
});

test('classify: Streams', () => {
  assert.equal(V.classify('https://x.test/master.m3u8', 'application/vnd.apple.mpegurl').kind, 'hls');
  assert.equal(V.classify('https://x.test/master.m3u8?token=1', 'text/plain').kind, 'hls');
  assert.equal(V.classify('https://x.test/stream', 'application/x-mpegURL').kind, 'hls');
  assert.equal(V.classify('https://x.test/manifest.mpd', 'application/dash+xml').kind, 'dash');
  assert.equal(V.classify('https://x.test/manifest.mpd', 'application/xml').kind, 'dash');
});

test('classify: generische MIME-Typen nutzen die Dateiendung', () => {
  assert.equal(V.classify('https://x.test/file.mkv', 'application/octet-stream').kind, 'video');
  assert.equal(V.classify('https://x.test/file.mp4', '').kind, 'video');
  assert.equal(V.classify('https://x.test/file.mp4', 'text/html'), null);
  assert.equal(V.classify('https://x.test/page.html', 'application/octet-stream'), null);
});

test('classify: Segmente und Nicht-HTTP werden ignoriert', () => {
  assert.equal(V.classify('https://x.test/seg-12-v1.ts', 'video/mp2t'), null);
  assert.equal(V.classify('https://x.test/chunk.m4s', 'video/iso.segment'), null);
  assert.equal(V.classify('https://x.test/video/segment_3.mp4', 'video/mp4'), null);
  assert.equal(V.classify('https://x.test/init-0.mp4', 'video/mp4'), null);
  assert.equal(V.classify('https://x.test/v.mp4?range=0-65535', 'video/mp4'), null);
  assert.equal(V.classify('blob:https://x.test/123', 'video/mp4'), null);
  assert.equal(V.classify('data:video/mp4;base64,AAAA', 'video/mp4'), null);
  assert.equal(V.classify('https://x.test/image.png', 'image/png'), null);
});

test('classifyDom', () => {
  assert.equal(V.classifyDom('https://x.test/watch?id=1', 'video').kind, 'video');
  assert.equal(V.classifyDom('https://x.test/watch?id=1', 'a'), null);
  assert.equal(V.classifyDom('https://x.test/clip.mp4', 'a').kind, 'video');
  assert.equal(V.classifyDom('https://x.test/podcast.mp4', 'audio').kind, 'audio');
  assert.equal(V.classifyDom('https://x.test/live.m3u8', 'meta').kind, 'hls');
  assert.equal(V.classifyDom('blob:https://x.test/abc', 'video'), null);
});

test('parseSize', () => {
  assert.equal(V.parseSize('bytes 0-1023/987654321', '1024', 206), 987654321);
  assert.equal(V.parseSize('bytes 0-1023/*', '1024', 206), null);
  assert.equal(V.parseSize('', '1024', 206), null);
  assert.equal(V.parseSize('', '5000', 200), 5000);
  assert.equal(V.parseSize('', '', 200), null);
});

test('normalizeUrl entfernt Fragmente', () => {
  assert.equal(V.normalizeUrl('https://x.test/a.mp4#t=10'), 'https://x.test/a.mp4');
  assert.equal(V.normalizeUrl('javascript:alert(1)'), null);
});

const MASTER = `#EXTM3U
#EXT-X-VERSION:6
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Deutsch",LANGUAGE="de",URI="audio/de.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aud"
low/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="aud"
https://other.test/high/index.m3u8?sig=abc
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
mid/index.m3u8
`;

test('parseHls: Master-Playlist', () => {
  const r = V.parseHls(MASTER, 'https://cdn.test/v/master.m3u8');
  assert.equal(r.isMaster, true);
  assert.deepEqual(r.variants.map((v) => v.label), ['1080p', '720p', '360p']);
  assert.equal(r.variants[0].url, 'https://other.test/high/index.m3u8?sig=abc');
  assert.equal(r.variants[2].url, 'https://cdn.test/v/low/index.m3u8');
  assert.equal(r.renditions[0].url, 'https://cdn.test/v/audio/de.m3u8');
  assert.equal(r.drm, false);
});

test('parseHls: Medien-Playlist mit Dauer, VOD und AES-128', () => {
  const text = `#EXTM3U
#EXT-X-TARGETDURATION:10
#EXT-X-KEY:METHOD=AES-128,URI="key.bin"
#EXTINF:10.0,
s1.ts
#EXTINF:10.0,
s2.ts
#EXTINF:5.5,
s3.ts
#EXT-X-ENDLIST`;
  const r = V.parseHls(text, 'https://cdn.test/v/index.m3u8');
  assert.equal(r.isMaster, false);
  assert.equal(r.duration, 26);
  assert.equal(r.live, false);
  assert.equal(r.encrypted, true);
  assert.equal(r.drm, false);
});

test('parseHls: Live und DRM', () => {
  const text = `#EXTM3U
#EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://abc",KEYFORMAT="com.apple.streamingkeydelivery"
#EXTINF:6,
a.ts`;
  const r = V.parseHls(text, 'https://cdn.test/live.m3u8');
  assert.equal(r.live, true);
  assert.equal(r.drm, true);
  assert.equal(V.parseHls('<html>', 'https://x.test/'), null);
});

test('parseHls: gleiche Auflösung bekommt Bitrate im Label', () => {
  const text = `#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
a.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
b.m3u8`;
  const r = V.parseHls(text, 'https://x.test/m.m3u8');
  assert.deepEqual(r.variants.map((v) => v.label), ['720p · 3,0 Mbit/s', '720p · 1,5 Mbit/s']);
});

test('parseDash', () => {
  const mpd = `<?xml version="1.0"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT1H2M3.4S">
 <Period><AdaptationSet mimeType="video/mp4">
  <Representation id="1" width="1920" height="1080" bandwidth="5000000"/>
  <Representation id="2" width="1280" height="720" bandwidth="2500000"/>
 </AdaptationSet></Period></MPD>`;
  const r = V.parseDash(mpd);
  assert.equal(r.duration, 3723);
  assert.deepEqual(r.heights, [1080, 720]);
  assert.equal(r.drm, false);
  assert.equal(r.live, false);
  const drm = V.parseDash('<MPD type="dynamic"><ContentProtection schemeIdUri="urn:uuid:edef8ba9"/></MPD>');
  assert.equal(drm.drm, true);
  assert.equal(drm.live, true);
});

test('parseIsoDuration', () => {
  assert.equal(V.parseIsoDuration('PT30S'), 30);
  assert.equal(V.parseIsoDuration('PT1M'), 60);
  assert.equal(V.parseIsoDuration('P1DT1H'), 90000);
  assert.equal(V.parseIsoDuration('quatsch'), 0);
});

test('buildIntentUrl', () => {
  const s = V.buildIntentUrl('https://cdn.test/path/video.mp4?a=1&b=2#t=5', {
    pkg: 'org.videolan.vlc',
    mime: 'video/*',
    title: 'Mein Film; Teil 1',
  });
  assert.equal(
    s,
    'intent://cdn.test/path/video.mp4?a=1&b=2#Intent;scheme=https;action=android.intent.action.VIEW;' +
      'type=video/*;package=org.videolan.vlc;S.title=Mein%20Film%3B%20Teil%201;end'
  );
  const chooser = V.buildIntentUrl('http://1.2.3.4:8080/live.m3u8', { mime: 'video/*' });
  assert.equal(chooser, 'intent://1.2.3.4:8080/live.m3u8#Intent;scheme=http;action=android.intent.action.VIEW;type=video/*;end');
  assert.equal(V.buildIntentUrl('ftp://x.test/a.mp4', {}), null);
});

test('buildM3u', () => {
  const m = V.buildM3u({
    url: 'https://cdn.test/a.m3u8',
    title: 'Titel\nmit Umbruch',
    referer: 'https://seite.test/video',
    userAgent: 'Mozilla/5.0',
  });
  assert.equal(
    m,
    '#EXTM3U\n#EXTINF:-1,Titel mit Umbruch\n#EXTVLCOPT:http-referrer=https://seite.test/video\n' +
      '#EXTVLCOPT:http-user-agent=Mozilla/5.0\nhttps://cdn.test/a.m3u8\n'
  );
});

test('Formatierung', () => {
  assert.equal(V.formatBytes(1536), '1,5 KB');
  assert.equal(V.formatBytes(1288490189), '1,2 GB');
  assert.equal(V.formatBytes(0), '');
  assert.equal(V.formatDuration(3723), '1:02:03');
  assert.equal(V.formatDuration(65), '1:05');
  assert.equal(V.safeFilename('a/b:c*?'), 'a b c');
  assert.equal(V.safeFilename(''), 'video');
  assert.equal(V.filenameFromUrl('https://x.test/dir/Mein%20Video.mp4?x=1'), 'Mein Video.mp4');
  assert.equal(V.filenameFromUrl('https://x.test/'), 'x.test');
});

test('hiddenReason und Sortierung', () => {
  const s = V.DEFAULT_SETTINGS;
  assert.equal(V.hiddenReason({ kind: 'audio' }, s), 'audio');
  assert.equal(V.hiddenReason({ kind: 'audio' }, { showAudio: true }), null);
  assert.equal(V.hiddenReason({ kind: 'hls', isVariant: true }, s), 'variant');
  assert.equal(V.hiddenReason({ kind: 'video', size: 200 * 1024 }, s), 'small');
  assert.equal(V.hiddenReason({ kind: 'video', size: 200 * 1024 }, { minSizeMB: 0 }), null);
  assert.equal(V.hiddenReason({ kind: 'video', size: null }, s), null);

  const sorted = V.sortItems([
    { url: 'a', kind: 'video', size: 10 },
    { url: 'b', kind: 'hls', analysis: { isMaster: true } },
    { url: 'c', kind: 'video', size: 500 },
    { url: 'd', kind: 'dash' },
    { url: 'e', kind: 'video' },
  ]);
  assert.deepEqual(sorted.map((i) => i.url), ['b', 'd', 'c', 'a', 'e']);
});

test('Player', () => {
  assert.equal(V.playerName('org.videolan.vlc'), 'VLC');
  assert.equal(V.playerName(''), 'App');
  assert.equal(V.effectivePlayer({ player: 'custom', customPackage: ' com.foo.bar ' }), 'com.foo.bar');
  assert.equal(V.effectivePlayer({}), 'org.videolan.vlc');
});

test('Teilstücke mit bytestart/byteend werden ignoriert', () => {
  assert.equal(V.classify('https://video.cdn.test/v/abc.mp4?bytestart=0&byteend=99999&oh=1', 'video/mp4'), null);
  assert.equal(V.classify('https://video.cdn.test/v/abc.mp4?oh=1', 'video/mp4').kind, 'video');
});

test('pickBest bevorzugt Master-Playlists ohne DRM und ignoriert Kleines', () => {
  const items = [
    { url: 'ad', kind: 'video', size: 300 * 1024 },
    { url: 'mp4', kind: 'video', size: 50 * 1048576 },
    { url: 'drm', kind: 'dash', analysis: { drm: true } },
    { url: 'master', kind: 'hls', analysis: { isMaster: true } },
    { url: 'audio', kind: 'audio', size: 9e9 },
  ];
  assert.equal(V.pickBest(items, {}).url, 'master');
  assert.equal(V.pickBest(items.filter((i) => i.url !== 'master'), {}).url, 'mp4');
  assert.equal(V.pickBest([{ url: 'drm', kind: 'dash', analysis: { drm: true } }], {}).url, 'drm');
  assert.equal(V.pickBest([{ url: 'ad', kind: 'video', size: 1000 }], {}), null);
});

const MASTER_4K = `#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=25000000,RESOLUTION=3840x2160
2160/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080
1080/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1280x720
720/index.m3u8
`;

test('chooseStream: 4K-Master startet mit der eingestellten Maximalqualität', () => {
  const analysis = V.parseHls(MASTER_4K, 'https://cdn.test/s/master.m3u8');
  const item = { url: 'https://cdn.test/s/master.m3u8', kind: 'hls', analysis };
  assert.deepEqual(V.chooseStream(item, {}), { url: 'https://cdn.test/s/1080/index.m3u8', label: '1080p', limited: true });
  assert.equal(V.chooseStream(item, { maxHeight: 720 }).url, 'https://cdn.test/s/720/index.m3u8');
  assert.equal(V.chooseStream(item, { maxHeight: 480 }).url, 'https://cdn.test/s/720/index.m3u8'); // niedrigste vorhandene
  assert.equal(V.chooseStream(item, { maxHeight: 0 }).url, item.url); // unbegrenzt -> Master
  assert.equal(V.chooseStream(item, { maxHeight: 2160 }).url, item.url); // passt -> Master (VLC regelt selbst)
  assert.equal(V.chooseStream({ url: 'https://x.test/a.mp4', kind: 'video' }, {}).url, 'https://x.test/a.mp4');
});

test('chooseStream: Varianten mit getrennter Tonspur bleiben beim Master', () => {
  const text = `#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Deutsch",URI="audio/de.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=25000000,RESOLUTION=3840x2160,AUDIO="aac"
2160/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,AUDIO="aac"
1080/index.m3u8
`;
  const analysis = V.parseHls(text, 'https://cdn.test/s/master.m3u8');
  assert.equal(analysis.variants[0].separateAudio, true);
  const item = { url: 'https://cdn.test/s/master.m3u8', kind: 'hls', analysis };
  assert.equal(V.chooseStream(item, {}).url, item.url);
  assert.equal(V.chooseStream(item, {}).limited, false);
});
