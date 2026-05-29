package com.wkonda.cubesuite.looper.audio

object LooperConfig {
    const val SAMPLE_RATE = 48000
    const val CROSSFADE_MS = 5
    const val FFT_WINDOW_SIZE = 512
    const val FFT_STEP_SIZE = 256
    const val SPEC_WINDOW_SIZE = 8192
    const val ZERO_CROSSING_SEARCH_RANGE = 1000

    // Musical Range Constants
    const val FREQ_E2 = 82.41f
    const val FREQ_E4 = 329.63f
    const val FREQ_A5 = 880.00f

    // MIDI Constants
    const val MIDI_E2 = 40
    const val MIDI_E4 = 64
    const val MIDI_A5 = 81
}
