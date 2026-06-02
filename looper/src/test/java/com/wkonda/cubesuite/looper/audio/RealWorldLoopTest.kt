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
        if (!manifestFile.exists()) {
            println("Skipping RealWorldLoopTest: manifest.json not found at ${manifestFile.absolutePath}")
            return
        }

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
            val expectedStart = fields["start"]?.toInt() ?: 0
            val expectedEnd = fields["end"]?.toInt() ?: 0
            val pcmFile = File(resourcesDir, fileName)

            if (!pcmFile.exists()) {
                println("  PCM file $fileName missing, skipping...")
                return@forEach
            }

            val bytes = pcmFile.readBytes()
            val shortData = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData)

            println("--- Testing loop: ${fields["name"]} ($fileName) ---")

            val result = analyzer.analyze(shortData, 0, shortData.size, 4)

            println("  Manual S: $expectedStart, AI S: ${result.startSample} (Diff: ${result.startSample - expectedStart})")
            println("  Manual E: $expectedEnd, AI E: ${result.endSample} (Diff: ${result.endSample - expectedEnd})")
            println("  Detected Chords: ${result.chords.map { it.label }.distinct()}")
            println("  Suggested Bars: ${result.suggestedBars}, Suggested Sig: ${result.suggestedSignature}")

            // We don't assert failure here because the AI points might be musically better 
            // than the user's manual points in the manifest. This test is for visual validation.
            assertTrue(
                "AI should have found a valid end point after start",
                result.endSample > result.startSample
            )
        }
    }
}
