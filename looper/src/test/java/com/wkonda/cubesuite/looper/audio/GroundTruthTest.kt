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
        val result = analyzer.analyze(shortData, 0, shortData.size, 4)

        // User's provided bar positions
        val userBars =
            listOf(124596L, 269772L, 414275L, 558628L, 703504L, 847483L, 991385L, 1136637L)

        println("CAMFG80 Result:")
        println("  Detected Start: ${result.startSample}")
        println("  Detected End: ${result.endSample}")

        val expectedStart = 269772L
        val startTolerance = 48000 * 0.5 // 500ms (Relaxed per user request)
        assertTrue(
            "Start ${result.startSample} should be near second bar $expectedStart",
            abs(result.startSample - expectedStart) < startTolerance
        )

        // Verify the entire grid matches the user's provided timestamps
        userBars.filter { it >= result.startSample && it <= result.endSample }.forEach { userBar ->
            val closest = result.beatGrid.minByOrNull { abs(it - userBar) } ?: 0
            assertTrue(
                "Grid should have a line near user bar $userBar, closest was $closest",
                abs(closest - userBar) < startTolerance
            )
        }

        // Verify BPM precision < 0.05
        // CAMFG80: 4 bars * 4 beats = 16 beats.
        val calculatedBpm = (16.0 * 60.0 * 48000.0) / (result.endSample - result.startSample)
        println("  Calculated BPM: $calculatedBpm")
        assertTrue(
            "BPM should be near 80, precision < 0.05. Got $calculatedBpm",
            abs(calculatedBpm - 80.0) < 0.05
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
        // Simulate analysis from 0
        val result = analyzer.analyze(shortData, 0, shortData.size, 32)

        println("Folk Rock Long 124 Result:")
        println("  Detected Start: ${result.startSample}")
        println("  Detected End: ${result.endSample}")

        // Calculation for 8 bars (32 beats) at 124 BPM
        val numBeats = 32.0
        val actualDuration = (result.endSample - result.startSample).toDouble()
        val calculatedBpm = (numBeats * 60.0 * 48000.0) / actualDuration

        println("  Calculated BPM: $calculatedBpm")
        assertTrue(
            "BPM should be near 124, precision < 0.1. Got $calculatedBpm",
            abs(calculatedBpm - 124.0) < 0.1
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
        // User says loop is 4 bars (16 beats), 144 BPM.
        val result = analyzer.analyze(shortData, 0, shortData.size, 16)

        println("Fast Rock 144 Result:")
        println("  Detected Start: ${result.startSample}")
        println("  Detected End: ${result.endSample}")

        val numBeats = 16.0
        val actualDuration = (result.endSample - result.startSample).toDouble()
        val calculatedBpm = (numBeats * 60.0 * 48000.0) / actualDuration

        println("  Calculated BPM: $calculatedBpm")
        // User requested 144 +- 1 BPM, but previous precision was < 0.05. 
        // Let's aim for the highest precision possible.
        assertTrue("BPM should be near 144, got $calculatedBpm", abs(calculatedBpm - 144.0) < 1.0)
        assertTrue(
            "BPM precision must be high, got $calculatedBpm",
            abs(calculatedBpm - 144.0) < 0.05
        )
    }
}
