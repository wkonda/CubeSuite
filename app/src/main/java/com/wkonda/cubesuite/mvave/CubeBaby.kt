package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnectionHandler

class CubeBaby(private val connexionHandler: UsbConnectionHandler) {
    companion object {
        private val getSettingsCommand = "F000320D410000400200000000000600004A01F7".hexToByteArray()
    }

    suspend fun getCurrentSettings(): Settings? {
        val sent = connexionHandler.send(getSettingsCommand)
        if (!sent) return null

        val received = connexionHandler.receive() ?: return null
        return parseSettings(received)
    }

    private fun parseSettings(buffer: ByteArray): Settings? {
        val values = MidiEncoder.parseSevenBitValues(buffer.copyOfRange(17, buffer.size - 3))
        val preset = 1
        val settings = values.copyOfRange(preset * 16, (preset + 1) * 16)
        return Settings.parseFromValues(settings)
    }

    suspend fun findAndOpen(context: Context): Boolean {
        return connexionHandler.findAndOpen(context)
    }

    suspend fun send(setting: Setting, value: Byte): Boolean {
        val m = getSysExMessage(setting.code, value)
        val sent = connexionHandler.send(m)
        if (!sent) return false

        connexionHandler.receive() ?: return false
        return true
    }

    private fun getSysExMessage(id: Byte, value: Byte): ByteArray {
        val totalCs = 2 * (377 - id - value)
        return byteArrayOf(
            0xF0.toByte(), //
            0x00, 0x32, 0x09, 0x49,//
            0x00, 0x00, 0x40, 0x02,//
            id,//
            0x00, 0x00, 0x00, 0x18,//
            0x00, 0x00, 0x00, value,//
            (totalCs % 128).toByte(), (totalCs / 128).toByte(),//
            0xF7.toByte()
        )
    }
}