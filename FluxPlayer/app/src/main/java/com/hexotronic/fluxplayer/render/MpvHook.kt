package com.hexotronic.fluxplayer.render

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/** Ein Render-Pass eines mpv-User-Shaders („//!HOOK“-Format). */
data class HookPass(
    val hook: String,
    val desc: String,
    val binds: List<String>,
    val save: String,
    val width: String,
    val height: String,
    val body: String,
    val unsupported: List<String> = emptyList()
)

/**
 * Parser und GLSL-ES-3.00-Generator für mpv-User-Shader (z. B. Anime4K).
 * Unterstützt die von CNN-Upscalern genutzten Direktiven: DESC, HOOK, BIND, SAVE, WIDTH, HEIGHT
 * (umgekehrte polnische Notation), COMPONENTS und WHEN (ignoriert – wir entscheiden selbst).
 */
object MpvHook {

    fun parse(text: String): List<HookPass> {
        val passes = ArrayList<HookPass>()
        var desc = ""
        var hook = ""
        val unsupported = ArrayList<String>()
        var binds = ArrayList<String>()
        var save = ""
        var width = ""
        var height = ""
        val body = StringBuilder()
        var inHeader = false
        var started = false

        fun flush() {
            if (started) {
                passes += HookPass(hook, desc, binds.toList(), save, width, height, body.toString().trim(), unsupported.toList())
            }
            desc = ""; hook = ""; binds = ArrayList(); save = ""; width = ""; height = ""
            unsupported.clear()
            body.setLength(0)
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("//!")) {
                if (!inHeader) {
                    flush()
                    inHeader = true
                    started = true
                }
                val key = line.substring(3).substringBefore(' ').uppercase()
                val value = line.substring(3).substringAfter(' ', "").trim()
                when (key) {
                    "DESC" -> desc = value
                    "HOOK" -> hook = value
                    "BIND" -> binds += value
                    "SAVE" -> save = value
                    "WIDTH" -> width = value
                    "HEIGHT" -> height = value
                    "COMPONENTS", "WHEN", "OFFSET" -> Unit
                    else -> unsupported += key
                }
            } else {
                inHeader = false
                if (started) body.append(raw).append('\n')
            }
        }
        flush()
        return passes.map { p ->
            val h = p.hook.ifEmpty { "MAIN" }
            val sizeRef = p.binds.firstOrNull { it != "HOOKED" } ?: h
            p.copy(
                hook = h,
                save = p.save.ifEmpty { h },
                width = p.width.ifEmpty { "$sizeRef.w" },
                height = p.height.ifEmpty { "$sizeRef.h" }
            )
        }
    }

    /**
     * Prüft, ob ein (z. B. vom Nutzer geladener) mpv-Shader mit diesem Player läuft.
     * @return null wenn ok, sonst eine verständliche Begründung.
     */
    fun validate(text: String): String? {
        val passes = try {
            parse(text)
        } catch (e: Exception) {
            return "Datei konnte nicht gelesen werden."
        }
        if (passes.isEmpty()) return "Keine mpv-Shader-Passes (//!HOOK) gefunden."
        val hook = passes.first().hook
        if (hook != "MAIN" && hook != "LUMA") return "Nur Shader für MAIN oder LUMA werden unterstützt (gefunden: $hook)."
        if (passes.any { it.hook != hook }) return "Shader mit mehreren Hook-Zielen werden nicht unterstützt."
        val unsupported = passes.flatMap { it.unsupported }.toSet()
        if (unsupported.isNotEmpty()) return "Nicht unterstützte Direktiven: ${unsupported.joinToString()} (z. B. TEXTURE/COMPUTE)."
        val known = HashSet<String>().apply { add(hook); add("HOOKED") }
        val sizes = HashMap<String, IntArray>().apply {
            put(hook, intArrayOf(640, 360))
            put("HOOKED", intArrayOf(640, 360))
        }
        var outW = 640
        for (p in passes) {
            val missing = p.binds.filter { it !in known }
            if (missing.isNotEmpty()) return "Unbekannte Texturen: ${missing.joinToString()}."
            val w = try {
                evalSize(p.width, sizes)
            } catch (e: Exception) {
                return "Größenangabe nicht auswertbar: ${p.width}"
            }
            val h = try {
                evalSize(p.height, sizes)
            } catch (e: Exception) {
                return "Größenangabe nicht auswertbar: ${p.height}"
            }
            if (w <= 0 || h <= 0 || w > 640 * 8) return "Ungültige Ausgabegröße."
            known += p.save
            if (p.save == hook) outW = w else sizes[p.save] = intArrayOf(w, h)
            if (!p.body.contains("hook()")) return "Pass ohne hook()-Funktion."
        }
        if (passes.none { it.save == hook }) return "Der Shader schreibt kein Ergebnis zurück ($hook)."
        if (outW <= 640) return "Das ist kein Upscaler (Ausgabe nicht größer als Eingabe)."
        return null
    }

    /** Wertet einen Größenausdruck in umgekehrter polnischer Notation aus, z. B. „conv2d_tf.w 2 *“. */
    fun evalSize(expr: String, sizes: Map<String, IntArray>): Int {
        val stack = ArrayDeque<Double>()
        for (tok in expr.trim().split(Regex("\\s+"))) {
            when {
                tok == "+" || tok == "-" || tok == "*" || tok == "/" -> {
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    stack.addLast(
                        when (tok) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            else -> a / b
                        }
                    )
                }
                tok.endsWith(".w") || tok.endsWith(".width") -> {
                    val name = tok.substringBeforeLast('.')
                    stack.addLast((sizes[name] ?: error("Unbekannte Textur $name"))[0].toDouble())
                }
                tok.endsWith(".h") || tok.endsWith(".height") -> {
                    val name = tok.substringBeforeLast('.')
                    stack.addLast((sizes[name] ?: error("Unbekannte Textur $name"))[1].toDouble())
                }
                else -> stack.addLast(tok.toDouble())
            }
        }
        return Math.round(stack.removeLast()).toInt()
    }

    /** Erzeugt den Fragment-Shader eines Passes (alle Texturen decken denselben Bildausschnitt ab). */
    fun fragmentSource(pass: HookPass): String {
        val sb = StringBuilder()
        sb.append("#version 300 es\n")
        sb.append("precision highp float;\nprecision highp int;\nprecision highp sampler2D;\n")
        sb.append("uniform vec2 uOutSize;\nout vec4 fragColor;\n")
        for (name in pass.binds) {
            sb.append("uniform sampler2D ${name}_raw;\n")
            sb.append("uniform vec2 ${name}_size;\n")
            sb.append("uniform vec2 ${name}_pt;\n")
            sb.append("#define ${name}_pos (gl_FragCoord.xy / uOutSize)\n")
            sb.append("#define ${name}_tex(pos) texture(${name}_raw, (pos))\n")
            sb.append("#define ${name}_texOff(off) ${name}_tex(${name}_pos + ${name}_pt * vec2(off))\n")
            sb.append("#define ${name}_mul 1.0\n")
        }
        val hooked = pass.hook
        if (hooked in pass.binds && "HOOKED" !in pass.binds) {
            for (suffix in listOf("raw", "size", "pt", "pos", "mul")) sb.append("#define HOOKED_$suffix ${hooked}_$suffix\n")
            sb.append("#define HOOKED_tex(pos) ${hooked}_tex(pos)\n#define HOOKED_texOff(off) ${hooked}_texOff(off)\n")
        }
        sb.append(pass.body).append('\n')
        sb.append("void main() {\n    fragColor = hook();\n}\n")
        return sb.toString()
    }
}

/**
 * Führt einen mpv-Hook-Upscaler (z. B. Anime4K CNN x2) auf der GPU aus.
 * Zwischenergebnisse liegen in RGBA16F-Texturen (Werte können negativ sein).
 * Nur auf dem GL-Thread verwenden.
 */
class HookUpscaler(private val passes: List<HookPass>, private val drawQuad: () -> Unit) {

    private class Target(val tex: Int, val fbo: Int, val w: Int, val h: Int)

    private val programs = IntArray(passes.size)
    private val outSizeLoc = IntArray(passes.size)
    private val bindLocs = Array(passes.size) { IntArray(passes[it].binds.size * 3) }
    private val targets = HashMap<String, Target>()
    private val hookName = passes.firstOrNull()?.hook ?: "MAIN"
    private var progLumaIn = 0
    private var progCombine = 0
    private var progCopy = 0
    var ok = false
        private set

    init {
        try {
            passes.forEachIndexed { i, p ->
                val prog = GlUtil.buildProgram(Shaders.VS_ES3, MpvHook.fragmentSource(p))
                programs[i] = prog
                outSizeLoc[i] = GLES20.glGetUniformLocation(prog, "uOutSize")
                p.binds.forEachIndexed { j, name ->
                    bindLocs[i][j * 3] = GLES20.glGetUniformLocation(prog, "${name}_raw")
                    bindLocs[i][j * 3 + 1] = GLES20.glGetUniformLocation(prog, "${name}_size")
                    bindLocs[i][j * 3 + 2] = GLES20.glGetUniformLocation(prog, "${name}_pt")
                }
            }
            if (hookName == "LUMA") {
                progLumaIn = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.LUMA_IN_FS)
                progCombine = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.LUMA_COMBINE_FS)
            }
            ok = passes.isNotEmpty() && passes.any { it.save == hookName }
        } catch (e: Exception) {
            Log.w("HookUpscaler", "Shader konnte nicht gebaut werden", e)
            release()
        }
    }

    /** Ausgabegröße für eine Eingabe der Größe w×h (bei Anime4K x2: doppelt). */
    fun outputSize(w: Int, h: Int): IntArray {
        val sizes = HashMap<String, IntArray>()
        sizes[hookName] = intArrayOf(w, h)
        sizes["HOOKED"] = intArrayOf(w, h)
        var out = intArrayOf(w, h)
        for (p in passes) {
            val s = intArrayOf(MpvHook.evalSize(p.width, sizes), MpvHook.evalSize(p.height, sizes))
            if (p.save == hookName) {
                out = s
                sizes[hookName] = s
                sizes["HOOKED"] = s
            } else sizes[p.save] = s
        }
        return out
    }

    /** Rechnet [srcTex] (RGB, w×h) hoch und schreibt das Ergebnis in [outFbo] (Größe = [outputSize]). */
    fun run(srcTex: Int, w: Int, h: Int, outFbo: Int) {
        if (!ok) return
        val sizes = HashMap<String, IntArray>()
        val texIds = HashMap<String, Int>()
        GLES20.glDisable(GLES20.GL_BLEND)
        var input = srcTex
        if (hookName == "LUMA") {
            // Helligkeit (BT.709) für Luma-Netze extrahieren
            val y = target("__Y_IN", w, h)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, y.fbo)
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(progLumaIn)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(progLumaIn, "uSrc"), 0)
            drawQuad()
            input = y.tex
        }
        sizes[hookName] = intArrayOf(w, h)
        texIds[hookName] = input
        sizes["HOOKED"] = intArrayOf(w, h)
        texIds["HOOKED"] = input
        var outW = w
        var outH = h
        passes.forEachIndexed { i, p ->
            val ow = MpvHook.evalSize(p.width, sizes)
            val oh = MpvHook.evalSize(p.height, sizes)
            val isOut = p.save == hookName
            val tgt = when {
                isOut && hookName == "MAIN" && i == passes.lastIndex -> null
                isOut -> target("__OUT_$i", ow, oh)
                else -> target(p.save, ow, oh)
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, tgt?.fbo ?: outFbo)
            GLES20.glViewport(0, 0, ow, oh)
            GLES20.glUseProgram(programs[i])
            GLES20.glUniform2f(outSizeLoc[i], ow.toFloat(), oh.toFloat())
            p.binds.forEachIndexed { j, name ->
                val tex = texIds[name] ?: error("Textur $name fehlt")
                val sz = sizes[name]!!
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + j)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glUniform1i(bindLocs[i][j * 3], j)
                GLES20.glUniform2f(bindLocs[i][j * 3 + 1], sz[0].toFloat(), sz[1].toFloat())
                GLES20.glUniform2f(bindLocs[i][j * 3 + 2], 1f / sz[0], 1f / sz[1])
            }
            drawQuad()
            if (tgt != null) {
                val key = if (isOut) hookName else p.save
                sizes[key] = intArrayOf(ow, oh)
                texIds[key] = tgt.tex
                if (isOut) {
                    sizes["HOOKED"] = intArrayOf(ow, oh)
                    texIds["HOOKED"] = tgt.tex
                }
            }
            if (isOut) {
                outW = ow
                outH = oh
            }
        }
        val result = texIds[hookName] ?: return
        if (hookName == "LUMA") {
            // hochgerechnete Helligkeit + Farbe aus der bikubischen Vergrößerung
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
            GLES20.glViewport(0, 0, outW, outH)
            GLES20.glUseProgram(progCombine)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, result)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(progCombine, "uSrc"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(progCombine, "uLuma"), 1)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(progCombine, "uOutSize"), outW.toFloat(), outH.toFloat())
            drawQuad()
        } else if (passes.last().save != hookName) {
            // Ergebnis liegt in einer Zwischentextur (Hook-Ziel nicht im letzten Pass) -> kopieren
            if (progCopy == 0) progCopy = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.BLIT_FS)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
            GLES20.glViewport(0, 0, outW, outH)
            GLES20.glUseProgram(progCopy)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, result)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(progCopy, "uTex"), 0)
            drawQuad()
        }
    }

    private fun target(name: String, w: Int, h: Int): Target {
        val key = "$name@${w}x$h"
        targets[key]?.let { return it }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA16F, w, h)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val fbo = GlUtil.createFramebuffer(ids[0])
        val t = Target(ids[0], fbo, w, h)
        targets[key] = t
        return t
    }

    /** Zwischentexturen freigeben (z. B. bei Auflösungswechsel). */
    fun releaseTargets() {
        for (t in targets.values) {
            GlUtil.deleteFramebuffer(t.fbo)
            GlUtil.deleteTexture(t.tex)
        }
        targets.clear()
    }

    fun release() {
        releaseTargets()
        for (p in programs) GlUtil.deleteProgram(p)
        programs.fill(0)
        GlUtil.deleteProgram(progLumaIn)
        GlUtil.deleteProgram(progCombine)
        GlUtil.deleteProgram(progCopy)
        progCopy = 0
        progLumaIn = 0
        progCombine = 0
        ok = false
    }

    companion object {
        /** Prüft, ob die GPU in RGBA16F rendern kann (Voraussetzung für die KI-Upscaler). */
        fun floatTargetsSupported(): Boolean {
            val version = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            val es32 = Regex("OpenGL ES (\\d+)\\.(\\d+)").find(version)?.let {
                val major = it.groupValues[1].toInt()
                val minor = it.groupValues[2].toInt()
                major > 3 || (major == 3 && minor >= 2)
            } ?: false
            if (!(es32 || ext.contains("GL_EXT_color_buffer_half_float") || ext.contains("GL_EXT_color_buffer_float"))) {
                return false
            }
            // tatsächlich ein RGBA16F-Framebuffer anlegen
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA16F, 4, 4)
            val fb = IntArray(1)
            GLES20.glGenFramebuffers(1, fb, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ids[0], 0)
            val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, fb, 0)
            GLES20.glDeleteTextures(1, ids, 0)
            return ok
        }
    }
}
