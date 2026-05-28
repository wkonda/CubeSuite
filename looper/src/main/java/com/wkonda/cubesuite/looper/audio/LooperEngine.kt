package com.wkonda.cubesuite.looper.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class LooperEngine {
    private val sampleRate = 48000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false

    private val _recordingData = MutableStateFlow<ShortArray?>(null)
    val recordingData: StateFlow<ShortArray?> = _recordingData

    private val _playbackPosition = MutableStateFlow(0)
    val playbackPosition: StateFlow<Int> = _playbackPosition

    private var currentData: ShortArray? = null
    private var startSample = 0
    private var endSample = 0

    @SuppressLint("MissingPermission")
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val dataList = mutableListOf<Short>()
        val buffer = ShortArray(bufferSize)

        audioRecord?.startRecording()
        isRecording = true

        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            if (read > 0) {
                for (i in 0 until read) {
                    dataList.add((buffer[i] * 3).toShort())
                }
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        currentData = dataList.toShortArray()
        startSample = 0
        endSample = currentData?.size ?: 0
        _recordingData.value = currentData
    }

    fun stopRecording() {
        isRecording = false
    }

    fun setLoopPoints(start: Int, end: Int) {
        startSample = start
        endSample = end
    }

    suspend fun startPlayback() = withContext(Dispatchers.IO) {
        val data = currentData ?: return@withContext
        if (startSample >= endSample || endSample > data.size) return@withContext

        val loopLength = endSample - startSample
        val crossfadeLen =
            (sampleRate * 0.005).toInt().coerceAtMost(loopLength / 4) // 5ms crossfade

        // Create a copy of the loop data to apply crossfade for seamless looping
        val loopBuffer = ShortArray(loopLength)
        System.arraycopy(data, startSample, loopBuffer, 0, loopLength)

        // Apply crossfade: blend the beginning of the loop into the end
        for (i in 0 until crossfadeLen) {
            val ratio = i.toDouble() / crossfadeLen
            val endIdx = loopLength - crossfadeLen + i
            val startIdx = i

            val startSampleVal = loopBuffer[startIdx].toDouble()
            val endSampleVal = loopBuffer[endIdx].toDouble()

            // Result = End * (1-ratio) + Start * ratio
            val blended = (endSampleVal * (1.0 - ratio) + startSampleVal * ratio).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            loopBuffer[endIdx] = blended.toShort()
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(loopLength * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(loopBuffer, 0, loopLength)
        audioTrack?.setLoopPoints(0, loopLength, -1)
        audioTrack?.play()
        isPlaying = true
    }

    fun stopPlayback() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _playbackPosition.value = 0
    }

    fun getPlaybackHeadPosition(): Int {
        return (audioTrack?.playbackHeadPosition ?: 0)
    }

    fun updatePlaybackPosition(pos: Int) {
        _playbackPosition.value = pos
    }

    fun loadData(data: ShortArray, start: Int, end: Int) {
        currentData = data
        startSample = start
        endSample = end
        _recordingData.value = data
    }
}
