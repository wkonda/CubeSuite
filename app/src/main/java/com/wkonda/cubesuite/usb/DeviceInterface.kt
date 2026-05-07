package com.wkonda.cubesuite.usb

import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

data class DeviceInterface(
    var usbInterface: UsbInterface? = null,
    var outEndpoint: UsbEndpoint? = null,
    var inEndpoint: UsbEndpoint? = null
) {
    fun isComplete(): Boolean  = inEndpoint!=null && outEndpoint!=null
}