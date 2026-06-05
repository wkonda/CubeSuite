package com.wkonda.cubesuite.looper.audio

import kotlin.math.pow
import kotlin.math.sqrt

class ChromaAnalyzer {
    fun computeChromagram(spec: List<List<Double>>): List<List<Double>> {
        return spec.map { frame ->
            val chroma = DoubleArray(12) { 0.0 }
            val maxDb = frame.maxOrNull() ?: -100.0
            if (maxDb < -65.0) return@map List(12) { 0.0 }
            for (bin in 0 until 51) {
                val db = frame[bin]
                val pc = (36 + bin) % 12
                val weight = when {
                    bin < 14 -> 0.6
                    bin < 39 -> 2.0
                    else -> 0.3
                }
                if (db > maxDb - 10.0) {
                    chroma[pc] += 10.0.pow((db - maxDb) / 10.0) * weight
                }
            }
            val sharp = DoubleArray(12) { chroma[it].pow(2.5) }
            val norm = sqrt(sharp.sumOf { it * it }).coerceAtLeast(1e-9)
            List(12) { sharp[it] / norm }
        }
    }
}
