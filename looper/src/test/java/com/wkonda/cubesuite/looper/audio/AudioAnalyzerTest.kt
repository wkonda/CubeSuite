package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioAnalyzerTest {

    @Test
    fun testBpmDetectionWithSyntheticPulses() {
        val sampleRate = 48000
        val bpm = 120.0
        val seconds = 4.0
        val dataSize = (sampleRate * seconds).toInt()
        val data = ShortArray(dataSize)

        val samplesPerBeat = (60.0 / bpm * sampleRate).toInt()

        // Add pulses every beat
        for (i in 0 until dataSize step samplesPerBeat) {
            // Pulse of 50ms
            val pulseSamples = (0.05 * sampleRate).toInt()
            for (j in 0 until pulseSamples) {
                if (i + j < dataSize) {
                    data[i + j] =
                        (Short.MAX_VALUE * 0.8 * sin(2.0 * Math.PI * 440.0 * j / sampleRate)).toInt()
                            .toShort()
                }
            }
        }

        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data)

        // We expect roughly 120 BPM
        assertEquals(120.0, result.bpm, 5.0)

        // Start sample should be near 0
        assertTrue(
            "Start sample should be near 0, but was ${result.startSample}",
            result.startSample < 500
        )

        // End sample for 4 bars (16 beats)
        val expectedLoopSamples = samplesPerBeat * 16
        assertEquals(
            expectedLoopSamples.toDouble(),
            (result.endSample - result.startSample).toDouble(),
            samplesPerBeat.toDouble()
        )

        // Verify onsets are detected
        assertTrue("Should detect multiple onsets", result.onsets.size > 5)
    }

    @Test
    fun testBpmDetectionWithSilentAudio() {
        val data = ShortArray(48000 * 2) // 2 seconds of silence
        val analyzer = AudioAnalyzer(48000)
        val result = analyzer.analyze(data)

        // Should return default BPM
        assertEquals(120.0, result.bpm, 0.1)
        assertEquals(0, result.startSample)
    }
}
