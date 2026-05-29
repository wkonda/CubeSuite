package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = 48000) {

    data class AnalysisResult(
        val bpm: Double,
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val fftMagnitudes: List<Double> = emptyList(),
        val timeSignature: String = "4/4"
    )

    fun analyze(data: ShortArray, targetBars: Int = 4): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(120.0, 0, 0, emptyList())
        val stepSize = 256
        val (onsets, flux) = detectOnsetsWithFlux(data, stepSize)
        val fluxSR = sampleRate.toDouble() / stepSize
        val beatPeriodFrames = estimateBeatPeriod(flux, fluxSR)
        val firstOnset =
            onsets.firstOrNull { it.energy > 0.5 } ?: onsets.firstOrNull() ?: Onset(0, 0.0)
        val startSample = findNearestZeroCrossing(data, firstOnset.sampleIndex)
        val (loopFrames, signature) = detectLoopAndSignature(flux, beatPeriodFrames)
        val refinedLoopSamples =
            findFineLoopPoint(data, startSample, (loopFrames * stepSize).toInt())
        return AnalysisResult(
            60.0 / (beatPeriodFrames / fluxSR),
            startSample,
            (startSample + refinedLoopSamples).coerceAtMost(data.size),
            onsets.map { it.sampleIndex },
            timeSignature = signature
        )
    }

    fun snapToSeamlessLoop(
        data: ShortArray,
        start: Int,
        end: Int,
        currentBpm: Double = 120.0,
        signature: String = "4/4"
    ): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(
            currentBpm,
            start,
            end,
            emptyList(),
            emptyList(),
            signature
        )
        val sS = findNearestZeroCrossing(data, start)
        val curLen = end - sS
        val range = (sampleRate * 2.0).toInt()
        val win = (sampleRate * 0.1).toInt()
        var bestL = curLen
        var minC = Double.MAX_VALUE
        val minS = (curLen - range).coerceAtLeast((sampleRate * 0.1).toInt())
        val maxS = (curLen + range).coerceAtMost(data.size - sS - win)

        for (lag in minS..maxS) {
            if (data[sS + lag] <= 0 && data[sS + lag + 1] > 0) {
                var mse = 0.0
                val eW = win.coerceAtMost(data.size - sS - lag)
                for (i in 0 until eW) {
                    val d = data[sS + i].toDouble() - data[sS + lag + i].toDouble()
                    mse += d * d
                }
                val cost = (mse / eW) + (abs((sS + lag) - end).toDouble() / sampleRate) * 1000.0
                if (cost < minC) {
                    minC = cost; bestL = lag
                }
            }
        }
        val sE = sS + bestL;
        val loopS = sE - sS
        val bpb = signature.split("/")[0].toIntOrNull() ?: 4
        val bars =
            ((loopS / ((60.0 / (if (currentBpm <= 0) 120.0 else currentBpm)) * sampleRate)) / bpb).roundToInt()
                .coerceAtLeast(1)
        return AnalysisResult(
            (bars * bpb * 60.0 * sampleRate) / loopS,
            sS,
            sE,
            emptyList(),
            emptyList(),
            signature
        )
    }

    private fun detectOnsetsWithFlux(
        data: ShortArray,
        stepSize: Int
    ): Pair<List<Onset>, DoubleArray> {
        val windowSize = 512
        val spectrums = mutableListOf<DoubleArray>()
        for (i in 0 until data.size - windowSize step stepSize) {
            val fftBuffer = DoubleArray(windowSize * 2)
            for (j in 0 until windowSize) {
                fftBuffer[j * 2] =
                    (data[i + j].toDouble() / Short.MAX_VALUE) * (0.5 * (1.0 - cos(2.0 * PI * j / (windowSize - 1))))
            }
            fft(fftBuffer)
            spectrums.add(DoubleArray(windowSize / 2) { k ->
                sqrt(
                    fftBuffer[k * 2].pow(2) + fftBuffer[k * 2 + 1].pow(
                        2
                    )
                )
            })
        }
        val flux = DoubleArray(spectrums.size)
        for (i in 1 until spectrums.size) {
            var sum = 0.0
            for (k in 0 until spectrums[i].size) {
                val diff = spectrums[i][k] - spectrums[i - 1][k]
                if (diff > 0.0) sum += diff
            }
            flux[i] = sum
        }
        val onsets = mutableListOf<Onset>()
        for (i in 1 until flux.size - 1) {
            val start = (i - 5).coerceAtLeast(0)
            val end = (i + 5).coerceAtMost(flux.size - 1)
            var localSum = 0.0
            for (j in start..end) localSum += flux[j]
            val localMean = localSum / (end - start + 1)
            if (flux[i] > flux[i - 1] && flux[i] > flux[i + 1] && flux[i] > localMean * 3.5 && flux[i] > 0.1) {
                onsets.add(Onset(i * stepSize, flux[i]))
            }
        }
        return Pair(onsets, flux)
    }

    private fun estimateBeatPeriod(flux: DoubleArray, fluxSR: Double): Double {
        val minLag = (60.0 / 220.0 * fluxSR).toInt()
        val maxLag = (60.0 / 60.0 * fluxSR).toInt()
        val correlations = DoubleArray(maxLag + 1)
        var maxCorr = -1.0
        var bestLagInt = 0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var count = 0
            for (i in 0 until flux.size - lag) {
                corr += flux[i] * flux[i + lag]
                count++
            }
            correlations[lag] = if (count > 0) corr / count else 0.0
            if (correlations[lag] > maxCorr) {
                maxCorr = correlations[lag]
                bestLagInt = lag
            }
        }
        if (bestLagInt <= minLag || bestLagInt >= maxLag) return bestLagInt.toDouble()
        val alpha = correlations[bestLagInt - 1]
        val beta = correlations[bestLagInt]
        val gamma = correlations[bestLagInt + 1]
        return bestLagInt + 0.5 * (alpha - gamma) / (alpha - 2.0 * beta + gamma)
    }

    private fun detectLoopAndSignature(
        flux: DoubleArray,
        beatPeriod: Double
    ): Pair<Double, String> {
        fun getCorr(lag: Int): Double {
            if (lag >= flux.size) return 0.0
            var corr = 0.0
            var count = 0
            for (i in 0 until flux.size - lag) {
                corr += flux[i] * flux[i + lag]
                count++
            }
            return if (count > 0) corr / count else 0.0
        }

        val beatsPerBar =
            if (getCorr((beatPeriod * 3).toInt()) > getCorr((beatPeriod * 4).toInt()) * 1.1) 3 else 4
        val candidates = if (beatsPerBar == 3) listOf(3, 6, 9, 12) else listOf(4, 8, 12, 16)
        var bestBars = candidates[0]
        var maxScore = -1.0
        for (bars in candidates) {
            val lag = (beatPeriod * beatsPerBar * bars).toInt()
            if (lag >= flux.size * 0.95) break
            val score = getCorr(lag) * (1.0 + (bars.toDouble() / 32.0))
            if (score > maxScore) {
                maxScore = score
                bestBars = bars
            }
        }
        if (maxScore < 0.05) bestBars =
            candidates.lastOrNull { (beatPeriod * beatsPerBar * it) < flux.size * 1.1 }
                ?: candidates[0]
        return Pair(beatPeriod * beatsPerBar * bestBars, "$beatsPerBar/4")
    }

    private fun findFineLoopPoint(data: ShortArray, startSample: Int, estimatedLength: Int): Int {
        val range = (sampleRate * 2.0).toInt()
        val win = (sampleRate * 0.05).toInt()
        var bestL = estimatedLength
        var minC = Double.MAX_VALUE
        val minS = (estimatedLength - range).coerceAtLeast(sampleRate / 4)
        val maxS = (estimatedLength + range).coerceAtMost(data.size - startSample - win)
        for (lag in minS..maxS) {
            if (data[startSample + lag] <= 0 && data[startSample + lag + 1] > 0) {
                var mse = 0.0
                val eW = win.coerceAtMost(data.size - startSample - lag)
                for (i in 0 until eW) {
                    val d =
                        data[startSample + i].toDouble() - data[startSample + lag + i].toDouble()
                    mse += d * d
                }
                val cost =
                    (mse / eW) + (abs(lag - estimatedLength).toDouble() / sampleRate) * 10000.0
                if (cost < minC) {
                    minC = cost; bestL = lag
                }
            }
        }
        return bestL
    }

    private fun findNearestZeroCrossing(data: ShortArray, index: Int): Int {
        val searchRange = 2000
        var bestIdx = index
        var minDistance = Int.MAX_VALUE
        for (offset in -searchRange..searchRange) {
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

    fun getSpectrogram(
        data: ShortArray,
        fromHz: Float,
        toHz: Float,
        numTimeWindows: Int,
        numFreqBands: Int = 40,
        windowSize: Int = 8192
    ): List<List<Double>> {
        if (data.isEmpty() || numTimeWindows <= 0) return emptyList()
        val step = (data.size - windowSize).toDouble() / (numTimeWindows - 1).coerceAtLeast(1)
        val binRes = sampleRate.toDouble() / windowSize
        val logStart = log2(fromHz.toDouble())
        val logStep = (log2(toHz.toDouble()) - logStart) / numFreqBands
        return List(numTimeWindows) { i ->
            val startIdx = (i * step).toInt().coerceIn(0, (data.size - windowSize).coerceAtLeast(0))
            val fftBuffer = DoubleArray(windowSize * 2)
            for (j in 0 until windowSize) {
                val idx = startIdx + j
                if (idx < data.size) fftBuffer[j * 2] =
                    (data[idx].toDouble() / Short.MAX_VALUE) * (0.5 * (1.0 - cos(2.0 * PI * j / (windowSize - 1))))
            }
            fft(fftBuffer)
            List(numFreqBands) { bIdx ->
                val bStart = (2.0.pow(logStart + bIdx * logStep) / binRes).toInt()
                    .coerceIn(0, windowSize / 2)
                val bEnd = (2.0.pow(logStart + (bIdx + 1) * logStep) / binRes).toInt()
                    .coerceIn(bStart + 1, windowSize / 2)
                var sumMag = 0.0
                var count = 0
                for (k in bStart until bEnd) {
                    sumMag += sqrt(fftBuffer[k * 2].pow(2) + fftBuffer[k * 2 + 1].pow(2))
                    count++
                }
                if (count > 0) sumMag / count else 0.0
            }
        }
    }

    data class Onset(val sampleIndex: Int, val energy: Double)

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
}
