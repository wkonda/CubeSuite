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
        val bpm: Double = 0.0,
        val correlationCurve: List<Pair<Int, Double>> = emptyList(),
        val rhythmicCurve: List<Pair<Int, Double>> = emptyList()
    )

    private val chromaAnalyzer = ChromaAnalyzer()

    private data class Cache(
        val hash: Int,
        val spec: List<List<Double>>,
        val flux: List<Double>,
        val novelty: List<Double>
    )
    private var cache: Cache? = null

    private val windowWeights by lazy {
        val win = LooperConfig.FFT_WINDOW_SIZE
        DoubleArray(win) { j ->
            0.35875 - 0.48829 * cos(2 * PI * j / (win - 1)) + 0.14128 * cos(4 * PI * j / (win - 1))
        }
    }

    private val midiBinRanges by lazy {
        val win = LooperConfig.FFT_WINDOW_SIZE
        val sr = sampleRate.toDouble()
        val bR = sr / win
        val bRDown = (sr / 4.0) / win

        List(51) { bI ->
            val midi = 36 + bI
            val isDown = midi <= 64 // Use downsampled for low frequencies
            val rBR = if (isDown) bRDown else bR
            val low = 440.0 * 2.0.pow((midi - 0.5 - 69.0) / 12.0)
            val high = 440.0 * 2.0.pow((midi + 0.5 - 69.0) / 12.0)
            val kStart = (low / rBR).toInt().coerceAtLeast(1)
            val kEnd = ceil(high / rBR).toInt().coerceAtMost(win / 2 - 1)
            val bins = (kStart..kEnd).filter { k ->
                kotlin.math.round(12.0 * kotlin.math.log2((k * rBR) / 440.0) + 69.0).toInt() == midi
            }.ifEmpty {
                listOf(
                    kotlin.math.round((low + high) / (2.0 * rBR)).toInt().coerceIn(1, win / 2 - 1)
                )
            }
            isDown to bins
        }
    }

    fun analyze(data: ShortArray, loopStart: Int, totalBeats: Int = 16): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(0, 0, 0.0)
        val cache = getOrCompute(data)
        if (cache.spec.isEmpty()) return AnalysisResult(0, data.size, 120.0)

        val step = LooperConfig.FFT_STEP_SIZE
        val onsetSamples = AudioUtils.findOnsetPeaks(cache.flux).map { it * step }

        val snappedS = if (loopStart == 0 && onsetSamples.isNotEmpty()) {
            AudioUtils.findNearestZeroCrossing(
                data,
                onsetSamples.firstOrNull { it > sampleRate / 10 } ?: 0)
        } else {
            loopStart.coerceIn(0, data.size - 1)
        }

        val rhythmicCurve = cache.novelty.let { nov ->
            val maxNov = nov.maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0
            nov.mapIndexed { i, d -> (i * step) to (d / maxNov) }
        }

        val refLen = (5.0 * sampleRate).toInt()
        val searchStart = snappedS + refLen
        val searchEnd = data.size - (sampleRate / 10)

        if (searchStart < searchEnd) {
            val startFrame = snappedS / step
            val numRefFrames = (refLen / step).coerceAtMost(cache.spec.size - startFrame)
            val refSpec = cache.spec.subList(startFrame, startFrame + numRefFrames)

            var bestScore = -1.0;
            var bestIdx = searchStart
            val correlationCurve = mutableListOf<Pair<Int, Double>>()
            val rawScores = mutableListOf<Pair<Int, Double>>()

            for (t in searchStart until searchEnd step 1024) {
                val tFrame = t / step
                if (tFrame + refSpec.size > cache.spec.size) break

                var dot = 0.0;
                var n1 = 0.0;
                var n2 = 0.0
                for (i in 0 until refSpec.size step 4) {
                    val s1 = refSpec[i];
                    val s2 = cache.spec[tFrame + i]
                    for (b in 0 until 51) {
                        val w = if (b < 20) 1.5 else 0.5;
                        val v1 = s1[b] * w;
                        val v2 = s2[b] * w
                        dot += v1 * v2; n1 += v1 * v1; n2 += v2 * v2
                    }
                }
                val sim = if (n1 > 0 && n2 > 0) dot / (sqrt(n1) * sqrt(n2)) else 0.0
                rawScores.add(t to sim)
                if (sim > bestScore) {
                    bestScore = sim; bestIdx = t
                }
            }

            val baseScore = rawScores.firstOrNull()?.second?.coerceAtLeast(1e-6) ?: 1.0
            for ((t, score) in rawScores) {
                if (score > baseScore) {
                    var norm = (score - baseScore) / (bestScore - baseScore).coerceAtLeast(1e-6)
                    val distEnd = searchEnd - t
                    if (distEnd < 0.2 * sampleRate) norm *= (0.5 + 0.5 * cos(PI * (1.0 - distEnd / (0.2 * sampleRate))))
                    correlationCurve.add(t to norm)
                }
            }

            val resE = AudioUtils.findNearestZeroCrossing(
                data,
                AudioUtils.findBestRhythmicSnap(data, bestIdx, onsetSamples)
            )
            val bpm = (totalBeats * 60.0) / ((resE - snappedS).toDouble() / sampleRate)
            return AnalysisResult(snappedS, resE, bpm, correlationCurve, rhythmicCurve)
        }

        return AnalysisResult(snappedS, data.size, 120.0, emptyList(), rhythmicCurve)
    }

    private fun getOrCompute(data: ShortArray): Cache {
        val h = data.contentHashCode(); cache?.let { if (it.hash == h) return it }
        val win = LooperConfig.FFT_WINDOW_SIZE
        val step = LooperConfig.FFT_STEP_SIZE
        if (data.size < win) return Cache(h, emptyList(), emptyList(), emptyList())

        val dataDown = DoubleArray(data.size / 4) { i ->
            (data[i * 4].toDouble() + data[i * 4 + 1] + data[i * 4 + 2] + data[i * 4 + 3]) / (4.0 * Short.MAX_VALUE) 
        }

        val numFrames = (data.size - win) / step
        val spec = ArrayList<List<Double>>(numFrames)
        val novelty = ArrayList<Double>(numFrames)
        val prevMag = DoubleArray(win / 2);
        val prevPhase = DoubleArray(win / 2);
        val prevPrevPhase = DoubleArray(win / 2)

        for (fIdx in 0 until numFrames) {
            val bufFull =
                DoubleArray(win * 2) { j -> if (j < win) (data[fIdx * step + j].toDouble() / Short.MAX_VALUE) * windowWeights[j] else 0.0 }
            AudioUtils.fft(bufFull)

            var frameNov = 0.0
            for (k in 0 until win / 2) {
                val re = bufFull[k * 2];
                val im = bufFull[k * 2 + 1];
                val mag = sqrt(re * re + im * im)
                val phase = kotlin.math.atan2(im, re);
                val targetPhase = 2 * prevPhase[k] - prevPrevPhase[k]
                frameNov += sqrt(
                    (prevMag[k] * cos(targetPhase) - re).pow(2) + (prevMag[k] * sin(
                        targetPhase
                    ) - im).pow(2)
                )
                prevPrevPhase[k] = prevPhase[k]; prevPhase[k] = phase; prevMag[k] = mag
            }
            novelty.add(frameNov)

            val bufDown = DoubleArray(win * 2)
            val downStart = ((fIdx * step + win / 2) / 4 - win / 2).coerceIn(
                0,
                (dataDown.size - win).coerceAtLeast(0)
            )
            if (dataDown.size >= win) {
                for (j in 0 until win) bufDown[j * 2] = dataDown[downStart + j] * windowWeights[j]
                AudioUtils.fft(bufDown)
            }

            spec.add(List(51) { bI ->
                val (isDown, range) = midiBinRanges[bI]
                val buf = if (isDown && dataDown.size >= win) bufDown else bufFull
                var energy =
                    0.0; for (k in range) energy += (buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2))
                10.0 * log10(max(1e-12, energy))
            })
        }

        val flux = List(spec.size) { t ->
            if (t == 0) 0.0 else {
                var f = 0.0; for (b in 0 until 12) {
                    val d = spec[t][b] - spec[t - 1][b]; if (d > 0) f += d
                }; f
            }
        }
        return Cache(h, spec, flux, novelty).also { cache = it }
    }

    fun getChromagram(data: ShortArray): List<List<Double>> {
        val c =
            getOrCompute(data); return if (c.spec.isEmpty()) emptyList() else chromaAnalyzer.computeChromagram(
            c.spec
        )
    }
}
