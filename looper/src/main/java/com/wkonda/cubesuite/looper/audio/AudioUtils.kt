package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object AudioUtils {
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
            val wlr = cos(ang);
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

    fun findNearestZeroCrossing(
        data: ShortArray,
        index: Int,
        range: Int = LooperConfig.ZERO_CROSSING_SEARCH_RANGE
    ): Int {
        var bestIdx = index
        var minDistance = Int.MAX_VALUE
        for (offset in -range..range) {
            val curr = index + offset
            if (curr < 0 || curr >= data.size - 1) continue
            if (data[curr] <= 0 && data[curr + 1] > 0) {
                if (abs(offset) < minDistance) {
                    minDistance = abs(offset)
                    bestIdx = curr
                }
            }
        }
        return bestIdx
    }
}
