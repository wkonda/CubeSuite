package com.wkonda.cubesuite.mvave

import com.wkonda.cubesuite.midi.MidiEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MidiEncoderTest {
    @Test
    fun decodeTest() {
        val input =
            "0000001000601F00000000000000000000001010700000012A0844400000400001000000404040030000280100000000000004000000".hexToByteArray()
        val decoded = MidiEncoder.parseSevenBitValues(input)
        val expected =
            "00000002007F0000000000000000000004020700022A0411080000010100000004020700002A000000000000010000".hexToByteArray()
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun encodeTest() {
        val input =
            "00000002007F0000000000000000000004020700022A0411080000010100000004020700002A00000000000001000000".hexToByteArray()
        val decoded = MidiEncoder.fromSevenBitsValues(input)
        val expected =
            "0000001000601F00000000000000000000001010700000012A084440000040000100000040404003000028010000000000000400000000".hexToByteArray()
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun decodeGetSettingsCommand() {
        val getSettingsCommand = "00320D410000400200000000000600004A01".hexToByteArray()
        val values = MidiEncoder.parseSevenBitValues(getSettingsCommand)
        val expectedValues =
            "0059 23 08 0000 05 00000000 30 0000 4A".replace(" ", "").hexToByteArray()
        assertArrayEquals(expectedValues, values)
    }

    @Test
    fun encodeGainToFive() {
        val encoded =
            MidiEncoder.fromSevenBitsValues("005922090000050100000001000005F3".hexToByteArray())
        val expected = "00320949000040020100000010000000056603".hexToByteArray()
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun decodeSetSettingCommand() {
        val setGainCommand = "00320949000040020100000018000000056605".hexToByteArray()
        val values = MidiEncoder.parseSevenBitValues(setGainCommand)
        val expectedValues =
            "0059 22 09 0000 05 0100 000001 0000 05 73".replace(" ", "").hexToByteArray()
        assertArrayEquals(expectedValues, values)
    }

    @Test
    fun decodeSetSettingCommand2() {
        val setGainCommand = "00320949000040022700000018000000041c05".hexToByteArray()
        val values = MidiEncoder.parseSevenBitValues(setGainCommand)
        val expectedValues =
            "00 59 22 09 0000 05 27 000000 01 0000 04 4E".replace(" ", "").hexToByteArray()
        assertArrayEquals(expectedValues, values)
    }

    @Test
    fun decodeRealGainToFiveCommand() {
        val realCommand =
            "04f000320409490004004002040100000400180004000005076601f7".hexToByteArray()
        val sysExCommand = MidiEncoder.usbToSysEx(realCommand)
        val commandData = sysExCommand.copyOfRange(1, sysExCommand.size - 1)
        val expectedCommandData =
            byteArrayOf(0, 50, 9, 73, 0, 0, 64, 2, 1, 0, 0, 0, 24, 0, 0, 0, 5, 102, 1)
        assertArrayEquals(expectedCommandData, commandData)

        val decodedMessage = MidiEncoder.parseSevenBitValues(commandData)
        val expectedDecoded =
            "0059 22 09 0000 05 01 0000 00 01 0000 05 73".replace(" ", "").hexToByteArray()
        assertArrayEquals(expectedDecoded, decodedMessage)
    }
}