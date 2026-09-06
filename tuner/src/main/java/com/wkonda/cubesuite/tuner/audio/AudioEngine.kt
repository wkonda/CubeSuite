package com.wkonda.cubesuite.tuner.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.wkonda.cubesuite.tuner.model.TunerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioEngine(
    private val sampleRate: Int = 44_100,
    private val preferredBufferSize: Int = 4_096
) {
    private external fun nativeInit(sampleRate: Int, bufferSize: Int): Boolean
    private external fun nativeProcess(samples: ShortArray, count: Int): FloatArray

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _frames = MutableSharedFlow<TunerFrame>(
        extraBufferCapacity = 24,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val frames: SharedFlow<TunerFrame> = _frames

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var capture: WavCapture? = null
    private var running = false

    init {
        System.loadLibrary("tuner_engine")
    }

    fun start(): Boolean {
        stop()

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = when {
            minBufferSize <= 0 -> preferredBufferSize
            else -> maxOf(minBufferSize, preferredBufferSize)
        }

        if (!nativeInit(sampleRate, bufferSize)) {
            return false
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        audioRecord = record
        running = true
        record.startRecording()

        recordJob = scope.launch {
            val buffer = ShortArray(bufferSize)
            while (running && isActive) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    synchronized(this@AudioEngine) {
                        val activeCapture = capture
                        if (activeCapture?.write(buffer, read) == true) {
                            capture = null
                        }
                    }
                    val raw = nativeProcess(buffer, read)
                    if (raw.size >= 12) {
                        val cents = FloatArray(6) { raw[it] }
                        val active = BooleanArray(6) { raw[it + 6] > 0.5f }
                        _frames.tryEmit(TunerFrame(cents = cents, active = active))
                    }
                }
            }
        }

        return true
    }

    fun captureTo(file: File, seconds: Int = 8): String {
        synchronized(this) {
            capture?.close()
            capture = WavCapture(
                file = file,
                sampleRate = sampleRate,
                maxSamples = sampleRate * seconds.coerceAtLeast(1)
            )
            return capture!!.path()
        }
    }

    fun stop() {
        running = false
        recordJob?.cancel()
        recordJob = null
        synchronized(this) {
            capture?.close()
            capture = null
        }
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
    }

    fun close() {
        stop()
        scope.cancel()
    }
}
