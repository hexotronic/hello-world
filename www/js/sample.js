// Beispielstapel "Wiesenpflanzen" mit gezeichneten Beispielbildern (keine echten Fotos).
import { createDeck, emptyCard, saveCard, addPhoto } from './db.js';
import { processCanvas } from './images.js';

const PLANTS = [
  {
    deName: 'Gänseblümchen', latinName: 'Bellis perennis', family: 'Asteraceae (Korbblütler)', bloom: [2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
    features: 'Blattrosette am Boden mit spatelförmigen Blättern. Ein Blütenkorb pro blattlosem Stängel. Weiße Zungenblüten, oft rötlich überlaufen, gelbe Röhrenblüten in der Mitte.',
    habitat: 'Rasen, Wiesen, Wegränder', toxicity: 'none', uses: 'Blüten und junge Blätter essbar, z. B. im Salat.',
    confusion: 'Echte Kamille, Margerite (beide mit beblättertem Stängel und größer)', tags: ['wiese', 'essbar'],
    look: { kind: 'ray', n: 34, petal: '#fbfaf5', tip: '#e58aa6', center: '#f4c21c', c: 0.3, len: 1, w: 0.075, leaf: 'rosette', leafColor: '#4f8a3a', size: 0.5 },
  },
  {
    deName: 'Löwenzahn', latinName: 'Taraxacum officinale', family: 'Asteraceae (Korbblütler)', bloom: [3, 4, 5, 6],
    features: 'Grundblätter tief und rückwärts gezähnt („Löwenzahn“). Hohler, blattloser Stängel mit weißem Milchsaft. Nur gelbe Zungenblüten. Fruchtstand als „Pusteblume“.',
    habitat: 'Fettwiesen, Weiden, Gärten, Wegränder', toxicity: 'low', uses: 'Junge Blätter als Salat, Blüten für Sirup. Milchsaft in größeren Mengen reizend.',
    confusion: 'Ferkelkraut, Habichtskraut, Pippau', tags: ['wiese', 'essbar'],
    look: { kind: 'dandelion', petal: '#f7c600', tip: '#ffde3b', center: '#e9a400', leaf: 'toothed', leafColor: '#3f7f33', size: 0.62 },
  },
  {
    deName: 'Klatschmohn', latinName: 'Papaver rhoeas', family: 'Papaveraceae (Mohngewächse)', bloom: [5, 6, 7],
    features: 'Vier scharlachrote, zerknittert wirkende Kronblätter, am Grund oft mit schwarzem Fleck. Abstehend behaarter Stängel, weißer Milchsaft. Kugelige Kapselfrucht mit Narbenscheibe.',
    habitat: 'Äcker, Brachen, Wegränder, Böschungen', toxicity: 'low', uses: 'Samen ungiftig, Pflanze schwach giftig.',
    confusion: 'Saat-Mohn (Kapsel länglich, Haare anliegend)', tags: ['acker'],
    look: { kind: 'poppy', petal: '#e3261b', tip: '#b3140e', center: '#1d1d1b', leaf: 'pinnate', leafColor: '#5c8a3e', size: 0.66 },
  },
  {
    deName: 'Kornblume', latinName: 'Centaurea cyanus', family: 'Asteraceae (Korbblütler)', bloom: [6, 7, 8, 9],
    features: 'Leuchtend blaue, trichterförmige Randblüten mit gezähntem Saum, innen violette Röhrenblüten. Schmale, graufilzige Blätter. Hüllblätter mit gefranstem Rand.',
    habitat: 'Getreidefelder, Ackerränder, Blühstreifen', toxicity: 'none', uses: 'Blüten als essbare Deko und in Teemischungen.',
    confusion: 'Berg-Flockenblume (größer, mehrjährig, im Gebirge)', tags: ['acker', 'essbar'],
    look: { kind: 'cornflower', petal: '#2f62d6', tip: '#5d8bf0', center: '#5a2e8f', leaf: 'narrow', leafColor: '#7c9b7a', size: 0.5 },
  },
  {
    deName: 'Wiesen-Salbei', latinName: 'Salvia pratensis', family: 'Lamiaceae (Lippenblütler)', bloom: [5, 6, 7, 8],
    features: 'Blauviolette Lippenblüten mit sichelförmiger Oberlippe in Scheinquirlen. Vierkantiger Stängel. Runzelige, gekerbte Grundblätter. Würziger Duft.',
    habitat: 'Magerwiesen, Halbtrockenrasen, Böschungen (kalkliebend)', toxicity: 'none', uses: 'Bienenweide. Mildes Würzkraut.',
    confusion: 'Wiesen-Knautie (Kopfblüte), Günsel (kleiner)', tags: ['wiese', 'bienen'],
    look: { kind: 'spike', petal: '#5a49c7', tip: '#8576e8', center: '#3e8a3a', leaf: 'basal', leafColor: '#4c7a3a', size: 0.7 },
  },
  {
    deName: 'Echte Kamille', latinName: 'Matricaria chamomilla', family: 'Asteraceae (Korbblütler)', bloom: [5, 6, 7, 8, 9],
    features: 'Gelber Blütenboden kegelförmig gewölbt und innen HOHL (Längsschnitt!). Weiße Zungenblüten, später zurückgeschlagen. Fein zerschlitzte Blätter. Typischer Kamillenduft.',
    habitat: 'Äcker, Wegränder, Schuttplätze', toxicity: 'none', uses: 'Heilpflanze: Tee bei Magen-Darm-Beschwerden und Entzündungen.',
    confusion: 'Geruchlose Kamille (Boden markig gefüllt), Hundskamille (Spreublätter)', tags: ['heilpflanze', 'acker'],
    look: { kind: 'chamomile', n: 18, petal: '#ffffff', tip: '#f1f1ea', center: '#f2c01d', c: 0.34, len: 0.75, w: 0.09, leaf: 'feathery', leafColor: '#5a9440', size: 0.46 },
  },
  {
    deName: 'Margerite', latinName: 'Leucanthemum vulgare', family: 'Asteraceae (Korbblütler)', bloom: [5, 6, 7, 8, 9, 10],
    features: 'Große Blütenkörbe (3–6 cm) mit weißen Zungenblüten und gelber, flacher Mitte. Stängelblätter sitzend und gezähnt, Grundblätter gestielt.',
    habitat: 'Fettwiesen, Wegraine, Böschungen', toxicity: 'none', uses: 'Junge Blätter essbar, beliebte Schnittblume.',
    confusion: 'Gänseblümchen (kleiner, Rosette), Kamille (fein zerteilte Blätter)', tags: ['wiese'],
    look: { kind: 'ray', n: 22, petal: '#ffffff', tip: '#f6f6f0', center: '#f0b912', c: 0.27, len: 1.05, w: 0.1, leaf: 'toothedStem', leafColor: '#3f7a35', size: 0.6 },
  },
  {
    deName: 'Roter Fingerhut', latinName: 'Digitalis purpurea', family: 'Plantaginaceae (Wegerichgewächse)', bloom: [6, 7, 8],
    features: 'Purpurne, glockenförmige Blüten in einseitswendiger, langer Traube, innen dunkel gefleckt. Zweijährig: im ersten Jahr nur Blattrosette. Blätter unterseits filzig.',
    habitat: 'Waldlichtungen, Kahlschläge, Waldränder (saurer Boden)', toxicity: 'high', uses: 'Herzglykoside – nur als Fertigarzneimittel! Alle Teile sehr giftig.',
    confusion: 'Blattrosette ähnelt Beinwell und Königskerze – Verwechslung lebensgefährlich', tags: ['wald', 'giftig'],
    look: { kind: 'foxglove', petal: '#b23a8a', tip: '#e07cbf', center: '#5a1640', leaf: 'basal', leafColor: '#4a7438', size: 0.78 },
  },
  {
    deName: 'Gewöhnliche Schafgarbe', latinName: 'Achillea millefolium', family: 'Asteraceae (Korbblütler)', bloom: [6, 7, 8, 9, 10],
    features: 'Viele kleine weiße (selten rosa) Körbchen in flacher Schirmrispe. Blätter mehrfach fein fiederteilig („Tausendblatt“). Aromatischer Duft.',
    habitat: 'Wiesen, Weiden, Wegränder', toxicity: 'none', uses: 'Heilpflanze, z. B. als Tee bei Verdauungsbeschwerden.',
    confusion: 'Giftige Doldenblütler wie Schierling (echte Dolden, andere Blätter)!', tags: ['wiese', 'heilpflanze'],
    look: { kind: 'umbel', petal: '#fbfbf4', tip: '#efe8d0', center: '#e7d69a', leaf: 'feathery', leafColor: '#4f8a3c', size: 0.56 },
  },
  {
    deName: 'Scharbockskraut', latinName: 'Ficaria verna', family: 'Ranunculaceae (Hahnenfußgewächse)', bloom: [3, 4, 5],
    features: 'Glänzend gelbe Blüten mit 8–12 schmalen Kronblättern. Herzförmige, fettig glänzende Blätter. Bildet im Frühjahr dichte Teppiche, zieht im Sommer ein.',
    habitat: 'Feuchte Laubwälder, Auen, Hecken, Parks', toxicity: 'toxic', uses: 'Früher vor der Blüte als Vitamin-C-Quelle gegen Skorbut („Scharbock“). Ab Blüte giftig.',
    confusion: 'Junge Blätter mit Bärlauch-Beständen vermischt; Sumpfdotterblume (größer)', tags: ['wald', 'frühblüher', 'giftig'],
    look: { kind: 'celandine', n: 9, petal: '#f6c800', tip: '#ffe45c', center: '#d8a600', leaf: 'heart', leafColor: '#2f7a33', size: 0.46 },
  },
];

const BASIC = [
  { front: 'Was bedeutet der Artname „officinalis“ bzw. „officinale“?', back: 'Arznei- oder Heilpflanze.\nVon lat. officina = Werkstatt, Apotheke.', tags: ['botanik-latein'] },
  { front: 'Woran erkennt man Kreuzblütler (Brassicaceae)?', back: '4 Kelchblätter und 4 kreuzförmig gestellte Kronblätter, 6 Staubblätter (4 lange, 2 kurze), Früchte als Schoten oder Schötchen.', tags: ['familien'] },
  { front: 'Wie unterscheidet man Echte Kamille sicher von der Geruchlosen Kamille?', back: 'Blütenkopf längs aufschneiden: Echte Kamille hat einen hohlen Blütenboden, die Geruchlose einen markig gefüllten.', tags: ['verwechslung'] },
];

// ---------- Zeichnen ----------
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function background(ctx, W, H, r, hue) {
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, `hsl(${hue} 30% 80%)`);
  g.addColorStop(0.55, `hsl(${hue + 15} 32% 60%)`);
  g.addColorStop(1, `hsl(${hue + 25} 38% 30%)`);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = `hsla(${hue + r() * 60 - 30}, 40%, ${55 + r() * 30}%, ${0.06 + r() * 0.14})`;
    ctx.beginPath();
    ctx.arc(r() * W, r() * H, 20 + r() * 110, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.lineCap = 'round';
  for (let i = 0; i < 140; i++) {
    const x = r() * W, hh = H * (0.08 + r() * 0.3);
    ctx.strokeStyle = `hsla(${95 + r() * 30}, 45%, ${20 + r() * 25}%, ${0.35 + r() * 0.4})`;
    ctx.lineWidth = 2 + r() * 5;
    ctx.beginPath();
    ctx.moveTo(x, H + 5);
    ctx.quadraticCurveTo(x + (r() - 0.5) * 40, H - hh * 0.6, x + (r() - 0.5) * 80, H - hh);
    ctx.stroke();
  }
}

function petalGradient(ctx, x, y, R, s) {
  const g = ctx.createRadialGradient(x, y, R * 0.15, x, y, R * 1.05);
  g.addColorStop(0, s.petal);
  g.addColorStop(0.7, s.petal);
  g.addColorStop(1, s.tip || s.petal);
  return g;
}

function disk(ctx, x, y, r, color, dome = false) {
  const g = ctx.createRadialGradient(x - r * 0.3, y - r * (dome ? 0.5 : 0.3), r * 0.1, x, y, r);
  g.addColorStop(0, '#fff6c4');
  g.addColorStop(0.35, color);
  g.addColorStop(1, shade(color, -0.35));
  ctx.fillStyle = g;
  ctx.beginPath();
  if (dome) ctx.ellipse(x, y - r * 0.15, r, r * 1.05, 0, 0, Math.PI * 2);
  else ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = shade(color, -0.25);
  for (let i = 0; i < 60; i++) {
    const a = i * 2.399, d = Math.sqrt(i / 60) * r * 0.85;
    ctx.beginPath();
    ctx.arc(x + Math.cos(a) * d, y + Math.sin(a) * d - (dome ? r * 0.15 : 0), r * 0.045, 0, Math.PI * 2);
    ctx.fill();
  }
}

function shade(hex, amt) {
  const n = parseInt(hex.slice(1), 16);
  const f = c => Math.max(0, Math.min(255, Math.round(c + (amt < 0 ? c * amt : (255 - c) * amt))));
  const r = f(n >> 16), g = f((n >> 8) & 255), b = f(n & 255);
  return `rgb(${r},${g},${b})`;
}

function drawFlower(ctx, x, y, R, s, r) {
  ctx.save();
  ctx.shadowColor = 'rgba(0,0,0,0.25)';
  ctx.shadowBlur = R * 0.12;
  ctx.shadowOffsetY = R * 0.04;
  switch (s.kind) {
    case 'ray':
    case 'chamomile': {
      ctx.fillStyle = petalGradient(ctx, x, y, R, s);
      const yOff = s.kind === 'chamomile' ? R * 0.12 : 0;
      for (let i = 0; i < s.n; i++) {
        const a = (i / s.n) * Math.PI * 2 + r() * 0.08;
        const d = R * (0.32 + 0.3 * s.len);
        ctx.beginPath();
        ctx.ellipse(x + Math.cos(a) * d, y + Math.sin(a) * d + yOff, R * 0.36 * s.len, R * s.w * 2.4, a, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.shadowColor = 'transparent';
      disk(ctx, x, y, R * s.c, s.center, s.kind === 'chamomile');
      break;
    }
    case 'dandelion': {
      for (let ring = 0; ring < 4; ring++) {
        const rr = R * (1 - ring * 0.2);
        const n = 46 - ring * 7;
        ctx.fillStyle = ring % 2 ? s.tip : s.petal;
        for (let i = 0; i < n; i++) {
          const a = (i / n) * Math.PI * 2 + ring * 0.2 + r() * 0.05;
          ctx.beginPath();
          ctx.ellipse(x + Math.cos(a) * rr * 0.55, y + Math.sin(a) * rr * 0.55, rr * 0.45, R * 0.045, a, 0, Math.PI * 2);
          ctx.fill();
        }
      }
      ctx.shadowColor = 'transparent';
      ctx.fillStyle = s.center;
      ctx.beginPath(); ctx.arc(x, y, R * 0.12, 0, Math.PI * 2); ctx.fill();
      break;
    }
    case 'poppy': {
      for (let i = 0; i < 4; i++) {
        const a = (i / 4) * Math.PI * 2 + 0.4;
        const g = ctx.createRadialGradient(x, y, R * 0.1, x, y, R * 1.1);
        g.addColorStop(0, '#1a1a18'); g.addColorStop(0.2, '#1a1a18'); g.addColorStop(0.27, s.petal);
        g.addColorStop(0.85, s.petal); g.addColorStop(1, s.tip);
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.ellipse(x + Math.cos(a) * R * 0.45, y + Math.sin(a) * R * 0.45, R * 0.62, R * 0.55, a, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.shadowColor = 'transparent';
      ctx.strokeStyle = 'rgba(120,10,10,0.35)';
      ctx.lineWidth = R * 0.01;
      for (let i = 0; i < 40; i++) {
        const a = r() * Math.PI * 2;
        ctx.beginPath(); ctx.moveTo(x + Math.cos(a) * R * 0.35, y + Math.sin(a) * R * 0.35);
        ctx.lineTo(x + Math.cos(a) * R * (0.7 + r() * 0.3), y + Math.sin(a) * R * (0.7 + r() * 0.3)); ctx.stroke();
      }
      ctx.fillStyle = '#20201c';
      for (let i = 0; i < 70; i++) {
        const a = r() * Math.PI * 2, d = R * (0.16 + r() * 0.1);
        ctx.beginPath(); ctx.arc(x + Math.cos(a) * d, y + Math.sin(a) * d, R * 0.02, 0, Math.PI * 2); ctx.fill();
      }
      ctx.fillStyle = '#7d8f6a';
      ctx.beginPath(); ctx.arc(x, y, R * 0.13, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = '#3a3f2c'; ctx.lineWidth = R * 0.015;
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2;
        ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + Math.cos(a) * R * 0.12, y + Math.sin(a) * R * 0.12); ctx.stroke();
      }
      break;
    }
    case 'cornflower': {
      const n = 9;
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2;
        ctx.save();
        ctx.translate(x, y); ctx.rotate(a);
        const g = ctx.createLinearGradient(0, 0, R, 0);
        g.addColorStop(0, shade(s.petal, -0.3)); g.addColorStop(1, s.tip);
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.moveTo(R * 0.15, -R * 0.05);
        ctx.lineTo(R * 0.82, -R * 0.22);
        for (let k = 0; k < 5; k++) {
          const t = -0.22 + (k + 0.5) * 0.088;
          ctx.lineTo(R * (k % 2 ? 0.9 : 1.0), R * t);
        }
        ctx.lineTo(R * 0.82, R * 0.22);
        ctx.lineTo(R * 0.15, R * 0.05);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      }
      ctx.shadowColor = 'transparent';
      for (let i = 0; i < 26; i++) {
        const a = i * 2.399, d = Math.sqrt(i / 26) * R * 0.28;
        ctx.fillStyle = i % 3 ? s.center : '#2d2a6a';
        ctx.beginPath(); ctx.arc(x + Math.cos(a) * d, y + Math.sin(a) * d, R * 0.06, 0, Math.PI * 2); ctx.fill();
      }
      break;
    }
    case 'celandine': {
      ctx.fillStyle = petalGradient(ctx, x, y, R, s);
      for (let i = 0; i < s.n; i++) {
        const a = (i / s.n) * Math.PI * 2;
        ctx.beginPath();
        ctx.ellipse(x + Math.cos(a) * R * 0.55, y + Math.sin(a) * R * 0.55, R * 0.48, R * 0.15, a, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.shadowColor = 'transparent';
      ctx.fillStyle = 'rgba(255,255,255,0.45)';
      for (let i = 0; i < s.n; i++) {
        const a = (i / s.n) * Math.PI * 2 - 0.05;
        ctx.beginPath();
        ctx.ellipse(x + Math.cos(a) * R * 0.6, y + Math.sin(a) * R * 0.6, R * 0.22, R * 0.035, a, 0, Math.PI * 2);
        ctx.fill();
      }
      disk(ctx, x, y, R * 0.2, s.center);
      break;
    }
    case 'umbel': {
      ctx.shadowColor = 'transparent';
      ctx.strokeStyle = '#557a3d'; ctx.lineWidth = R * 0.02;
      for (let i = 0; i < 9; i++) {
        ctx.beginPath(); ctx.moveTo(x, y + R * 0.9);
        ctx.lineTo(x + (i - 4) * R * 0.2, y + R * 0.05); ctx.stroke();
      }
      for (let i = 0; i < 70; i++) {
        const fx = x + (r() - 0.5) * R * 1.9, fy = y + (r() - 0.5) * R * 0.75;
        const fr = R * (0.06 + r() * 0.03);
        ctx.fillStyle = s.petal;
        for (let k = 0; k < 5; k++) {
          const a = (k / 5) * Math.PI * 2;
          ctx.beginPath(); ctx.arc(fx + Math.cos(a) * fr * 0.7, fy + Math.sin(a) * fr * 0.7, fr * 0.55, 0, Math.PI * 2); ctx.fill();
        }
        ctx.fillStyle = s.center;
        ctx.beginPath(); ctx.arc(fx, fy, fr * 0.45, 0, Math.PI * 2); ctx.fill();
      }
      break;
    }
    case 'spike':
    case 'foxglove': {
      ctx.strokeStyle = s.center; ctx.lineWidth = R * 0.05;
      ctx.beginPath(); ctx.moveTo(x, y + R * 1.6); ctx.lineTo(x, y - R * 1.3); ctx.stroke();
      const n = s.kind === 'spike' ? 6 : 9;
      for (let i = 0; i < n; i++) {
        const t = i / (n - 1);
        const yy = y + R * 1.3 - t * R * 2.5;
        const sz = R * (s.kind === 'spike' ? 0.32 : 0.3) * (1 - t * 0.45);
        if (s.kind === 'spike') {
          for (const side of [-1, 1, -0.4, 0.4]) {
            ctx.save();
            ctx.translate(x + side * sz * 0.6, yy);
            ctx.scale(side < 0 ? -1 : 1, 1);
            ctx.fillStyle = i % 2 ? s.petal : s.tip;
            ctx.beginPath();
            ctx.moveTo(0, 0);
            ctx.quadraticCurveTo(sz * 0.8, -sz * 1.2, sz * 1.5, -sz * 0.4);
            ctx.quadraticCurveTo(sz * 0.9, -sz * 0.2, sz * 0.4, sz * 0.1);
            ctx.quadraticCurveTo(sz * 1.0, sz * 0.5, sz * 1.3, sz * 0.5);
            ctx.quadraticCurveTo(sz * 0.6, sz * 0.7, 0, 0);
            ctx.fill();
            ctx.restore();
          }
        } else {
          ctx.save();
          ctx.translate(x + R * 0.03, yy);
          ctx.rotate(1.05 - t * 0.3);
          const g = ctx.createLinearGradient(0, 0, sz * 2, 0);
          g.addColorStop(0, s.petal); g.addColorStop(1, s.tip);
          ctx.fillStyle = g;
          ctx.beginPath();
          ctx.moveTo(0, -sz * 0.2);
          ctx.quadraticCurveTo(sz, -sz * 0.55, sz * 2, -sz * 0.6);
          ctx.lineTo(sz * 2, sz * 0.6);
          ctx.quadraticCurveTo(sz, sz * 0.55, 0, sz * 0.2);
          ctx.closePath();
          ctx.fill();
          ctx.fillStyle = '#f6dbe9';
          ctx.beginPath(); ctx.ellipse(sz * 2, 0, sz * 0.22, sz * 0.58, 0, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = s.center;
          for (let k = 0; k < 6; k++) { ctx.beginPath(); ctx.arc(sz * 1.97, (k - 2.5) * sz * 0.17, sz * 0.05, 0, Math.PI * 2); ctx.fill(); }
          ctx.restore();
        }
      }
      break;
    }
  }
  ctx.restore();
}

function leaf(ctx, x, y, len, wid, ang, color, { toothed = false, heart = false } = {}) {
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(ang);
  const g = ctx.createLinearGradient(0, -wid, 0, wid);
  g.addColorStop(0, shade(color, 0.2)); g.addColorStop(1, shade(color, -0.25));
  ctx.fillStyle = g;
  ctx.beginPath();
  if (heart) {
    ctx.moveTo(0, 0);
    ctx.bezierCurveTo(len * 0.3, -wid * 1.6, len * 1.0, -wid * 0.9, len, 0);
    ctx.bezierCurveTo(len * 1.0, wid * 0.9, len * 0.3, wid * 1.6, 0, 0);
  } else if (toothed) {
    ctx.moveTo(0, 0);
    const teeth = 7;
    for (let i = 1; i <= teeth; i++) {
      const t = i / teeth;
      const w = Math.sin(t * Math.PI) * wid;
      ctx.lineTo(len * (t - 0.08), -w * 0.45);
      ctx.lineTo(len * t, -w * 1.1);
    }
    for (let i = teeth; i >= 1; i--) {
      const t = i / teeth;
      const w = Math.sin(t * Math.PI) * wid;
      ctx.lineTo(len * t, w * 1.1);
      ctx.lineTo(len * (t - 0.08), w * 0.45);
    }
  } else {
    ctx.moveTo(0, 0);
    ctx.quadraticCurveTo(len * 0.5, -wid * 1.4, len, 0);
    ctx.quadraticCurveTo(len * 0.5, wid * 1.4, 0, 0);
  }
  ctx.fill();
  ctx.strokeStyle = 'rgba(255,255,255,0.25)';
  ctx.lineWidth = Math.max(1, wid * 0.06);
  ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(len * 0.95, 0); ctx.stroke();
  ctx.restore();
}

function feathery(ctx, x, y, len, ang, color) {
  ctx.save();
  ctx.translate(x, y); ctx.rotate(ang);
  ctx.strokeStyle = color; ctx.lineCap = 'round';
  ctx.lineWidth = len * 0.025;
  ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(len, 0); ctx.stroke();
  ctx.lineWidth = len * 0.012;
  for (let i = 1; i < 16; i++) {
    const t = i / 16, l = len * 0.18 * Math.sin(t * Math.PI);
    for (const sgn of [-1, 1]) {
      ctx.beginPath(); ctx.moveTo(len * t, 0); ctx.lineTo(len * t + l * 0.4, sgn * l); ctx.stroke();
    }
  }
  ctx.restore();
}

function drawLeaves(ctx, s, baseX, baseY, stemTopY, H, r) {
  const c = s.leafColor;
  const L = H * 0.22;
  switch (s.leaf) {
    case 'rosette':
      for (let i = 0; i < 9; i++) leaf(ctx, baseX, baseY, L * (0.7 + r() * 0.3), L * 0.16, Math.PI + 0.2 + (i / 8) * (Math.PI - 0.4), c);
      break;
    case 'toothed':
      for (let i = 0; i < 7; i++) leaf(ctx, baseX, baseY, L * (1 + r() * 0.3), L * 0.16, Math.PI + 0.15 + (i / 6) * (Math.PI - 0.3), c, { toothed: true });
      break;
    case 'heart':
      for (let i = 0; i < 6; i++) leaf(ctx, baseX + (r() - 0.5) * L, baseY - r() * L * 0.3, L * 0.5, L * 0.28, Math.PI + 0.3 + (i / 5) * (Math.PI - 0.6), c, { heart: true });
      break;
    case 'basal':
      for (let i = 0; i < 6; i++) leaf(ctx, baseX, baseY, L * (0.9 + r() * 0.4), L * 0.24, Math.PI + 0.25 + (i / 5) * (Math.PI - 0.5), c);
      break;
    case 'feathery':
      for (let i = 0; i < 6; i++) {
        const yy = baseY - (i / 6) * (baseY - stemTopY) * 0.8;
        feathery(ctx, baseX, yy, L * (0.8 - i * 0.08), i % 2 ? -0.5 : Math.PI + 0.5, c);
      }
      break;
    case 'pinnate':
    case 'toothedStem':
    case 'narrow':
    default:
      for (let i = 0; i < 6; i++) {
        const yy = baseY - (i / 6) * (baseY - stemTopY) * 0.85;
        const w = s.leaf === 'narrow' ? 0.07 : 0.13;
        leaf(ctx, baseX, yy, L * (0.75 - i * 0.07), L * w, i % 2 ? -0.6 : Math.PI + 0.6, c, { toothed: s.leaf !== 'narrow' });
      }
  }
}

function render(s, view, seed) {
  const W = 1200, H = 900;
  const canvas = document.createElement('canvas');
  canvas.width = W; canvas.height = H;
  const ctx = canvas.getContext('2d');
  const r = rng(seed);
  background(ctx, W, H, r, 75 + (seed % 40));
  const tall = s.kind === 'spike' || s.kind === 'foxglove';
  if (view === 'close') {
    const R = H * (tall ? 0.26 : 0.36);
    if (!tall) {
      ctx.strokeStyle = shade(s.leafColor, -0.1); ctx.lineWidth = R * 0.08;
      ctx.beginPath(); ctx.moveTo(W * 0.5, H * 0.5); ctx.quadraticCurveTo(W * 0.53, H * 0.8, W * 0.5, H + 10); ctx.stroke();
    }
    drawFlower(ctx, W * 0.5, H * (tall ? 0.48 : 0.47), R, s, r);
  } else {
    const baseX = W * 0.5, baseY = H * 0.95;
    const heads = tall ? 1 : s.kind === 'umbel' ? 1 : 3;
    const R = H * (tall ? 0.15 : 0.12) * (s.size / 0.55);
    const tops = [];
    for (let i = 0; i < heads; i++) {
      const tx = baseX + (i - (heads - 1) / 2) * W * 0.22 + (r() - 0.5) * 40;
      const ty = H * (tall ? 0.42 : 0.24 + r() * 0.18);
      tops.push([tx, ty]);
      ctx.strokeStyle = shade(s.leafColor, -0.15); ctx.lineWidth = Math.max(5, R * 0.1);
      ctx.beginPath(); ctx.moveTo(baseX + (i - 1) * 8, baseY);
      ctx.quadraticCurveTo((baseX + tx) / 2 + (r() - 0.5) * 60, (baseY + ty) / 2, tx, ty + (tall ? R * 1.5 : 0)); ctx.stroke();
    }
    drawLeaves(ctx, s, baseX, baseY, Math.min(...tops.map(t => t[1])), H, r);
    for (const [tx, ty] of tops) drawFlower(ctx, tx, ty, R, s, r);
  }
  ctx.font = '600 26px system-ui, sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.8)';
  ctx.textAlign = 'right';
  ctx.fillText('Beispielbild', W - 24, H - 24);
  return canvas;
}

/** Legt den Beispielstapel an. */
export async function createSampleDeck() {
  const deck = await createDeck('Beispiel · Wiesenpflanzen', {
    description: 'Gezeichnete Beispielkarten zum Ausprobieren. Du kannst den Stapel jederzeit löschen.',
    sample: true,
  });
  let seed = 11;
  let t = Date.now() - PLANTS.length * 1000;
  for (const p of PLANTS) {
    const card = emptyCard(deck.id, 'plant');
    card.created = t++;
    Object.assign(card.plant, {
      deName: p.deName, latinName: p.latinName, family: p.family, bloom: p.bloom, features: p.features,
      habitat: p.habitat, toxicity: p.toxicity, uses: p.uses, confusion: p.confusion,
    });
    card.tags = ['beispiel', ...p.tags];
    for (const [view, caption] of [['close', 'Blüte'], ['habit', 'Ganze Pflanze']]) {
      const data = await processCanvas(render(p.look, view, seed++));
      const photo = await addPhoto({ ...data, caption }, card.id);
      card.photoIds.push(photo.id);
    }
    await saveCard(card);
  }
  for (const b of BASIC) {
    const card = emptyCard(deck.id, 'basic');
    card.created = t++;
    card.front = b.front;
    card.back = b.back;
    card.tags = ['beispiel', ...b.tags];
    await saveCard(card);
  }
  return deck;
}
