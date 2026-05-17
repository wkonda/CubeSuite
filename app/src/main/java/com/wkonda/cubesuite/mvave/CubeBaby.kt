package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnection
import kotlin.experimental.or

class CubeBaby(private val connexionHandler: UsbConnection) {
    suspend fun findAndOpen(context: Context) = connexionHandler.findAndOpen(context)

    suspend fun getCurrentSettings(): Map<Preset, Settings>? {
        val request = buildCommand(Command.GetSettings, Direction.PedalToHost, 5, len = 0x30)
        return sendAndReceive(request)?.let(::parseSettings)
    }

    private fun parseSettings(buffer: ByteArray): Map<Preset, Settings>? {
        if (buffer.size < 74) return null
        val values = MidiEncoder.parseSevenBitValues(buffer.copyOfRange(1, buffer.size - 1))
        return Preset.entries.associateWith { preset ->
            Settings.parseFromValues(
                values.copyOfRange(
                    preset.index * 16 + 14, (preset.index + 1) * 16 + 14
                )
            )
        }
    }

    suspend fun send(preset: Preset, setting: Setting, value: Byte): Boolean {
        val request = buildCommand(
            Command.ChangeRamSetting,
            Direction.HostToPedal,
            5,
            (preset.index shl 4).toByte() or setting.code,
            0x80.toByte(),
            byteArrayOf(value)
        )
        return sendAndReceive(request) != null
    }

    suspend fun save(presets: Map<Preset, Settings>): Boolean {
        val values = presets.values.flatMap { it.toBytes().toList() }.toByteArray()
        val message = buildCommand(Command.SaveSettings, Direction.HostToPedal, 5, data = values)
        return sendAndReceive(message) != null
    }

    enum class Direction(val code: Byte) {
        HostToPedal(0x22), PedalToHost(0x23)
    }

    enum class Command(val code: Byte) {
        ChangeRamSetting(0x09), GetSettings(0x08), SaveSettings(0x38), RequestNameVersion(0x03),
    }

    private fun buildCommand(
        command: Command,
        direction: Direction = Direction.HostToPedal,
        address: Byte,
        index: Byte = 0,
        flag: Byte = 0,
        data: ByteArray = ByteArray(0),
        len: Int = data.size,
    ): ByteArray {
        val payload = byteArrayOf(address, index, 0, 0, flag, len.toByte(), 0, 0) + data
        val checksum = payload.sum().inv().toByte()
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