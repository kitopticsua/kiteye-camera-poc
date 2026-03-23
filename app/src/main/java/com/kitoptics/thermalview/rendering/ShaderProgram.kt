package com.kitoptics.thermalview.rendering

import android.content.Context
import android.opengl.GLES30
import android.util.Log

private const val TAG = "ShaderProgram"

/**
 * Compiles and links GLSL vertex + fragment shaders.
 * Must be created on the GL thread.
 */
class ShaderProgram(context: Context) {

    private var programId: Int = 0

    // Uniform/attribute locations (cached after link)
    var uTexture: Int = 0; private set
    var uInvertPalette: Int = 0; private set
    var aPosition: Int = 0; private set
    var aTexCoord: Int = 0; private set

    init {
        val vertSrc = context.resources.openRawResource(
            context.resources.getIdentifier("thermal_vertex", "raw", context.packageName)
        ).bufferedReader().readText()

        val fragSrc = context.resources.openRawResource(
            context.resources.getIdentifier("thermal_fragment", "raw", context.packageName)
        ).bufferedReader().readText()

        programId = createProgram(vertSrc, fragSrc)
        cacheLocations()
    }

    fun use() = GLES30.glUseProgram(programId)

    fun delete() = GLES30.glDeleteProgram(programId)

    private fun cacheLocations() {
        uTexture = GLES30.glGetUniformLocation(programId, "uTexture")
        uInvertPalette = GLES30.glGetUniformLocation(programId, "uInvertPalette")
        aPosition = GLES30.glGetAttribLocation(programId, "aPosition")
        aTexCoord = GLES30.glGetAttribLocation(programId, "aTexCoord")
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vert)
        GLES30.glAttachShader(program, frag)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile error: $log")
            GLES30.glDeleteShader(shader)
        }
        return shader
    }
}
