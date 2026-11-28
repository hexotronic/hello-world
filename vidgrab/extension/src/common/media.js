/*
 * VidGrab – gemeinsame Hilfsfunktionen.
 * Wird im Hintergrundskript, im Content-Script, in den UI-Seiten und in den
 * Node-Tests verwendet. Enthält nur reine Funktionen ohne Browser-APIs.
 */
(function (root) {
  'use strict';

  const EXT = {
    video: ['mp4', 'm4v', 'webm', 'mkv', 'mov', 'avi', 'flv', 'f4v', '3gp', '3g2', 'ogv', 'wmv', 'mpg', 'mpeg'],
    audio: ['mp3', 'm4a', 'aac', 'ogg', 'oga', 'opus', 'flac', 'wav', 'weba'],
    hls: ['m3u8'],
    dash: ['mpd'],
  };

  // Einzelne Stücke von HLS/DASH-Streams – für externe Player wertlos.
  const SEGMENT_EXT = ['ts', 'm4s', 'cmfv', 'cmfa', 'm4f', 'm4i'];
  const SEGMENT_MIMES = ['video/mp2t', 'video/iso.segment', 'audio/mp2t'];
  const HLS_MIMES = ['application/x-mpegurl', 'application/vnd.apple.mpegurl', 'audio/mpegurl', 'audio/x-mpegurl'];
  const DASH_MIMES = ['application/dash+xml'];
  // Server liefern Videos oft mit nichtssagenden Typen aus – dann zählt die Dateiendung.
  const GENERIC_MIMES = ['', 'application/octet-stream', 'binary/octet-stream', 'application/x-binary',
    'application/binary', 'application/force-download', 'application/download'];
  const MANIFEST_TEXT_MIMES = ['text/plain', 'application/xml', 'text/xml'];

  const PLAYERS = [
    { id: 'org.videolan.vlc', name: 'VLC' },
    { id: 'com.mxtech.videoplayer.ad', name: 'MX Player' },
    { id: 'com.mxtech.videoplayer.pro', name: 'MX Player Pro' },
    { id: 'is.xyz.mpv', name: 'mpv' },
    { id: 'com.brouken.player', name: 'Just Player' },
    { id: 'org.xbmc.kodi', name: 'Kodi' },
    { id: '', name: 'App-Auswahl (jedes Mal fragen)' },
  ];

  const DEFAULT_SETTINGS = {
    player: 'org.videolan.vlc',
    customPackage: '',
    launchMethod: 'tab', // 'tab' | 'popup' | 'page'
    showAudio: false,
    showVariants: false,
    minSizeMB: 1,
    analyzeManifests: true,
    showOverlay: true,
    maxHeight: 1080, // 0 = unbegrenzt
  };

  const QUALITY_LIMITS = [
    { value: 0, name: 'Unbegrenzt (höchste verfügbare)' },
    { value: 2160, name: '2160p (4K)' },
    { value: 1440, name: '1440p' },
    { value: 1080, name: '1080p (empfohlen)' },
    { value: 720, name: '720p' },
    { value: 480, name: '480p' },
  ];

  function baseMime(mime) {
    return String(mime || '').split(';')[0].trim().toLowerCase();
  }

  function getExtension(pathname) {
    const last = String(pathname || '').split('/').pop() || '';
    const dot = last.lastIndexOf('.');
    if (dot < 0) return '';
    const ext = last.slice(dot + 1).toLowerCase();
    return /^[a-z0-9]{1,5}$/.test(ext) ? ext : '';
  }

  function kindFromExt(ext) {
    for (const kind of Object.keys(EXT)) {
      if (EXT[kind].includes(ext)) return kind;
    }
    return null;
  }

  function kindFromMime(mime) {
    const m = baseMime(mime);
    if (!m) return null;
    if (HLS_MIMES.includes(m)) return 'hls';
    if (DASH_MIMES.includes(m)) return 'dash';
    if (SEGMENT_MIMES.includes(m)) return 'segment';
    if (m.startsWith('video/')) return 'video';
    if (m.startsWith('audio/')) return 'audio';
    return null;
  }

  function parseUrl(url) {
    try {
      const u = new URL(url);
      return /^https?:$/.test(u.protocol) ? u : null;
    } catch (e) {
      return null;
    }
  }

  function normalizeUrl(url) {
    const u = parseUrl(url);
    if (!u) return null;
    u.hash = '';
    return u.href;
  }

  function isSegmentUrl(url) {
    const u = typeof url === 'string' ? parseUrl(url) : url;
    if (!u) return false;
    const path = u.pathname.toLowerCase();
    const file = path.split('/').pop() || '';
    if (SEGMENT_EXT.includes(getExtension(path))) return true;
    // Init-Segmente fragmentierter MP4-Streams
    if (/^init[^/]*\.(mp4|m4a|m4v|cmf[av])$/.test(file)) return true;
    // seg-12.mp4, segment_3.mp4, frag5.mp4, chunk-0001.webm …
    if (/(?:^|[_.-])(?:seg|segment|frag|fragment|chunk)[-_]?\d+/.test(file)) return true;
    // Byte-Range in der URL (z. B. ?range=0-65535 oder /range/0-65535)
    if (/(?:^|[?&])(?:range|bytes?range)=\d+-\d+/i.test(u.search)) return true;
    // Teilstücke per Byte-Parameter (z. B. ?bytestart=0&byteend=99999)
    if (/(?:^|[?&])(?:bytestart|byteend|byte_start|byte_end)=\d+/i.test(u.search)) return true;
    if (/\/range\/\d+-\d+/.test(path)) return true;
    return false;
  }

  /**
   * Ordnet eine Netzwerk-Antwort ein.
   * @returns {{kind: string, ext: string, mime: string} | null}
   */
  function classify(url, mime) {
    const u = parseUrl(url);
    if (!u) return null;
    const m = baseMime(mime);
    const ext = getExtension(u.pathname);
    const mimeKind = kindFromMime(m);
    if (mimeKind === 'segment' || isSegmentUrl(u)) return null;
    const extKind = kindFromExt(ext);

    let kind = mimeKind;
    if (!kind && extKind) {
      const manifest = extKind === 'hls' || extKind === 'dash';
      if (GENERIC_MIMES.includes(m) || (manifest && MANIFEST_TEXT_MIMES.includes(m))) kind = extKind;
    }
    if (!kind) return null;
    return { kind, ext, mime: m };
  }

  /**
   * Ordnet eine URL ein, die im Seiten-DOM gefunden wurde (ohne Header).
   * @param {string} tag 'video' | 'audio' | 'a' | 'meta'
   */
  function classifyDom(url, tag) {
    const u = parseUrl(url);
    if (!u || isSegmentUrl(u)) return null;
    const c = classify(u.href, '');
    if (c) {
      if (tag === 'audio' && c.kind === 'video') c.kind = 'audio';
      return c;
    }
    // <video src="https://cdn/stream?id=1"> – Typ ist durch das Element klar
    if (tag === 'video' || tag === 'audio') return { kind: tag, ext: getExtension(u.pathname), mime: '' };
    return null;
  }

  /** Ermittelt die Gesamtgröße aus Content-Range bzw. Content-Length. */
  function parseSize(contentRange, contentLength, statusCode) {
    const total = /\/\s*(\d+)\s*$/.exec(String(contentRange || ''));
    if (total) return Number(total[1]);
    if (statusCode === 206) return null;
    const len = Number(contentLength);
    return Number.isFinite(len) && len > 0 ? len : null;
  }

  function resolveUrl(ref, base) {
    try {
      return new URL(ref, base).href;
    } catch (e) {
      return null;
    }
  }

  function parseAttributes(str) {
    const attrs = {};
    const re = /([A-Z0-9-]+)=("[^"]*"|[^,]*)/g;
    let m;
    while ((m = re.exec(str))) {
      let v = m[2];
      if (v.startsWith('"')) v = v.slice(1, -1);
      attrs[m[1]] = v;
    }
    return attrs;
  }

  function formatBitrate(bps) {
    if (!bps) return '';
    if (bps >= 1e6) return (bps / 1e6).toFixed(1).replace('.', ',') + ' Mbit/s';
    return Math.round(bps / 1000) + ' kbit/s';
  }

  /** Analysiert eine HLS-Playlist (m3u8). */
  function parseHls(text, baseUrl) {
    if (!/^﻿?\s*#EXTM3U/.test(String(text || ''))) return null;
    const res = {
      type: 'hls', isMaster: false, variants: [], renditions: [],
      drm: false, encrypted: false, live: false, duration: 0,
    };
    let pendingVariant = null;
    let segmentSeconds = 0;
    let hasSegments = false;
    let ended = false;

    for (const raw of String(text).split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) continue;
      if (line.startsWith('#EXT-X-STREAM-INF:')) {
        pendingVariant = parseAttributes(line.slice(18));
        res.isMaster = true;
      } else if (line.startsWith('#EXT-X-MEDIA:') || line.startsWith('#EXT-X-I-FRAME-STREAM-INF:')) {
        const a = parseAttributes(line.slice(line.indexOf(':') + 1));
        if (a.URI) {
          const url = resolveUrl(a.URI, baseUrl);
          if (url) {
            res.renditions.push({
              type: a.TYPE || 'IFRAME', groupId: a['GROUP-ID'] || '', name: a.NAME || '', language: a.LANGUAGE || '', url,
            });
          }
        }
      } else if (line.startsWith('#EXT-X-KEY:') || line.startsWith('#EXT-X-SESSION-KEY:')) {
        const a = parseAttributes(line.slice(line.indexOf(':') + 1));
        const method = String(a.METHOD || '').toUpperCase();
        const format = String(a.KEYFORMAT || 'identity').toLowerCase();
        if (method && method !== 'NONE') {
          if (format !== 'identity' || method === 'SAMPLE-AES-CTR') res.drm = true;
          else res.encrypted = true; // AES-128 mit offenem Schlüssel – VLC kann das
        }
      } else if (line.startsWith('#EXTINF:')) {
        segmentSeconds += parseFloat(line.slice(8)) || 0;
        hasSegments = true;
      } else if (line.startsWith('#EXT-X-ENDLIST')) {
        ended = true;
      } else if (line.startsWith('#EXT-X-PLAYLIST-TYPE:')) {
        if (/VOD/i.test(line)) ended = true;
      } else if (!line.startsWith('#') && pendingVariant) {
        const url = resolveUrl(line, baseUrl);
        if (url) {
          const resolution = pendingVariant.RESOLUTION || '';
          const height = Number((/x(\d+)$/i.exec(resolution) || [])[1]) || 0;
          const bandwidth = Number(pendingVariant['AVERAGE-BANDWIDTH'] || pendingVariant.BANDWIDTH) || 0;
          res.variants.push({
            url, resolution, height, bandwidth,
            codecs: pendingVariant.CODECS || '',
            audioGroup: pendingVariant.AUDIO || '',
            label: height ? height + 'p' : (formatBitrate(bandwidth) || 'Variante'),
          });
        }
        pendingVariant = null;
      }
    }

    res.variants.sort((a, b) => (b.height - a.height) || (b.bandwidth - a.bandwidth));
    // Gleiche Auflösung mehrfach? Dann Bitrate dazuschreiben.
    // Ton als getrennte Spur? Dann hat die Varianten-Playlist allein keinen Ton.
    for (const v of res.variants) {
      v.separateAudio = !!v.audioGroup && res.renditions.some((r) => r.type === 'AUDIO' && r.groupId === v.audioGroup);
    }
    const heights = {};
    for (const v of res.variants) heights[v.label] = (heights[v.label] || 0) + 1;
    for (const v of res.variants) {
      if (heights[v.label] > 1 && v.height && v.bandwidth) v.label += ' · ' + formatBitrate(v.bandwidth);
    }
    if (!res.isMaster && hasSegments) {
      res.duration = Math.round(segmentSeconds);
      res.live = !ended;
    }
    return res;
  }

  function parseIsoDuration(str) {
    const m = /^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/.exec(String(str || '').trim());
    if (!m) return 0;
    const [d, h, min, s] = m.slice(1).map((x) => parseFloat(x) || 0);
    return Math.round(d * 86400 + h * 3600 + min * 60 + s);
  }

  /** Analysiert ein DASH-Manifest (mpd). */
  function parseDash(text) {
    const t = String(text || '');
    if (!/<(?:\w+:)?MPD[\s>]/.test(t)) return null;
    const heights = new Set();
    const re = /<(?:\w+:)?(?:Representation|AdaptationSet)\b[^>]*?\sheight="(\d+)"/g;
    let m;
    while ((m = re.exec(t))) heights.add(Number(m[1]));
    const dur = /mediaPresentationDuration="([^"]+)"/.exec(t);
    return {
      type: 'dash',
      drm: /<(?:\w+:)?ContentProtection\b/.test(t),
      live: /\stype="dynamic"/.test(t),
      duration: dur ? parseIsoDuration(dur[1]) : 0,
      heights: [...heights].sort((a, b) => b - a),
    };
  }

  /**
   * Baut einen Android-Intent-Link, den Firefox für Android an die App weiterreicht.
   * Format: intent://host/pfad#Intent;scheme=https;type=video/*;package=…;end
   */
  function buildIntentUrl(mediaUrl, opts) {
    const o = opts || {};
    const u = parseUrl(mediaUrl);
    if (!u) return null;
    u.hash = '';
    const scheme = u.protocol.slice(0, -1);
    const rest = u.href.slice(u.protocol.length + 2);
    let s = 'intent://' + rest + '#Intent;scheme=' + scheme + ';action=android.intent.action.VIEW;';
    if (o.mime) s += 'type=' + o.mime + ';';
    if (o.pkg) s += 'package=' + o.pkg + ';';
    if (o.title) s += 'S.title=' + encodeURIComponent(String(o.title).slice(0, 200)) + ';';
    return s + 'end';
  }

  function intentMime(kind) {
    return kind === 'audio' ? 'audio/*' : 'video/*';
  }

  /** M3U-Playlist mit Referer/User-Agent für VLC (hilft bei geschützten Streams). */
  function buildM3u(opts) {
    const o = opts || {};
    const title = String(o.title || 'Video').replace(/[\r\n]+/g, ' ');
    const lines = ['#EXTM3U', '#EXTINF:-1,' + title];
    if (o.referer && parseUrl(o.referer)) lines.push('#EXTVLCOPT:http-referrer=' + o.referer);
    if (o.userAgent) lines.push('#EXTVLCOPT:http-user-agent=' + String(o.userAgent).replace(/[\r\n]+/g, ' '));
    lines.push(o.url);
    return lines.join('\n') + '\n';
  }

  function safeFilename(name) {
    const s = String(name || '')
      .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 80)
      .replace(/^\.+/, '');
    return s || 'video';
  }

  function filenameFromUrl(url) {
    const u = parseUrl(url);
    if (!u) return '';
    const parts = u.pathname.split('/').filter(Boolean);
    let name = parts.length ? parts[parts.length - 1] : '';
    try {
      name = decodeURIComponent(name);
    } catch (e) { /* Rohform behalten */ }
    return name || u.hostname;
  }

  function formatBytes(n) {
    if (!n || n < 0) return '';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let i = 0;
    let v = n;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i++;
    }
    const num = i === 0 ? String(v) : v.toFixed(v < 10 ? 1 : 0).replace('.', ',');
    return num + ' ' + units[i];
  }

  function formatDuration(sec) {
    if (!sec || sec < 0) return '';
    const s = Math.round(sec);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const ss = String(s % 60).padStart(2, '0');
    return h ? h + ':' + String(m).padStart(2, '0') + ':' + ss : m + ':' + ss;
  }

  function typeLabel(item) {
    if (item.kind === 'hls') return 'HLS';
    if (item.kind === 'dash') return 'DASH';
    if (item.ext) return item.ext.toUpperCase();
    return item.kind === 'audio' ? 'AUDIO' : 'VIDEO';
  }

  function maxQuality(item) {
    const a = item.analysis;
    if (!a) return 0;
    if (a.variants && a.variants.length) return a.variants[0].height || 0;
    if (a.heights && a.heights.length) return a.heights[0];
    return 0;
  }

  /** Grund, warum ein Eintrag (mit den aktuellen Einstellungen) ausgeblendet wird – oder null. */
  function hiddenReason(item, settings) {
    const s = Object.assign({}, DEFAULT_SETTINGS, settings);
    if (item.kind === 'audio' && !s.showAudio) return 'audio';
    if (item.isVariant && !s.showVariants) return 'variant';
    const min = Number(s.minSizeMB) || 0;
    if ((item.kind === 'video' || item.kind === 'audio') && item.size && min > 0 && item.size < min * 1048576) return 'small';
    return null;
  }

  function sortRank(item) {
    const a = item.analysis || {};
    if (item.kind === 'hls') return a.isMaster ? 0 : 2;
    if (item.kind === 'dash') return 1;
    if (item.kind === 'video') return item.size ? 3 : 4;
    return 5;
  }

  function sortItems(items) {
    return items.slice().sort((a, b) =>
      (sortRank(a) - sortRank(b)) || ((b.size || 0) - (a.size || 0)) || ((a.firstSeen || 0) - (b.firstSeen || 0)));
  }

  /**
   * Welche Adresse soll der Player bekommen? Bei HLS-Master-Playlists mit Qualitäten über dem
   * eingestellten Maximum wird direkt die passende Variante gewählt (sonst nimmt VLC z. B. 4K).
   * Varianten mit getrennter Tonspur bleiben unberührt – allein hätten sie keinen Ton.
   * @returns {{url: string, label: string, limited: boolean}}
   */
  function chooseStream(item, settings) {
    const s = Object.assign({}, DEFAULT_SETTINGS, settings);
    const max = Number(s.maxHeight) || 0;
    const a = item.analysis;
    const plain = { url: item.url, label: '', limited: false };
    if (!max || item.kind !== 'hls' || !a || !a.isMaster || !a.variants || !a.variants.length) return plain;
    const top = Math.max(...a.variants.map((v) => v.height || 0));
    if (top <= max) return plain;
    const usable = a.variants.filter((v) => v.height && !v.separateAudio);
    if (!usable.length) return plain;
    const pick = usable.find((v) => v.height <= max) || usable[usable.length - 1];
    return { url: pick.url, label: pick.height + 'p', limited: true };
  }

  /** Bester Kandidat zum direkten Abspielen (für den Knopf am Video). */
  function pickBest(items, settings) {
    const candidates = sortItems(items.filter((i) => i.kind !== 'audio' && !hiddenReason(i, settings)));
    return candidates.find((i) => !(i.analysis && i.analysis.drm)) || candidates[0] || null;
  }

  function playerName(pkg) {
    const p = PLAYERS.find((x) => x.id === pkg);
    if (p) return p.id ? p.name : 'App';
    return pkg ? pkg.split('.').pop() : 'App';
  }

  function effectivePlayer(settings) {
    const s = Object.assign({}, DEFAULT_SETTINGS, settings);
    return s.player === 'custom' ? String(s.customPackage || '').trim() : s.player;
  }

  const api = {
    EXT, PLAYERS, DEFAULT_SETTINGS, QUALITY_LIMITS, chooseStream,
    baseMime, getExtension, normalizeUrl, isSegmentUrl, classify, classifyDom, parseSize,
    parseHls, parseDash, parseIsoDuration, buildIntentUrl, intentMime, buildM3u,
    safeFilename, filenameFromUrl, formatBytes, formatDuration, formatBitrate, typeLabel,
    maxQuality, hiddenReason, sortItems, pickBest, playerName, effectivePlayer,
  };

  root.VidGrab = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
