package com.kitoptics.thermalview.rendering

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.kitoptics.thermalview.pipeline.FrameDistributor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "ThermalGLRenderer"
private const val CAMERA_WIDTH = 640
private const val CAMERA_HEIGHT = 480

// Fullscreen quad: position (x,y) + texcoord (u,v)
private val QUAD_COORDS = floatArrayOf(
    -1f, -1f,  0f, 1f,   // bottom-left
     1f, -1f,  1f, 1f,   // bottom-right
    -1f,  1f,  0f, 0f,   // top-left
     1f,  1f,  1f, 0f    // top-right
)

/**
 * OpenGL ES 3.2 renderer for thermal camera preview.
 * Uploads YUYV frames as RGBA8 texture → GLSL shader converts to RGB.
 *
 * GL thread only — never call from USB or UI thread.
 * Zero allocations in onDrawFrame().
 */
class ThermalGLRenderer(
    private val context: Context,
    private val distributor: FrameDistributor
) : GLSurfaceView.Renderer {

    private var shader: ShaderProgram? = null
    private var textureId: Int = 0
    private var vboId: Int = 0
    private var invertPalette: Float = 0f  // 0=White-hot, 1=Black-hot

    // Pre-allocated quad vertex buffer — no allocations in onDrawFrame
    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD_COORDS); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        shader = ShaderProgram(context)
        setupTexture()
        setupVbo()
        Log.i(TAG, "GL surface created")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Letterbox to maintain 4:3 aspect ratio
        val aspect = CAMERA_WIDTH.toFloat() / CAMERA_HEIGHT
        val screenAspect = width.toFloat() / height
        if (screenAspect > aspect) {
            val w = (height * aspect).toInt()
            val x = (width - w) / 2
            GLES30.glViewport(x, 0, w, height)
        } else {
            val h = (width / aspect).toInt()
            val y = (height - h) / 2
            GLES30.glViewport(0, y, width, h)
        }
        Log.i(TAG, "Surface changed: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val frame = distributor.getLatestFrame() ?: return

        // Upload YUYV data as RGBA8 texture (each RGBA texel = 2 YUYV pixels)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0,
            CAMERA_WIDTH / 2, CAMERA_HEIGHT,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            frame
        )

        val s = shader ?: return
        s.use()
        GLES30.glUniform1i(s.uTexture, 0)
        GLES30.glUniform1f(s.uInvertPalette, invertPalette)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(s.aPosition)
        GLES30.glVertexAttribPointer(s.aPosition, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(s.aTexCoord)
        GLES30.glVertexAttribPointer(s.aTexCoord, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Toggle between White-hot (false) and Black-hot (true). GL thread safe via queueEvent. */
    fun setBlackHot(blackHot: Boolean) {
        invertPalette = if (blackHot) 1f else 0f
    }

    private fun setupTexture() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // Allocate texture storage: CAMERA_WIDTH/2 texels wide (each texel = 2 YUYV pixels)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            CAMERA_WIDTH / 2, CAMERA_HEIGHT, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
    }

    private fun setupVbo() {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vboId = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            (QUAD_COORDS.size * 4),
            quadBuffer,
            GLES30.GL_STATIC_DRAW
        )
    }
}
