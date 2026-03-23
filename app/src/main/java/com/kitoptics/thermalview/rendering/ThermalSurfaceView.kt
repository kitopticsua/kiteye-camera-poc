package com.kitoptics.thermalview.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.kitoptics.thermalview.pipeline.FrameDistributor

/**
 * GLSurfaceView configured for OpenGL ES 3.2.
 * Hosts [ThermalGLRenderer] for thermal camera preview.
 */
class ThermalSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private lateinit var thermalRenderer: ThermalGLRenderer

    fun init(distributor: FrameDistributor) {
        setEGLContextClientVersion(3)  // OpenGL ES 3.2
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        thermalRenderer = ThermalGLRenderer(context, distributor)
        setRenderer(thermalRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Toggle palette — queued to GL thread */
    fun setBlackHot(blackHot: Boolean) {
        queueEvent { thermalRenderer.setBlackHot(blackHot) }
    }
}
