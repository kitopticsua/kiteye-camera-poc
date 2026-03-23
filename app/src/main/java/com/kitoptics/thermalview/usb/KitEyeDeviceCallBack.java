package com.kitoptics.thermalview.usb;

import android.hardware.usb.UsbDevice;
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack;
import com.serenegiant.usb.USBMonitor;

/**
 * Java base class implementing IDeviceConnectCallBack with no-op defaults.
 *
 * This exists because Kotlin 2.0 K2 compiler cannot override methods from interfaces
 * compiled with Kotlin 1.5 metadata (AUSBC 3.2.7) — this Java class bypasses the
 * metadata issue entirely, since Java reads bytecode directly.
 */
public abstract class KitEyeDeviceCallBack implements IDeviceConnectCallBack {
    @Override public void onAttachDev(UsbDevice device) {}
    @Override public void onDetachDec(UsbDevice device) {}
    @Override public void onCancelDev(UsbDevice device) {}
    @Override public void onConnectDev(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {}
    @Override public void onDisConnectDec(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {}
}
