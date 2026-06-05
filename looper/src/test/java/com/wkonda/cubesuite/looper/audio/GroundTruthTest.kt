package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class GroundTruthTest {
    private val resourcesDir =
        File("/home/sid/AndroidStudioProjects/CubeSuite/looper/src/test/resources")

    @Test
    fun testCamfg80Precise() {
        val pcmFile = File(resourcesDir, "camfg80.pcm")
        if (!pcmFile.exists()) return
        val bytes = pcmFile.readBytes();
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)
        val analyzer = AudioAnalyzer(LooperConfig.SAMPLE_RATE);
        val result = analyzer.analyze(shortData, 0, 4)
        val bpm =
            (16.0 * 60.0 * LooperConfig.SAMPLE_RATE.toDouble()) / (result.endSample - result.startSample)
        assertTrue(abs(bpm - 80.0) < 0.02)
    }

    @Test
    fun testFolkRockLong124() {
        val pcmFile = File(resourcesDir, "folkrock124.pcm")
        if (!pcmFile.exists()) return
        val bytes = pcmFile.readBytes();
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)
        val analyzer = AudioAnalyzer(LooperConfig.SAMPLE_RATE);
        val result = analyzer.analyze(shortData, 210000, 32)
        assertTrue(abs(result.bpm - 124.0) < 0.1)
    }

    @Test
    fun testFastRock144() {
        val pcmFile = File(resourcesDir, "fastrock144.pcm")
        if (!pcmFile.exists()) return
        val bytes = pcmFile.readBytes();
        val shortData = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)
        val analyzer = AudioAnalyzer(LooperConfig.SAMPLE_RATE);
        val result = analyzer.analyze(shortData, 400000, 16)
        assertTrue(abs(result.bpm - 144.0) < 0.5)
    }
}
