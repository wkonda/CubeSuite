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
            "00000002007F0000000000000000000004020700022A0411080000010100000004020700002A00000000000001000000".hexToByteArray()
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun encodeTest() {
        val input =
            "00000002007F0000000000000000000004020700022A0411080000010100000004020700002A00000000000001000000".hexToByteArray()
        val decoded = MidiEncoder.fromSevenBitsValues(input)
        val expected =
            "0000001000601F00000000000000000000001010700000012A0844400000400001000000404040030000280100000000000004000000".hexToByteArray()
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun headerExtraction() {
        val input = "00320949000040020A00000018000000015C05".hexToByteArray()
        val decoded = MidiEncoder.parseSevenBitValues(input)
        //cubeBaby.send(Preset.A, Setting.CAB_SW, 1)
        // SAVE_HEADER = byteArrayOf(0x00, 0x59, 0x22, 0x38, 0x00, 0x00)
        val expected =
            "005922 09 0000 05 0A 000000 01 0000 01 6E00".replace(" ", "").hexToByteArray()
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun headerExtraction2() {
        val input = "00320D410000400200000000000600004A01".hexToByteArray()
        val decoded = MidiEncoder.parseSevenBitValues(input)
        val expected = "005923 08 0000 05 00 000000 30 0000 4a00".replace(" ", "").hexToByteArray()
        assertArrayEquals(expected, decoded)
    }
}