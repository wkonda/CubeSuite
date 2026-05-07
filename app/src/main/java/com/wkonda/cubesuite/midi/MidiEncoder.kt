package com.wkonda.cubesuite.midi

import java.io.ByteArrayOutputStream

object MidiEncoder {
    fun sysExToUsb(sysExMsg: ByteArray, cableNumber: Int = 0): ByteArray {
        val result = ByteArrayOutputStream()
        val cablePrefix = cableNumber shl 4

        var i = 0
        while (i < sysExMsg.size) {
            val remaining = sysExMsg.size - i

            when {
                remaining == 1 -> {
                    result.write(cablePrefix or 0x05)
                    result.write(sysExMsg[i].toInt())
                    result.write(0x00)
                    result.write(0x00)
                    i += 1
                }

                remaining == 2 -> {
                    result.write(cablePrefix or 0x06)
                    result.write(sysExMsg[i].toInt())
                    result.write(sysExMsg[i + 1].toInt())
                    result.write(0x00)
                    i += 2
                }

                remaining >= 3 -> {
                    if (sysExMsg[i + 2] == 0xF7.toByte()) {
                        result.write(cablePrefix or 0x07)
                    } else {
                        result.write(cablePrefix or 0x04)
                    }
                    result.write(sysExMsg[i].toInt())
                    result.write(sysExMsg[i + 1].toInt())
                    result.write(sysExMsg[i + 2].toInt())
                    i += 3
                }
            }
        }
        return result.toByteArray()
    }

    fun usbToSysEx(usbMidiData: ByteArray, length: Int = usbMidiData.size): ByteArray {
        val result = ByteArrayOutputStream()

        for (i in 0 until length step 4) {
            if (i + 3 >= length) break

            val cin = usbMidiData[i].toInt() and 0x0F
            when (cin) {
                0x04 -> {
                    result.write(usbMidiData[i + 1].toInt())
                    result.write(usbMidiData[i + 2].toInt())
                    result.write(usbMidiData[i + 3].toInt())
                }

                0x05 -> {
                    result.write(usbMidiData[i + 1].toInt())
                }

                0x06 -> {
                    result.write(usbMidiData[i + 1].toInt())
                    result.write(usbMidiData[i + 2].toInt())
                }

                0x07 -> {
                    result.write(usbMidiData[i + 1].toInt())
                    result.write(usbMidiData[i + 2].toInt())
                    result.write(usbMidiData[i + 3].toInt())
                }
            }
        }
        return result.toByteArray()
    }

    fun parseSevenBitValues(buffer: ByteArray): ByteArray {
        val p = buffer.map { it.toInt() and 0xFF }
        val result = mutableListOf<Byte>()

        var i = 0
        while (true) {
            val m = i % 7
            val idx = i + i / 7

            if (idx + 1 >= p.size) break

            val value =
                ((p[idx] shr m) or
                        ((p[idx + 1] and ((1 shl (m + 1)) - 1)) shl (7 - m))) and 0x7F

            result.add(value.toByte())
            i++
        }

        return result.toByteArray()
    }
}