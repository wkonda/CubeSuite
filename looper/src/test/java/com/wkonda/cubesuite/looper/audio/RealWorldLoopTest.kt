package com.wkonda.cubesuite.looper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RealWorldLoopTest {

    private val resourcesDir =
        File("/home/sid/AndroidStudioProjects/CubeSuite/looper/src/test/resources")

    @Test
    fun testManifestLoops() {
        val manifestFile = File(resourcesDir, "manifest.json")
        if (!manifestFile.exists()) return

        val manifestContent = manifestFile.readText()
        val entries = manifestContent.trim()
            .removeSurrounding("[", "]")
            .split("},{")
            .map { it.removeSurrounding("{", "}") }

        val analyzer = AudioAnalyzer(48000)

        entries.forEach { entry ->
            val fields = entry.split(",")
                .associate {
                    val parts = it.split(":")
                    val key = parts[0].trim().removeSurrounding("\"")
                    val value = parts[1].trim().removeSurrounding("\"")
                    key to value
                }

            val fileName = fields["file"] ?: return@forEach
            val pcmFile = File(resourcesDir, fileName)

            if (!pcmFile.exists()) return@forEach

            val bytes = pcmFile.readBytes()
            val shortData = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)

            val result = analyzer.analyze(shortData, 0, 4)

            assertTrue(
                "AI should have found a valid end point after start",
                result.endSample > result.startSample
            )
        }
    }
}
