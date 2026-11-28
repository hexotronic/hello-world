package com.hexotronic.fluxplayer.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object GlUtil {

    class GlException(message: String) : RuntimeException(message)

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) throw GlException("glCreateShader fehlgeschlagen")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw GlException("Shader-Kompilierung fehlgeschlagen: $log")
        }
        return shader
    }

    fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        } catch (e: GlException) {
            GLES20.glDeleteShader(vs)
            throw e
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glBindAttribLocation(program, 0, "aPos")
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw GlException("Programm-Linken fehlgeschlagen: $log")
        }
        return program
    }

    fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    /** Unveränderliche RGBA8-Textur (optional mit Mipmap-Kette). */
    fun createStorageTexture(width: Int, height: Int, levels: Int, mipmapped: Boolean, linear: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, levels, GLES30.GL_RGBA8, width, height)
        val minFilter = when {
            mipmapped -> GLES20.GL_LINEAR_MIPMAP_LINEAR
            linear -> GLES20.GL_LINEAR
            else -> GLES20.GL_NEAREST
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, minFilter)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
            if (linear || mipmapped) GLES20.GL_LINEAR else GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    fun createFramebuffer(texture: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenFramebuffers(1, ids, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, ids, 0)
            throw GlException("Framebuffer unvollständig: 0x" + Integer.toHexString(status))
        }
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun deleteFramebuffer(id: Int) {
        if (id != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(id), 0)
    }

    fun deleteProgram(id: Int) {
        if (id != 0) GLES20.glDeleteProgram(id)
    }

    fun createQuadBuffer(): Int {
        val coords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val fb: FloatBuffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(coords).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, coords.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }
}
