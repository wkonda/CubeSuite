package com.wkonda.cubesuite.looper.data

import com.wkonda.cubesuite.looper.audio.LooperConfig

data class LoopMetadata(
    val id: String,
    val name: String,
    val fileName: String,
    val startSample: Int,
    val endSample: Int,
    val totalSamples: Int,
    val bpm: Double? = null,
    val timeSignature: String = "4/4",
    val bars: Int = 4,
    val sampleRate: Int = LooperConfig.SAMPLE_RATE
)
