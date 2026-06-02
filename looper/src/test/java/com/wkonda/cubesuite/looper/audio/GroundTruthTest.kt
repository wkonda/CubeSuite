package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class GroundTruthTest {

    private val resourcesDir =
        File("/home/sid/AndroidStudioProjects/CubeSuite/looper/src/test/resources")

    @Test
    fun testCamfg80Precise() {
        val fileName = "1780154358141.pcm"
        val pcmFile = File(resourcesDir, fileName)
        if (!pcmFile.exists()) {
            println("Skipping testCamfg80Precise: File not found at ${pcmFile.absolutePath}")
            return
        }

        val bytes = pcmFile.readBytes()
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)

        val analyzer = AudioAnalyzer(48000)
        val result = analyzer.analyze(shortData, 0, 4)

        val calculatedBpm = (16.0 * 60.0 * 48000.0) / (result.endSample - result.startSample)
        println("  Calculated BPM: $calculatedBpm")
        assertTrue(
            "BPM should be near 80, precision < 0.02. Got $calculatedBpm",
            abs(calculatedBpm - 80.0) < 0.02
        )
    }

    @Test
    fun testFolkRockLong124() {
        val fileName = "1780250714235.pcm"
        val pcmFile = File(resourcesDir, fileName)
        if (!pcmFile.exists()) {
            println("Skipping testFolkRockLong124: File not found")
            return
        }

        val bytes = pcmFile.readBytes()
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)

        val analyzer = AudioAnalyzer(48000)
        val result = analyzer.analyze(shortData, 210000, 8)

        println("  Calculated BPM: ${result.bpm}")
        assertTrue(
            "BPM should be near 124, precision < 0.1. Got ${result.bpm}",
            abs(result.bpm - 124.0) < 0.1
        )
    }

    @Test
    fun testFastRock144() {
        val fileName = "1780251494594.pcm"
        val pcmFile = File(resourcesDir, fileName)
        if (!pcmFile.exists()) {
            println("Skipping testFastRock144: File not found")
            return
        }

        val bytes = pcmFile.readBytes()
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)

        val analyzer = AudioAnalyzer(48000)
        val result = analyzer.analyze(shortData, 210000, 4)

        println("Fast Rock 144 Result:")
        println("  Detected Start: ${result.startSample}")
        println("  Detected End: ${result.endSample}")

        println("  Calculated BPM: ${result.bpm}")
        assertTrue("BPM should be near 144, got ${result.bpm}", abs(result.bpm - 144.0) < 0.5)
    }
}
