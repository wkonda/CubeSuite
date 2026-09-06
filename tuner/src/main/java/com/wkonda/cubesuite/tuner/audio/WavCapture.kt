package com.wkonda.cubesuite.tuner.audio

import java.io.File
import java.io.RandomAccessFile

class WavCapture(
    private val file: File,
    private val sampleRate: Int,
    maxSamples: Int
) {
    private val targetSamples = maxSamples.coerceAtLeast(1)
    private val raf: RandomAccessFile
    private var writtenSamples = 0
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        writeHeader(0)
    }

    fun write(samples: ShortArray, count: Int): Boolean {
        if (closed) {
            return true
        }
        val usable = minOf(count, samples.size, targetSamples - writtenSamples)
        for (i in 0 until usable) {
            val value = samples[i].toInt()
            raf.write(value and 0xff)
            raf.write((value ushr 8) and 0xff)
        }
        writtenSamples += usable
        if (writtenSamples >= targetSamples) {
            close()
            return true
        }
        return false
    }

    fun close() {
        if (closed) {
            return
        }
        closed = true
        raf.seek(0)
        writeHeader(writtenSamples * 2)
        raf.close()
    }

    fun path(): String {
        return file.absolutePath
    }

    private fun writeHeader(dataSize: Int) {
        raf.writeBytes("RIFF")
        writeIntLE(36 + dataSize)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(16)
        writeShortLE(1)
        writeShortLE(1)
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * 2)
        writeShortLE(2)
        writeShortLE(16)
        raf.writeBytes("data")
        writeIntLE(dataSize)
    }

    private fun writeIntLE(value: Int) {
        raf.write(value and 0xff)
        raf.write((value ushr 8) and 0xff)
        raf.write((value ushr 16) and 0xff)
        raf.write((value ushr 24) and 0xff)
    }

    private fun writeShortLE(value: Int) {
        raf.write(value and 0xff)
        raf.write((value ushr 8) and 0xff)
    }
}
