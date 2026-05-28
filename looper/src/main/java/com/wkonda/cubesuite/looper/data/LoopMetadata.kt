package com.wkonda.cubesuite.looper.data

data class LoopMetadata(
    val id: String,
    var name: String,
    val fileName: String,
    var startSample: Int,
    var endSample: Int,
    val totalSamples: Int,
    val sampleRate: Int = 44100
)
