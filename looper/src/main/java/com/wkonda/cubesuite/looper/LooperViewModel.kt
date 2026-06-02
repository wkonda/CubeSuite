package com.wkonda.cubesuite.looper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wkonda.cubesuite.looper.audio.AudioAnalyzer
import com.wkonda.cubesuite.looper.audio.ChordRegion
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopMetadata
import com.wkonda.cubesuite.looper.data.LoopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LooperUiState(
    val screen: String = "looper",
    val recordingData: ShortArray? = null,
    val playbackPosition: Int = 0,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isAnalyzing: Boolean = false,
    val startSample: Int = 0,
    val endSample: Int = 0,
    val onsets: List<Int> = emptyList(),
    val beatGrid: List<Int> = emptyList(),
    val chords: List<ChordRegion> = emptyList(),
    val signature: Pair<Int, Int> = 4 to 4,
    val bars: Int = 4,
    val spectrogram: List<List<Double>> = emptyList(),
    val showSpectrogram: Boolean = false,
    val activeLoop: LoopMetadata? = null,
    val loops: List<LoopMetadata> = emptyList(),
    val showSaveDialog: Boolean = false,
    val loopName: String = "New Loop",
    val liveBpm: Double = 0.0,
    val correlationCurve: List<Pair<Int, Double>> = emptyList(),
    val rhythmicCurve: List<Pair<Int, Double>> = emptyList(),
) {
    val totalBeats: Int get() = bars * signature.first
    val bpm: Double?
        get() {
            if (isRecording) return liveBpm
            if (recordingData == null || (endSample - startSample) <= 0) return null
            return (totalBeats.toDouble() * 60.0 * LooperConfig.SAMPLE_RATE) / (endSample - startSample)
        }
}

class LooperViewModel(private val engine: LooperEngine, private val repository: LoopRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(LooperUiState())
    val uiState = _uiState.asStateFlow()
    private val analyzer = AudioAnalyzer()

    init {
        viewModelScope.launch {
            engine.recordingData.collect { d ->
                if (d == null) return@collect
                _uiState.update { it.copy(recordingData = d) }
                if (_uiState.value.isRecording) _uiState.update {
                    it.copy(
                        startSample = 0,
                        endSample = d.size
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.playbackPosition.collect { p -> _uiState.update { it.copy(playbackPosition = p) } }
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
                    onsets = emptyList(),
                    beatGrid = emptyList(),
                    chords = emptyList(),
                    correlationCurve = emptyList(),
                    rhythmicCurve = emptyList(),
                    activeLoop = null,
                    liveBpm = 0.0
                )
            }
            engine.startRecording()
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
            )
            engine.startPlayback(); _uiState.update { it.copy(isPlaying = true) }; updatePlaybackProgress()
        }
    }

    private fun updatePlaybackProgress() = viewModelScope.launch {
        while (_uiState.value.isPlaying) {
            val l = _uiState.value.endSample - _uiState.value.startSample
            if (l > 0) engine.updatePlaybackPosition(engine.getPlaybackHeadPosition() % l)
            delay(16)
        }
    }

    fun analyze() = viewModelScope.launch {
        val d = _uiState.value.recordingData ?: return@launch
        _uiState.update { it.copy(isAnalyzing = true) }
        withContext(Dispatchers.Default) {
            val r = analyzer.analyze(d, _uiState.value.startSample, _uiState.value.bars)
            _uiState.update {
                it.copy(
                    startSample = r.startSample,
                    endSample = r.endSample,
                    onsets = r.onsets,
                    beatGrid = r.beatGrid,
                    chords = r.chords,
                    spectrogram = analyzer.getSpectrogram(d),
                    correlationCurve = r.correlationCurve,
                    rhythmicCurve = r.rhythmicCurve,
                    isAnalyzing = false
                )
            }
            updateGrid()
        }
    }

    private fun updateGrid() {
        val s = _uiState.value
        if ((s.endSample - s.startSample) <= 0) return
        val grid =
            List(s.totalBeats) { i -> s.startSample + (i * (s.endSample - s.startSample).toDouble() / s.totalBeats).toInt() }
        _uiState.update { it.copy(beatGrid = grid) }
    }

    fun updateStart(s: Int) {
        _uiState.update { it.copy(startSample = s) }; updateGrid()
    }

    fun updateEnd(e: Int) {
        _uiState.update { it.copy(endSample = e) }; updateGrid()
    }

    fun showSave() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    fun hideSave() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun updateLoopName(n: String) {
        _uiState.update { it.copy(loopName = n) }
    }

    fun toggleView() {
        _uiState.update { it.copy(showSpectrogram = !it.showSpectrogram) }
    }

    fun setUserSignature(num: Int, den: Int) {
        _uiState.update { it.copy(signature = num to den) }; updateGrid()
    }

    fun setUserBars(bars: Int) {
        _uiState.update { it.copy(bars = bars) }; updateGrid()
    }

    fun saveOrUpdate() = viewModelScope.launch {
        val s = _uiState.value
        val d = s.recordingData ?: return@launch
        val sigString = "${s.signature.first}/${s.signature.second}"
        if (s.activeLoop != null) {
            val updated = s.activeLoop.copy(
                name = s.loopName,
                startSample = s.startSample,
                endSample = s.endSample,
                bpm = s.bpm,
                timeSignature = sigString,
                bars = s.bars
            )
            repository.updateLoop(updated); _uiState.update {
                it.copy(
                    showSaveDialog = false,
                    activeLoop = updated
                )
            }
        } else {
            val meta = repository.saveLoop(
                s.loopName,
                d,
                s.startSample,
                s.endSample,
                s.bpm,
                sigString,
                s.bars
            )
            _uiState.update { it.copy(showSaveDialog = false, activeLoop = meta) }
        }
        loadLoops()
    }

    fun loadLoop(m: LoopMetadata) = viewModelScope.launch {
        engine.stopPlayback();
        val d = repository.loadLoopData(m)
        val sig = m.timeSignature.split("/").let { it[0].toInt() to it[1].toInt() }
        _uiState.update {
            it.copy(
                screen = "looper",
                recordingData = d,
                startSample = m.startSample,
                endSample = m.endSample,
                bars = m.bars,
                signature = sig,
                activeLoop = m,
                loopName = m.name,
                spectrogram = emptyList(),
                onsets = emptyList(),
                beatGrid = emptyList(),
                chords = emptyList(),
                correlationCurve = emptyList(),
                rhythmicCurve = emptyList()
            )
        }
        engine.loadData(d, m.startSample, m.endSample); updateGrid()
    }

    fun deleteLoop(m: LoopMetadata) =
        viewModelScope.launch { repository.deleteLoop(m); loadLoops() }

    fun loadLoops() =
        viewModelScope.launch { _uiState.update { it.copy(loops = repository.getAllLoops()) } }
}
