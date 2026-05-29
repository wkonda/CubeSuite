package com.wkonda.cubesuite.looper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wkonda.cubesuite.looper.audio.AudioAnalyzer
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopMetadata
import com.wkonda.cubesuite.looper.data.LoopRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LooperUiState(
    val screen: String = "looper",
    val recordingData: ShortArray? = null,
    val playbackPosition: Int = 0,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val startSample: Int = 0,
    val endSample: Int = 0,
    val bpm: Double? = null,
    val timeSignature: String = "4/4",
    val spectrogram: List<List<Double>> = emptyList(),
    val showSpectrogram: Boolean = false,
    val activeLoop: LoopMetadata? = null,
    val loops: List<LoopMetadata> = emptyList(),
    val showSaveDialog: Boolean = false,
    val loopName: String = "New Loop"
)

class LooperViewModel(private val engine: LooperEngine, private val repository: LoopRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(LooperUiState())
    val uiState = _uiState.asStateFlow()
    private val analyzer = AudioAnalyzer()

    init {
        viewModelScope.launch {
            engine.recordingData.collect { d ->
                if (d == null) return@collect
                val s = _uiState.value
                _uiState.update { it.copy(recordingData = d) }
                if (s.isRecording) _uiState.update { it.copy(startSample = 0, endSample = d.size) }
                else if (s.activeLoop == null && s.spectrogram.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            startSample = 0,
                            endSample = d.size,
                            showSpectrogram = false
                        )
                    }
                    updateSpectrogram(d)
                }
            }
        }
        viewModelScope.launch {
            engine.playbackPosition.collect { p ->
                _uiState.update {
                    it.copy(
                        playbackPosition = p
                    )
                }
            }
        }
        loadLoops()
    }

    fun setScreen(s: String) {
        _uiState.update { it.copy(screen = s) }; if (s == "library") loadLoops()
    }

    fun startRecording() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecording = true,
                    spectrogram = emptyList(),
                    showSpectrogram = false,
                    activeLoop = null
                )
            }; engine.startRecording()
        }
    }

    fun stopRecording() {
        engine.stopRecording(); _uiState.update { it.copy(isRecording = false) }
    }

    fun togglePlayback() = viewModelScope.launch {
        if (_uiState.value.isPlaying) {
            engine.stopPlayback(); _uiState.update { it.copy(isPlaying = false) }
        } else {
            engine.stopPlayback(); engine.setLoopPoints(
                _uiState.value.startSample,
                _uiState.value.endSample
            ); engine.startPlayback(); _uiState.update { it.copy(isPlaying = true) }; updatePlaybackProgress()
        }
    }

    private fun updatePlaybackProgress() = viewModelScope.launch {
        while (_uiState.value.isPlaying) {
            val l = _uiState.value.endSample - _uiState.value.startSample
            if (l > 0) engine.updatePlaybackPosition(engine.getPlaybackHeadPosition() % l)
            delay(16)
        }
    }

    fun analyze() {
        val d = _uiState.value.recordingData ?: return
        val r = analyzer.analyze(d)
        _uiState.update {
            it.copy(
                startSample = r.startSample,
                endSample = r.endSample,
                bpm = r.bpm,
                timeSignature = r.timeSignature
            )
        }
    }

    fun updateStart(s: Int) =
        _uiState.update { it.copy(startSample = s.coerceIn(0, it.endSample - 100)) }

    fun updateEnd(e: Int) = _uiState.update {
        it.copy(
            endSample = e.coerceIn(
                it.startSample + 100,
                it.recordingData?.size ?: 0
            )
        )
    }

    fun showSave() = _uiState.update { it.copy(showSaveDialog = true) }
    fun hideSave() = _uiState.update { it.copy(showSaveDialog = false) }
    fun updateLoopName(n: String) = _uiState.update { it.copy(loopName = n) }
    fun toggleView() = _uiState.update { it.copy(showSpectrogram = !it.showSpectrogram) }

    fun saveOrUpdate() = viewModelScope.launch {
        val s = _uiState.value;
        val d = s.recordingData ?: return@launch
        if (s.activeLoop != null) {
            val u = s.activeLoop.copy(
                startSample = s.startSample,
                endSample = s.endSample,
                bpm = s.bpm,
                timeSignature = s.timeSignature
            ); repository.updateLoop(u); _uiState.update { it.copy(activeLoop = u) }
        } else {
            val v = repository.saveLoop(
                s.loopName,
                d,
                s.startSample,
                s.endSample,
                s.bpm,
                s.timeSignature
            ); _uiState.update { it.copy(activeLoop = v, showSaveDialog = false) }
        }
    }

    fun loadLoop(l: LoopMetadata) = viewModelScope.launch {
        val d = repository.loadLoopData(l); engine.loadData(d, l.startSample, l.endSample)
        _uiState.update {
            it.copy(
                activeLoop = l,
                startSample = l.startSample,
                endSample = l.endSample,
                bpm = l.bpm,
                timeSignature = l.timeSignature,
                screen = "looper",
                showSpectrogram = false
            )
        }
        updateSpectrogram(d)
    }

    fun deleteLoop(l: LoopMetadata) = viewModelScope.launch {
        repository.deleteLoop(l); if (_uiState.value.activeLoop?.id == l.id) _uiState.update {
        it.copy(activeLoop = null)
    }; loadLoops()
    }

    private fun loadLoops() = viewModelScope.launch {
        val l = repository.getAllLoops(); _uiState.update { it.copy(loops = l) }
    }

    private fun updateSpectrogram(d: ShortArray) = viewModelScope.launch {
        val s = analyzer.getSpectrogram(
            d,
            LooperConfig.FREQ_E2,
            LooperConfig.FREQ_E4,
            400,
            24
        ); _uiState.update { it.copy(spectrogram = s) }
    }
}
