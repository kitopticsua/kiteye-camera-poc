package com.kitoptics.thermalview.usb

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UvcDeviceManagerTest {

    private lateinit var manager: UvcDeviceManager

    @Before
    fun setUp() {
        manager = UvcDeviceManager()
    }

    // UT-01: Initial state is Disconnected
    @Test
    fun `initial state is Disconnected`() = runTest {
        val state = manager.state.first()
        assertEquals(UsbCameraState.Disconnected, state)
    }

    // UT-02: Device attached with correct VID/PID → RequestingPermission
    @Test
    fun `device attached with correct VID PID transitions to RequestingPermission`() = runTest {
        manager.onDeviceAttached(vendorId = KITEYE_VID, productId = KITEYE_PID)
        val state = manager.state.first()
        assertEquals(UsbCameraState.RequestingPermission, state)
    }

    // UT-03: Permission granted → Connecting
    @Test
    fun `permission granted transitions to Connecting`() = runTest {
        manager.onDeviceAttached(vendorId = KITEYE_VID, productId = KITEYE_PID)
        manager.onPermissionGranted()
        val state = manager.state.first()
        assertEquals(UsbCameraState.Connecting, state)
    }

    // UT-04: Permission denied → Error
    @Test
    fun `permission denied transitions to Error`() = runTest {
        manager.onDeviceAttached(vendorId = KITEYE_VID, productId = KITEYE_PID)
        manager.onPermissionDenied()
        val state = manager.state.first()
        assertTrue(state is UsbCameraState.Error)
        assertEquals("USB permission denied", (state as UsbCameraState.Error).message)
    }

    // UT-05: Device detached from any state → Disconnected
    @Test
    fun `device detached from Connecting state transitions to Disconnected`() = runTest {
        manager.onDeviceAttached(vendorId = KITEYE_VID, productId = KITEYE_PID)
        manager.onPermissionGranted()
        manager.onDeviceDetached()
        val state = manager.state.first()
        assertEquals(UsbCameraState.Disconnected, state)
    }

    // UT-06: Invalid VID/PID not detected — state stays Disconnected
    @Test
    fun `device attached with wrong VID PID stays Disconnected`() = runTest {
        manager.onDeviceAttached(vendorId = 0x1234, productId = 0x5678)
        val state = manager.state.first()
        assertEquals(UsbCameraState.Disconnected, state)
    }
}
