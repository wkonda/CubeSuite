package com.wkonda.cubesuite.mvave

enum class Setting(val code: Byte) {
    TYPE(0), GAIN(1), TONE(2), REVERB(3), FB(4), //
    VOLUME(5), TIME(6), MIX(7), MOD(8), IR(9), //
    CAB_SW(10), MOD_SW(11), AMP_SW(12);


    fun maxValue() = when (this) {
        VOLUME -> 127f
        AMP_SW -> 1f
        TONE -> 15f
        GAIN -> 7f
        TYPE -> 8f
        CAB_SW -> 1f
        IR -> 8f
        REVERB -> 15f
        MOD_SW -> 1f
        MIX -> 118f
        FB -> 127f
        TIME -> 31f
        MOD -> 15f
    }
}