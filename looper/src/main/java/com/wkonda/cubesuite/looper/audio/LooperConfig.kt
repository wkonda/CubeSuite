package com.wkonda.cubesuite.looper.audio

object LooperConfig {
    const val SAMPLE_RATE = 48000
    const val CROSSFADE_MS = 5
    const val FFT_WINDOW_SIZE = 4096 // Optimized: 11.7Hz resolution (enough for E2)
    const val FFT_STEP_SIZE = 2048   // Faster: ~42ms per step (4x faster than before)
    const val SPEC_WINDOW_SIZE = 8192
    const val ZERO_CROSSING_SEARCH_RANGE = 1000

    // 10 minutes max record
    const val MAX_RECORD_MINUTES = 10
    const val MAX_RECORD_SAMPLES = SAMPLE_RATE * 60 * MAX_RECORD_MINUTES

    // Musical Range Constants (C3 to C6)
    const val FREQ_E2 = 82.41f
    const val FREQ_E4 = 329.63f
    const val MIDI_E2 = 40
    const val MIDI_E4 = 64
}
