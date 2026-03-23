package com.kitoptics.thermalview.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FpsCounterTest {

    private lateinit var counter: FpsCounter

    @Before
    fun setUp() {
        counter = FpsCounter(windowSize = 30)
    }

    // UT-01: 30 frames in 1s → ~30fps
    @Test
    fun `30 frames in 1 second gives 30 fps`() {
        val startNs = 0L
        val intervalNs = 1_000_000_000L / 30  // 33.3ms per frame
        repeat(30) { i ->
            counter.onFrame(startNs + i * intervalNs)
        }
        val fps = counter.getFps()
        assertEquals(30.0f, fps, 2.0f)  // ±2fps tolerance
    }

    // UT-02: 60 frames in 1s → ~60fps
    @Test
    fun `60 frames in 1 second gives 60 fps`() {
        val startNs = 0L
        val intervalNs = 1_000_000_000L / 60  // 16.67ms per frame
        repeat(60) { i ->
            counter.onFrame(startNs + i * intervalNs)
        }
        val fps = counter.getFps()
        assertEquals(60.0f, fps, 2.0f)
    }

    // UT-03: reset → 0.0 fps
    @Test
    fun `reset returns 0 fps`() {
        val intervalNs = 1_000_000_000L / 60
        repeat(10) { i -> counter.onFrame(i * intervalNs) }
        counter.reset()
        assertEquals(0.0f, counter.getFps(), 0.01f)
    }
}
