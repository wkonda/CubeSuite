package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = 48000) {

    data class AnalysisResult(
        val bpm: Double,
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val fftMagnitudes: List<Double> = emptyList()
    )

    fun analyze(data: ShortArray, targetBars: Int = 4): AnalysisResult {
        val onsets = detectOnsetsFFT(data)
        val bpm = estimateBpm(onsets)

        // Find first significant onset as start
        val firstOnset = onsets.firstOrNull { it.energy > 0.1 } ?: onsets.firstOrNull() ?: 0
        val startSample = firstOnset.let { if (it is Onset) it.sampleIndex else 0 }

        val samplesPerBeat = (60.0 / bpm * sampleRate).toInt()
        val totalSamples = samplesPerBeat * 4 * targetBars
        val endSample = (startSample + totalSamples).coerceAtMost(data.size)

        return AnalysisResult(
            bpm,
            startSample,
            endSample,
            onsets.map { it.sampleIndex },
            emptyList()
        )
    }

    fun getSpectrogram(
        data: ShortArray,
        fromHz: Float,
        toHz: Float,
        numTimeWindows: Int,
        numFreqBands: Int = 40,
        windowSize: Int = 8192 // Increased for better low-frequency resolution
    ): List<List<Double>> {
        if (data.isEmpty() || numTimeWindows <= 0) return emptyList()

        val step = (data.size - windowSize).toDouble() / (numTimeWindows - 1).coerceAtLeast(1)
        val binResolution = sampleRate.toDouble() / windowSize

        // Use logarithmic spacing for "by notes"
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

            // Group bins into bands logarithmically
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

    private fun detectOnsetsFFT(data: ShortArray): List<Onset> {
        val windowSize = 512 // Standard for guitar (approx 11.6ms at 44.1kHz)
        val stepSize = 256   // 50% overlap for good temporal resolution

        // 1. Calculate Magnitude Spectrums for each window
        val spectrums = mutableListOf<DoubleArray>()

        for (i in 0 until data.size - windowSize step stepSize) {
            // Prepare complex array for FFT: [real0, imag0, real1, imag1, ...]
            val fftBuffer = DoubleArray(windowSize * 2)

            for (j in 0 until windowSize) {
                // Apply a Hann window to reduce spectral leakage (crucial for guitar transients)
                val windowCoef = 0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * j / (windowSize - 1)))
                val sample = (data[i + j].toDouble() / Short.MAX_VALUE) * windowCoef

                fftBuffer[j * 2] = sample     // Real part
                fftBuffer[j * 2 + 1] = 0.0    // Imaginary part
            }

            // Perform in-place FFT
            fft(fftBuffer)

            // Calculate magnitudes for the positive frequencies (first half)
            val magnitudes = DoubleArray(windowSize / 2)
            for (k in 0 until windowSize / 2) {
                val real = fftBuffer[k * 2]
                val imag = fftBuffer[k * 2 + 1]
                magnitudes[k] = sqrt(real * real + imag * imag)
            }
            spectrums.add(magnitudes)
        }

        // 2. Compute Spectral Flux (Novelty Curve)
        val spectralFlux = mutableListOf<Double>()
        spectralFlux.add(0.0) // First frame has no predecessor

        for (i in 1 until spectrums.size) {
            val prevSpec = spectrums[i - 1]
            val currSpec = spectrums[i]
            var flux = 0.0

            for (k in 0 until currSpec.size) {
                // Rectified difference: only count energy increases (onsets)
                val diff = currSpec[k] - prevSpec[k]
                if (diff > 0.0) {
                    flux += diff
                }
            }
            spectralFlux.add(flux)
        }

        // 3. Peak Detection with Dynamic Thresholding
        val onsets = mutableListOf<Onset>()
        val thresholdWindow = 5 // Look at 5 neighboring frames to calculate local average
        val thresholdMultiplier = 4 // Tune this to adjust sensitivity

        for (i in 1 until spectralFlux.size - 1) {
            val prev = spectralFlux[i - 1]
            val curr = spectralFlux[i]
            val next = spectralFlux[i + 1]

            // Calculate a local adaptive threshold to avoid false positives on string rings
            val start = (i - thresholdWindow).coerceAtLeast(0)
            val end = (i + thresholdWindow).coerceAtMost(spectralFlux.size - 1)
            val localMean = spectralFlux.subList(start, end + 1).average()
            val adaptiveThreshold = localMean * thresholdMultiplier

            // Local peak criteria
            if (curr > prev && curr > next && curr > adaptiveThreshold && curr > 0.5) {
                val sampleIndex = i * stepSize
                onsets.add(Onset(sampleIndex, curr))
            }
        }

        return onsets
    }

    fun fft(complexData: DoubleArray) {
        val n = complexData.size / 2
        if (n <= 1) return

        // 1. Bit-reversal permutation (shuffling the input data)
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                // Swap real parts
                val tempReal = complexData[i * 2]
                complexData[i * 2] = complexData[j * 2]
                complexData[j * 2] = tempReal

                // Swap imaginary parts
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

        // 2. Cooley-Tukey Butterfly combinations
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

                    // Complex multiplication: T = W * V
                    val vReal = complexData[vIdx]
                    val vImag = complexData[vIdx + 1]

                    val tReal = wReal * vReal - wImag * vImag
                    val tImag = wReal * vImag + wImag * vReal

                    // Butterfly update:
                    // Device 1: U = U + T
                    // Device 2: V = U - T
                    complexData[vIdx] = complexData[uIdx] - tReal
                    complexData[vIdx + 1] = complexData[uIdx + 1] - tImag

                    complexData[uIdx] += tReal
                    complexData[uIdx + 1] += tImag

                    // Increment twiddle factor: W = W * W_len
                    val nextWReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWReal
                }
            }
            len = len shl 1
        }
    }

    private fun estimateBpm(onsets: List<Onset>): Double {
        if (onsets.size < 2) return 120.0

        val intervals = mutableListOf<Int>()
        for (i in 0 until onsets.size - 1) {
            for (j in i + 1 until (i + 5).coerceAtMost(onsets.size)) {
                intervals.add(onsets[j].sampleIndex - onsets[i].sampleIndex)
            }
        }

        // Cluster intervals into BPMs
        val bpmCandidates = intervals.map { interval ->
            val beatDurationSec = interval.toDouble() / sampleRate
            if (beatDurationSec == 0.0) 0.0 else 60.0 / beatDurationSec
        }.filter { it in 60.0..180.0 }

        if (bpmCandidates.isEmpty()) return 120.0

        // Find most frequent BPM (roughly)
        return bpmCandidates.groupBy { it.toInt() }
            .maxByOrNull { it.value.size }
            ?.key?.toDouble() ?: 120.0
    }
}
