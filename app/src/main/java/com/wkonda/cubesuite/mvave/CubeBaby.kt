package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.midi.MidiEncoder
import com.wkonda.cubesuite.usb.UsbConnection
import kotlin.experimental.or

class CubeBaby(private val connexionHandler: UsbConnection) {
    suspend fun findAndOpen(context: Context) = connexionHandler.findAndOpen(context)

    suspend fun getCurrentSettings(): Map<Preset, Settings>? {
        val request = buildCommand(CommandType.Read, Command.GetSettings, 5, len = 0x30)
        return sendAndReceive(request)?.let(::parseSettings)
    }

    suspend fun getDeviceName(): String? {
        val request = buildCommand(CommandType.RequestNameVersion)
        val response = sendAndReceive(request) ?: return null
        val values = MidiEncoder.parseSevenBitValues(response.copyOfRange(1, response.size - 1))
        if (values.size < 34 || values.size < values[3] + 6) return null
        val payload = values.copyOfRange(6, values[3] + 6)
        return String(payload.copyOf(16)).trim()
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
            command = Command.ChangeRamSetting,
            address = 5,
            index = (preset.index shl 4).toByte() or setting.code,
            flag = 0x80.toByte(),
            data = byteArrayOf(value)
        )
        return sendAndReceive(request) != null
    }

    suspend fun save(presets: Map<Preset, Settings>): Boolean {
        val values = presets.values.flatMap { it.toBytes().toList() }.toByteArray()
        val message = buildCommand(command = Command.SaveSettings, address = 5, data = values)
        return sendAndReceive(message) != null
    }

    enum class CommandType(val code: Byte) {
        Write(0x22), Read(0x23), RequestNameVersion(0x11)
    }

    enum class Command(val code: Byte) {
        ChangeRamSetting(0x09), GetSettings(0x08), SaveSettings(0x38)
    }

    private fun buildCommand(
        commandType: CommandType = CommandType.Write,
        command: Command? = null,
        address: Byte? = null,
        index: Byte = 0,
        flag: Byte = 0,
        data: ByteArray = byteArrayOf(),
        len: Int = data.size,
    ): ByteArray {
        val payload = (address?.let { byteArrayOf(it, index, 0, 0, flag) }
            ?: byteArrayOf()) + byteArrayOf(len.toByte(), 0, 0) + data
        val fullMsg =
            byteArrayOf(0x00, 0x59, commandType.code) + (command?.let { byteArrayOf(it.code, 0, 0) }
                ?: byteArrayOf()) + payload + payload.sum().inv().toByte()
        return byteArrayOf(0xF0.toByte()) + MidiEncoder.fromSevenBitsValues(fullMsg) + byteArrayOf(
            0xF7.toByte()
        )
    }

    private suspend fun sendAndReceive(message: ByteArray): ByteArray? {
        if (!connexionHandler.send(message)) return null
        return connexionHandler.receive()
    }
}