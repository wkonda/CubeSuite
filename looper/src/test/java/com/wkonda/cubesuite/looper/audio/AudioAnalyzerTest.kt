package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AudioAnalyzerTest {

    @Test
    fun testChordTransitionLooping() {
        val sampleRate = 48000
        // Need at least 25s of data for 10s loop + 10s context + padding
        val dataSize = sampleRate * 30
        val data = ShortArray(dataSize)

        // Pattern: C (4s) -> Am (4s) -> C (4s) -> Am (4s) -> C (4s)
        // This ensures the first transition is at 4s (Bar 2 equivalent)
        // and the repeat is at 18s.
        fun fill(start: Int, end: Int, freqs: List<Double>) {
            for (i in start until end) {
                var s = 0.0
                freqs.forEach { f -> s += sin(2.0 * Math.PI * f * i / sampleRate) }
                data[i] = (s / freqs.size * Short.MAX_VALUE * 0.5).toInt().toShort()
            }
        }

        val cMaj = listOf(261.63, 329.63, 392.00)
        val aMin = listOf(220.00, 261.63, 329.63)

        fill(0, sampleRate * 4, cMaj)
        fill(sampleRate * 4, sampleRate * 12, aMin)
        fill(sampleRate * 8, sampleRate * 18, cMaj)
        fill(sampleRate * 12, sampleRate * 24, aMin)
        fill(sampleRate * 16, dataSize, cMaj)

        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data, 6 * sampleRate, 4)

        // Loop Duration: 8s
        println("Synthetic Test Result:")
        println("  Start: ${result.startSample / sampleRate.toDouble()}s")
        println("  End: ${result.endSample / sampleRate.toDouble()}s")

        assertTrue(
            "End should be near 14s, got ${result.endSample / sampleRate.toDouble()}s",
            abs(result.endSample - 14 * sampleRate) < 50000
        )

        val duration = (result.endSample - result.startSample).toDouble() / sampleRate
        assertTrue("Duration should be near 8s, got ${duration}s", Math.abs(duration - 8.0) < 0.1)
    }
}
