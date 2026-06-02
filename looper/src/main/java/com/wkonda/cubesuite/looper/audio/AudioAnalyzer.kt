package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = LooperConfig.SAMPLE_RATE) {

    data class AnalysisResult(
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val bpm: Double = 0.0,
        val beatGrid: List<Int> = emptyList(),
        val chords: List<ChordRegion> = emptyList(),
        val correlationCurve: List<Pair<Int, Double>> = emptyList(),
        val rhythmicCurve: List<Pair<Int, Double>> = emptyList()
    )

    private val beatTracker = BeatTracker()
    private val chordDetector = ChordDetector()

    private data class Cache(
        val hash: Int,
        val spec: List<List<Double>>,
        val flux: List<Double>,
        val onsets: List<Int>,
        val novelty: List<Double>
    )
    private var cache: Cache? = null

    fun analyze(data: ShortArray, loopStart: Int, bars: Int = 4): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(0, 0, emptyList())
        val c = getOrCompute(data, bars * 4)
        val step = LooperConfig.FFT_STEP_SIZE
        val correlationCurve = mutableListOf<Pair<Int, Double>>()
        val rhythmicCurve = mutableListOf<Pair<Int, Double>>()
        val beatRes = beatTracker.trackBeats(c.novelty.ifEmpty { c.flux }, sampleRate, step)
        val onsetSamples = c.onsets.map { it * step }
        val snappedS = if (loopStart == 0) AudioUtils.findNearestZeroCrossing(
            data,
            onsetSamples.firstOrNull { it > sampleRate / 10 } ?: 0) else loopStart
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
                    var dot = 0.0;
                    var n1 = 0.0;
                    var n2 = 0.0
                    for (i in 0 until refSpec.size step 4) {
                        val s1 = refSpec[i];
                        val s2 = c.spec[tFrame + i]
                        for (b in s1.indices) {
                            val w = if (b < 15) 1.5 else 0.5;
                            val v1 = s1[b] * w;
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
                chordDetector.detectChords(c.spec, sampleRate, step),
                correlationCurve,
                rhythmicCurve
            )
        }
        return AnalysisResult(snappedS, data.size, onsetSamples, beatRes.bpm)
    }

    private fun getOrCompute(data: ShortArray, beats: Int): Cache {
        val h = data.contentHashCode(); cache?.let { if (it.hash == h) return it }
        val win = LooperConfig.FFT_WINDOW_SIZE;
        val step = LooperConfig.FFT_STEP_SIZE;
        val bR = sampleRate.toDouble() / win
        val spec = List((data.size - win) / step) { i ->
            val buf = DoubleArray(win * 2); for (j in 0 until win) buf[j * 2] =
            (data[i * step + j].toDouble() / Short.MAX_VALUE) * (0.35875 - 0.48829 * cos(2 * PI * j / (win - 1)) + 0.14128 * cos(
                4 * PI * j / (win - 1)
            ) - 0.01168 * cos(6 * PI * j / (win - 1)))
            AudioUtils.fft(buf); List(49) { bI ->
            val midi = 36 + bI;
            val low = 440.0 * 2.0.pow((midi - 0.5 - 69.0) / 12.0);
            val high = 440.0 * 2.0.pow((midi + 0.5 - 69.0) / 12.0)
            val bS = max(0, round(low / bR).toInt());
            val bE = min(win / 2, round(high / bR).toInt())
            var maxM = 0.0; for (k in bS until bE) {
            val m = sqrt(buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2)); if (m > maxM) maxM = m
        }; 20.0 * log10(max(1e-9, maxM))
            }
        }
        val flux = List(spec.size) { t ->
            if (t == 0) 0.0 else {
                var f = 0.0;
                val s1 = spec[t];
                val s2 = spec[t - 1]; for (b in 0 until 12) {
                    val d = s1[b] - s2[b]; if (d > 0) f += d
                }; f
            }
        }
        return Cache(
            h,
            spec,
            flux,
            AudioUtils.findOnsetPeaks(flux, beats),
            AudioUtils.calculateComplexNovelty(data, win, step)
        ).also { cache = it }
    }

    fun getSpectrogram(data: ShortArray): List<List<Double>> {
        if (data.isEmpty()) return emptyList();
        val c = getOrCompute(data, 4)
        return List(600) { i ->
            c.spec[(i.toDouble() / 600 * c.spec.size).toInt().coerceIn(c.spec.indices)].subList(
                4,
                29
            )
        }
    }
}
