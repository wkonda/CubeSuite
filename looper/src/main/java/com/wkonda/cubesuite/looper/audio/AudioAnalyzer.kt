package com.wkonda.cubesuite.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = LooperConfig.SAMPLE_RATE) {

    data class AnalysisResult(
        val bpm: Double,
        val startSample: Int,
        val endSample: Int,
        val onsets: List<Int>,
        val fftMagnitudes: List<Double> = emptyList(),
        val timeSignature: String = "4/4",
    )

    fun analyze(data: ShortArray): AnalysisResult {
        if (data.isEmpty()) return AnalysisResult(120.0, 0, 0, emptyList())
        val step = LooperConfig.FFT_STEP_SIZE
        val (onsets, flux) = detectOnsetsWithFlux(data)
        val fluxSR = sampleRate.toDouble() / step
        val beatP = estimateBeatPeriod(flux, fluxSR)
        val first = onsets.firstOrNull { it.energy > 0.5 } ?: onsets.firstOrNull() ?: Onset(0, 0.0)
        val start = AudioUtils.findNearestZeroCrossing(data, first.sampleIndex)
        val (loopF, sig) = detectLoopAndSignature(flux, beatP)
        val refinedL = findFineLoopPoint(data, start, (loopF * step).toInt())
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
        val range = (sampleRate * 2.0).toInt()
        val win = (sampleRate * 0.1).toInt()
        var bestL = end - sS
        var minC = Double.MAX_VALUE
        val minS =
            (bestL - range).coerceAtLeast(((sampleRate * LooperConfig.MIN_LOOP_LENGTH_MS) / 1000.0).toInt())
        val maxS = (bestL + range).coerceAtMost(data.size - sS - win)

        for (lag in minS..maxS) {
            if (data[sS + lag] <= 0 && data[sS + lag + 1] > 0) {
                var mse = 0.0
                val eW = win.coerceAtMost(data.size - sS - lag)
                for (i in 0 until eW) mse += (data[sS + i].toDouble() - data[sS + lag + i].toDouble()).pow(
                    2
                )
                val cost = (mse / eW) + (abs((sS + lag) - end).toDouble() / sampleRate) * 1000.0
                if (cost < minC) {
                    minC = cost; bestL = lag
                }
            }
        }
        val sE = sS + bestL;
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

    private fun detectOnsetsWithFlux(data: ShortArray): Pair<List<Onset>, DoubleArray> {
        val win = LooperConfig.FFT_WINDOW_SIZE;
        val step = LooperConfig.FFT_STEP_SIZE
        val specs = mutableListOf<DoubleArray>()
        for (i in 0 until data.size - win step step) {
            val buf = DoubleArray(win * 2)
            for (j in 0 until win) buf[j * 2] =
                (data[i + j].toDouble() / Short.MAX_VALUE) * (0.5 * (1.0 - cos(2.0 * PI * j / (win - 1))))
            AudioUtils.fft(buf)
            specs.add(DoubleArray(win / 2) { k -> sqrt(buf[k * 2].pow(2) + buf[k * 2 + 1].pow(2)) })
        }
        val flux = DoubleArray(specs.size)
        for (i in 1 until specs.size) {
            var sum = 0.0; for (k in 0 until specs[i].size) {
                val d = specs[i][k] - specs[i - 1][k]; if (d > 0) sum += d
            }
            flux[i] = sum
        }
        val onsets = mutableListOf<Onset>()
        for (i in 1 until flux.size - 1) {
            val start = (i - 5).coerceAtLeast(0);
            val end = (i + 5).coerceAtMost(flux.size - 1)
            var sum = 0.0; for (j in start..end) sum += flux[j]
            if (flux[i] > flux[i - 1] && flux[i] > flux[i + 1] && flux[i] > (sum / (end - start + 1)) * 3.5 && flux[i] > 0.1) onsets.add(
                Onset(i * step, flux[i])
            )
        }
        return Pair(onsets, flux)
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

    private fun findFineLoopPoint(data: ShortArray, start: Int, estL: Int): Int {
        val range = (sampleRate * 0.15).toInt();
        val win = (sampleRate * 0.03).toInt()
        var bestL = estL;
        var minC = Double.MAX_VALUE
        val minS = (estL - range).coerceAtLeast(sampleRate / 4)
        val maxS = (estL + range).coerceAtMost(data.size - start - win)
        for (lag in minS..maxS) {
            if (data[start + lag] <= 0 && data[start + lag + 1] > 0) {
                var mse = 0.0;
                val eW = win.coerceAtMost(data.size - start - lag)
                for (i in 0 until eW) mse += (data[start + i].toDouble() - data[start + lag + i].toDouble()).pow(
                    2
                )
                val cost = (mse / eW) + (abs(lag - estL).toDouble() / sampleRate) * 50000.0
                if (cost < minC) {
                    minC = cost; bestL = lag
                }
            }
        }
        return bestL
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
