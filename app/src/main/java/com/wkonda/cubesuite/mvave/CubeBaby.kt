package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnectionHandler

class CubeBaby(private val connexionHandler: UsbConnectionHandler) {
    companion object {
        private val getSettingsCommand = "F000320D410000400200000000000600004A01F7".hexToByteArray()
    }

    suspend fun getCurrentSettings(): Map<Preset, Settings>? {
        val sent = connexionHandler.send(getSettingsCommand)
        if (!sent) return null

        val received = connexionHandler.receive() ?: return null
        return parseSettings(received)
    }

    private fun parseSettings(buffer: ByteArray): Map<Preset, Settings>? {
        if (buffer.size < 74) return null
        val values = MidiEncoder.parseSevenBitValues(buffer.copyOfRange(17, buffer.size - 3))
        return Preset.entries.associateWith { preset ->
            Settings.parseFromValues(values.copyOfRange(preset.index * 16, (preset.index + 1) * 16))
        }
    }

    suspend fun findAndOpen(context: Context): Boolean {
        return connexionHandler.findAndOpen(context)
    }

    suspend fun send(preset: Preset, setting: Setting, value: Byte): Boolean {
        val m = getSysExMessage((preset.index * 16 + setting.code).toByte(), value)
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

    suspend fun save(presets: Map<Preset, Settings>): Boolean {
        val values: ByteArray = presets.values.flatMap { settings ->
            settings.toBytes().toList()
        }.toByteArray()
        val payload = byteArrayOf(
            0x05/*mem_type*/,
            0x00,
            0x00,
            0x00,
            0x00,
            0x30 /*mem_len*/,
            0x00,
            0x00
        ) + values
        val checksum = (payload.sum().inv()) and 0xFF
        val full8BitMessage = byteArrayOf(
            0x00,
            0x59,
            0x22,
            0x38,
            0x00,
            0x00
        ) + payload + byteArrayOf(checksum.toByte())
        val message =
            byteArrayOf(0xF0.toByte()) + MidiEncoder.fromSevenBitsValues(full8BitMessage) + byteArrayOf(
                0xF7.toByte()
            )

        val sent = connexionHandler.send(message)
        if (!sent) return false

        connexionHandler.receive() ?: return false
        return true
    }
}