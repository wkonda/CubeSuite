package com.wkonda.cubesuite.mvave

sealed class Module {
    data class General(
        val volume: Byte, val ampSw: Byte, val cabSw: Byte, val modSw: Byte
    )

    data class CAB(
        val ir: Byte, val reverb: Byte
    )

    data class MOD(
        val mix: Byte, val fb: Byte, val time: Byte, val mod: Byte
    )

    data class AMP(
        val tone: Byte, val gain: Byte, val type: Byte
    )
}