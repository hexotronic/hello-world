// Läuft im Browser. SHADERS wird vom Node-Skript injiziert.
function run(SHADERS, scenes, quality) {
  const out = { compile: {}, scenes: [] };
  const canvas = document.createElement('canvas');
  canvas.width = 16; canvas.height = 16;
  const gl = canvas.getContext('webgl2', { antialias: false });
  if (!gl) return { error: 'kein WebGL2' };
  out.renderer = gl.getParameter(gl.RENDERER);

  function compile(type, src, name) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(name + ': ' + gl.getShaderInfoLog(s));
    return s;
  }
  function program(vsSrc, fsSrc, name) {
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl.VERTEX_SHADER, vsSrc, name + '.vs'));
    gl.attachShader(p, compile(gl.FRAGMENT_SHADER, fsSrc, name + '.fs'));
    gl.bindAttribLocation(p, 0, 'aPos');
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(name + ' link: ' + gl.getProgramInfoLog(p));
    out.compile[name] = 'ok';
    return p;
  }
  const progs = {};
  for (const q of ['PERFORMANCE', 'BALANCED', 'QUALITY', 'ULTRA']) {
    progs['coarse_' + q] = program(SHADERS.vs_es3, SHADERS['coarse_' + q], 'coarse_' + q);
    progs['refine_' + q] = program(SHADERS.vs_es3, SHADERS['refine_' + q], 'refine_' + q);
  }
  progs.median = program(SHADERS.vs_es3, SHADERS.median, 'median');
  progs.display = program(SHADERS.vs_es3, SHADERS.display, 'display');
  progs.upscale = program(SHADERS.vs_es3, SHADERS.upscale, 'upscale');
  progs.boundary = program(SHADERS.vs_es3, SHADERS.boundary, 'boundary');

  const vbo = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  function draw() {
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  function tex(w, h, levels, mip, data) {
    const t = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texStorage2D(gl.TEXTURE_2D, levels, gl.RGBA8, w, h);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, mip ? gl.LINEAR_MIPMAP_LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, mip ? gl.LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    if (data) {
      gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, data);
      if (mip) gl.generateMipmap(gl.TEXTURE_2D);
    }
    return t;
  }
  function fbo(t) {
    const f = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, f);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
    if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('fbo');
    return f;
  }
  function bind(unit, t) { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); }
  function u(p, n) { return gl.getUniformLocation(p, n); }
  function read(f, w, h) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, f);
    const px = new Uint8Array(w * h * 4);
    gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);
    return px;
  }
  const decode = (hi, lo) => ((hi / 255 + (lo / 255) / 255) - 0.5);

  // ---- prozedurales Muster (stetig, damit Ground-Truth bei Subpixel-Verschiebung exakt ist)
  function makeNoise(seed, gw, gh) {
    let s = seed;
    const rnd = () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
    const g = new Float32Array(gw * gh * 3);
    for (let i = 0; i < g.length; i++) g[i] = rnd();
    return { g, gw, gh };
  }
  function sampleNoise(n, x, y, cell) {
    const fx = x / cell, fy = y / cell;
    let x0 = Math.floor(fx), y0 = Math.floor(fy);
    const tx = fx - x0, ty = fy - y0;
    const sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
    const at = (xx, yy, c) => {
      xx = ((xx % n.gw) + n.gw) % n.gw; yy = ((yy % n.gh) + n.gh) % n.gh;
      return n.g[(yy * n.gw + xx) * 3 + c];
    };
    const r = [0, 0, 0];
    for (let c = 0; c < 3; c++) {
      const a = at(x0, y0, c) * (1 - sx) + at(x0 + 1, y0, c) * sx;
      const b = at(x0, y0 + 1, c) * (1 - sx) + at(x0 + 1, y0 + 1, c) * sx;
      r[c] = a * (1 - sy) + b * sy;
    }
    return r;
  }
  let NATURAL = false;
  function pattern(n1, n2, x, y) {
    if (NATURAL) {
      const r = [0, 0, 0]; let wsum = 0;
      const cells = [160, 80, 40, 20, 10, 5];
      cells.forEach((cell, k) => {
        const w = Math.pow(0.6, k);
        const v = sampleNoise(k % 2 ? n2 : n1, x + k * 37, y - k * 53, cell);
        r[0] += w * v[0]; r[1] += w * v[1]; r[2] += w * v[2]; wsum += w;
      });
      return [r[0] / wsum, r[1] / wsum, r[2] / wsum].map(v => Math.min(1, Math.max(0, (v - 0.5) * 2.2 + 0.5)));
    }
    const a = sampleNoise(n1, x, y, 23);
    const b = sampleNoise(n2, x, y, 7);
    return [0.65 * a[0] + 0.35 * b[0], 0.65 * a[1] + 0.35 * b[1], 0.65 * a[2] + 0.35 * b[2]];
  }

  const W = 640, H = 360;
  const nb1 = makeNoise(11, 64, 64), nb2 = makeNoise(29, 128, 128);
  const no1 = makeNoise(77, 64, 64), no2 = makeNoise(91, 128, 128);

  function render(scene, t) {
    const img = new Uint8Array(W * H * 4);
    const vx = scene.vx * t, vy = scene.vy * t;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      let c;
      if (scene.kind === 'pan') {
        c = pattern(nb1, nb2, x - vx + 1000, y - vy + 1000);
      } else {
        const ox = scene.ox + vx, oy = scene.oy + vy;
        if (x >= ox && x < ox + scene.size && y >= oy && y < oy + scene.size) {
          c = pattern(no1, no2, x - ox, y - oy);
          c = [c[0] * 0.5 + 0.5, c[1] * 0.4, c[2] * 0.3];
        } else c = pattern(nb1, nb2, x + 1000, y + 1000);
      }
      const i = (y * W + x) * 4;
      img[i] = Math.round(c[0] * 255); img[i + 1] = Math.round(c[1] * 255); img[i + 2] = Math.round(c[2] * 255); img[i + 3] = 255;
    }
    return img;
  }
  function psnr(a, b, mask) {
    let se = 0, n = 0;
    for (let y = 8; y < H - 8; y++) for (let x = 8; x < W - 8; x++) {
      if (mask && !mask(x, y)) continue;
      const i = (y * W + x) * 4;
      for (let c = 0; c < 3; c++) { const d = a[i + c] - b[i + c]; se += d * d; n++; }
    }
    const mse = se / n;
    return mse === 0 ? 99 : 10 * Math.log10(255 * 255 / mse);
  }

  let levels = 1, s = Math.max(W, H); while (s > 1) { s >>= 1; levels++; }
  const Q = { PERFORMANCE: [4, 6, false], BALANCED: [3, 5, false], QUALITY: [2, 4, false], ULTRA: [1, 5, true] };

  for (const scene of scenes) {
    NATURAL = !!scene.natural;
    const q = scene.quality || quality;
    let cl = Q[q][1];
    while (cl > 1 && (Math.min(W, H) >> cl) < 4) cl--;
    const fl = Math.max(0, Math.min(Q[q][0], cl - 1));
    const bidi = Q[q][2] && scene.dual !== false;
    const fw = W >> fl, fh = H >> fl, cw = W >> cl, ch = H >> cl;
    const A = render(scene, 0), B = render(scene, 1), GT = render(scene, 0.5);
    const tA = tex(W, H, levels, true, A), tB = tex(W, H, levels, true, B);
    const tCoarse = tex(cw, ch, 1, false), fCoarse = fbo(tCoarse);
    const tFine = tex(fw, fh, 1, false), fFine = fbo(tFine);
    const tFlow = tex(fw, fh, 1, false), fFlow = fbo(tFlow);
    const tOut = tex(W, H, 1, false), fOut = fbo(tOut);

    const t0 = performance.now();
    const lvl = {};
    for (let lod = cl - 1; lod > fl; lod--) { const t = tex(W >> lod, H >> lod, 1, false); lvl[lod] = { t, f: fbo(t) }; }
    const tBwd = tex(fw, fh, 1, false), fBwd = fbo(tBwd);
    const tFine2 = tex(fw, fh, 1, false), fFine2 = fbo(tFine2);
    function estimate(searchIn, grid, temporalTex, outFbo) {
      let p = progs['coarse_' + q];
      gl.bindFramebuffer(gl.FRAMEBUFFER, fCoarse); gl.viewport(0, 0, cw, ch); gl.useProgram(p);
      bind(0, searchIn); bind(1, grid);
      gl.uniform1i(u(p, 'uPrev'), 0); gl.uniform1i(u(p, 'uCurr'), 1); gl.uniform1i(u(p, 'uLod'), cl);
      draw();
      p = progs['refine_' + q];
      gl.useProgram(p);
      let pred = tCoarse;
      for (let lod = cl - 1; lod >= fl; lod--) {
        const out = lod === fl ? { t: tFine, f: fFine } : lvl[lod];
        gl.bindFramebuffer(gl.FRAMEBUFFER, out.f); gl.viewport(0, 0, W >> lod, H >> lod);
        bind(0, searchIn); bind(1, grid); bind(2, pred); bind(3, temporalTex || pred);
        gl.uniform1i(u(p, 'uPrev'), 0); gl.uniform1i(u(p, 'uCurr'), 1); gl.uniform1i(u(p, 'uPred'), 2);
        gl.uniform1i(u(p, 'uTemporal'), 3); gl.uniform1i(u(p, 'uUseTemporal'), temporalTex ? 1 : 0);
        gl.uniform1i(u(p, 'uLod'), lod);
        draw();
        pred = out.t;
      }
      p = progs.median;
      gl.bindFramebuffer(gl.FRAMEBUFFER, bidi ? fFine2 : outFbo); gl.viewport(0, 0, fw, fh); gl.useProgram(p);
      bind(0, tFine); gl.uniform1i(u(p, 'uFlow'), 0);
      draw();
      if (bidi) {
        p = progs.boundary;
        gl.bindFramebuffer(gl.FRAMEBUFFER, outFbo); gl.viewport(0, 0, fw, fh); gl.useProgram(p);
        bind(0, searchIn); bind(1, grid); bind(2, tFine2);
        gl.uniform1i(u(p, 'uPrev'), 0); gl.uniform1i(u(p, 'uCurr'), 1); gl.uniform1i(u(p, 'uPred'), 2); gl.uniform1i(u(p, 'uLod'), fl);
        draw();
      }
    }
    estimate(tA, tB, null, fFlow);
    if (scene.temporal) {
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, fFlow); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, fFine2);
      gl.blitFramebuffer(0, 0, fw, fh, 0, 0, fw, fh, gl.COLOR_BUFFER_BIT, gl.NEAREST);
      estimate(tA, tB, tFine2, fFlow);
    }
    if (bidi) estimate(tB, tA, null, fBwd);
    const flowPx = read(fFlow, fw, fh);
    const mePx = (performance.now() - t0);

    // Fluss auswerten (Erwartung: F = -v, in Pixeln)
    const ex = -scene.vx, ey = -scene.vy;
    let good = 0, total = 0, sumx = 0, sumy = 0;
    for (let y = 1; y < fh - 1; y++) for (let x = 1; x < fw - 1; x++) {
      const px = (x + 0.5) * (W / fw), py = (y + 0.5) * (H / fh);
      if (scene.kind === 'object') {
        const inside = px >= scene.ox + 12 && px < scene.ox + scene.size - 12 && py >= scene.oy + 12 && py < scene.oy + scene.size - 12;
        if (!inside) continue;
      }
      const i = (y * fw + x) * 4;
      const fx = decode(flowPx[i], flowPx[i + 1]) * W, fy = decode(flowPx[i + 2], flowPx[i + 3]) * H;
      total++; sumx += fx; sumy += fy;
      if (Math.abs(fx - ex) < 1.5 && Math.abs(fy - ey) < 1.5) good++;
    }

    function displayAt(mode, tt, dual) {
      const p = progs.display;
      gl.bindFramebuffer(gl.FRAMEBUFFER, fOut); gl.viewport(0, 0, W, H); gl.useProgram(p);
      bind(0, tA); bind(1, tB); bind(2, tFlow); bind(3, tBwd);
      gl.uniform1i(u(p, 'uA'), 0); gl.uniform1i(u(p, 'uB'), 1); gl.uniform1i(u(p, 'uFlow'), 2);
      gl.uniform1i(u(p, 'uFlowB'), 3); gl.uniform1i(u(p, 'uDual'), dual ? 1 : 0);
      gl.uniform4f(u(p, 'uRect'), 0, 0, W, H);
      gl.uniform1f(u(p, 'uT'), tt); gl.uniform1i(u(p, 'uMode'), mode);
      gl.uniform4f(u(p, 'uAdj'), 0, 1, 1, 1); gl.uniform1i(u(p, 'uAdjOn'), 0);
      draw();
      return read(fOut, W, H);
    }
    const motion = displayAt(2, 0.5, false), blend = displayAt(1, 0.5, false), single = displayAt(0, 0.5, false);
    const dualImg = bidi ? displayAt(2, 0.5, true) : null;
    // Zeitlupe: viele Zwischenzeitpunkte gegen Ground Truth
    let slowSum = 0, slowN = 0;
    if (scene.slowmo) {
      for (const tt of [0.1, 0.25, 0.4, 0.6, 0.75, 0.9]) {
        const img = displayAt(2, tt, bidi);
        slowSum += psnr(img, render(scene, tt), null); slowN++;
      }
    }
    const mask = scene.kind === 'object'
      ? (x, y) => x >= scene.ox - 10 && x < scene.ox + scene.size + scene.vx + 10 && y >= scene.oy - 10 && y < scene.oy + scene.size + scene.vy + 10
      : null;
    // DEBUG_IMAGES: Ausschnitte für visuelle Kontrolle
    if (scene.debug) {
      const crop = (img) => {
        const cx = scene.ox - 40, cy = scene.oy - 40, cwid = scene.size + scene.vx + 80, chei = scene.size + scene.vy + 80;
        const cv = document.createElement('canvas'); cv.width = cwid; cv.height = chei;
        const ctx = cv.getContext('2d'); const id = ctx.createImageData(cwid, chei);
        for (let y = 0; y < chei; y++) for (let x = 0; x < cwid; x++) {
          const si = ((cy + y) * W + (cx + x)) * 4, di = ((chei - 1 - y) * cwid + x) * 4;
          for (let c = 0; c < 4; c++) id.data[di + c] = img[si + c];
        }
        ctx.putImageData(id, 0, 0); return cv.toDataURL('image/png');
      };
      const errImg = (img) => { const e = new Uint8Array(img.length); for (let i = 0; i < img.length; i += 4) { const d = Math.max(Math.abs(img[i]-GT[i]), Math.abs(img[i+1]-GT[i+1]), Math.abs(img[i+2]-GT[i+2])); e[i] = Math.min(255, d*4); e[i+1] = Math.min(255, d*4); e[i+2] = Math.min(255, d*4); e[i+3] = 255; } return e; };
      out.debug = out.debug || {};
      out.debug[scene.name + '_' + q] = { gt: crop(GT), single: crop(motion), dual: dualImg ? crop(dualImg) : null, blend: crop(blend),
        errSingle: crop(errImg(motion)), errDual: dualImg ? crop(errImg(dualImg)) : null };
    }
    out.scenes.push({
      name: scene.name + (scene.natural ? ' [natürlich]' : ' [Rauschen]') + (scene.temporal ? ' +zeitl.' : ''), quality: q, bidi, flowSize: fw + 'x' + fh, coarseSize: cw + 'x' + ch,
      expected: [ex, ey], meanFlow: [+(sumx / total).toFixed(2), +(sumy / total).toFixed(2)],
      flowAccuracy: +(100 * good / total).toFixed(1),
      psnrMotion: +psnr(motion, GT, mask).toFixed(2), psnrBlend: +psnr(blend, GT, mask).toFixed(2),
      psnrRepeat: +psnr(single, GT, mask).toFixed(2), meMs: +mePx.toFixed(1),
      psnrDual: dualImg ? +psnr(dualImg, GT, mask).toFixed(2) : null,
      psnrSlowmoAvg: slowN ? +(slowSum / slowN).toFixed(2) : null
    });
  }
  // ---------------------------------------------------------------- Upscaler
  out.upscale = [];
  function upTest(name, img) {
    // 2x verkleinern (Box), wieder auf W x H hochrechnen, mit Original vergleichen
    const w2 = W / 2, h2 = H / 2;
    const small = new Uint8Array(w2 * h2 * 4);
    for (let y = 0; y < h2; y++) for (let x = 0; x < w2; x++) for (let c = 0; c < 4; c++) {
      const i = (a, b) => ((b * W + a) * 4 + c);
      small[(y * w2 + x) * 4 + c] = Math.round((img[i(2 * x, 2 * y)] + img[i(2 * x + 1, 2 * y)] + img[i(2 * x, 2 * y + 1)] + img[i(2 * x + 1, 2 * y + 1)]) / 4);
    }
    const tS = tex(w2, h2, 1, false, small);
    gl.bindTexture(gl.TEXTURE_2D, tS);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    const tO = tex(W, H, 1, false), fO = fbo(tO);
    const res = { name };
    // bilinear über Display-Shader (Modus 0)
    let p = progs.display;
    gl.bindFramebuffer(gl.FRAMEBUFFER, fO); gl.viewport(0, 0, W, H); gl.useProgram(p);
    bind(0, tS); bind(1, tS); bind(2, tS); bind(3, tS);
    gl.uniform1i(u(p, 'uA'), 0); gl.uniform1i(u(p, 'uB'), 1); gl.uniform1i(u(p, 'uFlow'), 2); gl.uniform1i(u(p, 'uFlowB'), 3);
    gl.uniform4f(u(p, 'uRect'), 0, 0, W, H); gl.uniform1i(u(p, 'uMode'), 0); gl.uniform1i(u(p, 'uDual'), 0);
    gl.uniform1f(u(p, 'uT'), 0); gl.uniform4f(u(p, 'uAdj'), 0, 1, 1, 1); gl.uniform1i(u(p, 'uAdjOn'), 0);
    draw();
    res.bilinear = +psnr(read(fO, W, H), img, null).toFixed(2);
    p = progs.upscale;
    for (const [label, kernel, sharp] of [['bikubisch', 0, 0], ['bikubisch+schärfe0.3', 0, 0.3], ['kantenadaptiv', 1, 0], ['kantenadaptiv+schärfe0.3', 1, 0.3]]) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, fO); gl.viewport(0, 0, W, H); gl.useProgram(p);
      bind(0, tS); gl.uniform1i(u(p, 'uSrc'), 0);
      gl.uniform4f(u(p, 'uRect'), 0, 0, W, H); gl.uniform1i(u(p, 'uKernel'), kernel); gl.uniform1f(u(p, 'uSharp'), sharp);
      draw();
      res[label] = +psnr(read(fO, W, H), img, null).toFixed(2);
    }
    out.upscale.push(res);
  }
  NATURAL = true;
  upTest('natürliches Muster', render({ kind: 'pan', vx: 0, vy: 0 }, 0));
  // harte Kanten: gedrehtes Quadrat + Kreis + Linien (geglättet)
  const edges = new Uint8Array(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let v = 0;
    const ss = 4;
    for (let sy = 0; sy < ss; sy++) for (let sx = 0; sx < ss; sx++) {
      const px = x + (sx + 0.5) / ss, py = y + (sy + 0.5) / ss;
      const rx = (px - 200) * Math.cos(0.4) + (py - 180) * Math.sin(0.4), ry = -(px - 200) * Math.sin(0.4) + (py - 180) * Math.cos(0.4);
      let inside = Math.abs(rx) < 90 && Math.abs(ry) < 90;
      if (Math.hypot(px - 470, py - 180) < 110) inside = !inside;
      if (Math.abs((px * 0.3 + py) % 40 - 20) < 3 && px > 380) inside = !inside;
      v += inside ? 1 : 0;
    }
    v /= ss * ss;
    const i = (y * W + x) * 4;
    edges[i] = Math.round(30 + 200 * v); edges[i + 1] = Math.round(40 + 180 * v); edges[i + 2] = Math.round(60 + 140 * v); edges[i + 3] = 255;
  }
  upTest('harte Kanten', edges);
  return out;
}
