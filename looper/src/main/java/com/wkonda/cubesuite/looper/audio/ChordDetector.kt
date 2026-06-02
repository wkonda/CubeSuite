package com.wkonda.cubesuite.looper.audio

import kotlin.math.pow
import kotlin.math.sqrt

data class ChordRegion(val startSample: Int, val endSample: Int, val label: String)

class ChordDetector {
    private val pitchNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val templates: Map<String, DoubleArray> by lazy {
        val map = mutableMapOf<String, DoubleArray>()
        for (i in 0..11) {
            // Simple Root, 3rd, 5th templates
            map[pitchNames[i]] = createTemplate(listOf(i, (i + 4) % 12, (i + 7) % 12))
            map[pitchNames[i] + "m"] = createTemplate(listOf(i, (i + 3) % 12, (i + 7) % 12))
        }
        map
    }

    private fun createTemplate(semitones: List<Int>): DoubleArray {
        val t = DoubleArray(12)
        semitones.forEach { t[it] = 1.0 }
        val norm = sqrt(t.sumOf { it * it })
        return DoubleArray(12) { t[it] / norm }
    }

    fun detectChords(spec: List<List<Double>>, sampleRate: Int, stepSize: Int): List<ChordRegion> {
        if (spec.isEmpty()) return emptyList()

        // 1. Basic Chromagram Calculation
        val chromas = spec.map { frame ->
            val chroma = DoubleArray(12) { 0.0 }
            val maxDb = frame.maxOrNull() ?: -100.0
            
            frame.forEachIndexed { bin, db ->
                val pc = (36 + bin) % 12
                if (db > maxDb - 30.0) {
                    val mag = 10.0.pow((db - maxDb) / 20.0)
                    chroma[pc] += mag
                }
            }

            val norm = sqrt(chroma.sumOf { it * it }).coerceAtLeast(1e-9)
            DoubleArray(12) { i -> chroma[i] / norm }
        }

        // 2. Simple Temporal Smoothing (0.8s window)
        val smoothed = List(chromas.size) { i ->
            val c = DoubleArray(12) { 0.0 }
            val window = 37
            val start = (i - (window / 2)).coerceAtLeast(0)
            val end = (i + window / 2).coerceAtMost(chromas.size - 1)
            for (j in start..end) for (k in 0 until 12) c[k] += chromas[j][k]
            val norm = sqrt(c.sumOf { it * it }).coerceAtLeast(1e-9)
            DoubleArray(12) { k -> c[k] / norm }
        }

        // 3. Max Similarity Template Matching
        val frameLabels = smoothed.map { chroma ->
            var best = "None";
            var maxSim = -1.0
            templates.forEach { (name, temp) ->
                var dot = 0.0
                for (i in 0 until 12) dot += chroma[i] * temp[i]
                if (dot > maxSim) {
                    maxSim = dot; best = name
                }
            }
            if (maxSim < 0.5) "None" else best
        }

        // 4. Region Extraction
        val regions = mutableListOf<ChordRegion>()
        if (frameLabels.isEmpty()) return regions
        var current = frameLabels[0];
        var startF = 0
        for (i in 1 until frameLabels.size) {
            if (frameLabels[i] != current) {
                if (current != "None") regions.add(
                    ChordRegion(
                        startF * stepSize,
                        i * stepSize,
                        current
                    )
                )
                current = frameLabels[i]; startF = i
            }
        }
        if (current != "None") regions.add(
            ChordRegion(
                startF * stepSize,
                frameLabels.size * stepSize,
                current
            )
        )

        return regions.filter { (it.endSample - it.startSample) > sampleRate * 0.7 }
    }
}
