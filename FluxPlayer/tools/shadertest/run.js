const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const dir = process.argv[2];
const SH = {};
for (const f of fs.readdirSync(dir)) {
  if (f.endsWith('.glsl')) SH[f.replace('.glsl', '')] = fs.readFileSync(path.join(dir, f), 'utf8');
  if (f.startsWith('ai_') && f.endsWith('.json')) { SH.ai = SH.ai || {}; SH.ai[f.slice(3, -5)] = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); }
}
const harness = fs.readFileSync(path.join(__dirname, 'harness.js'), 'utf8');
if (process.env.UPSCALE_OLD) SH.upscale_old = fs.readFileSync(process.env.UPSCALE_OLD, 'utf8');
// UPVARIANTS: optionale Shader-Varianten für Experimente (Umgebungsvariable UPVARIANT)
if (process.env.UPVARIANT === 'noalong' || process.env.UPVARIANT === 'both') SH.upscale = SH.upscale.replace('res = mix(res, 0.5 * (res + along), edge);', '');
if (process.env.UPVARIANT === 'sharpbil' || process.env.UPVARIANT === 'both') SH.upscale = SH.upscale.replace('res = clamp(res + (res - avg) * amount, lo - k, hi + k);', 'res = clamp(res + (res - tex(uv)) * amount * 1.5, lo - k, hi + k);');
if (process.env.UPVARIANT === 'iso') SH.upscale = SH.upscale.replace(/float edge = [^;]+;/, 'float edge = 0.0;');
if (process.env.UPVARIANT === 'noclamp') SH.upscale = SH.upscale.replace(/res = mix\(res, clamp\(res, mn4, mx4\), edge\);/, '');
const scenes = [
  { name: 'Schwenk 14/-6', kind: 'pan', vx: 14, vy: -6, natural: true },
  { name: 'Schneller Schwenk 47/12', kind: 'pan', vx: 47, vy: 12, natural: true, temporal: true },
  { name: 'Subpixel 3.5/1.25', kind: 'pan', vx: 3.5, vy: 1.25, natural: true },
  { name: 'Objekt 20/8', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140, natural: true },
  { name: 'Objekt 20/8', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140, natural: false },
  { name: 'Schwenk 14/-6 Ultra', kind: 'pan', vx: 14, vy: -6, natural: true, quality: 'ULTRA', slowmo: true },
  { name: 'Objekt 20/8 Ultra', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140, natural: true, quality: 'ULTRA', slowmo: true, debug: true },
  { name: 'Objekt 20/8 Ultra', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140, natural: false, quality: 'ULTRA' },
  { name: 'Objekt 20/8 Ausgew.', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140, natural: true, slowmo: true },
];
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  page.on('console', m => console.log('console:', m.text()));
  await page.setContent('<html><body></body></html>');
  await page.addScriptTag({ content: harness });
  const res = await page.evaluate(([SH, scenes]) => {
    try { return run(SH, scenes, 'BALANCED'); } catch (e) { return { error: String(e) }; }
  }, [SH, scenes]);
  console.log(JSON.stringify(res, null, 1));
  await browser.close();
})();
