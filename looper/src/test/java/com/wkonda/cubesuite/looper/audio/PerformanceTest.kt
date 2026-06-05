package com.wkonda.cubesuite.looper.audio

import org.junit.Test
import java.util.ArrayDeque

class PerformanceTest {
    @Test
    fun testArrayDequeConversionPerformance() {
        val maxSamples = LooperConfig.MAX_RECORD_SAMPLES
        val list = ArrayDeque<Short>(maxSamples)

        println("Filling ArrayDeque with $maxSamples samples...")
        for (i in 0 until maxSamples) {
            list.addLast((i % 32767).toShort())
        }

        println("Measuring toShortArray()...")
        val startTime = System.currentTimeMillis()
        val array = list.toShortArray()
        val endTime = System.currentTimeMillis()

        println("toShortArray() took ${endTime - startTime} ms for $maxSamples samples.")
        println("Array size: ${array.size}")
    }
}
