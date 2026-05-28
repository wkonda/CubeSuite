package com.wkonda.cubesuite.looper.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LoopRepository(private val context: Context) {
    private val loopsDir = File(context.filesDir, "loops").apply { mkdirs() }
    private val manifestFile = File(loopsDir, "manifest.json")

    suspend fun saveLoop(name: String, data: ShortArray, start: Int, end: Int): LoopMetadata =
        withContext(Dispatchers.IO) {
            val id = System.currentTimeMillis().toString()
            val fileName = "$id.pcm"
            val file = File(loopsDir, fileName)

            FileOutputStream(file).use { fos ->
                val buffer = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in data) buffer.putShort(s)
                fos.write(buffer.array())
            }

            val metadata = LoopMetadata(id, name, fileName, start, end, data.size)
            val loops = getAllLoops().toMutableList()
            loops.add(metadata)
            saveManifest(loops)
            metadata
        }

    suspend fun getAllLoops(): List<LoopMetadata> = withContext(Dispatchers.IO) {
        if (!manifestFile.exists()) return@withContext emptyList<LoopMetadata>()
        val json = manifestFile.readText()
        val array = JSONArray(json)
        val result = mutableListOf<LoopMetadata>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                LoopMetadata(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.getString("fileName"),
                    obj.getInt("startSample"),
                    obj.getInt("endSample"),
                    obj.getInt("totalSamples")
                )
            )
        }
        result
    }

    suspend fun loadLoopData(metadata: LoopMetadata): ShortArray = withContext(Dispatchers.IO) {
        val file = File(loopsDir, metadata.fileName)
        val size = file.length().toInt()
        val bytes = ByteArray(size)
        FileInputStream(file).use { it.read(bytes) }
        val shorts = ShortArray(size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        shorts
    }

    suspend fun deleteLoop(metadata: LoopMetadata) = withContext(Dispatchers.IO) {
        File(loopsDir, metadata.fileName).delete()
        val loops = getAllLoops().filter { it.id != metadata.id }
        saveManifest(loops)
    }

    suspend fun updateLoop(metadata: LoopMetadata) = withContext(Dispatchers.IO) {
        val loops = getAllLoops().map { if (it.id == metadata.id) metadata else it }
        saveManifest(loops)
    }

    private fun saveManifest(loops: List<LoopMetadata>) {
        val array = JSONArray()
        loops.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("fileName", it.fileName)
                put("startSample", it.startSample)
                put("endSample", it.endSample)
                put("totalSamples", it.totalSamples)
            })
        }
        manifestFile.writeText(array.toString())
    }
}
