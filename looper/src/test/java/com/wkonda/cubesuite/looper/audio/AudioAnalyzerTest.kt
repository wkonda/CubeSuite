package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AudioAnalyzerTest {

    @Test
    fun testLoopingSearch() {
        val sampleRate = LooperConfig.SAMPLE_RATE
        val dataSize = sampleRate * 30
        val data = ShortArray(dataSize)

        fun fill(start: Int, end: Int, freqs: List<Double>) {
            for (i in start until end) {
                var s = 0.0
                freqs.forEach { f -> s += sin(2.0 * Math.PI * f * i / sampleRate) }
                data[i] = (s / freqs.size * Short.MAX_VALUE * 0.5).toInt().toShort()
            }
        }

        val f1 = listOf(261.63, 329.63, 392.00)
        val f2 = listOf(196.00, 246.94, 293.66)

        fill(sampleRate * 0, sampleRate * 4, f1)
        fill(sampleRate * 4, sampleRate * 8, f2)
        fill(sampleRate * 8, sampleRate * 12, f1)
        fill(sampleRate * 12, sampleRate * 16, f2)
        fill(sampleRate * 16, sampleRate * 20, f1)
        fill(sampleRate * 20, dataSize, f2)

        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data, 4 * sampleRate, 16)
        
        assertTrue(
            "End should be near 12s, got ${result.endSample / sampleRate.toDouble()}s",
            abs(result.endSample - 12 * sampleRate) < 50000
        )

        val duration = (result.endSample - result.startSample).toDouble() / sampleRate
        assertTrue("Duration should be near 8s, got ${duration}s", abs(duration - 8.0) < 0.2)
    }

    @Test
    fun testShortRecordingNoCrash() {
        val sampleRate = LooperConfig.SAMPLE_RATE
        val data = ShortArray(sampleRate / 10) // 100ms
        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data, 0, 16)
        assertTrue("Should not crash and return a result", result.endSample > 0)
    }
}
