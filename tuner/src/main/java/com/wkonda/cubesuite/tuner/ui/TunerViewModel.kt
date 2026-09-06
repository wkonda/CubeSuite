package com.wkonda.cubesuite.tuner.ui

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import com.wkonda.cubesuite.tuner.audio.AudioEngine
import com.wkonda.cubesuite.tuner.model.TunerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class TunerViewModel : ViewModel() {
    private val engine = AudioEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val historySize = 180

    private val _latest = MutableStateFlow(TunerFrame.empty())
    val latest: StateFlow<TunerFrame> = _latest

    private val _history = MutableStateFlow<List<TunerFrame>>(emptyList())
    val history: StateFlow<List<TunerFrame>> = _history

    private val _capturePath = MutableStateFlow<String?>(null)
    val capturePath: StateFlow<String?> = _capturePath

    private val deque = ArrayDeque<TunerFrame>(historySize)
    private var collectorJob: Job? = null

    init {
        collectorJob = scope.launch {
            engine.frames.collect { frame ->
                _latest.value = frame
                deque.addLast(frame)
                while (deque.size > historySize) {
                    deque.removeFirst()
                }
                _history.value = deque.toList()
            }
        }
        scope.launch(Dispatchers.IO) {
            engine.start()
        }
    }

    fun captureExample(context: Context) {
        scope.launch(Dispatchers.IO) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = context.applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: context.applicationContext.filesDir
            val file = File(dir, "tuner_guitar_$stamp.wav")
            val path = engine.captureTo(file)
            _capturePath.value = path
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        engine.close()
        scope.cancel()
        super.onCleared()
    }
}
