package com.wkonda.cubesuite.looper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wkonda.cubesuite.looper.audio.AudioAnalyzer
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopMetadata
import com.wkonda.cubesuite.looper.data.LoopRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val bpm: Double = 0.0,
    val timeSignature: String = "4/4",
    val spectrogram: List<List<Double>> = emptyList(),
    val showSpectrogram: Boolean = false,
    val activeLoop: LoopMetadata? = null,
    val loops: List<LoopMetadata> = emptyList(),
    val showSaveDialog: Boolean = false,
    val loopName: String = "New Loop"
)

class LooperViewModel(
    private val engine: LooperEngine,
    private val repository: LoopRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LooperUiState())
    val uiState: StateFlow<LooperUiState> = _uiState.asStateFlow()

    private val analyzer = AudioAnalyzer()

    init {
        viewModelScope.launch {
            engine.recordingData.collect { data ->
                _uiState.update { it.copy(recordingData = data) }
                if (data != null && _uiState.value.activeLoop == null) {
                    _uiState.update {
                        it.copy(
                            startSample = 0,
                            endSample = data.size,
                            showSpectrogram = false
                        )
                    }
                    updateSpectrogram(data)
                }
            }
        }
        viewModelScope.launch {
            engine.playbackPosition.collect { pos -> _uiState.update { it.copy(playbackPosition = pos) } }
        }
        loadLoops()
    }

    fun setScreen(screen: String) {
        _uiState.update { it.copy(screen = screen) }
        if (screen == "library") loadLoops()
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
            }
            engine.startRecording()
        }
    }

    fun stopRecording() {
        engine.stopRecording()
        _uiState.update { it.copy(isRecording = false) }
    }

    fun togglePlayback() {
        viewModelScope.launch {
            if (_uiState.value.isPlaying) {
                engine.stopPlayback()
                _uiState.update { it.copy(isPlaying = false) }
            } else {
                engine.setLoopPoints(_uiState.value.startSample, _uiState.value.endSample)
                engine.startPlayback()
                _uiState.update { it.copy(isPlaying = true) }
                updatePlaybackProgress()
            }
        }
    }

    private fun updatePlaybackProgress() {
        viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                val len = _uiState.value.endSample - _uiState.value.startSample
                if (len > 0) {
                    engine.updatePlaybackPosition(engine.getPlaybackHeadPosition() % len)
                }
                delay(16)
            }
        }
    }

    fun analyze() {
        val data = _uiState.value.recordingData ?: return
        if (!_uiState.value.showSpectrogram) {
            val res = analyzer.analyze(data)
            _uiState.update {
                it.copy(
                    startSample = res.startSample,
                    endSample = res.endSample,
                    bpm = res.bpm,
                    timeSignature = res.timeSignature,
                    showSpectrogram = true
                )
            }
        } else {
            val res = analyzer.snapToSeamlessLoop(
                data,
                _uiState.value.startSample,
                _uiState.value.endSample,
                _uiState.value.bpm,
                _uiState.value.timeSignature
            )
            _uiState.update {
                it.copy(
                    startSample = res.startSample,
                    endSample = res.endSample,
                    bpm = res.bpm
                )
            }
        }
    }

    fun updateStart(s: Int) {
        _uiState.update { it.copy(startSample = s.coerceIn(0, it.endSample - 100)) }
    }

    fun updateEnd(e: Int) {
        _uiState.update {
            it.copy(
                endSample = e.coerceIn(
                    it.startSample + 100,
                    it.recordingData?.size ?: 0
                )
            )
        }
    }

    fun showSave() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    fun hideSave() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun updateLoopName(name: String) {
        _uiState.update { it.copy(loopName = name) }
    }

    fun saveOrUpdate() {
        viewModelScope.launch {
            val state = _uiState.value
            val data = state.recordingData ?: return@launch
            if (state.activeLoop != null) {
                val updated = state.activeLoop.copy(
                    startSample = state.startSample,
                    endSample = state.endSample
                )
                repository.updateLoop(updated)
                _uiState.update { it.copy(activeLoop = updated) }
            } else {
                val saved =
                    repository.saveLoop(state.loopName, data, state.startSample, state.endSample)
                _uiState.update { it.copy(activeLoop = saved, showSaveDialog = false) }
            }
        }
    }

    fun loadLoop(loop: LoopMetadata) {
        viewModelScope.launch {
            val data = repository.loadLoopData(loop)
            engine.loadData(data, loop.startSample, loop.endSample)
            _uiState.update {
                it.copy(
                    activeLoop = loop,
                    startSample = loop.startSample,
                    endSample = loop.endSample,
                    screen = "looper",
                    showSpectrogram = false
                )
            }
            updateSpectrogram(data)
        }
    }

    fun deleteLoop(loop: LoopMetadata) {
        viewModelScope.launch {
            repository.deleteLoop(loop)
            if (_uiState.value.activeLoop?.id == loop.id) _uiState.update { it.copy(activeLoop = null) }
            loadLoops()
        }
    }

    private fun loadLoops() {
        viewModelScope.launch {
            val loops = repository.getAllLoops()
            _uiState.update { it.copy(loops = loops) }
        }
    }

    private fun updateSpectrogram(data: ShortArray) {
        viewModelScope.launch {
            val spec = analyzer.getSpectrogram(data, 82.41f, 329.63f, 400, 24)
            _uiState.update { it.copy(spectrogram = spec) }
        }
    }
}
