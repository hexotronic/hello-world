// Misst, wie gut gesprochene (oft verstümmelt erkannte) Pflanzennamen zugeordnet werden.
// Aufruf: npm run test:speech
import { matchSpoken } from '../www/js/util.js';
import { PLANTS, HEARD, WRONG, HARD } from './speech-corpus.mjs';

const find = n => PLANTS.find(p => p.includes(n));
const othersOf = p => PLANTS.filter(q => q !== p).flat();
let failed = false;

function run(set, label, minPercent) {
  let ok = 0;
  const miss = [];
  for (const [heard, target] of set) {
    const p = find(target);
    const r = matchSpoken([heard], [p[0], p[1]], othersOf(p));
    if (r.result !== 'wrong') ok++; else miss.push(heard);
  }
  const pct = Math.round((ok / set.length) * 100);
  console.log(`${label}: ${ok}/${set.length} zugeordnet (${pct} %)${miss.length ? ' – nicht: ' + miss.join(' | ') : ''}`);
  if (pct < minPercent) failed = true;
}

run(HEARD, 'Typische Erkennungen', 97);
run(HARD, 'Stark verstümmelt   ', 90);

let fp = 0, total = 0;
for (const [heard, target] of WRONG) { total++; const p = find(target); if (matchSpoken([heard], [p[0], p[1]], othersOf(p)).result !== 'wrong') fp++; }
for (const p of PLANTS) for (const q of PLANTS) if (p !== q) for (const said of q) { total++; if (matchSpoken([said], [p[0], p[1]], othersOf(p)).result !== 'wrong') fp++; }
console.log(`Andere Pflanze gesagt, trotzdem akzeptiert: ${fp}/${total}`);
if (fp > 0) failed = true;
process.exit(failed ? 1 : 0);
