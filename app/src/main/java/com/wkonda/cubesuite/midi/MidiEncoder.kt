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
        val resultSize = (buffer.size * 7 + 7) / 8
        val result = ByteArray(resultSize)
        for (i in 0 until resultSize) {
            val m = i % 7
            val idx = i + i / 7
            if (idx + 1 >= buffer.size) break

            val b0 = buffer[idx].toInt()
            val b1 = buffer[idx + 1].toInt()
            result[i] = (((b0 shr m) or (b1 shl (7 - m))) and 0x7F).toByte()
        }
        return result
    }

    fun fromSevenBitsValues(buffer: ByteArray): ByteArray {
        val resultSize = (buffer.size * 8) / 7
        val result = ByteArray(resultSize)
        for (i in 0 until resultSize) {
            val m = i % 8
            val idx = i - i / 8
            val bPrev = if (idx > 0) buffer[idx - 1].toInt() and 0xFF else 0
            val bCurr = if (idx < buffer.size) buffer[idx].toInt() and 0xFF else 0

            val value = when (m) {
                0 -> bCurr and 0x7F
                7 -> (bPrev shr 1) and 0x7F
                else -> ((bPrev shr (8 - m)) or (bCurr shl m)) and 0x7F
            }
            result[i] = value.toByte()
        }
        return result
    }
}