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
    private val sampleRate = 44100
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

    private var playbackJob: kotlinx.coroutines.Job? = null

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
                    dataList.add(buffer[i])
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

        audioTrack?.write(data, startSample, loopLength)
        audioTrack?.setLoopPoints(0, loopLength, -1) // Loop infinitely
        audioTrack?.play()
        isPlaying = true

        withContext(Dispatchers.Main) {
            // We can't easily launch a long-running coroutine from startPlayback without returning
            // But startPlayback is called from scope.launch in the UI
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
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
