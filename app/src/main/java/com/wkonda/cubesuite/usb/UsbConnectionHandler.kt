package com.wkonda.cubesuite.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.wkonda.cubesuite.midi.MidiEncoder
import kotlin.math.min

class UsbConnectionHandler(
    private val manager: UsbManager
) {
    companion object {
        private const val USB_SUBCLASS_MIDI_STREAMING = 3
        private const val CUBE_BABY_VID = 12314
        private const val CUBE_BABY_PID = 21845
    }

    private val itf = DeviceInterface()
    private var connection: UsbDeviceConnection? = null

    suspend fun findAndOpen(context: Context): Boolean {
        val device = manager.deviceList.values.find {
            it.vendorId == CUBE_BABY_VID && it.productId == CUBE_BABY_PID
        } ?: return false

        if (!manager.hasPermission(device)) {
            @SuppressLint("MutableImplicitPendingIntent")
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                84455,
                Intent("com.wkonda.cubesuite.USB_PERMISSION"),
                PendingIntent.FLAG_MUTABLE
            )
            manager.requestPermission(device, permissionIntent)
            return false
        }

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == USB_SUBCLASS_MIDI_STREAMING) {
                itf.usbInterface = iface
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        itf.outEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_IN) {
                        itf.inEndpoint = ep
                    }
                }
                if (itf.isComplete()) break
            }
        }

        if (!itf.isComplete()) {
            return false
        }

        connection = manager.openDevice(device) ?: return false
        val claimed = connection?.claimInterface(itf.usbInterface, true) ?: false

        return claimed
    }


    fun close() {
        connection?.releaseInterface(itf.usbInterface)
        connection?.close()
        connection = null
    }

    suspend fun send(sysExMessage: ByteArray): Boolean {
        val message = MidiEncoder.sysExToUsb(sysExMessage)

        var offset = 0
        while (offset < message.size) {
            val length = min(message.size - offset, itf.outEndpoint?.maxPacketSize ?: 0)
            val transferred =
                connection?.bulkTransfer(itf.outEndpoint, message, offset, length, 10) ?: -1
            if (transferred < 0) return false
            offset += transferred
        }

        return true
    }

    suspend fun receive(): ByteArray? {
        val buffer = ByteArray(128)
        val receivedBytes = connection?.bulkTransfer(itf.inEndpoint, buffer, buffer.size, 100) ?: 0
        if (receivedBytes <= 0) return null

        return MidiEncoder.usbToSysEx(buffer, receivedBytes)
    }
}