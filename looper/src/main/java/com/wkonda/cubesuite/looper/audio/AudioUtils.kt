package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AudioUtils {
    /**
     * Standard Fast Fourier Transform.
     */
    fun fft(complexData: DoubleArray) {
        val n = complexData.size / 2
        if (n <= 1) return
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = complexData[i * 2]; complexData[i * 2] =
                    complexData[j * 2]; complexData[j * 2] = tr
                val ti = complexData[i * 2 + 1]; complexData[i * 2 + 1] =
                    complexData[j * 2 + 1]; complexData[j * 2 + 1] = ti
            }
            var bit = n shr 1
            while (j >= bit && bit > 0) {
                j -= bit; bit = bit shr 1
            }
            j += bit
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlr = cos(ang)
            val wli = sin(ang)
            for (i in 0 until n step len) {
                var wr = 1.0;
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val u = (i + k) * 2;
                    val v = (i + k + len / 2) * 2
                    val tr = wr * complexData[v] - wi * complexData[v + 1]
                    val ti = wr * complexData[v + 1] + wi * complexData[v]
                    complexData[v] = complexData[u] - tr; complexData[v + 1] =
                        complexData[u + 1] - ti
                    complexData[u] += tr; complexData[u + 1] += ti
                    val nwr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr; wr = nwr
                }
            }
            len = len shl 1
        }
    }

    fun findNearestZeroCrossing(data: ShortArray, index: Int): Int {
        val range = LooperConfig.ZERO_CROSSING_SEARCH_RANGE
        var bestIdx = index
        var minDistance = Int.MAX_VALUE
        for (offset in -range..range) {
            val curr = index + offset
            if (curr < 0 || curr >= data.size - 1) continue
            if ((data[curr] <= 0 && data[curr + 1] > 0) || (data[curr] >= 0 && data[curr + 1] < 0)) {
                if (abs(offset) < minDistance) {
                    minDistance = abs(offset)
                    bestIdx = curr
                }
            }
        }
        return bestIdx
    }

    fun calculateSpectralFlux(spec: List<List<Double>>): List<Double> {
        return List(spec.size) { t ->
            if (t == 0) 0.0 else {
                var flux = 0.0
                for (b in spec[t].indices) {
                    val diff = spec[t][b] - spec[t - 1][b]
                    if (diff > 0) flux += diff
                }
                flux
            }
        }
    }

    fun findOnsetPeaks(
        flux: List<Double>,
        totalSamples: Int = 0,
        numBeats: Int = 4
    ): List<Int> {
        if (flux.isEmpty()) return emptyList()
        val threshold = flux.average() * 4.0
        val minGap = 25

        val rawCandidates = mutableListOf<Pair<Int, Double>>()
        var lastPeakIdx = -minGap

        for (i in 1 until flux.size - 1) {
            if (flux[i] > threshold && flux[i] > flux[i - 1] && flux[i] > flux[i + 1]) {
                if (i - lastPeakIdx >= minGap) {
                    rawCandidates.add(i to flux[i])
                    lastPeakIdx = i
                } else if (flux[i] > rawCandidates.last().second) {
                    rawCandidates[rawCandidates.size - 1] = i to flux[i]
                    lastPeakIdx = i
                }
            }
        }

        return rawCandidates.sortedByDescending { it.second }.take(40).map { it.first }.sorted()
    }

    /**
     * Normalized Cross-Correlation (NCC) for maximum similarity accuracy.
     */
    fun calculateRawSimilarity(
        data: ShortArray,
        posA: Int,
        posB: Int,
        length: Int,
        step: Int = 1
    ): Double {
        if (posA < 0 || posB < 0 || posA + length > data.size || posB + length > data.size) return 0.0

        var sumA = 0.0;
        var sumB = 0.0
        var count = 0
        for (i in 0 until length step step) {
            sumA += data[posA + i]; sumB += data[posB + i]
            count++
        }
        val meanA = sumA / count;
        val meanB = sumB / count

        var dot = 0.0;
        var varA = 0.0;
        var varB = 0.0
        for (i in 0 until length step step) {
            val vA = data[posA + i] - meanA
            val vB = data[posB + i] - meanB
            dot += vA * vB; varA += vA * vA; varB += vB * vB
        }

        val den = sqrt(varA) * sqrt(varB)
        return if (den > 0) dot / den else 0.0
    }

    fun calculateEnvelopeSimilarity(
        data: ShortArray,
        posA: Int,
        posB: Int,
        length: Int,
        step: Int = 1024
    ): Double {
        if (posA < 0 || posB < 0 || posA + length > data.size || posB + length > data.size) return 0.0
        var dot = 0.0;
        var n1 = 0.0;
        var n2 = 0.0
        for (i in 0 until length step step) {
            val v1 = abs(data[posA + i].toDouble())
            val v2 = abs(data[posB + i].toDouble())
            dot += v1 * v2; n1 += v1 * v1; n2 += v2 * v2
        }
        return if (n1 > 0 && n2 > 0) dot / (sqrt(n1) * sqrt(n2)) else 0.0
    }
}
