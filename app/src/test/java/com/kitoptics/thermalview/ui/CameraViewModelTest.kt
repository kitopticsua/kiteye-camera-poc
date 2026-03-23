package com.kitoptics.thermalview.ui

import com.kitoptics.thermalview.usb.UsbCameraState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        viewModel = CameraViewModel()
    }

    // UT-01: Initial state is Disconnected
    @Test
    fun `initial state is Disconnected`() = runTest {
        val state = viewModel.cameraState.first()
        assertEquals(UsbCameraState.Disconnected, state)
    }

    // UT-02: onPermissionGranted() → state transitions away from Disconnected
    @Test
    fun `onPermissionGranted transitions state to Connecting`() = runTest {
        viewModel.onDeviceDetected()   // must first detect device
        viewModel.onPermissionGranted()
        val state = viewModel.cameraState.first()
        assertEquals(UsbCameraState.Connecting, state)
    }

    // UT-03: onPermissionDenied() → Error state
    @Test
    fun `onPermissionDenied transitions to Error state`() = runTest {
        viewModel.onDeviceDetected()
        viewModel.onPermissionDenied()
        val state = viewModel.cameraState.first()
        assertTrue(state is UsbCameraState.Error)
        assertEquals("USB permission denied", (state as UsbCameraState.Error).message)
    }

    // UT-04: onCameraDisconnected() → Disconnected from any state
    @Test
    fun `onCameraDisconnected resets to Disconnected`() = runTest {
        viewModel.onDeviceDetected()
        viewModel.onPermissionGranted()
        viewModel.onCameraDisconnected()
        val state = viewModel.cameraState.first()
        assertEquals(UsbCameraState.Disconnected, state)
    }

    // UT-05: togglePalette() flips between White-hot and Black-hot
    @Test
    fun `togglePalette flips between white-hot and black-hot`() {
        assertFalse(viewModel.isBlackHot)   // default White-hot
        viewModel.togglePalette()
        assertTrue(viewModel.isBlackHot)
        viewModel.togglePalette()
        assertFalse(viewModel.isBlackHot)
    }
}
