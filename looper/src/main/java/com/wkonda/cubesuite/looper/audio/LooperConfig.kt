package com.wkonda.cubesuite.looper.audio

object LooperConfig {
    const val SAMPLE_RATE = 48000
    const val CROSSFADE_MS = 5
    const val FFT_WINDOW_SIZE = 2048
    const val FFT_STEP_SIZE = 1024
    const val ZERO_CROSSING_SEARCH_RANGE = 1000

    // 2 minutes max record
    const val MAX_RECORD_MINUTES = 2
    const val MAX_RECORD_SAMPLES = SAMPLE_RATE * 60 * MAX_RECORD_MINUTES
}
