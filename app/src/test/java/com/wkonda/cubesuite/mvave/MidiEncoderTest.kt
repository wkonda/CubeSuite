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
}