// Beispielbilder aus dem Internet: iNaturalist (geprüfte Pflanzenfotos) und Wikimedia Commons.
// Beide Dienste sind frei nutzbar, brauchen keinen Schlüssel und erlauben den Abruf aus der App (CORS).
import { norm } from './util.js';

const INAT = 'https://api.inaturalist.org/v1';
const COMMONS = 'https://commons.wikimedia.org/w/api.php';
const FREE_LICENSES = 'cc0,cc-by,cc-by-sa,cc-by-nc,cc-by-nc-sa,cc-by-nd,cc-by-nc-nd';

export class WebImageError extends Error {}

async function getJson(url) {
  let res;
  try {
    res = await fetch(url, { credentials: 'omit' });
  } catch {
    throw new WebImageError('Keine Internetverbindung oder der Dienst ist nicht erreichbar.');
  }
  if (res.status === 429) throw new WebImageError('Der Dienst ist gerade überlastet. Bitte gleich noch einmal versuchen oder die andere Quelle wählen.');
  if (!res.ok) throw new WebImageError(`Der Dienst hat mit Fehler ${res.status} geantwortet.`);
  return res.json();
}

/** Bild herunterladen (als Blob). */
export async function downloadImage(url) {
  let res;
  try {
    res = await fetch(url, { credentials: 'omit' });
  } catch {
    throw new WebImageError('Bild konnte nicht geladen werden (keine Verbindung).');
  }
  if (!res.ok) throw new WebImageError(`Bild konnte nicht geladen werden (Fehler ${res.status}).`);
  return res.blob();
}

function licenseLabel(code) {
  if (!code) return '';
  if (code === 'cc0') return 'CC0';
  return code.toUpperCase().replace(/^CC-/, 'CC ').replace(/-/g, '-');
}

function stripHtml(html) {
  if (!html) return '';
  try {
    return (new DOMParser().parseFromString(String(html), 'text/html').body.textContent || '').replace(/\s+/g, ' ').trim();
  } catch {
    return String(html).replace(/<[^>]*>/g, '').trim();
  }
}

// ---------- iNaturalist ----------
const taxonCache = new Map();

/** Taxon zum Namen suchen; bevorzugt exakten Treffer des wissenschaftlichen Namens. */
export async function findTaxon(name) {
  const key = norm(name);
  if (taxonCache.has(key)) return taxonCache.get(key);
  const d = await getJson(`${INAT}/taxa?q=${encodeURIComponent(name)}&per_page=10&locale=de&is_active=true`);
  const results = d.results || [];
  const pick = results.find(r => norm(r.name) === key)
    || results.find(r => norm(r.matched_term) === key)
    || results.find(r => norm(r.preferred_common_name) === key)
    || results.find(r => ['species', 'subspecies', 'variety', 'hybrid'].includes(r.rank))
    || results[0];
  if (!pick) { taxonCache.set(key, null); return null; }
  // Details (kuratierte Fotos, Familie)
  const detail = (await getJson(`${INAT}/taxa/${pick.id}?locale=de`)).results?.[0] || pick;
  const family = (detail.ancestors || []).find(a => a.rank === 'family');
  const taxon = {
    id: pick.id,
    name: pick.name,
    rank: pick.rank,
    commonName: detail.preferred_common_name || pick.preferred_common_name || '',
    family: family ? family.name : '',
    familyCommon: family?.preferred_common_name || '',
    photos: (detail.taxon_photos || []).map(tp => tp.photo).filter(Boolean),
  };
  taxonCache.set(key, taxon);
  return taxon;
}

function inatItem(p) {
  const base = String(p.url || '');
  return {
    id: 'inat-' + p.id,
    thumb: p.small_url || base.replace('/square.', '/small.'),
    full: p.large_url || base.replace('/square.', '/large.'),
    author: (p.attribution || '').replace(/, uploaded by .*$/i, '').replace(/,? ?(some|all|no) rights reserved.*$/i, '').replace(/^\(c\)\s*/i, '© ').trim(),
    license: licenseLabel(p.license_code),
    source: 'iNaturalist',
    page: `https://www.inaturalist.org/photos/${p.id}`,
  };
}

/** Fotos von iNaturalist. page 1: kuratierte Taxon-Fotos + beste Beobachtungen, danach weitere Beobachtungen. */
export async function searchINaturalist(name, page = 1) {
  const taxon = await findTaxon(name);
  if (!taxon) return { taxon: null, items: [], hasMore: false };
  const photos = page === 1 ? taxon.photos.filter(p => p.license_code) : [];
  const obs = await getJson(`${INAT}/observations?taxon_id=${taxon.id}&photo_license=${FREE_LICENSES}&quality_grade=research&photos=true&order_by=votes&per_page=20&page=${page}`);
  for (const o of obs.results || []) photos.push(...(o.photos || []).filter(p => p.license_code).slice(0, 2));
  const seen = new Set();
  const items = photos.map(inatItem).filter(it => !seen.has(it.id) && seen.add(it.id));
  return { taxon, items, hasMore: (obs.total_results || 0) > page * 20 };
}

// ---------- Wikimedia Commons ----------
/** Bilder von Wikimedia Commons. offset: Fortsetzung der vorherigen Suche. */
export async function searchCommons(name, offset = 0) {
  const params = new URLSearchParams({
    action: 'query', format: 'json', origin: '*',
    generator: 'search', gsrnamespace: '6', gsrsearch: `"${name}" filetype:bitmap`, gsrlimit: '24', gsroffset: String(offset),
    prop: 'imageinfo', iiprop: 'url|size|mime|extmetadata', iiextmetadatafilter: 'Artist|LicenseShortName', iiurlwidth: '330',
  });
  const d = await getJson(`${COMMONS}?${params}`);
  const pages = Object.values(d.query?.pages || {}).sort((a, b) => (a.index || 0) - (b.index || 0));
  const items = pages.map(p => {
    const ii = p.imageinfo?.[0];
    if (!ii || !/jpeg|png|webp/.test(ii.mime || '') || !ii.thumburl) return null;
    const em = ii.extmetadata || {};
    const full = ii.width > 1280 && /\/\d+px-/.test(ii.thumburl) ? ii.thumburl.replace(/\/\d+px-/, '/1280px-') : ii.url;
    return {
      id: 'commons-' + p.pageid,
      thumb: ii.thumburl,
      full,
      author: stripHtml(em.Artist?.value),
      license: stripHtml(em.LicenseShortName?.value),
      source: 'Wikimedia Commons',
      page: ii.descriptionurl,
    };
  }).filter(Boolean);
  return { items, next: d.continue?.gsroffset ?? null };
}
