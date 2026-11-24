// Fotos verkleinern, als Blob speichern und als Object-URL anzeigen.
import { getPhoto } from './db.js';

const FULL_MAX = 1600;
const THUMB_MAX = 360;

async function decode(file) {
  if ('createImageBitmap' in window) {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch { /* Fallback unten */ }
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { resolve(img); setTimeout(() => URL.revokeObjectURL(url), 1000); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Bild konnte nicht gelesen werden.')); };
    img.src = url;
  });
}

function toBlob(canvas, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(b => (b ? resolve(b) : reject(new Error('Bild konnte nicht gespeichert werden.'))), 'image/jpeg', quality);
  });
}

function scaled(src, max, quality) {
  const w0 = src.width, h0 = src.height;
  const k = Math.min(1, max / Math.max(w0, h0));
  const w = Math.max(1, Math.round(w0 * k)), h = Math.max(1, Math.round(h0 * k));
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(src, 0, 0, w, h);
  return toBlob(c, quality).then(blob => ({ blob, w, h }));
}

/** Liest eine Bilddatei ein und erzeugt eine verkleinerte Version plus Vorschaubild. */
export async function processImage(file) {
  const src = await decode(file);
  const full = await scaled(src, FULL_MAX, 0.85);
  const thumb = await scaled(src, THUMB_MAX, 0.72);
  if (src.close) src.close();
  return { blob: full.blob, thumb: thumb.blob, w: full.w, h: full.h };
}

/** Erzeugt Foto-Daten direkt aus einem Canvas (für Beispielbilder). */
export async function processCanvas(canvas) {
  const full = await scaled(canvas, FULL_MAX, 0.86);
  const thumb = await scaled(canvas, THUMB_MAX, 0.72);
  return { blob: full.blob, thumb: thumb.blob, w: full.w, h: full.h };
}

const cache = new Map();

/** Object-URL für ein gespeichertes Foto (kind: 'full' | 'thumb'). */
export async function photoURL(id, kind = 'full') {
  const key = id + ':' + kind;
  if (cache.has(key)) return cache.get(key);
  const p = await getPhoto(id);
  if (!p) return null;
  const blob = kind === 'thumb' ? (p.thumb || p.blob) : p.blob;
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  cache.set(key, url);
  return url;
}

export function blobURL(blob) {
  return URL.createObjectURL(blob);
}

export function forgetPhoto(id) {
  for (const kind of ['full', 'thumb']) {
    const key = id + ':' + kind;
    if (cache.has(key)) {
      URL.revokeObjectURL(cache.get(key));
      cache.delete(key);
    }
  }
}

export function forgetAll() {
  for (const url of cache.values()) URL.revokeObjectURL(url);
  cache.clear();
}

/** Lädt Vorschaubilder erst, wenn sie sichtbar werden. */
const observer = 'IntersectionObserver' in window
  ? new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      observer.unobserve(e.target);
      loadInto(e.target);
    }
  }, { rootMargin: '200px' })
  : null;

async function loadInto(img) {
  const id = img.dataset.photo;
  const kind = img.dataset.kind || 'thumb';
  const url = await photoURL(id, kind);
  if (url) img.src = url;
  else img.classList.add('missing');
}

export function lazyPhoto(img, id, kind = 'thumb') {
  img.dataset.photo = id;
  img.dataset.kind = kind;
  if (observer) observer.observe(img);
  else loadInto(img);
  return img;
}
