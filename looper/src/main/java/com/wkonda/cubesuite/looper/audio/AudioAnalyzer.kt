package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.cos
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

    /**
     * Analyzes audio data to find the best loop points and estimate BPM.
     * Uses spectral flux autocorrelation for BPM and waveform correlation for loop points.
     */
    fun analyze(data: ShortArray, targetBars: Int = 4): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(120.0, 0, 0, emptyList())

        val stepSize = 256
        val (onsets, flux) = detectOnsetsWithFlux(data, stepSize)
        val fluxSR = sampleRate.toDouble() / stepSize

        // 1. Estimate BPM using autocorrelation of the novelty curve (flux)
        val beatPeriodFrames = estimateBeatPeriod(flux, fluxSR)
        val bpm = 60.0 / (beatPeriodFrames / fluxSR)

        // 2. Find first significant onset as start and align to zero-crossing
        val firstOnset =
            onsets.firstOrNull { it.energy > 0.5 } ?: onsets.firstOrNull() ?: Onset(0, 0.0)
        val startSample = findNearestZeroCrossing(data, firstOnset.sampleIndex)

        // 3. Detect loop length and time signature
        val (loopFrames, signature) = detectLoopAndSignature(flux, beatPeriodFrames, targetBars)

        val estimatedLoopSamples = (loopFrames * stepSize).toInt()

        // 4. Fine-tune end sample using waveform correlation AND zero-crossing alignment
        val refinedLoopSamples = findFineLoopPoint(data, startSample, estimatedLoopSamples)
        val endSample = (startSample + refinedLoopSamples).coerceAtMost(data.size)

        return AnalysisResult(
            bpm = bpm,
            startSample = startSample,
            endSample = endSample,
            onsets = onsets.map { it.sampleIndex },
            timeSignature = signature
        )
    }

    /**
     * Snap a user-selected range to the best local zero-crossings and waveform matches.
     * Also recalculates BPM based on the assumption of a musically consistent loop length.
     */
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

        // 1. Snap start to nearest zero crossing
        val snappedStart = findNearestZeroCrossing(data, start)

        // 2. Snap end to the best waveform match near the current selection
        // Use a wider search range (300ms) for re-analysis to be more flexible
        val currentLength = end - snappedStart
        val searchRange = (sampleRate * 2.0).toInt()
        val windowSize = (sampleRate * 0.1).toInt()

        var bestLag = currentLength
        var minCost = Double.MAX_VALUE

        val minSearch = (currentLength - searchRange).coerceAtLeast(sampleRate / 2)
        val maxSearch =
            (currentLength + searchRange).coerceAtMost(data.size - snappedStart - windowSize)

        for (lag in minSearch..maxSearch) {
            if (data[snappedStart + lag] <= 0 && data[snappedStart + lag + 1] > 0) {
                var mse = 0.0
                val effectiveWindow = windowSize.coerceAtMost(data.size - snappedStart - lag)
                for (i in 0 until effectiveWindow) {
                    val d =
                        data[snappedStart + i].toDouble() - data[snappedStart + lag + i].toDouble()
                    mse += d * d
                }
                mse /= effectiveWindow

                // Penalty for moving away from the user's manual selection
                val userPenalty =
                    (kotlin.math.abs((snappedStart + lag) - end).toDouble() / sampleRate) * 5000.0
                val cost = mse + userPenalty

                if (cost < minCost) {
                    minCost = cost
                    bestLag = lag
                }
            }
        }

        val snappedEnd = snappedStart + bestLag
        val loopSamples = snappedEnd - snappedStart

        // 3. Recalculate BPM
        // Estimate how many beats/bars are in this loop based on the previous BPM estimate
        val beatsPerBar = signature.split("/")[0].toIntOrNull() ?: 4
        val beatPeriodSamples = (60.0 / currentBpm) * sampleRate
        val estimatedBeats = (loopSamples / beatPeriodSamples).roundToInt().coerceAtLeast(1)

        // Snap to nearest bar multiple for BPM stability
        val bars = (estimatedBeats.toDouble() / beatsPerBar).roundToInt().coerceAtLeast(1)
        val totalBeats = bars * beatsPerBar

        val newBpm = (totalBeats.toDouble() * 60.0 * sampleRate) / loopSamples

        return AnalysisResult(
            bpm = newBpm,
            startSample = snappedStart,
            endSample = snappedEnd,
            onsets = emptyList(),
            timeSignature = signature
        )
    }

    private fun detectOnsetsWithFlux(
        data: ShortArray,
        stepSize: Int
    ): Pair<List<Onset>, DoubleArray> {
        val windowSize = 512
        val spectrums = mutableListOf<DoubleArray>()

        // 1. Calculate Magnitude Spectrums
        for (i in 0 until data.size - windowSize step stepSize) {
            val fftBuffer = DoubleArray(windowSize * 2)
            for (j in 0 until windowSize) {
                val windowCoef = 0.5 * (1.0 - cos(2.0 * PI * j / (windowSize - 1)))
                fftBuffer[j * 2] = (data[i + j].toDouble() / Short.MAX_VALUE) * windowCoef
                fftBuffer[j * 2 + 1] = 0.0
            }
            fft(fftBuffer)
            val magnitudes = DoubleArray(windowSize / 2) { k ->
                sqrt(fftBuffer[k * 2].pow(2) + fftBuffer[k * 2 + 1].pow(2))
            }
            spectrums.add(magnitudes)
        }

        // 2. Compute Spectral Flux (Novelty Curve)
        val flux = DoubleArray(spectrums.size)
        for (i in 1 until spectrums.size) {
            val prev = spectrums[i - 1]
            val curr = spectrums[i]
            var sum = 0.0
            for (k in 0 until curr.size) {
                val diff = curr[k] - prev[k]
                if (diff > 0.0) sum += diff
            }
            flux[i] = sum
        }

        // 3. Peak Detection for Onsets
        val onsets = mutableListOf<Onset>()
        val thresholdWindow = 5
        val thresholdMultiplier = 3.5

        for (i in 1 until flux.size - 1) {
            val prev = flux[i - 1]
            val curr = flux[i]
            val next = flux[i + 1]

            val start = (i - thresholdWindow).coerceAtLeast(0)
            val end = (i + thresholdWindow).coerceAtMost(flux.size - 1)
            var localSum = 0.0
            for (j in start..end) localSum += flux[j]
            val localMean = localSum / (end - start + 1)

            if (curr > prev && curr > next && curr > localMean * thresholdMultiplier && curr > 0.1) {
                onsets.add(Onset(i * stepSize, curr))
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

        // Parabolic interpolation for sub-sample precision
        val alpha = correlations[bestLagInt - 1]
        val beta = correlations[bestLagInt]
        val gamma = correlations[bestLagInt + 1]

        val p = 0.5 * (alpha - gamma) / (alpha - 2.0 * beta + gamma)
        return bestLagInt + p
    }

    private fun detectLoopAndSignature(
        flux: DoubleArray,
        beatPeriod: Double,
        targetBars: Int
    ): Pair<Double, String> {
        fun getNormalizedCorr(lag: Int): Double {
            if (lag >= flux.size) return 0.0
            var corr = 0.0
            var count = 0
            for (i in 0 until flux.size - lag) {
                // Focus on the novelty peaks
                corr += flux[i] * flux[i + lag]
                count++
            }
            return if (count > 0) corr / count else 0.0
        }

        // 1. Detect Time Signature (3/4 vs 4/4)
        val corr3 = getNormalizedCorr((beatPeriod * 3).toInt())
        val corr4 = getNormalizedCorr((beatPeriod * 4).toInt())
        val beatsPerBar = if (corr3 > corr4 * 1.1) 3 else 4

        // 2. Search for the best bar length
        // The user records 1-3 loops of 3+ bars.
        // We check 3, 4, 6, 8, 12, 16 bars.
        val candidates = if (beatsPerBar == 3) listOf(3, 6, 9, 12) else listOf(4, 8, 12, 16)
        var bestBars = candidates[0]
        var maxScore = -1.0

        for (bars in candidates) {
            val lag = (beatPeriod * beatsPerBar * bars).toInt()
            if (lag >= flux.size * 0.95) break

            val corr = getNormalizedCorr(lag)
            // Heuristic: Prefer matches that use more of the recording if correlation is high,
            // but favor shorter patterns if they are very strong (to avoid picking 2x loop length).
            val score = corr * (1.0 + (bars.toDouble() / 32.0))

            if (score > maxScore) {
                maxScore = score
                bestBars = bars
            }
        }

        // 3. If no strong repetition found (e.g. they recorded only 1 loop),
        // we assume the recording *is* roughly one loop and find the best bar fit.
        if (maxScore < 0.05) {
            bestBars = candidates.lastOrNull { (beatPeriod * beatsPerBar * it) < flux.size * 1.1 }
                ?: candidates[0]
        }

        val totalBeats = beatsPerBar * bestBars
        return Pair(beatPeriod * totalBeats, "$beatsPerBar/4")
    }

    private fun findFineLoopPoint(data: ShortArray, startSample: Int, estimatedLength: Int): Int {
        val searchRange = (sampleRate * 0.15).toInt() // 150ms search range
        val windowSize = (sampleRate * 0.03).toInt()  // 30ms comparison window

        var bestLag = estimatedLength
        var minCost = Double.MAX_VALUE

        val minSearch = (estimatedLength - searchRange).coerceAtLeast(sampleRate / 4)
        val maxSearch =
            (estimatedLength + searchRange).coerceAtMost(data.size - startSample - windowSize)

        // Find candidates: positive-going zero crossings in the search range
        for (lag in minSearch..maxSearch) {
            // Check for zero crossing at both start and end to ensure phase alignment
            // We want the end to transition exactly like the start did
            if (data[startSample + lag] <= 0 && data[startSample + lag + 1] > 0) {
                var mse = 0.0
                // Use a larger window for more stable matching
                val effectiveWindow = windowSize.coerceAtMost(data.size - startSample - lag)
                for (i in 0 until effectiveWindow) {
                    val d =
                        data[startSample + i].toDouble() - data[startSample + lag + i].toDouble()
                    mse += d * d
                }
                mse /= effectiveWindow

                // Heavily penalize distance from estimated length to maintain tempo
                val distancePenalty =
                    (kotlin.math.abs(lag - estimatedLength).toDouble() / sampleRate) * 50000.0
                val cost = mse + distancePenalty

                if (cost < minCost) {
                    minCost = cost
                    bestLag = lag
                }
            }
        }

        // If no zero-crossing found (unlikely), fall back to original search
        if (minCost == Double.MAX_VALUE) {
            return estimatedLength
        }

        return bestLag
    }

    private fun findNearestZeroCrossing(data: ShortArray, index: Int): Int {
        val searchRange = 2000 // samples (~40ms)
        var bestIdx = index
        var minDistance = Int.MAX_VALUE

        for (offset in -searchRange..searchRange) {
            val curr = index + offset
            if (curr < 0 || curr >= data.size - 1) continue

            // Look for positive-going zero crossing (slope > 0)
            if (data[curr] <= 0 && data[curr + 1] > 0) {
                val dist = kotlin.math.abs(offset)
                if (dist < minDistance) {
                    minDistance = dist
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
        val binResolution = sampleRate.toDouble() / windowSize

        val logStart = kotlin.math.log2(fromHz.toDouble())
        val logEnd = kotlin.math.log2(toHz.toDouble())
        val logStep = (logEnd - logStart) / numFreqBands

        return List(numTimeWindows) { i ->
            val startIdx = (i * step).toInt().coerceIn(0, (data.size - windowSize).coerceAtLeast(0))
            val fftBuffer = DoubleArray(windowSize * 2)

            for (j in 0 until windowSize) {
                val idx = startIdx + j
                if (idx >= data.size) break
                val windowCoef = 0.5 * (1.0 - cos(2.0 * PI * j / (windowSize - 1)))
                fftBuffer[j * 2] = (data[idx].toDouble() / Short.MAX_VALUE) * windowCoef
                fftBuffer[j * 2 + 1] = 0.0
            }

            fft(fftBuffer)

            List(numFreqBands) { bandIdx ->
                val fLow = 2.0.pow(logStart + bandIdx * logStep)
                val fHigh = 2.0.pow(logStart + (bandIdx + 1) * logStep)

                val bStart = (fLow / binResolution).toInt().coerceIn(0, windowSize / 2)
                val bEnd = (fHigh / binResolution).toInt().coerceIn(bStart + 1, windowSize / 2)

                var sumMag = 0.0
                var count = 0
                for (k in bStart until bEnd) {
                    val real = fftBuffer[k * 2]
                    val imag = fftBuffer[k * 2 + 1]
                    sumMag += sqrt(real * real + imag * imag)
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
                val tempReal = complexData[i * 2]
                complexData[i * 2] = complexData[j * 2]
                complexData[j * 2] = tempReal
                val tempImag = complexData[i * 2 + 1]
                complexData[i * 2 + 1] = complexData[j * 2 + 1]
                complexData[j * 2 + 1] = tempImag
            }
            var bit = n shr 1
            while (j >= bit && bit > 0) {
                j -= bit
                bit = bit shr 1
            }
            j += bit
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenReal = cos(angle)
            val wLenImag = sin(angle)

            for (i in 0 until n step len) {
                var wReal = 1.0
                var wImag = 0.0

                for (k in 0 until len / 2) {
                    val uIdx = (i + k) * 2
                    val vIdx = (i + k + len / 2) * 2

                    val vReal = complexData[vIdx]
                    val vImag = complexData[vIdx + 1]

                    val tReal = wReal * vReal - wImag * vImag
                    val tImag = wReal * vImag + wImag * vReal

                    complexData[vIdx] = complexData[uIdx] - tReal
                    complexData[vIdx + 1] = complexData[uIdx + 1] - tImag
                    complexData[uIdx] += tReal
                    complexData[uIdx + 1] += tImag

                    val nextWReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWReal
                }
            }
            len = len shl 1
        }
    }
}
