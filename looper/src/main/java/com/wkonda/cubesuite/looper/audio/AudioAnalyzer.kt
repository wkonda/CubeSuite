package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = LooperConfig.SAMPLE_RATE) {

    data class AnalysisResult(
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val bpm: Double = 0.0,
        val beatGrid: List<Int> = emptyList(),
        val correlationCurve: List<Pair<Int, Double>> = emptyList(),
        val rhythmicCurve: List<Pair<Int, Double>> = emptyList()
    )

    private val beatTracker = BeatTracker()
    private val chromaAnalyzer = ChromaAnalyzer()

    private data class Cache(
        val hash: Int,
        val spec: List<List<Double>>,
        val cqt: List<List<Double>>,
        val flux: List<Double>,
        val onsets: List<Int>,
        val novelty: List<Double>
    )
    private var cache: Cache? = null

    // Precompute Blackman-Harris window weights once to save CPU cycles on mobile
    private val windowWeights by lazy {
        val win = LooperConfig.FFT_WINDOW_SIZE
        DoubleArray(win) { j ->
            0.35875 - 0.48829 * cos(2 * PI * j / (win - 1)) + 0.14128 * cos(4 * PI * j / (win - 1))
        }
    }

    // Original overlapping mapping starting at MIDI 36 (C2) to ensure loop point matching is 100% identical to original code
    private val hybridBinRangesOriginal by lazy {
        val win = LooperConfig.FFT_WINDOW_SIZE
        val sr = sampleRate.toDouble()
        val srDown = sr / 4.0
        val bR = sr / win
        val bRDown = srDown / win

        List(51) { bI ->
            val midi = 36 + bI
            val isDown = midi <= 60
            val rBR = if (isDown) bRDown else bR

            val low = 440.0 * 2.0.pow((midi - 0.5 - 69.0) / 12.0)
            val high = 440.0 * 2.0.pow((midi + 0.5 - 69.0) / 12.0)

            val bS = (low / rBR).toInt().coerceAtLeast(0)
            val bE = ceil(high / rBR).toInt().coerceIn(bS + 1, win / 2)
            isDown to (bS until bE)
        }
    }

    // High-resolution non-overlapping mapping starting at MIDI 38 (D2) up to 86 (49 bins) for CQT analysis
    private val cqtBinRanges by lazy {
        val win = LooperConfig.FFT_WINDOW_SIZE
        val sr = sampleRate.toDouble()
        val srDown = sr / 4.0
        val bR = sr / win
        val bRDown = srDown / win

        List(49) { bI ->
            val midi = 38 + bI
            val isDown = midi <= 68
            val rBR = if (isDown) bRDown else bR

            val low = 440.0 * 2.0.pow((midi - 0.5 - 69.0) / 12.0)
            val high = 440.0 * 2.0.pow((midi + 0.5 - 69.0) / 12.0)

            val kStart = (low / rBR).toInt().coerceAtLeast(1)
            val kEnd = ceil(high / rBR).toInt().coerceAtMost(win / 2 - 1)

            val bins = mutableListOf<Int>()
            for (k in kStart..kEnd) {
                val f = k * rBR
                val m = kotlin.math.round(12.0 * kotlin.math.log2(f / 440.0) + 69.0).toInt()
                if (m == midi) {
                    bins.add(k)
                }
            }
            if (bins.isEmpty()) {
                val nearestK = kotlin.math.round(low / rBR).toInt().coerceIn(1, win / 2 - 1)
                bins.add(nearestK)
            }
            isDown to bins
        }
    }

    fun analyze(data: ShortArray, loopStart: Int, bars: Int = 4): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(0, 0, emptyList())
        val c = getOrCompute(data, bars * 4)
        val step = LooperConfig.FFT_STEP_SIZE
        val correlationCurve = mutableListOf<Pair<Int, Double>>()
        val rhythmicCurve = mutableListOf<Pair<Int, Double>>()
        val beatRes = beatTracker.trackBeats(c.novelty.ifEmpty { c.flux }, sampleRate, step)
        val onsetSamples = c.onsets.map { it * step }
        val snappedS =
            if (loopStart == 0 && onsetSamples.isNotEmpty()) AudioUtils.findNearestZeroCrossing(
            data,
            onsetSamples.firstOrNull { it > sampleRate / 10 } ?: 0
        ) else loopStart
        val refLen = (5.0 * sampleRate).toInt()
        val maxNovelty = c.novelty.maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0
        for (i in c.novelty.indices) rhythmicCurve.add((i * step) to (c.novelty[i] / maxNovelty))

        val searchStart = snappedS + refLen
        if (data.size > searchStart + refLen) {
            val searchEnd = data.size - refLen
            val startFrame = snappedS / step
            val numRefFrames = refLen / step

            val refSpec = if (startFrame + numRefFrames <= c.spec.size) {
                c.spec.subList(startFrame, startFrame + numRefFrames)
            } else {
                c.spec.subList(startFrame, c.spec.size)
            }

            var bestScore = -1.0
            var bestIdx = searchStart
            val rawScores = mutableListOf<Double>()

            for (t in searchStart until searchEnd step 1024) {
                val tFrame = t / step
                var specSim = 0.0
                if (tFrame + refSpec.size <= c.spec.size) {
                    var dot = 0.0
                    var n1 = 0.0
                    var n2 = 0.0
                    for (i in 0 until refSpec.size step 4) {
                        val s1 = refSpec[i]
                        val s2 = c.spec[tFrame + i]
                        for (b in 0 until 49) { // Compare the first 49 bins (MIDI 36 to 84) to be identical to original search
                            val w = if (b < 15) 1.5 else 0.5
                            val v1 = s1[b] * w
                            val v2 = s2[b] * w
                            dot += v1 * v2; n1 += v1 * v1; n2 += v2 * v2
                        }
                    }
                    specSim = if (n1 > 0 && n2 > 0) dot / (sqrt(n1) * sqrt(n2)) else 0.0
                }

                val score = specSim // 100% Timbre similarity
                rawScores.add(score)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = t
                }
            }

            val firstScore = rawScores.firstOrNull()?.coerceAtLeast(1e-6) ?: 1.0
            for (i in rawScores.indices) {
                val t = searchStart + i * 1024
                if (rawScores[i] > firstScore) {
                    var norm =
                        (rawScores[i] - firstScore) / (bestScore - firstScore).coerceAtLeast(1e-6)
                    val distEnd = searchEnd - t
                    if (distEnd < 0.2 * sampleRate) norm *= (0.5 + 0.5 * cos(PI * (1.0 - distEnd / (0.2 * sampleRate))))
                    correlationCurve.add(t to norm)
                }
            }
            val resE = AudioUtils.findNearestZeroCrossing(
                data,
                AudioUtils.findBestRhythmicSnap(data, bestIdx, onsetSamples)
            )
            val finalDur = (resE - snappedS).toDouble() / sampleRate
            return AnalysisResult(
                snappedS,
                resE,
                onsetSamples,
                (bars * 4 * 60.0) / finalDur,
                List(bars * 4 + 1) { i -> (snappedS + i * (resE - snappedS).toDouble() / (bars * 4)).toInt() },
                correlationCurve,
                rhythmicCurve
            )
        }
        return AnalysisResult(snappedS, data.size, onsetSamples, beatRes.bpm)
    }

    private fun getOrCompute(data: ShortArray, beats: Int): Cache {
        val h = data.contentHashCode(); cache?.let { if (it.hash == h) return it }
        val win = LooperConfig.FFT_WINDOW_SIZE
        val step = LooperConfig.FFT_STEP_SIZE

        if (data.size < win) return Cache(
            h,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        )
        val dataDown = DoubleArray(data.size / 4)
        for (i in dataDown.indices) {
            dataDown[i] =
                (data[i * 4].toDouble() + data[i * 4 + 1] + data[i * 4 + 2] + data[i * 4 + 3]) / (4.0 * Short.MAX_VALUE)
        }

        val numFrames = (data.size - win) / step
        val prevMag = DoubleArray(win / 2)
        val prevPhase = DoubleArray(win / 2)
        val prevPrevPhase = DoubleArray(win / 2)
        val novelty = ArrayList<Double>(numFrames)

        val spec = ArrayList<List<Double>>(numFrames)
        val cqt = ArrayList<List<Double>>(numFrames)

        for (fIdx in 0 until numFrames) {
            // Original FFT for high freqs
            val bufFull = DoubleArray(win * 2)
            for (j in 0 until win) {
                bufFull[j * 2] =
                    (data[fIdx * step + j].toDouble() / Short.MAX_VALUE) * windowWeights[j]
            }
            AudioUtils.fft(bufFull)

            // Compute novelty frame value inline (saving a whole extra FFT pass)
            var frameNovelty = 0.0
            for (k in 0 until win / 2) {
                val re = bufFull[k * 2]
                val im = bufFull[k * 2 + 1]
                val mag = sqrt(re * re + im * im)
                val phase = kotlin.math.atan2(im, re)
                val targetPhase = 2 * prevPhase[k] - prevPrevPhase[k]
                val targetRe = prevMag[k] * cos(targetPhase)
                val targetIm = prevMag[k] * sin(targetPhase)
                frameNovelty += sqrt((targetRe - re) * (targetRe - re) + (targetIm - im) * (targetIm - im))
                prevPrevPhase[k] = prevPhase[k]
                prevPhase[k] = phase
                prevMag[k] = mag
            }
            novelty.add(frameNovelty)

            // Downsampled FFT for low freqs
            val bufDown = DoubleArray(win * 2)
            val centerSample = fIdx * step + win / 2
            val downStart = (centerSample / 4 - win / 2).coerceIn(0, dataDown.size - win)
            for (j in 0 until win) {
                bufDown[j * 2] = dataDown[downStart + j] * windowWeights[j]
            }
            AudioUtils.fft(bufDown)

            // 1. Overlapping spec for loop matching
            val specFrame = List(51) { bI ->
                val (isDown, range) = hybridBinRangesOriginal[bI]
                val buf = if (isDown) bufDown else bufFull

                var energySum = 0.0
                for (k in range) {
                    val re = buf[k * 2]
                    val im = buf[k * 2 + 1]
                    energySum += (re * re + im * im)
                }
                10.0 * log10(max(1e-12, energySum))
            }
            spec.add(specFrame)

            // 2. High-res non-overlapping spec for chroma (CQT)
            val cqtFrame = List(49) { bI ->
                val (isDown, range) = cqtBinRanges[bI]
                val buf = if (isDown) bufDown else bufFull

                var energySum = 0.0
                for (k in range) {
                    val re = buf[k * 2]
                    val im = buf[k * 2 + 1]
                    energySum += (re * re + im * im)
                }
                10.0 * log10(max(1e-12, energySum))
            }
            cqt.add(cqtFrame)
        }

        val flux = List(spec.size) { t ->
            if (t == 0) 0.0 else {
                var f = 0.0
                val s1 = spec[t]
                val s2 = spec[t - 1]
                for (b in 0 until 12) {
                    val d = s1[b] - s2[b]
                    if (d > 0) f += d
                }
                f
            }
        }

        return Cache(
            h,
            spec,
            cqt,
            flux,
            AudioUtils.findOnsetPeaks(flux, beats),
            novelty
        ).also { cache = it }
    }

    fun getChromagram(data: ShortArray): List<List<Double>> {
        if (data.isEmpty()) return emptyList()
        val c = getOrCompute(data, 4)
        if (c.cqt.isEmpty()) return emptyList()

        // Return a 12-bin chromagram instead of the 49-bin spec
        return chromaAnalyzer.computeChromagram(c.cqt)
    }
}
