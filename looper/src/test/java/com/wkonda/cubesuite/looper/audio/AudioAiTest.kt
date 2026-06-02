package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAiTest {

    @Test
    fun testBeatTracking() {
        val sampleRate = 48000
        val bpm = 120.0
        val beatInterval = (60.0 * sampleRate / bpm).toInt()
        val data = ShortArray(sampleRate * 2) // 2 seconds

        // Add clicks every beat
        for (i in 0 until 4) {
            val start = i * beatInterval
            for (j in 0 until 100) {
                if (start + j < data.size) data[start + j] = Short.MAX_VALUE
            }
        }

        val tracker = BeatTracker()
        val result = tracker.trackBeats(
            emptyList(),
            sampleRate,
            256
        ) // Use empty list for flux as it's not used in simplified beat tracking test

        // In the new logic, trackBeats uses flux. Let's create a synthetic flux.
        val flux = List((data.size / 256)) { i ->
            val sampleIdx = i * 256
            if (sampleIdx % beatInterval < 256) 1.0 else 0.0
        }

        val res = tracker.trackBeats(flux, sampleRate, 256)
        println("Detected BPM: ${res.bpm}")
        assertTrue("BPM should be near 120, got ${res.bpm}", res.bpm in 115.0..125.0)
    }

    @Test
    fun testChordDetection() {
        val sampleRate = 48000
        val freqC = 261.63 // C4
        val freqE = 329.63 // E4
        val freqG = 392.00 // G4

        val data = ShortArray(sampleRate) // 1 second
        for (i in data.indices) {
            val t = i.toDouble() / sampleRate
            val signal = sin(2 * PI * freqC * t) + sin(2 * PI * freqE * t) + sin(2 * PI * freqG * t)
            data[i] = (signal / 3.0 * Short.MAX_VALUE).toInt().toShort()
        }

        val analyzer = AudioAnalyzer(sampleRate)
        val result = analyzer.analyze(data, 0, data.size, 4)

        println("Detected Chords: ${result.chords.map { it.label }.distinct()}")
        assertTrue(
            "Should detect C chord, got ${result.chords.map { it.label }}",
            result.chords.any { it.label == "C" })
    }
}
