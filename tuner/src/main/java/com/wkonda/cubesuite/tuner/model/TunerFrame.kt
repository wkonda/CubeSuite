package com.wkonda.cubesuite.tuner.model

data class TunerFrame(
    val cents: FloatArray,
    val active: BooleanArray,
    val activeCount: Int = active.count { it }
) {
    companion object {
        fun empty(): TunerFrame {
            return TunerFrame(
                cents = FloatArray(6) { Float.NaN },
                active = BooleanArray(6)
            )
        }
    }
}
