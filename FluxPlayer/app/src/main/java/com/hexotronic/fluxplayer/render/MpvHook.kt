package com.hexotronic.fluxplayer.render

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/** Ein Render-Pass eines mpv-User-Shaders („//!HOOK“-Format). */
data class HookPass(
    val desc: String,
    val binds: List<String>,
    val save: String,
    val width: String,
    val height: String,
    val body: String
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
        var binds = ArrayList<String>()
        var save = ""
        var width = ""
        var height = ""
        val body = StringBuilder()
        var inHeader = false
        var started = false

        fun flush() {
            if (started) {
                passes += HookPass(desc, binds.toList(), save, width, height, body.toString().trim())
            }
            desc = ""; binds = ArrayList(); save = ""; width = ""; height = ""
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
                    "BIND" -> binds += value
                    "SAVE" -> save = value
                    "WIDTH" -> width = value
                    "HEIGHT" -> height = value
                }
            } else {
                inHeader = false
                if (started) body.append(raw).append('\n')
            }
        }
        flush()
        return passes.map { p ->
            p.copy(
                save = p.save.ifEmpty { "MAIN" },
                width = p.width.ifEmpty { "${p.binds.firstOrNull() ?: "MAIN"}.w" },
                height = p.height.ifEmpty { "${p.binds.firstOrNull() ?: "MAIN"}.h" }
            )
        }
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
            sb.append("#define ${name}_texOff(off) ${name}_tex(${name}_pos + ${name}_pt * (off))\n")
            sb.append("#define ${name}_mul 1.0\n")
        }
        if ("MAIN" in pass.binds) {
            for (suffix in listOf("raw", "size", "pt", "pos", "mul")) sb.append("#define HOOKED_$suffix MAIN_$suffix\n")
            sb.append("#define HOOKED_tex(pos) MAIN_tex(pos)\n#define HOOKED_texOff(off) MAIN_texOff(off)\n")
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
            ok = passes.isNotEmpty() && passes.last().save == "MAIN"
        } catch (e: Exception) {
            Log.w("HookUpscaler", "Shader konnte nicht gebaut werden", e)
            release()
        }
    }

    /** Ausgabegröße für eine Eingabe der Größe w×h (bei Anime4K x2: doppelt). */
    fun outputSize(w: Int, h: Int): IntArray {
        val sizes = HashMap<String, IntArray>()
        sizes["MAIN"] = intArrayOf(w, h)
        var out = intArrayOf(w, h)
        for (p in passes) {
            val s = intArrayOf(MpvHook.evalSize(p.width, sizes), MpvHook.evalSize(p.height, sizes))
            if (p.save == "MAIN") out = s else sizes[p.save] = s
        }
        return out
    }

    /** Rechnet [srcTex] (w×h) hoch und schreibt das Ergebnis in [outFbo] (Größe = [outputSize]). */
    fun run(srcTex: Int, w: Int, h: Int, outFbo: Int) {
        if (!ok) return
        val sizes = HashMap<String, IntArray>()
        val texIds = HashMap<String, Int>()
        sizes["MAIN"] = intArrayOf(w, h)
        texIds["MAIN"] = srcTex
        GLES20.glDisable(GLES20.GL_BLEND)
        passes.forEachIndexed { i, p ->
            val ow = MpvHook.evalSize(p.width, sizes)
            val oh = MpvHook.evalSize(p.height, sizes)
            val fbo = if (p.save == "MAIN") outFbo else target(p.save, ow, oh).fbo
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
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
            if (p.save != "MAIN") {
                sizes[p.save] = intArrayOf(ow, oh)
                texIds[p.save] = target(p.save, ow, oh).tex
            }
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
