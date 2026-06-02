package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AudioUtils {
    fun fft(complexData: DoubleArray) {
        val n = complexData.size / 2; if (n <= 1) return
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = complexData[i * 2]; complexData[i * 2] =
                    complexData[j * 2]; complexData[j * 2] = tr
                val ti = complexData[i * 2 + 1]; complexData[i * 2 + 1] =
                    complexData[j * 2 + 1]; complexData[j * 2 + 1] = ti
            }
            var bit = n shr 1; while (j >= bit && bit > 0) {
                j -= bit; bit = bit shr 1
            }; j += bit
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len;
            val wlr = cos(ang);
            val wli = sin(ang)
            for (i in 0 until n step len) {
                var wr = 1.0;
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val u = (i + k) * 2;
                    val v = (i + k + len / 2) * 2
                    val tr = wr * complexData[v] - wi * complexData[v + 1];
                    val ti = wr * complexData[v + 1] + wi * complexData[v]
                    complexData[v] = complexData[u] - tr; complexData[v + 1] =
                        complexData[u + 1] - ti
                    complexData[u] += tr; complexData[u + 1] += ti
                    val nwr = wr * wlr - wi * wli; wi = wr * wli + wi * wlr; wr = nwr
                }
            }
            len = len shl 1
        }
    }

    fun findNearestZeroCrossing(data: ShortArray, index: Int): Int {
        val range = LooperConfig.ZERO_CROSSING_SEARCH_RANGE;
        var bestIdx = index;
        var minDistance = Int.MAX_VALUE
        for (offset in -range..range) {
            val curr = index + offset; if (curr < 0 || curr >= data.size - 1) continue
            if ((data[curr] <= 0 && data[curr + 1] > 0) || (data[curr] >= 0 && data[curr + 1] < 0)) {
                if (abs(offset) < minDistance) {
                    minDistance = abs(offset); bestIdx = curr
                }
            }
        }
        return bestIdx
    }

    fun findOnsetPeaks(flux: List<Double>, numBeats: Int = 4): List<Int> {
        if (flux.isEmpty()) return emptyList()
        val threshold = flux.average() * 4.0;
        val minGap = 25;
        val candidates = mutableListOf<Pair<Int, Double>>();
        var lastPeak = -minGap
        for (i in 1 until flux.size - 1) {
            if (flux[i] > threshold && flux[i] > flux[i - 1] && flux[i] > flux[i + 1]) {
                if (i - lastPeak >= minGap) {
                    candidates.add(i to flux[i]); lastPeak = i
                } else if (flux[i] > candidates.last().second) {
                    candidates[candidates.size - 1] = i to flux[i]; lastPeak = i
                }
            }
        }
        return candidates.sortedByDescending { it.second }.take(40).map { it.first }.sorted()
    }

    fun calculateRawSimilarity(
        data: ShortArray,
        posA: Int,
        posB: Int,
        length: Int,
        step: Int = 1
    ): Double {
        if (posA < 0 || posB < 0 || posA + length > data.size || posB + length > data.size) return 0.0
        var sumA = 0.0;
        var sumB = 0.0;
        var count = 0
        for (i in 0 until length step step) {
            sumA += data[posA + i]; sumB += data[posB + i]; count++
        }
        val meanA = sumA / count;
        val meanB = sumB / count
        var dot = 0.0;
        var varA = 0.0;
        var varB = 0.0
        for (i in 0 until length step step) {
            val vA = data[posA + i] - meanA;
            val vB = data[posB + i] - meanB
            dot += vA * vB; varA += vA * vA; varB += vB * vB
        }
        val den = sqrt(varA) * sqrt(varB); return if (den > 0) dot / den else 0.0
    }

    fun calculateRMS(data: ShortArray, start: Int, length: Int): Double {
        if (start < 0 || start + length > data.size || length <= 0) return 0.0
        var sumSq = 0.0;
        val step = (length / 1000).coerceAtLeast(1);
        var count = 0
        for (i in start until (start + length) step step) {
            val v = data[i].toDouble(); sumSq += v * v; count++
        }
        return sqrt(sumSq / count)
    }

    fun calculateComplexNovelty(data: ShortArray, winSize: Int, stepSize: Int): List<Double> {
        val numFrames = (data.size - winSize) / stepSize; if (numFrames <= 2) return emptyList()
        val prevMag = DoubleArray(winSize / 2);
        val prevPhase = DoubleArray(winSize / 2);
        val prevPrevPhase = DoubleArray(winSize / 2);
        val novelty = mutableListOf<Double>()
        for (f in 0 until numFrames) {
            val buf = DoubleArray(winSize * 2); for (j in 0 until winSize) {
                buf[j * 2] =
                    (data[f * stepSize + j].toDouble() / Short.MAX_VALUE) * (0.5 * (1 - cos(2 * PI * j / (winSize - 1))))
            }
            fft(buf);
            var frameNovelty = 0.0
            for (k in 0 until winSize / 2) {
                val re = buf[k * 2];
                val im = buf[k * 2 + 1];
                val mag = sqrt(re * re + im * im);
                val phase = kotlin.math.atan2(im, re)
                val targetPhase = 2 * prevPhase[k] - prevPrevPhase[k];
                val targetRe = prevMag[k] * cos(targetPhase);
                val targetIm = prevMag[k] * sin(targetPhase)
                frameNovelty += sqrt((targetRe - re) * (targetRe - re) + (targetIm - im) * (targetIm - im))
                prevPrevPhase[k] = prevPhase[k]; prevPhase[k] = phase; prevMag[k] = mag
            }
            novelty.add(frameNovelty)
        }
        return novelty
    }

    fun findBestRhythmicSnap(data: ShortArray, targetIdx: Int, onsets: List<Int>): Int {
        val neighborhood = LooperConfig.SAMPLE_RATE / 10
        val nearest = onsets.filter { it in (targetIdx - neighborhood)..(targetIdx + neighborhood) }
            .minByOrNull { abs(it - targetIdx) }
        return nearest ?: findNearestZeroCrossing(data, targetIdx)
    }
}
