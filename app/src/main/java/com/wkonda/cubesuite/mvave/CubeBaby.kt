package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnection

class CubeBaby(private val connexionHandler: UsbConnection) {
    companion object {
        private val GET_SETTINGS_COMMAND =
            "F000320D410000400200000000000600004A01F7".hexToByteArray()
        private val SAVE_HEADER = byteArrayOf(0x00, 0x59, 0x22, 0x38, 0x00, 0x00)
    }

    suspend fun findAndOpen(context: Context) = connexionHandler.findAndOpen(context)

    suspend fun getCurrentSettings(): Map<Preset, Settings>? {
        return sendAndReceive(GET_SETTINGS_COMMAND)?.let(::parseSettings)
    }

    private fun parseSettings(buffer: ByteArray): Map<Preset, Settings>? {
        if (buffer.size < 74) return null
        val values = MidiEncoder.parseSevenBitValues(buffer.copyOfRange(17, buffer.size - 3))
        return Preset.entries.associateWith { preset ->
            Settings.parseFromValues(values.copyOfRange(preset.index * 16, (preset.index + 1) * 16))
        }
    }

    suspend fun send(preset: Preset, setting: Setting, value: Byte): Boolean {
        val id = (preset.index * 16 + setting.code).toByte()
        val totalCs = 2 * (377 - id - value)
        val message = byteArrayOf(
            0xF0.toByte(),//
            0x00, 0x32, 0x09, 0x49,//
            0x00, 0x00, 0x40, 0x02,//
            id, 0x00, 0x00, 0x00,//
            0x18, 0x00, 0x00, 0x00,//
            value,//
            (totalCs % 128).toByte(), (totalCs / 128).toByte(),//
            0xF7.toByte()
        )
        return sendAndReceive(message) != null
    }

    suspend fun save(presets: Map<Preset, Settings>): Boolean {
        val values = presets.values.flatMap { it.toBytes().toList() }.toByteArray()
        val payload = byteArrayOf(0x05, 0, 0, 0, 0, values.size.toByte(), 0, 0) + values
        val checksum = (payload.sum().inv() and 0xFF).toByte()
        val fullMsg = SAVE_HEADER + payload + byteArrayOf(checksum)
        val message =
            byteArrayOf(0xF0.toByte()) + MidiEncoder.fromSevenBitsValues(fullMsg) + byteArrayOf(0xF7.toByte())

        return sendAndReceive(message) != null
    }

    private suspend fun sendAndReceive(message: ByteArray): ByteArray? {
        if (!connexionHandler.send(message)) return null
        return connexionHandler.receive()
    }
}