/*
 * SPDX-FileCopyrightText: The hcam project
 * SPDX-License-Identifier: Apache-2.0
 *
 * GLES2 renderer for the recovered HTC effect shaders.
 * v1 implements Bypass and all four Vintage variants; the remaining effects
 * are added only after their uniform math has been recovered from
 * GEShader*::SetParam (see EFFECT-PARAMS.md), never invented.
 */

package org.lineageos.aperture.htc

import android.content.Context
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class HtcEffectRenderer(private val context: Context) {
    private var eglDisplay: android.opengl.EGLDisplay = android.opengl.EGL14.EGL_NO_DISPLAY
    private var eglContext: android.opengl.EGLContext = android.opengl.EGL14.EGL_NO_CONTEXT
    private var eglSurface: android.opengl.EGLSurface = android.opengl.EGL14.EGL_NO_SURFACE

    private val programs = HashMap<String, Program>()
    private val textures = HashMap<String, Int>()

    private data class Program(
        val id: Int,
        val vertexCoord: Int,
        val textureCoord: Int,
        val globalTexCoord: Int,
        val samplers: Map<String, Int>,
        val uniforms: Map<String, Int>,
        val yuvMatrix: Int,
    )

    init {
        initEgl()
    }

    private fun initEgl() {
        eglDisplay = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != android.opengl.EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val version = IntArray(2)
        check(android.opengl.EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "eglInitialize failed"
        }
        val configAttribs = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
            android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
            android.opengl.EGL14.EGL_RED_SIZE, 8,
            android.opengl.EGL14.EGL_GREEN_SIZE, 8,
            android.opengl.EGL14.EGL_BLUE_SIZE, 8,
            android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
            android.opengl.EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(
            android.opengl.EGL14.eglChooseConfig(
                eglDisplay, configAttribs, 0, configs, 0, 1, count, 0
            ) && count[0] > 0
        ) { "no EGL config" }

        eglContext = android.opengl.EGL14.eglCreateContext(
            eglDisplay, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT,
            intArrayOf(android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE), 0
        )
        check(eglContext != android.opengl.EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        eglSurface = android.opengl.EGL14.eglCreatePbufferSurface(
            eglDisplay, configs[0],
            intArrayOf(
                android.opengl.EGL14.EGL_WIDTH, 1,
                android.opengl.EGL14.EGL_HEIGHT, 1,
                android.opengl.EGL14.EGL_NONE,
            ), 0
        )
        check(eglSurface != android.opengl.EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        check(android.opengl.EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed"
        }
        val maxTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        android.util.Log.i("HtcRenderer", "GL_MAX_TEXTURE_SIZE=${maxTex[0]}")
    }

    /**
     * @param rgba packed input (r,g,b) = (Y,U,V), alpha ignored
     * @return RGBA effect output
     */
    fun render(
        effect: HtcEffect,
        rgba: ByteArray,
        width: Int,
        height: Int,
        vintage: VintageVariant = VintageVariant.CROSS,
        large: Boolean = false,
    ): ByteArray {
        require(rgba.size == width * height * 4) { "input size mismatch" }
        val passes = HtcPrograms.passes(effect, large)
        if (effect != HtcEffect.NONE && effect != HtcEffect.VINTAGE &&
            effect != HtcEffect.CONTRAST && effect != HtcEffect.BLOOM &&
            effect != HtcEffect.MIRROR && effect != HtcEffect.VIGNETTE
        ) {
            throw UnsupportedOperationException(
                "Effect ${effect.name} is not implemented yet: its SetParam math has not been ported"
            )
        }

        val inputTex = createTexture(width, height, rgba)
        val targetTextures = intArrayOf(
            createEmptyTexture(width, height),
            createEmptyTexture(width, height),
        )
        val targetFbos = intArrayOf(0, 0)
        GLES20.glGenFramebuffers(2, targetFbos, 0)

        var src = inputTex
        var lastFbo = -1
        for ((index, pass) in passes.withIndex()) {
            val slot = index % 2
            val dstTex = targetTextures[slot]
            val fbo = targetFbos[slot]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, dstTex, 0
            )
            check(
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
                    GLES20.GL_FRAMEBUFFER_COMPLETE
            ) { "framebuffer incomplete" }

            draw(pass, src, width, height, effect, vintage, index == 0)
            src = dstTex
            lastFbo = fbo
        }

        val out = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, lastFbo)
        GLES20.glReadPixels(
            0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, out
        )
        GLES20.glFinish()

        // cleanup
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(2, targetFbos, 0)
        GLES20.glDeleteTextures(3, intArrayOf(inputTex, targetTextures[0], targetTextures[1]), 0)

        val result = ByteArray(width * height * 4)
        out.position(0)
        out.get(result)
        return result
    }

    private fun draw(
        pass: HtcPass,
        srcTexture: Int,
        width: Int,
        height: Int,
        effect: HtcEffect,
        vintage: VintageVariant,
        firstPass: Boolean,
    ) {
        val program = programs.getOrPut("${pass.vertex}|${pass.fragment}") {
            buildProgram(pass)
        }
        GLES20.glUseProgram(program.id)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        val quad = floatArrayOf(
            -1f, -1f, 0f, 1f, 0f, 0f,
            1f, -1f, 0f, 1f, 1f, 0f,
            -1f, 1f, 0f, 1f, 0f, 1f,
            1f, 1f, 0f, 1f, 1f, 1f,
        )
        val buffer: FloatBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(quad)
                position(0)
            }

        val stride = 6 * 4
        GLES20.glEnableVertexAttribArray(program.vertexCoord)
        buffer.position(0)
        GLES20.glVertexAttribPointer(program.vertexCoord, 4, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(program.textureCoord)
        buffer.position(4)
        GLES20.glVertexAttribPointer(program.textureCoord, 2, GLES20.GL_FLOAT, false, stride, buffer)
        if (program.globalTexCoord >= 0) {
            GLES20.glEnableVertexAttribArray(program.globalTexCoord)
            buffer.position(4)
            GLES20.glVertexAttribPointer(
                program.globalTexCoord, 2, GLES20.GL_FLOAT, false, stride, buffer
            )
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTexture)
        program.samplers["s_texture0"]?.let { GLES20.glUniform1i(it, 0) }

        val curveTex: Int
        val vignetteTex: Int
        val wantsCurveMask = firstPass && when (effect) {
            HtcEffect.VINTAGE -> true
            HtcEffect.CONTRAST -> true
            else -> false
        }
        if (wantsCurveMask) {
            // Load lazy textures *before* binding the units below: texture()
            // changes the active texture unit to upload.
            val curveAsset: String
            val maskAsset: String
            if (effect == HtcEffect.VINTAGE) {
                curveAsset = vintage.curveAsset
                maskAsset = vintage.vignetteAsset
            } else {
                // GEShaderContrast::Init: CurveCross + aspect-matched mask.
                curveAsset = "CurveCross.png"
                val aspect = width.toFloat() / height
                maskAsset = if (kotlin.math.abs(aspect - 16f / 9f) < 0.05f) {
                    "mask_vignette_wide.png"
                } else {
                    "mask_vignette_legacy.png"
                }
            }
            curveTex = texture("curve:$curveAsset", curveAsset)
            vignetteTex = texture("vignette:$maskAsset", maskAsset)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curveTex)
            program.samplers["s_curve"]?.let { GLES20.glUniform1i(it, 1) }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, vignetteTex)
            program.samplers["s_vignette"]?.let { GLES20.glUniform1i(it, 2) }
            // Restore unit 0: texture() left it active.
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTexture)
        }

        if (program.yuvMatrix >= 0) {
            GLES20.glUniformMatrix4fv(program.yuvMatrix, 1, false, YUV_TO_RGB, 0)
        }

        if (effect == HtcEffect.VIGNETTE) {
            // GEShaderVignette::SetParam: strength -> inner radius,
            // geometry (530x370 on a 480x800 preview) -> outer radius.
            val inner = (100f - 70f) * 0.37f / 100f + 0.03f
            program.uniforms["u_center"]?.let { GLES20.glUniform2f(it, 0f, 0f) }
            program.uniforms["u_fRadius"]?.let { GLES20.glUniform2f(it, inner, 1.197f) }
            program.uniforms["u_fStrength"]?.let { GLES20.glUniform1f(it, inner) }
            program.uniforms["u_fScreenRatio"]?.let {
                GLES20.glUniform1f(it, width.toFloat() / height.toFloat())
            }
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun buildProgram(pass: HtcPass): Program {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, readShader(pass.vertex))
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, readShader(pass.fragment))
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vertex)
        GLES20.glAttachShader(id, fragment)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "link failed (${pass.vertex}/${pass.fragment}): ${GLES20.glGetProgramInfoLog(id)}"
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)

        val samplers = HashMap<String, Int>()
        for (name in listOf("s_texture0", "s_curve", "s_vignette", "s_dotMask")) {
            val loc = GLES20.glGetUniformLocation(id, name)
            if (loc >= 0) samplers[name] = loc
        }
        val uniforms = HashMap<String, Int>()
        for (name in listOf("u_center", "u_fRadius", "u_fStrength", "u_fScreenRatio")) {
            val loc = GLES20.glGetUniformLocation(id, name)
            if (loc >= 0) uniforms[name] = loc
        }
        return Program(
            id = id,
            vertexCoord = GLES20.glGetAttribLocation(id, "a_vertexCoord"),
            textureCoord = GLES20.glGetAttribLocation(id, "a_textureCoord"),
            globalTexCoord = GLES20.glGetAttribLocation(id, "a_globalTexCoord"),
            samplers = samplers,
            uniforms = uniforms,
            yuvMatrix = GLES20.glGetUniformLocation(id, "u_mat_yuv2rgb"),
        )
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}\n$source"
        }
        return shader
    }

    private fun readShader(name: String): String =
        context.assets.open("htc/shaders/$name").bufferedReader().use { it.readText() }

    private fun texture(key: String, asset: String): Int {
        return textures.getOrPut(key) {
            val bitmap = android.graphics.BitmapFactory.decodeStream(
                context.assets.open("htc/luts/$asset")
            )
            val w = bitmap.width
            val h = bitmap.height
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(buf)
            bitmap.recycle()
            // The original GLTexture::LoadBMP uploads the BMP bytes raw (BGR)
            // and the shaders compensate (e.g. Vintage reads sampled.b as red).
            // Swap R/B here so the shader math is used verbatim.
            var i = 0
            while (i < w * h * 4) {
                val r = buf.get(i)
                buf.put(i, buf.get(i + 2))
                buf.put(i + 2, r)
                i += 4
            }
            val id = createEmptyTexture(w, h)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            buf.position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
            )
            id
        }
    }

    private fun createTexture(width: Int, height: Int, data: ByteArray): Int {
        val id = createEmptyTexture(width, height)
        val buf = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
        buf.put(data)
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
        )
        return id
    }

    private fun createEmptyTexture(width: Int, height: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        return ids[0]
    }

    fun close() {
        for (p in programs.values) GLES20.glDeleteProgram(p.id)
        programs.clear()
        val ids = textures.values.toIntArray()
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        textures.clear()
        if (eglDisplay != android.opengl.EGL14.EGL_NO_DISPLAY) {
            android.opengl.EGL14.eglMakeCurrent(
                eglDisplay, android.opengl.EGL14.EGL_NO_SURFACE,
                android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT
            )
            android.opengl.EGL14.eglDestroySurface(eglDisplay, eglSurface)
            android.opengl.EGL14.eglDestroyContext(eglDisplay, eglContext)
            android.opengl.EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = android.opengl.EGL14.EGL_NO_DISPLAY
    }

    private companion object {
        // Full-range BT.601, exactly the constants embedded at 0xab54 in
        // libposteffect.so (1.402, -0.714, -0.344, 1.772, -0.701, 0.529, -0.886).
        val YUV_TO_RGB = floatArrayOf(
            1f, 1f, 1f, 0f,
            0f, -0.344f, 1.772f, 0f,
            1.402f, -0.714f, 0f, 0f,
            -0.701f, 0.529f, -0.886f, 1f,
        )
    }
}
