package com.wkonda.cubesuite.looper.audio

import kotlin.math.pow
import kotlin.math.sqrt

data class ChordRegion(val startSample: Int, val endSample: Int, val label: String)

class ChordDetector {
    private val pitchNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val templates: Map<String, DoubleArray> by lazy {
        val map = mutableMapOf<String, DoubleArray>()
        for (i in 0..11) {
            map[pitchNames[i]] = createTemplate(listOf(i, (i + 4) % 12, (i + 7) % 12))
            map[pitchNames[i] + "m"] = createTemplate(listOf(i, (i + 3) % 12, (i + 7) % 12))
        }
        map
    }

    private fun createTemplate(semitones: List<Int>): DoubleArray {
        val t = DoubleArray(12)
        semitones.forEach { t[it] = 1.0 }
        return t
    }

    fun detectChords(spec: List<List<Double>>, sampleRate: Int, stepSize: Int): List<ChordRegion> {
        if (spec.isEmpty()) return emptyList()

        val chromas = spec.map { frame ->
            val chroma = DoubleArray(12) { 0.0 }
            frame.forEachIndexed { bin, db ->
                val midi = 36 + bin
                val pc = midi % 12
                val mag = 10.0.pow(db / 20.0)
                // Bass Weighting: MIDI 36-48 (index 0-12) gets more weight to resolve C/Am
                val weight = if (bin < 13) 2.5 else 1.0
                chroma[pc] += mag * weight
            }
            chroma
        }

        val smoothed = List(chromas.size) { i ->
            val c = DoubleArray(12) { 0.0 }
            val w = 9
            val start = (i - (w / 2)).coerceAtLeast(0)
            val end = (i + w / 2).coerceAtMost(chromas.size - 1)
            for (j in start..end) {
                for (k in 0 until 12) c[k] += chromas[j][k]
            }
            c
        }

        val frameLabels = smoothed.map { chroma ->
            var best = "None"
            var maxSim = -1.0
            templates.forEach { (name, temp) ->
                var dot = 0.0
                var magA = 0.0
                var magB = 0.0
                for (i in 0 until 12) {
                    dot += chroma[i] * temp[i]
                    magA += chroma[i] * chroma[i]
                    magB += temp[i] * temp[i]
                }
                val sim = dot / (sqrt(magA) * sqrt(magB)).coerceAtLeast(1e-9)
                if (sim > maxSim) {
                    maxSim = sim
                    best = name
                }
            }
            if (maxSim < 0.35) "None" else best
        }

        val regions = mutableListOf<ChordRegion>()
        if (frameLabels.isEmpty()) return regions

        var current = frameLabels[0]
        var startF = 0
        for (i in 1 until frameLabels.size) {
            if (frameLabels[i] != current) {
                if (current != "None") {
                    regions.add(ChordRegion(startF * stepSize, i * stepSize, current))
                }
                current = frameLabels[i]
                startF = i
            }
        }
        if (current != "None") regions.add(
            ChordRegion(
                startF * stepSize,
                frameLabels.size * stepSize,
                current,
            )
        )

        return regions.filter { (it.endSample - it.startSample) > sampleRate * 0.3 }
    }
}
