package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioAnalyzerTest {

    @Test
    fun testChordTransitionLooping() {
        val sampleRate = 48000
        // Need at least 25s of data for 10s loop + 10s context + padding
        val dataSize = sampleRate * 30
        val data = ShortArray(dataSize)

        // Pattern: C (6s) -> Am (6s) -> C (6s) -> Am (6s) -> C (6s)
        // This ensures the first transition is at 6s (Bar 2 equivalent)
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

        fill(0, sampleRate * 6, cMaj)
        fill(sampleRate * 6, sampleRate * 12, aMin)
        fill(sampleRate * 12, sampleRate * 18, cMaj)
        fill(sampleRate * 18, sampleRate * 24, aMin)
        fill(sampleRate * 24, dataSize, cMaj)

        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data, 0, data.size, 4)

        // Expected: Start at first C->Am transition (6s)
        // Expected: End at second C->Am transition (18s)
        // Loop Duration: 12s
        println("Synthetic Test Result:")
        println("  Start: ${result.startSample / sampleRate.toDouble()}s")
        println("  End: ${result.endSample / sampleRate.toDouble()}s")

        assertTrue(
            "Start should be near 6s, got ${result.startSample / sampleRate.toDouble()}s",
            Math.abs(result.startSample - 6 * sampleRate) < 50000
        )

        assertTrue(
            "End should be near 18s, got ${result.endSample / sampleRate.toDouble()}s",
            Math.abs(result.endSample - 18 * sampleRate) < 50000
        )

        val duration = (result.endSample - result.startSample).toDouble() / sampleRate
        assertTrue("Duration should be near 12s, got ${duration}s", Math.abs(duration - 12.0) < 0.1)
    }
}
