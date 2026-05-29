package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = LooperConfig.SAMPLE_RATE) {

    data class AnalysisResult(
        val bpm: Double? = null,
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val fftMagnitudes: List<Double> = emptyList(),
        val timeSignature: String = "4/4",
    )

    fun analyze(data: ShortArray): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(120.0, 0, 0, emptyList())
        val step = LooperConfig.FFT_STEP_SIZE
        val (onsets, lowFlux, highFlux) = detectMultiBandNovelty(data, step)
        val combinedFlux = DoubleArray(lowFlux.size) { i -> lowFlux[i] * 1.5 + highFlux[i] }
        val fluxSR = sampleRate.toDouble() / step
        val beatP = estimateBeatPeriod(combinedFlux, fluxSR)
        val first = onsets.firstOrNull { it.energy > 0.5 } ?: onsets.firstOrNull() ?: Onset(0, 0.0)
        val start = AudioUtils.findNearestZeroCrossing(data, first.sampleIndex)
        val (loopF, sig) = detectLoopAndSignature(combinedFlux, beatP)
        val refinedL = findProLoopPoint(data, start, (loopF * step).toInt())
        return AnalysisResult(
            60.0 / (beatP / fluxSR),
            start,
            (start + refinedL).coerceAtMost(data.size),
            onsets.map { it.sampleIndex },
            timeSignature = sig
        )
    }

    fun snapToSeamlessLoop(
        data: ShortArray,
        start: Int,
        end: Int,
        curBpm: Double = 120.0,
        sig: String = "4/4"
    ): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(curBpm, start, end, emptyList(), emptyList(), sig)
        val sS = AudioUtils.findNearestZeroCrossing(data, start)
        val refinedL = findProLoopPoint(data, sS, end - sS, searchRangeSec = 2.0)
        val sE = sS + refinedL;
        val loopS = sE - sS
        val bpb = sig.split("/")[0].toIntOrNull() ?: 4
        val bars =
            ((loopS / ((60.0 / (if (curBpm <= 0) 120.0 else curBpm)) * sampleRate)) / bpb).roundToInt()
                .coerceAtLeast(1)
        return AnalysisResult(
            (bars * bpb * 60.0 * sampleRate) / loopS,
            sS,
            sE,
            emptyList(),
            emptyList(),
            sig
        )
    }

    private fun detectMultiBandNovelty(
        data: ShortArray,
        step: Int
    ): Triple<List<Onset>, DoubleArray, DoubleArray> {
        val win = LooperConfig.FFT_WINDOW_SIZE;
        val specs = mutableListOf<DoubleArray>()
        for (i in 0 until data.size - win step step) {
            val buf = DoubleArray(win * 2)
            for (j in 0 until win) buf[j * 2] =
                (data[i + j].toDouble() / Short.MAX_VALUE) * (0.5 * (1.0 - cos(2.0 * PI * j / (win - 1))))
            AudioUtils.fft(buf)
            specs.add(DoubleArray(win / 2) { k -> sqrt(buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2)) })
        }
        val lowFlux = DoubleArray(specs.size);
        val highFlux = DoubleArray(specs.size)
        val lowCut = (win / 2) / 8 
        for (i in 1 until specs.size) {
            for (k in 0 until specs[i].size) {
                val d = max(0.0, specs[i][k] - specs[i - 1][k])
                if (k < lowCut) lowFlux[i] += d else highFlux[i] += d
            }
        }
        val onsets = mutableListOf<Onset>();
        val combined = DoubleArray(lowFlux.size) { i -> lowFlux[i] + highFlux[i] }
        for (i in 2 until combined.size - 2) {
            var sum = 0.0; for (j in -2..2) sum += combined[i + j]
            val localMean = sum / 5.0
            if (combined[i] > combined[i - 1] && combined[i] > combined[i + 1] && combined[i] > localMean * 2.5 && combined[i] > 0.05) onsets.add(
                Onset(i * step, combined[i])
            )
        }
        return Triple(onsets, lowFlux, highFlux)
    }

    private fun findProLoopPoint(
        data: ShortArray,
        start: Int,
        estL: Int,
        searchRangeSec: Double = 0.15
    ): Int {
        val range = (sampleRate * searchRangeSec).toInt();
        val win = (sampleRate * 0.04).toInt()
        var bestL = estL;
        var maxSim = -1.0
        val minS = (estL - range).coerceAtLeast((sampleRate * 0.1).toInt())
        val maxS = (estL + range).coerceAtMost(data.size - start - win)

        val sSig = DoubleArray(win) { i -> data[start + i].toDouble() }
        var sSum = 0.0; for (v in sSig) sSum += v * v
        val sNorm = sqrt(sSum)

        for (lag in minS..maxS) {
            if (data[start + lag] <= 0 && data[start + lag + 1] > 0) {
                val eSig = DoubleArray(win) { i -> data[start + lag + i].toDouble() }
                var eSum = 0.0; for (v in eSig) eSum += v * v
                val eNorm = sqrt(eSum)
                if (sNorm > 0 && eNorm > 0) {
                    var dot = 0.0; for (i in 0 until win) dot += sSig[i] * eSig[i]
                    val ncc = dot / (sNorm * eNorm)
                    val sim = ncc - (abs(lag - estL).toDouble() / sampleRate) * 0.1
                    if (sim > maxSim) {
                        maxSim = sim; bestL = lag
                    }
                }
            }
        }
        return bestL
    }

    private fun estimateBeatPeriod(flux: DoubleArray, fluxSR: Double): Double {
        val minL = (60.0 / 220.0 * fluxSR).toInt();
        val maxL = (60.0 / 60.0 * fluxSR).toInt()
        val corrs = DoubleArray(maxL + 1);
        var maxC = -1.0;
        var bestL = 0
        for (lag in minL..maxL) {
            var c = 0.0;
            var cnt = 0; for (i in 0 until flux.size - lag) {
                c += flux[i] * flux[i + lag]; cnt++
            }
            corrs[lag] = if (cnt > 0) c / cnt else 0.0
            if (corrs[lag] > maxC) {
                maxC = corrs[lag]; bestL = lag
            }
        }
        if (bestL !in (minL + 1) until maxL) return bestL.toDouble()
        val a = corrs[bestL - 1];
        val b = corrs[bestL];
        val g = corrs[bestL + 1]
        return bestL + 0.5 * (a - g) / (a - 2.0 * b + g)
    }

    private fun detectLoopAndSignature(flux: DoubleArray, beatP: Double): Pair<Double, String> {
        fun getC(lag: Int): Double {
            if (lag >= flux.size) return 0.0
            var c = 0.0;
            var cnt = 0; for (i in 0 until flux.size - lag) {
                c += flux[i] * flux[i + lag]; cnt++
            }
            return if (cnt > 0) c / cnt else 0.0
        }
        val bpb = if (getC((beatP * 3).toInt()) > getC((beatP * 4).toInt()) * 1.1) 3 else 4
        val cands = if (bpb == 3) listOf(3, 6, 9, 12) else listOf(4, 8, 12, 16)
        var bestB = cands[0];
        var maxS = -1.0
        for (b in cands) {
            val lag = (beatP * bpb * b).toInt(); if (lag >= flux.size * 0.95) break
            val s = getC(lag) * (1.0 + (b.toDouble() / 32.0))
            if (s > maxS) {
                maxS = s; bestB = b
            }
        }
        if (maxS < 0.05) bestB =
            cands.lastOrNull { (beatP * bpb * it) < flux.size * 1.1 } ?: cands[0]
        return Pair(beatP * bpb * bestB, "$bpb/4")
    }

    fun getSpectrogram(
        data: ShortArray,
        fromH: Float,
        toH: Float,
        numT: Int,
        numF: Int = 40
    ): List<List<Double>> {
        if (data.isEmpty() || numT <= 0) return emptyList()
        val win = LooperConfig.SPEC_WINDOW_SIZE;
        val bR = sampleRate.toDouble() / win
        val step = (data.size - win).toDouble() / (numT - 1).coerceAtLeast(1)
        val lS = log2(fromH.toDouble());
        val lSt = (log2(toH.toDouble()) - lS) / numF
        return List(numT) { i ->
            val sI = (i * step).toInt().coerceIn(0, (data.size - win).coerceAtLeast(0))
            val buf = DoubleArray(win * 2)
            for (j in 0 until win) {
                val idx = sI + j; if (idx < data.size) buf[j * 2] =
                    (data[idx].toDouble() / Short.MAX_VALUE) * (0.5 * (1.0 - cos(2.0 * PI * j / (win - 1))))
            }
            AudioUtils.fft(buf)
            List(numF) { bI ->
                val bS = (2.0.pow(lS + bI * lSt) / bR).toInt().coerceIn(0, win / 2);
                val bE = (2.0.pow(lS + (bI + 1) * lSt) / bR).toInt().coerceIn(bS + 1, win / 2)
                var sM = 0.0;
                var cnt = 0; for (k in bS until bE) {
                sM += sqrt(buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2)); cnt++
            }
                if (cnt > 0) sM / cnt else 0.0
            }
        }
    }

    data class Onset(val sampleIndex: Int, val energy: Double)
}
