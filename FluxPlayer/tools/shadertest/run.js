const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const dir = process.argv[2];
const SH = {};
for (const f of fs.readdirSync(dir)) SH[f.replace('.glsl', '')] = fs.readFileSync(path.join(dir, f), 'utf8');
const harness = fs.readFileSync(path.join(__dirname, 'harness.js'), 'utf8');
const scenes = [
  { name: 'Schwenk 14/-6', kind: 'pan', vx: 14, vy: -6 , natural: false },
  { name: 'Schwenk 14/-6', kind: 'pan', vx: 14, vy: -6 , natural: true },
  { name: 'Schneller Schwenk 47/12', kind: 'pan', vx: 47, vy: 12 , natural: false },
  { name: 'Schneller Schwenk 47/12', kind: 'pan', vx: 47, vy: 12 , natural: true },
  { name: 'Subpixel 3.5/1.25', kind: 'pan', vx: 3.5, vy: 1.25 , natural: false },
  { name: 'Subpixel 3.5/1.25', kind: 'pan', vx: 3.5, vy: 1.25 , natural: true },
  { name: 'Objekt 20/8', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140 , natural: false },
  { name: 'Objekt 20/8', kind: 'object', vx: 20, vy: 8, ox: 200, oy: 110, size: 140 , natural: true },
  { name: 'Schwenk 14/-6', kind: 'pan', vx: 14, vy: -6, natural: true, temporal: true },
  { name: 'Schneller Schwenk 47/12', kind: 'pan', vx: 47, vy: 12, natural: true, temporal: true },
  { name: 'Schwenk 14/-6 Leistung', kind: 'pan', vx: 14, vy: -6, natural: true, quality: 'PERFORMANCE' },
  { name: 'Schwenk 14/-6 Qualität', kind: 'pan', vx: 14, vy: -6, natural: true, quality: 'QUALITY' }
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
