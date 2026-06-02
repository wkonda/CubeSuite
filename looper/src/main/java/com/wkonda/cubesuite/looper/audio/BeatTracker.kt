package com.wkonda.cubesuite.looper.audio

class BeatTracker {

    data class BeatResult(val bpm: Double, val beatGrid: List<Int>)

    fun trackBeats(
        flux: List<Double>,
        sampleRate: Int,
        stepSize: Int,
        chordChanges: List<Int> = emptyList()
    ): BeatResult {
        if (flux.isEmpty()) return BeatResult(120.0, emptyList())

        // 1. AUTOCORRELATION (40-220 BPM)
        val minLag = (60.0 * sampleRate / (220.0 * stepSize)).toInt()
        val maxLag = (60.0 * sampleRate / (40.0 * stepSize)).toInt()

        val rawCorr = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until (flux.size - lag)) {
                sum += flux[i] * flux[i + lag]
            }
            rawCorr[lag] = sum
        }

        // 2. HARMONIC AGGREGATION (Comb Filter)
        var bestLag = (60.0 * sampleRate / (120.0 * stepSize)).toInt()
        var bestScore = -1.0

        for (lag in minLag..maxLag) {
            // Aggregate fundamental and harmonics
            var score = rawCorr[lag]
            if (lag * 2 <= maxLag) score += 0.8 * rawCorr[lag * 2]
            if (lag * 3 <= maxLag) score += 0.6 * rawCorr[lag * 3]
            if (lag / 2 >= minLag) score += 0.5 * rawCorr[lag / 2]

            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        val bpm = (60.0 * sampleRate) / (bestLag * stepSize)
        val grid = mutableListOf<Int>()
        var curr = 0
        while (curr < flux.size) {
            grid.add(curr * stepSize)
            curr += bestLag
        }

        return BeatResult(bpm, grid)
    }
}
