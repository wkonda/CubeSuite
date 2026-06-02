package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
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
        val suggestedBars: Int = 4,
        val suggestedSignature: Pair<Int, Int> = 4 to 4,
        val correlationCurve: List<Pair<Int, Double>> = emptyList()
    )

    private val beatTracker = BeatTracker()
    private val chordDetector = ChordDetector()

    private data class Cache(
        val hash: Int,
        val spec: List<List<Double>>,
        val flux: List<Double>,
        val onsets: List<Int>
    )

    private var cache: Cache? = null

    fun analyze(data: ShortArray, loopStart: Int, _loopEnd: Int, beats: Int): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(0, 0, emptyList())
        val c = getOrCompute(data, beats, data.size)
        val step = LooperConfig.FFT_STEP_SIZE

        var sumSq = 0.0
        for (i in 0 until data.size step 100) sumSq += data[i].toDouble().pow(2)
        val rms = sqrt(sumSq / (data.size / 100.0))
        val noiseThreshold = (rms * 0.1).coerceAtLeast(600.0)

        val trimmedStart =
            (0 until data.size).firstOrNull { abs(data[it].toInt()) > noiseThreshold } ?: 0
        val onsets = c.onsets.map { it * step }.filter { it >= trimmedStart }

        val snappedS = if (loopStart == 0) {
            val s =
                if (onsets.size >= 2) onsets[1] else if (onsets.size == 1) onsets[0] else trimmedStart
            AudioUtils.findNearestZeroCrossing(data, s)
        } else loopStart

        val refLen = 5 * sampleRate
        var finalE = snappedS + (sampleRate * 12)
        val correlationCurve = mutableListOf<Pair<Int, Double>>()
        val beatRes = beatTracker.trackBeats(c.flux, sampleRate, step)

        if (data.size > snappedS + refLen + (sampleRate * 2)) {
            val searchStart = snappedS + (sampleRate * 5)
            val searchEnd = (data.size - refLen).coerceAtLeast(searchStart + 1)

            val startFrame = snappedS / step
            val fluxRefLen = refLen / step
            var bestSim1 = -1.0;
            var bestT1Frame = searchStart / step

            for (tFrame in (searchStart / step) until (searchEnd / step)) {
                if (tFrame + fluxRefLen > c.flux.size) break
                var dot = 0.0;
                var n1 = 0.0;
                var n2 = 0.0
                for (i in 0 until fluxRefLen) {
                    val v1 = c.flux[startFrame + i];
                    val v2 = c.flux[tFrame + i]
                    dot += v1 * v2; n1 += v1 * v1; n2 += v2 * v2
                }
                val sim = if (n1 > 0 && n2 > 0) dot / (sqrt(n1) * sqrt(n2)) else 0.0
                correlationCurve.add((tFrame * step) to sim)
                if (sim > bestSim1) {
                    bestSim1 = sim; bestT1Frame = tFrame
                }
            }

            if (_loopEnd > 0 && _loopEnd < data.size) {
                finalE = snapToBestEnd(data, snappedS, _loopEnd)
            } else {
                val medT = bestT1Frame * step
                val fineWin = sampleRate * 4
                val fineRange = step
                var bestSimFine = -2.0;
                var bestTFine = medT.toDouble()
                val fineRes = mutableListOf<Double>()

                for (off in -fineRange..fineRange) {
                    val currT = medT + off
                    if (currT >= searchStart && currT + fineWin <= data.size) {
                        val sim =
                            AudioUtils.calculateRawSimilarity(data, snappedS, currT, fineWin, 1)
                        fineRes.add(sim)
                        if (sim > bestSimFine) {
                            bestSimFine = sim; bestTFine = currT.toDouble()
                        }
                    }
                }

                val pkIdx = fineRes.indexOfFirst { it == bestSimFine }
                if (pkIdx > 0 && pkIdx < fineRes.size - 1) {
                    val y1 = fineRes[pkIdx - 1];
                    val y2 = fineRes[pkIdx];
                    val y3 = fineRes[pkIdx + 1]
                    val d = 2 * (y1 - 2 * y2 + y3)
                    if (abs(d) > 1e-9) bestTFine += (y1 - y3) / d
                }
                finalE = bestTFine.toInt()
            }
        }

        val resS = snappedS
        val resE = AudioUtils.findNearestZeroCrossing(data, finalE)
        val finalDurSec = (resE - resS).toDouble() / sampleRate

        val hypotheses = (1..64).toList()
        val suggestedBars = hypotheses.minByOrNull { h ->
            val hypBpm = (h * 4 * 60.0) / finalDurSec
            abs(log2(hypBpm / beatRes.bpm))
        } ?: 8

        val totalBeats = suggestedBars * 4
        val detectedBpm = (totalBeats * 60.0) / finalDurSec

        return AnalysisResult(
            startSample = resS,
            endSample = resE,
            onsets = c.onsets.map { it * step },
            bpm = detectedBpm,
            beatGrid = List(totalBeats) { i -> (resS + i * (resE - resS).toDouble() / totalBeats).toInt() },
            chords = chordDetector.detectChords(c.spec, sampleRate, step),
            suggestedBars = suggestedBars,
            suggestedSignature = 4 to 4,
            correlationCurve = correlationCurve
        )
    }

    fun snapToBestStart(data: ShortArray, loopStart: Int): Int {
        val neighborhood = (0.5 * sampleRate).toInt()
        val startSearch = (loopStart - neighborhood).coerceAtLeast(0)
        val endSearch = (loopStart + neighborhood).coerceAtMost(data.size - 1)
        var bestEnergy = -1.0;
        var bestIdx = loopStart
        for (i in startSearch..endSearch step 10) {
            val energy = abs(data[i].toDouble())
            if (energy > bestEnergy) {
                bestEnergy = energy; bestIdx = i
            }
        }
        return AudioUtils.findNearestZeroCrossing(data, bestIdx)
    }

    fun snapToBestEnd(data: ShortArray, snappedS: Int, loopEnd: Int): Int {
        val refLen = 5 * sampleRate
        val neighborhood = (0.25 * sampleRate).toInt()
        val searchStart = (loopEnd - neighborhood).coerceAtLeast(snappedS + refLen)
        val searchEnd = (loopEnd + neighborhood).coerceAtMost(data.size - refLen)

        var bestSim = -2.0;
        var bestT = loopEnd.toDouble()
        for (t in searchStart..searchEnd step 32) {
            val sim = AudioUtils.calculateRawSimilarity(data, snappedS, t, refLen, 32)
            if (sim > bestSim) {
                bestSim = sim; bestT = t.toDouble()
            }
        }

        val medT = bestT.toInt()
        var bestSimFine = -2.0;
        var bestTFine = bestT
        val fineRange = 32
        val fineResults = mutableListOf<Double>()
        for (off in -fineRange..fineRange) {
            val currT = medT + off
            if (currT in searchStart..searchEnd) {
                val sim =
                    AudioUtils.calculateRawSimilarity(data, snappedS, currT, sampleRate * 4, 1)
                fineResults.add(sim)
                if (sim > bestSimFine) {
                    bestSimFine = sim; bestTFine = currT.toDouble()
                }
            }
        }

        val pkIdx = fineResults.indexOfFirst { it == bestSimFine }
        if (pkIdx > 0 && pkIdx < fineResults.size - 1) {
            val y1 = fineResults[pkIdx - 1];
            val y2 = fineResults[pkIdx];
            val y3 = fineResults[pkIdx + 1]
            val d = 2 * (y1 - 2 * y2 + y3)
            if (abs(d) > 1e-9) bestTFine += (y1 - y3) / d
        }
        return bestTFine.toInt()
    }

    private fun getOrCompute(data: ShortArray, beats: Int, loopSamples: Int): Cache {
        val h = data.contentHashCode()
        cache?.let { if (it.hash == h) return it }
        val win = LooperConfig.FFT_WINDOW_SIZE
        val step = LooperConfig.FFT_STEP_SIZE
        val bR = sampleRate.toDouble() / win
        val spec = List((data.size - win) / step) { i ->
            val buf = DoubleArray(win * 2)
            for (j in 0 until win) {
                val w =
                    0.35875 - 0.48829 * cos(2 * PI * j / (win - 1)) + 0.14128 * cos(4 * PI * j / (win - 1)) - 0.01168 * cos(
                        6 * PI * j / (win - 1)
                    )
                buf[j * 2] = (data[i * step + j].toDouble() / Short.MAX_VALUE) * w
            }
            AudioUtils.fft(buf)
            List(49) { bI ->
                val midi = 36 + bI
                val low = 440.0 * 2.0.pow((midi - 0.5 - 69.0) / 12.0)
                val high = 440.0 * 2.0.pow((midi + 0.5 - 69.0) / 12.0)
                val bStart = round(low / bR).toInt().coerceIn(0, win / 2)
                val bEnd = round(high / bR).toInt().coerceIn(bStart + 1, win / 2)
                var maxMag = 0.0
                for (k in bStart until bEnd) {
                    val mag = sqrt(buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2))
                    if (mag > maxMag) maxMag = mag
                }
                20.0 * log10(maxMag.coerceAtLeast(1e-9))
            }
        }
        val flux = List(spec.size) { t ->
            if (t == 0) 0.0 else {
                var f = 0.0
                for (b in 0 until 12) {
                    val d = spec[t][b] - spec[t - 1][b]
                    if (d > 0) f += d
                }
                f
            }
        }
        val onsets = AudioUtils.findOnsetPeaks(flux, loopSamples, beats)
        return Cache(h, spec, flux, onsets).also { cache = it }
    }

    fun getSpectrogram(data: ShortArray): List<List<Double>> {
        if (data.isEmpty()) return emptyList()
        val c = getOrCompute(data, 4, data.size)
        val numFrames = 600
        return List(numFrames) { i ->
            val idx = (i.toDouble() / numFrames * c.spec.size).toInt().coerceIn(0, c.spec.size - 1)
            c.spec[idx].subList(4, 29)
        }
    }

    fun getFlux(data: ShortArray) = cache?.flux ?: emptyList()
    fun getBeatTracker() = beatTracker
}
