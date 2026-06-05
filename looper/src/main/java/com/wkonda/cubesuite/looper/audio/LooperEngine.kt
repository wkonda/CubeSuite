package com.wkonda.cubesuite.looper.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class LooperEngine {
    private val sR = LooperConfig.SAMPLE_RATE
    private val enc = AudioFormat.ENCODING_PCM_16BIT
    private val bS = AudioRecord.getMinBufferSize(sR, AudioFormat.CHANNEL_IN_MONO, enc)

    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var isR = false

    private val _data = MutableStateFlow<ShortArray?>(null)
    val recordingData: StateFlow<ShortArray?> = _data

    private val _pos = MutableStateFlow(0)
    val playbackPosition: StateFlow<Int> = _pos

    private var curD: ShortArray? = null
    private var sS = 0
    var eS = 0

    @SuppressLint("MissingPermission")
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        rec = AudioRecord(MediaRecorder.AudioSource.MIC, sR, AudioFormat.CHANNEL_IN_MONO, enc, bS)
        val maxSamples = LooperConfig.MAX_RECORD_SAMPLES
        val recordingBuffer = ShortArray(maxSamples)
        var writeIndex = 0
        var bufferFull = false
        val buf = ShortArray(bS)
        rec?.startRecording(); isR = true
        var lastUpdate = 0L
        while (isR) {
            val r = rec?.read(buf, 0, bS) ?: 0
            if (r > 0) {
                for (i in 0 until r) {
                    recordingBuffer[writeIndex] = buf[i]
                    writeIndex++
                    if (writeIndex >= maxSamples) {
                        writeIndex = 0
                        bufferFull = true
                    }
                }
                val now = System.currentTimeMillis()
                if ((now - lastUpdate) > 100) {
                    _data.value = getSnapshot(recordingBuffer, writeIndex, bufferFull, maxSamples)
                    lastUpdate = now
                }
            }
        }
        rec?.stop(); rec?.release(); rec = null
        curD = getSnapshot(recordingBuffer, writeIndex, bufferFull, maxSamples)
        sS = 0; eS = curD?.size ?: 0; _data.value = curD
    }

    private fun getSnapshot(
        buffer: ShortArray,
        writeIndex: Int,
        full: Boolean,
        max: Int
    ): ShortArray {
        val size = if (full) max else writeIndex
        val result = ShortArray(size)
        if (!full) {
            System.arraycopy(buffer, 0, result, 0, writeIndex)
        } else {
            System.arraycopy(buffer, writeIndex, result, 0, max - writeIndex)
            System.arraycopy(buffer, 0, result, max - writeIndex, writeIndex)
        }
        return result
    }

    fun stopRecording() {
        isR = false
    }

    fun setLoopPoints(s: Int, e: Int) {
        sS = s; eS = e
    }

    suspend fun startPlayback() = withContext(Dispatchers.IO) {
        val d = curD ?: return@withContext
        if (sS >= eS || eS > d.size) return@withContext
        val len = eS - sS
        val xL = (sR * LooperConfig.CROSSFADE_MS / 1000).coerceAtMost(len / 4)
        val buf = ShortArray(len); System.arraycopy(d, sS, buf, 0, len)
        for (i in 0 until xL) {
            val r = i.toDouble() / xL;
            val eI = len - xL + i
            buf[eI] = (buf[eI] * (1.0 - r) + buf[i] * r).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        track = AudioTrack.Builder().setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
        )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(enc).setSampleRate(sR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(len * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track?.write(buf, 0, len); track?.setLoopPoints(0, len, -1); track?.play()
    }

    fun stopPlayback() {
        track?.stop(); track?.release(); track = null; _pos.value = 0
    }

    fun getPlaybackHeadPosition() = track?.playbackHeadPosition ?: 0
    fun updatePlaybackPosition(p: Int) {
        _pos.value = p
    }

    fun loadData(d: ShortArray, s: Int, e: Int) {
        curD = d; sS = s; eS = e; _data.value = d
    }
}
