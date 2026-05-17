package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnection

class CubeBaby(private val connexionHandler: UsbConnection) {
    suspend fun findAndOpen(context: Context) = connexionHandler.findAndOpen(context)

    companion object {
        private val GET_SETTINGS_COMMAND =
            "F000320D410000400200000000000600004A01F7".hexToByteArray()
    }
    suspend fun getCurrentSettings(): Map<Preset, Settings>? {
        return sendAndReceive(GET_SETTINGS_COMMAND)?.let(::parseSettings)
    }

    suspend fun getCurrentSettingsNew(): Map<Preset, Settings>? {
        val request =
            buildCommand(Command.GetSettings, Direction.PedalToHost, 5, len = 0x30) + byteArrayOf(0)
        return sendAndReceive(request)?.let(::parseSettings)
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
        val message = buildCommand(Command.DumpSettings, Direction.HostToPedal, 5, values)
        return sendAndReceive(message) != null
    }

    enum class Direction(val code: Byte) {
        HostToPedal(0x22), PedalToHost(0x23)
    }

    enum class Command(val code: Byte) {
        ChangeRamSetting(0x09), GetSettings(0x08), DumpSettings(0x38), RequestNameVersion(0x03),
    }

    private fun buildCommand(
        command: Command,
        direction: Direction,
        address: Byte,
        data: ByteArray = ByteArray(0),
        len: Int = data.size,
    ): ByteArray {
        val payload = byteArrayOf(address, 0, 0, 0, 0, len.toByte(), 0, 0) + data
        val checksum = (payload.sum().inv()).toByte()
        val fullMsg = byteArrayOf(
            0x00, 0x59, direction.code, command.code, 0x00, 0x00
        ) + payload + byteArrayOf(checksum)
        return byteArrayOf(0xF0.toByte()) + MidiEncoder.fromSevenBitsValues(fullMsg) + byteArrayOf(
            0xF7.toByte()
        )
    }

    private suspend fun sendAndReceive(message: ByteArray): ByteArray? {
        if (!connexionHandler.send(message)) return null
        return connexionHandler.receive()
    }
}