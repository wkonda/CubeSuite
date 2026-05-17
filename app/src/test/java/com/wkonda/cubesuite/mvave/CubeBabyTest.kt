package com.wkonda.cubesuite.mvave

import android.content.Context
import com.wkonda.cubesuite.usb.UsbConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeBabyTest {

    private class FakeUsbConnection : UsbConnection {
        var lastSent: ByteArray? = null
        var responseToReturn: ByteArray? = null
        var sendResult = true

        override suspend fun findAndOpen(context: Context): Boolean = true

        override suspend fun send(sysExMessage: ByteArray): Boolean {
            lastSent = sysExMessage
            return sendResult
        }

        override suspend fun receive(): ByteArray? = responseToReturn

        override fun close() {}
    }

    @Test
    fun `getCurrentSettings returns null when receive fails`() = runBlocking {
        val fake = FakeUsbConnection()
        fake.sendResult = false
        val cubeBaby = CubeBaby(fake)

        val settings = cubeBaby.getCurrentSettings()

        assertNull(settings)
    }

    @Test
    fun `getCurrentSettings returns null when buffer too small`() = runBlocking {
        val fake = FakeUsbConnection()
        fake.responseToReturn = ByteArray(70)
        val cubeBaby = CubeBaby(fake)

        val settings = cubeBaby.getCurrentSettings()

        assertNull(settings)
    }

    @Test
    fun `send correctly formats Gain change for Preset B`() = runBlocking {
        val fake = FakeUsbConnection()
        val cubeBaby = CubeBaby(fake)

        cubeBaby.send(Preset.B, Setting.GAIN, 7)

        assertNotNull(fake.lastSent)
        val msg = fake.lastSent!!
        val expected = "F000320949000040021100000018000000074205F7".hexToByteArray()
        assertArrayEquals(expected, msg)
    }

    @Test
    fun sendSettingACabSwOn() = runBlocking {
        val fake = FakeUsbConnection()
        val cubeBaby = CubeBaby(fake)

        cubeBaby.send(Preset.A, Setting.CAB_SW, 1)

        assertNotNull(fake.lastSent)
        val msg = fake.lastSent!!
        val expected = "F000320949000040020A00000018000000015C05F7".hexToByteArray()
        assertArrayEquals(expected, msg)
    }

    @Test
    fun `save sends correctly formatted message`() = runBlocking {
        val fake = FakeUsbConnection()
        val cubeBaby = CubeBaby(fake)

        // Minimal settings map
        val presets = Preset.entries.associateWith {
            Settings.parseFromValues(ByteArray(16))
        }
        fake.responseToReturn = "".hexToByteArray()
        val result = cubeBaby.save(presets)

        assertTrue(result)
        assertNotNull(fake.lastSent)
        val msg = fake.lastSent!!
        val expected =
            ("F00032094103004002000000000006" + "00".repeat(57) + "65F7").hexToByteArray()
        assertArrayEquals(expected, msg)
    }

    @Test
    fun `save with specific values matches expected message`() = runBlocking {
        val fake = FakeUsbConnection()
        val cubeBaby = CubeBaby(fake)

        val presets = mapOf(
            Preset.A to Settings(
                general = Module.General(127, 0, 1, 0),
                cab = Module.CAB(0, 2),
                mod = Module.MOD(0, 0, 0, 0),
                amp = Module.AMP(0, 0, 0)
            ), Preset.B to Settings(
                general = Module.General(42, 1, 0, 1),
                cab = Module.CAB(0, 0),
                mod = Module.MOD(17, 2, 4, 8),
                amp = Module.AMP(7, 2, 4)
            ), Preset.C to Settings(
                general = Module.General(42, 1, 0, 0),
                cab = Module.CAB(0, 0),
                mod = Module.MOD(0, 0, 0, 0),
                amp = Module.AMP(7, 2, 4)
            )
        )

        cubeBaby.save(presets)

        val expectedMessage =
            "F0003209410300400200000000000600000000001000601F00000000080000000000001010700000012A0844400000400001000000404040030000280100000000000004000000005CF7".hexToByteArray()

        assertArrayEquals(expectedMessage, fake.lastSent)
    }

    @Test
    fun getSettingsCommand() = runBlocking {
        val fake = FakeUsbConnection()
        val cubeBaby = CubeBaby(fake)
        cubeBaby.getCurrentSettings()
        val expectedToSend = "F000320D410000400200000000000600004A01F7".hexToByteArray()
        assertArrayEquals(expectedToSend, fake.lastSent)
    }
}
