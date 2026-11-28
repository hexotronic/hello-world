package com.hexotronic.fluxplayer.render

/**
 * GLSL-Shader der Render-Pipeline.
 *
 * Pipeline je neuem Videobild:
 *  1. OES-Kopie: SurfaceTexture -> RGBA-Textur mit Mipmaps (Ringpuffer)
 *  2. Grobe Bewegungsschätzung (volle Block-Suche auf kleiner Mipmap-Stufe, Subpixel)
 *  3. Zwei Verfeinerungsstufen mit Kandidatensuche (räumliche + zeitliche Vorhersage)
 *  4. 3x3-Median-Filter auf dem Bewegungsfeld
 *
 * Pro Bildschirm-Refresh:
 *  5. Anzeige: Zwischenbild zum exakten Zeitpunkt t zwischen Bild A und B,
 *     bewegungskompensiert (Warping beider Bilder entlang des Flusses), inkl. Zoom/Pan
 *     und Bildeinstellungen.
 *
 * Das Bewegungsfeld F ist auf dem Raster von Bild B definiert: A(x + F(x)) ≈ B(x).
 * Es wird mit 16 Bit Genauigkeit in RGBA8 gepackt (x: RG, y: BA).
 */
internal object Shaders {

    // ---------------------------------------------------------------- ES 1.00: OES-Kopie
    val VS_COPY = """
        attribute vec2 aPos;
        uniform mat4 uTexMatrix;
        uniform vec2 uOff;
        varying vec2 vUv0;
        varying vec2 vUv1;
        varying vec2 vUv2;
        varying vec2 vUv3;
        void main() {
            gl_Position = vec4(aPos, 0.0, 1.0);
            vec2 uv = aPos * 0.5 + 0.5;
            vUv0 = (uTexMatrix * vec4(uv + vec2(-uOff.x, -uOff.y), 0.0, 1.0)).xy;
            vUv1 = (uTexMatrix * vec4(uv + vec2( uOff.x, -uOff.y), 0.0, 1.0)).xy;
            vUv2 = (uTexMatrix * vec4(uv + vec2(-uOff.x,  uOff.y), 0.0, 1.0)).xy;
            vUv3 = (uTexMatrix * vec4(uv + vec2( uOff.x,  uOff.y), 0.0, 1.0)).xy;
        }
    """.trimIndent()

    val FS_COPY_OES = """
        #extension GL_OES_EGL_image_external : require
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform samplerExternalOES uTex;
        varying vec2 vUv0;
        varying vec2 vUv1;
        varying vec2 vUv2;
        varying vec2 vUv3;
        void main() {
            vec4 c = texture2D(uTex, vUv0) + texture2D(uTex, vUv1) + texture2D(uTex, vUv2) + texture2D(uTex, vUv3);
            gl_FragColor = vec4(c.rgb * 0.25, 1.0);
        }
    """.trimIndent()

    // ---------------------------------------------------------------- ES 3.00
    val VS_ES3 = """
        #version 300 es
        in vec2 aPos;
        void main() {
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """.trimIndent()

    private val HEADER = """
        #version 300 es
        precision highp float;
        precision highp int;
        precision highp sampler2D;
    """.trimIndent()

    private val COMMON = """
        const float FLOW_RANGE = 0.5;
        vec4 packFlow(vec2 f) {
            vec2 u = clamp(f / (2.0 * FLOW_RANGE) + 0.5, 0.0, 1.0);
            vec2 s = u * 255.0;
            vec2 hi = floor(s);
            vec2 lo = s - hi;
            return vec4(hi.x / 255.0, lo.x, hi.y / 255.0, lo.y);
        }
        vec2 unpackFlow(vec4 p) {
            vec2 u = vec2(p.x + p.y / 255.0, p.z + p.w / 255.0);
            return (u - 0.5) * (2.0 * FLOW_RANGE);
        }
        float lumaOf(vec3 c) {
            return dot(c, vec3(0.299, 0.587, 0.114));
        }
    """.trimIndent()

    private fun es3(defines: String, body: String): String =
        HEADER + "\n" + defines + "\n" + COMMON + "\n" + body.trimIndent() + "\n"

    /**
     * Grobe Suche: volle Suche ±R Texel auf Mipmap-Stufe uLod mit 3x3-Block
     * und Subpixel-Verfeinerung (Parabelanpassung).
     */
    fun coarseFs(radius: Int, lambda: Float): String = es3(
        "#define SEARCH_R $radius\n#define LAMBDA ${glFloat(lambda)}",
        """
        uniform sampler2D uPrev;
        uniform sampler2D uCurr;
        uniform int uLod;
        out vec4 fragColor;

        ivec2 gSz;
        ivec2 gP;
        float gB[9];

        float fetchL(sampler2D s, ivec2 p) {
            return lumaOf(texelFetch(s, clamp(p, ivec2(0), gSz - ivec2(1)), uLod).rgb);
        }

        float cost(ivec2 d) {
            float c = 0.0;
            for (int i = 0; i < 9; i++) {
                c += abs(gB[i] - fetchL(uPrev, gP + d + ivec2(i % 3 - 1, i / 3 - 1)));
            }
            return c;
        }

        void main() {
            gSz = textureSize(uCurr, uLod);
            gP = ivec2(gl_FragCoord.xy);
            for (int i = 0; i < 9; i++) {
                gB[i] = fetchL(uCurr, gP + ivec2(i % 3 - 1, i / 3 - 1));
            }
            float best = 1e9;
            ivec2 bestD = ivec2(0);
            for (int dy = -SEARCH_R; dy <= SEARCH_R; dy++) {
                for (int dx = -SEARCH_R; dx <= SEARCH_R; dx++) {
                    ivec2 d = ivec2(dx, dy);
                    float c = cost(d) + LAMBDA * length(vec2(d));
                    if (c < best) {
                        best = c;
                        bestD = d;
                    }
                }
            }
            float c0 = cost(bestD);
            float cxm = cost(bestD - ivec2(1, 0));
            float cxp = cost(bestD + ivec2(1, 0));
            float cym = cost(bestD - ivec2(0, 1));
            float cyp = cost(bestD + ivec2(0, 1));
            vec2 sub = vec2(0.0);
            float ddx = cxm - 2.0 * c0 + cxp;
            float ddy = cym - 2.0 * c0 + cyp;
            if (ddx > 1e-4) sub.x = clamp(0.5 * (cxm - cxp) / ddx, -0.5, 0.5);
            if (ddy > 1e-4) sub.y = clamp(0.5 * (cym - cyp) / ddy, -0.5, 0.5);
            fragColor = packFlow((vec2(bestD) + sub) / vec2(gSz));
        }
        """
    )

    /**
     * Verfeinerung auf einer feineren Pyramidenstufe (Kandidatensuche wie bei 3D-Recursive-Search):
     * Kandidaten = Vorhersage der gröberen Stufe an dieser Stelle und bei den 4 Nachbarn,
     * Nullvektor und zeitliche Vorhersage (Fluss des vorherigen Bildpaars).
     * Um jeden Kandidaten wird ±R gesucht, danach Subpixel-Verfeinerung.
     */
    fun refineFs(radius: Int, lambda: Float, zeroBonus: Float): String = es3(
        "#define SEARCH_R $radius\n#define LAMBDA ${glFloat(lambda)}\n#define ZERO_BONUS ${glFloat(zeroBonus)}\n#define PATCH_STEP 2",
        """
        uniform sampler2D uPrev;
        uniform sampler2D uCurr;
        uniform sampler2D uPred;
        uniform sampler2D uTemporal;
        uniform int uLod;
        uniform int uUseTemporal;
        out vec4 fragColor;

        ivec2 gSz;
        ivec2 gP;
        float gB[9];
        float gBest;
        ivec2 gBestD;

        float fetchL(sampler2D s, ivec2 p) {
            return lumaOf(texelFetch(s, clamp(p, ivec2(0), gSz - ivec2(1)), uLod).rgb);
        }

        ivec2 offs(int i) {
            return ivec2(i % 3 - 1, i / 3 - 1) * PATCH_STEP;
        }

        float cost(ivec2 d) {
            float c = 0.0;
            for (int i = 0; i < 9; i++) {
                c += abs(gB[i] - fetchL(uPrev, gP + d + offs(i)));
            }
            return c;
        }

        vec2 flowAtTexel(sampler2D s, vec2 uv, ivec2 offset) {
            ivec2 sz = textureSize(s, 0);
            ivec2 t = clamp(ivec2(uv * vec2(sz)) + offset, ivec2(0), sz - ivec2(1));
            return unpackFlow(texelFetch(s, t, 0));
        }

        void tryCandidate(vec2 flowUv, float bias) {
            ivec2 c = ivec2(floor(flowUv * vec2(gSz) + 0.5));
            for (int dy = -SEARCH_R; dy <= SEARCH_R; dy++) {
                for (int dx = -SEARCH_R; dx <= SEARCH_R; dx++) {
                    ivec2 d = c + ivec2(dx, dy);
                    float v = cost(d) + bias + LAMBDA * length(vec2(float(dx), float(dy)));
                    if (v < gBest) {
                        gBest = v;
                        gBestD = d;
                    }
                }
            }
        }

        void main() {
            gSz = textureSize(uCurr, uLod);
            gP = ivec2(gl_FragCoord.xy);
            for (int i = 0; i < 9; i++) {
                gB[i] = fetchL(uCurr, gP + offs(i));
            }
            vec2 uv = (vec2(gP) + 0.5) / vec2(gSz);

            gBest = cost(ivec2(0)) - ZERO_BONUS;
            gBestD = ivec2(0);
            tryCandidate(flowAtTexel(uPred, uv, ivec2(0, 0)), 0.0);
            tryCandidate(flowAtTexel(uPred, uv, ivec2(-1, 0)), LAMBDA);
            tryCandidate(flowAtTexel(uPred, uv, ivec2(1, 0)), LAMBDA);
            tryCandidate(flowAtTexel(uPred, uv, ivec2(0, -1)), LAMBDA);
            tryCandidate(flowAtTexel(uPred, uv, ivec2(0, 1)), LAMBDA);
            if (uUseTemporal == 1) {
                tryCandidate(flowAtTexel(uTemporal, uv, ivec2(0, 0)), LAMBDA * 0.5);
            }

            ivec2 bd = gBestD;
            float c0 = cost(bd);
            float cxm = cost(bd - ivec2(1, 0));
            float cxp = cost(bd + ivec2(1, 0));
            float cym = cost(bd - ivec2(0, 1));
            float cyp = cost(bd + ivec2(0, 1));
            vec2 sub = vec2(0.0);
            float ddx = cxm - 2.0 * c0 + cxp;
            float ddy = cym - 2.0 * c0 + cyp;
            if (ddx > 1e-4) sub.x = clamp(0.5 * (cxm - cxp) / ddx, -0.5, 0.5);
            if (ddy > 1e-4) sub.y = clamp(0.5 * (cym - cyp) / ddy, -0.5, 0.5);
            fragColor = packFlow((vec2(bd) + sub) / vec2(gSz));
        }
        """
    )

    /**
     * Kanten-Verfeinerung (nur „Ultra“): kleine 3x3-Blöcke mit Farbvergleich (RGB) statt Helligkeit,
     * Kandidaten = eigener Vektor, 8 direkte und 4 entfernte Nachbarn, Nullvektor.
     * Dadurch übernehmen Pixel am Objektrand den Vektor der richtigen Seite statt eines Mischvektors.
     */
    fun boundaryFs(lambda: Float): String = es3(
        "#define LAMBDA ${glFloat(lambda)}",
        """
        uniform sampler2D uPrev;
        uniform sampler2D uCurr;
        uniform sampler2D uPred;
        uniform int uLod;
        out vec4 fragColor;

        ivec2 gSz;
        ivec2 gP;
        vec3 gB[9];
        float gBest;
        ivec2 gBestD;

        vec3 fetchC(sampler2D s, ivec2 p) {
            return texelFetch(s, clamp(p, ivec2(0), gSz - ivec2(1)), uLod).rgb;
        }

        float cost(ivec2 d) {
            float c = 0.0;
            for (int i = 0; i < 9; i++) {
                vec3 e = abs(gB[i] - fetchC(uPrev, gP + d + ivec2(i % 3 - 1, i / 3 - 1)));
                c += e.r + e.g + e.b;
            }
            return c;
        }

        void tryVec(vec2 flowUv, float bias) {
            ivec2 d = ivec2(floor(flowUv * vec2(gSz) + 0.5));
            float v = cost(d) + bias;
            if (v < gBest) {
                gBest = v;
                gBestD = d;
            }
        }

        vec2 predAt(ivec2 o) {
            ivec2 sz = textureSize(uPred, 0);
            return unpackFlow(texelFetch(uPred, clamp(gP + o, ivec2(0), sz - ivec2(1)), 0));
        }

        void main() {
            gSz = textureSize(uCurr, uLod);
            gP = ivec2(gl_FragCoord.xy);
            for (int i = 0; i < 9; i++) {
                gB[i] = fetchC(uCurr, gP + ivec2(i % 3 - 1, i / 3 - 1));
            }
            vec2 own = predAt(ivec2(0));
            gBest = 1e9;
            gBestD = ivec2(0);
            tryVec(own, 0.0);
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                tryVec(predAt(ivec2(i % 3 - 1, i / 3 - 1)), LAMBDA);
            }
            tryVec(predAt(ivec2(-3, 0)), LAMBDA * 2.0);
            tryVec(predAt(ivec2(3, 0)), LAMBDA * 2.0);
            tryVec(predAt(ivec2(0, -3)), LAMBDA * 2.0);
            tryVec(predAt(ivec2(0, 3)), LAMBDA * 2.0);
            tryVec(vec2(0.0), LAMBDA * 2.0);

            ivec2 bd = gBestD;
            // Hat sich der Vektor nicht geändert, die Subpixel-Genauigkeit der Vorstufe behalten
            if (bd == ivec2(floor(own * vec2(gSz) + 0.5))) {
                fragColor = packFlow(own);
                return;
            }
            float c0 = cost(bd);
            float cxm = cost(bd - ivec2(1, 0));
            float cxp = cost(bd + ivec2(1, 0));
            float cym = cost(bd - ivec2(0, 1));
            float cyp = cost(bd + ivec2(0, 1));
            vec2 sub = vec2(0.0);
            float ddx = cxm - 2.0 * c0 + cxp;
            float ddy = cym - 2.0 * c0 + cyp;
            if (ddx > 1e-4) sub.x = clamp(0.5 * (cxm - cxp) / ddx, -0.5, 0.5);
            if (ddy > 1e-4) sub.y = clamp(0.5 * (cym - cyp) / ddy, -0.5, 0.5);
            fragColor = packFlow((vec2(bd) + sub) / vec2(gSz));
        }
        """
    )

    /** Komponentenweiser 3x3-Median (entfernt Ausreißer im Bewegungsfeld). */
    val MEDIAN_FS: String = es3(
        "",
        """
        uniform sampler2D uFlow;
        out vec4 fragColor;

        void s2(inout vec2 a, inout vec2 b) {
            vec2 t = min(a, b);
            b = max(a, b);
            a = t;
        }

        vec2 at(ivec2 p, ivec2 sz) {
            return unpackFlow(texelFetch(uFlow, clamp(p, ivec2(0), sz - ivec2(1)), 0));
        }

        void main() {
            ivec2 sz = textureSize(uFlow, 0);
            ivec2 p = ivec2(gl_FragCoord.xy);
            vec2 v0 = at(p + ivec2(-1, -1), sz);
            vec2 v1 = at(p + ivec2( 0, -1), sz);
            vec2 v2 = at(p + ivec2( 1, -1), sz);
            vec2 v3 = at(p + ivec2(-1,  0), sz);
            vec2 v4 = at(p, sz);
            vec2 v5 = at(p + ivec2( 1,  0), sz);
            vec2 v6 = at(p + ivec2(-1,  1), sz);
            vec2 v7 = at(p + ivec2( 0,  1), sz);
            vec2 v8 = at(p + ivec2( 1,  1), sz);
            s2(v1, v2); s2(v4, v5); s2(v7, v8);
            s2(v0, v1); s2(v3, v4); s2(v6, v7);
            s2(v1, v2); s2(v4, v5); s2(v7, v8);
            s2(v0, v3); s2(v5, v8); s2(v4, v7);
            s2(v3, v6); s2(v1, v4); s2(v2, v5);
            s2(v4, v7); s2(v4, v2); s2(v6, v4);
            s2(v4, v2);
            fragColor = packFlow(v4);
        }
        """
    )

    /** Anzeige: Einzelbild / Überblendung / bewegungskompensiertes Zwischenbild + Zoom + Bildeinstellungen. */
    val DISPLAY_FS: String = es3(
        "",
        """
        uniform sampler2D uA;
        uniform sampler2D uB;
        uniform sampler2D uFlow;
        uniform sampler2D uFlowB;
        uniform int uDual;
        uniform vec4 uRect;
        uniform float uT;
        uniform int uMode;
        uniform vec4 uAdj;
        uniform int uAdjOn;
        out vec4 fragColor;

        vec2 flowAt(sampler2D fl, vec2 uv) {
            ivec2 fsz = textureSize(fl, 0);
            vec2 pos = uv * vec2(fsz) - 0.5;
            vec2 i0 = floor(pos);
            vec2 fr = pos - i0;
            ivec2 a = ivec2(i0);
            ivec2 mx = fsz - ivec2(1);
            vec2 f00 = unpackFlow(texelFetch(fl, clamp(a, ivec2(0), mx), 0));
            vec2 f10 = unpackFlow(texelFetch(fl, clamp(a + ivec2(1, 0), ivec2(0), mx), 0));
            vec2 f01 = unpackFlow(texelFetch(fl, clamp(a + ivec2(0, 1), ivec2(0), mx), 0));
            vec2 f11 = unpackFlow(texelFetch(fl, clamp(a + ivec2(1, 1), ivec2(0), mx), 0));
            return mix(mix(f00, f10, fr.x), mix(f01, f11, fr.x), fr.y);
        }

        vec3 adjust(vec3 c) {
            c = (c - 0.5) * uAdj.y + 0.5 + uAdj.x;
            float l = lumaOf(c);
            c = mix(vec3(l), c, uAdj.z);
            c = pow(clamp(c, 0.0, 1.0), vec3(1.0 / uAdj.w));
            return clamp(c, 0.0, 1.0);
        }

        void main() {
            vec2 uv = (gl_FragCoord.xy - uRect.xy) / uRect.zw;
            vec2 gx = dFdx(uv);
            vec2 gy = dFdy(uv);
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
                fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }
            vec3 c;
            if (uMode == 0) {
                c = textureGrad(uA, uv, gx, gy).rgb;
            } else if (uMode == 1) {
                c = mix(textureGrad(uA, uv, gx, gy).rgb, textureGrad(uB, uv, gx, gy).rgb, uT);
            } else if (uDual == 1) {
                // Bidirektional: jede Seite löst ihre Warping-Gleichung per Fixpunkt-Iteration
                // (A-Seite mit G auf dem A-Raster, B-Seite mit F auf dem B-Raster).
                // Springt die Lösung, gibt es dort kein passendes Pixel (Verdeckung oder Fehlschätzung)
                // und die andere Seite übernimmt.
                vec2 fsz = vec2(textureSize(uA, 0));
                vec2 G0 = flowAt(uFlowB, uv);
                vec2 xa = uv - uT * G0;
                vec2 G1 = flowAt(uFlowB, xa);
                xa = uv - uT * G1;
                vec2 F0 = flowAt(uFlow, uv);
                vec2 xb = uv - (1.0 - uT) * F0;
                vec2 F1 = flowAt(uFlow, xb);
                xb = uv - (1.0 - uT) * F1;
                float jumpA = length((G1 - G0) * fsz) * uT;
                float jumpB = length((F1 - F0) * fsz) * (1.0 - uT);
                float relA = 1.0 - smoothstep(0.75, 3.0, jumpA);
                float relB = 1.0 - smoothstep(0.75, 3.0, jumpB);
                vec3 ca = textureGrad(uA, xa, gx, gy).rgb;
                vec3 cb = textureGrad(uB, xb, gx, gy).rgb;
                float wA = (1.0 - uT) * (relA + 0.02);
                float wB = uT * (relB + 0.02);
                vec3 warped = (ca * wA + cb * wB) / (wA + wB);
                vec3 d = abs(ca - cb);
                float diff = max(max(d.r, d.g), d.b) * relA * relB;
                float w = smoothstep(0.15, 0.45, diff);
                c = warped;
                if (w > 0.0) {
                    vec3 plain = mix(textureGrad(uA, uv, gx, gy).rgb, textureGrad(uB, uv, gx, gy).rgb, uT);
                    c = mix(warped, plain, w);
                }
            } else {
                vec2 f = flowAt(uFlow, uv);
                vec3 ca = textureGrad(uA, uv + uT * f, gx, gy).rgb;
                vec3 cb = textureGrad(uB, uv - (1.0 - uT) * f, gx, gy).rgb;
                vec3 warped = mix(ca, cb, uT);
                vec3 d = abs(ca - cb);
                float diff = max(max(d.r, d.g), d.b);
                float w = smoothstep(0.12, 0.40, diff);
                c = warped;
                if (w > 0.0) {
                    vec3 plain = mix(textureGrad(uA, uv, gx, gy).rgb, textureGrad(uB, uv, gx, gy).rgb, uT);
                    c = mix(warped, plain, w);
                }
            }
            if (uAdjOn == 1) {
                c = adjust(c);
            }
            fragColor = vec4(c, 1.0);
        }
        """
    )

    /**
     * Upscaler (Bildschirmauflösung), auf Mobil-GPUs optimiert:
     *  - Catmull-Rom (bikubisch) über 9 bilineare Abtastungen statt 16 Einzeltexel
     *  - kantenadaptiv: Gradient aus den 4 nächsten Texeln, an echten Kanten 2 zusätzliche
     *    Abtastungen entlang der Kante (glättet Treppen) + Anti-Ringing auf die 2x2-Spanne
     *  - Schärfung (kontrastadaptiv): bikubisch minus bilinear als Hochpass, 1 zusätzliche Abtastung
     * Keine Arrays, keine Schleifen -> kein Register-Spilling.
     */
    val UPSCALE_FS: String = es3(
        "",
        """
        uniform sampler2D uSrc;
        uniform vec4 uRect;
        uniform int uKernel;
        uniform float uSharp;
        out vec4 fragColor;

        vec3 tex(vec2 p) {
            return textureLod(uSrc, p, 0.0).rgb;
        }

        void main() {
            vec2 uv = (gl_FragCoord.xy - uRect.xy) / uRect.zw;
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
                fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }
            ivec2 isz = textureSize(uSrc, 0);
            vec2 fsz = vec2(isz);
            vec2 inv = 1.0 / fsz;

            // --- Catmull-Rom mit 9 bilinearen Abtastungen
            vec2 sp = uv * fsz;
            vec2 tc1 = floor(sp - 0.5) + 0.5;
            vec2 f = sp - tc1;
            vec2 f2 = f * f;
            vec2 f3 = f2 * f;
            vec2 w0 = f2 - 0.5 * (f3 + f);
            vec2 w1 = 1.5 * f3 - 2.5 * f2 + 1.0;
            vec2 w3 = 0.5 * (f3 - f2);
            vec2 w2 = 1.0 - w0 - w1 - w3;
            vec2 w12 = w1 + w2;
            vec2 t0 = (tc1 - 1.0) * inv;
            vec2 t3 = (tc1 + 2.0) * inv;
            vec2 t12 = (tc1 + w2 / w12) * inv;
            vec3 res =
                (tex(vec2(t0.x, t0.y)) * w0.x + tex(vec2(t12.x, t0.y)) * w12.x + tex(vec2(t3.x, t0.y)) * w3.x) * w0.y +
                (tex(vec2(t0.x, t12.y)) * w0.x + tex(vec2(t12.x, t12.y)) * w12.x + tex(vec2(t3.x, t12.y)) * w3.x) * w12.y +
                (tex(vec2(t0.x, t3.y)) * w0.x + tex(vec2(t12.x, t3.y)) * w12.x + tex(vec2(t3.x, t3.y)) * w3.x) * w3.y;

            // --- die 4 nächsten Texel (Kanten, Anti-Ringing, Schärfung)
            ivec2 b = ivec2(tc1 - 0.5);
            ivec2 mx = isz - ivec2(1);
            vec3 c00 = texelFetch(uSrc, clamp(b, ivec2(0), mx), 0).rgb;
            vec3 c10 = texelFetch(uSrc, clamp(b + ivec2(1, 0), ivec2(0), mx), 0).rgb;
            vec3 c01 = texelFetch(uSrc, clamp(b + ivec2(0, 1), ivec2(0), mx), 0).rgb;
            vec3 c11 = texelFetch(uSrc, clamp(b + ivec2(1, 1), ivec2(0), mx), 0).rgb;
            vec3 lo = min(min(c00, c10), min(c01, c11));
            vec3 hi = max(max(c00, c10), max(c01, c11));

            if (uKernel == 1) {
                float l00 = lumaOf(c00);
                float l10 = lumaOf(c10);
                float l01 = lumaOf(c01);
                float l11 = lumaOf(c11);
                vec2 g = vec2((l10 - l00) + (l11 - l01), (l01 - l00) + (l11 - l10)) * 0.5;
                float len = length(g);
                float edge = smoothstep(0.05, 0.2, len);
                if (edge > 0.0) {
                    vec2 d = vec2(-g.y, g.x) / len * 0.85 * inv;
                    vec3 along = 0.5 * (tex(uv + d) + tex(uv - d));
                    res = mix(res, 0.5 * (res + along), edge);
                    res = mix(res, clamp(res, lo, hi), edge);
                }
            }

            if (uSharp > 0.0) {
                // Hochpass = bikubisch minus bilinear an derselben Stelle (1 Abtastung)
                vec3 dd = hi - lo;
                float contrast = max(max(dd.r, dd.g), dd.b);
                float amount = 1.5 * uSharp * (1.0 - smoothstep(0.2, 0.7, contrast));
                float k = 0.1 * uSharp;
                res = clamp(res + (res - tex(uv)) * amount, lo - k, hi + k);
            }
            fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
        }
        """
    )

    /** 1:1-Kopie einer bildschirmgroßen Textur (zwischengespeichertes Upscaling). */
    val BLIT_FS: String = es3(
        "",
        """
        uniform sampler2D uTex;
        out vec4 fragColor;

        void main() {
            fragColor = texelFetch(uTex, ivec2(gl_FragCoord.xy), 0);
        }
        """
    )

    private fun glFloat(v: Float): String {
        val s = String.format(java.util.Locale.US, "%.6f", v)
        return s
    }
}
