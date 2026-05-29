package com.wkonda.cubesuite.looper.data

import android.content.Context
import com.wkonda.cubesuite.looper.audio.LooperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LoopRepository(context: Context) {
    private val dir = File(context.filesDir, "loops").apply { mkdirs() }
    private val manifest = File(dir, "manifest.json")

    suspend fun saveLoop(
        name: String,
        data: ShortArray,
        start: Int,
        end: Int,
        bpm: Double?,
        sig: String
    ) = withContext(Dispatchers.IO) {
        val id = System.currentTimeMillis().toString()
        val file = File(dir, "$id.pcm")
        FileOutputStream(file).use { fos ->
            val buf = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.forEach { buf.putShort(it) }
            fos.write(buf.array())
        }
        val meta = LoopMetadata(
            id,
            name,
            file.name,
            start,
            end,
            data.size,
            bpm,
            sig,
            LooperConfig.SAMPLE_RATE
        )
        saveManifest(getAllLoops() + meta)
        meta
    }

    suspend fun getAllLoops(): List<LoopMetadata> = withContext(Dispatchers.IO) {
        if (!manifest.exists()) return@withContext emptyList()
        val arr = JSONArray(manifest.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            LoopMetadata(
                o.getString("id"), o.getString("name"), o.getString("file"),
                o.getInt("start"), o.getInt("end"), o.getInt("total"),
                if (o.has("bpm")) o.getDouble("bpm") else null, o.optString("sig", "4/4"),
                o.optInt("rate", LooperConfig.SAMPLE_RATE)
            )
        }
    }

    suspend fun loadLoopData(meta: LoopMetadata) = withContext(Dispatchers.IO) {
        val file = File(dir, meta.fileName)
        val bytes = ByteArray(file.length().toInt())
        FileInputStream(file).use { it.read(bytes) }
        ShortArray(bytes.size / 2).also {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it)
        }
    }

    suspend fun deleteLoop(meta: LoopMetadata) = withContext(Dispatchers.IO) {
        File(dir, meta.fileName).delete()
        saveManifest(getAllLoops().filter { it.id != meta.id })
    }

    suspend fun updateLoop(meta: LoopMetadata) = withContext(Dispatchers.IO) {
        saveManifest(getAllLoops().map { if (it.id == meta.id) meta else it })
    }

    private fun saveManifest(loops: List<LoopMetadata>) {
        val arr = JSONArray()
        loops.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("file", it.fileName)
                put("start", it.startSample); put("end", it.endSample); put(
                "total",
                it.totalSamples
            )
                put("bpm", it.bpm ?: JSONObject.NULL); put("sig", it.timeSignature); put(
                "rate",
                it.sampleRate
            )
            })
        }
        manifest.writeText(arr.toString())
    }
}
