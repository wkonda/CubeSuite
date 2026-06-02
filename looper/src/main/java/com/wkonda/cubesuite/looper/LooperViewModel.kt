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
    val userSignature: Pair<Int, Int>? = null,
    val userBars: Int? = null,
    val suggestedSignature: Pair<Int, Int> = 4 to 4,
    val suggestedBars: Int = 4,
    val spectrogram: List<List<Double>> = emptyList(),
    val showSpectrogram: Boolean = false,
    val activeLoop: LoopMetadata? = null,
    val loops: List<LoopMetadata> = emptyList(),
    val showSaveDialog: Boolean = false,
    val loopName: String = "New Loop",
    val liveBpm: Double = 0.0,
    val correlationCurve: List<Pair<Int, Double>> = emptyList()
) {
    val currentSignature: Pair<Int, Int> get() = userSignature ?: suggestedSignature
    val currentBars: Int get() = userBars ?: suggestedBars

    val totalBeats: Int get() = currentBars * currentSignature.first

    val bpm: Double?
        get() {
            if (isRecording) return liveBpm
            if (recordingData == null) return null
            val durationSamples = (endSample - startSample).toDouble()
            if (durationSamples <= 0) return null
            return (totalBeats.toDouble() * 60.0 * LooperConfig.SAMPLE_RATE) / durationSamples
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
                val s = _uiState.value
                if (s.isRecording) {
                    _uiState.update { it.copy(startSample = 0, endSample = d.size) }
                } else if (s.activeLoop == null && s.spectrogram.isEmpty()) analyze()
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
                    activeLoop = null,
                    liveBpm = 0.0
                )
            }
            engine.startRecording()
        }
    }

    fun stopRecording() {
        engine.stopRecording()
        _uiState.update { it.copy(isRecording = false) }
        analyze()
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
        val s = _uiState.value
        _uiState.update { it.copy(isAnalyzing = true) }

        withContext(Dispatchers.Default) {
            val r = analyzer.analyze(d, s.startSample, s.endSample, 4)
            val spec = analyzer.getSpectrogram(d)

            _uiState.update {
                it.copy(
                    startSample = r.startSample,
                    endSample = r.endSample,
                    onsets = r.onsets,
                    beatGrid = r.beatGrid,
                    chords = r.chords,
                    suggestedBars = r.suggestedBars,
                    suggestedSignature = r.suggestedSignature,
                    spectrogram = spec,
                    correlationCurve = r.correlationCurve,
                    isAnalyzing = false
                )
            }
            updateGrid()
        }
    }

    private fun updateGrid() {
        val s = _uiState.value
        val totalSamples = s.endSample - s.startSample
        if (totalSamples <= 0) return

        val grid = mutableListOf<Int>()
        val totalBeats = s.totalBeats
        val samplesPerBeat = totalSamples / totalBeats
        for (i in 0 until totalBeats) {
            grid.add(s.startSample + i * samplesPerBeat)
        }
        _uiState.update { it.copy(beatGrid = grid) }
    }

    fun updateStart(v: Int) {
        _uiState.update {
            it.copy(
                startSample = v.coerceIn(0, it.endSample - 100),
                correlationCurve = emptyList()
            )
        }
        updateGrid()
    }

    fun updateEnd(v: Int) {
        _uiState.update {
            it.copy(
                endSample = v.coerceIn(
                    it.startSample + 100,
                    it.recordingData?.size ?: 0
                )
            )
        }
        updateGrid()
    }

    fun showSave() = _uiState.update { it.copy(showSaveDialog = true) }
    fun hideSave() = _uiState.update { it.copy(showSaveDialog = false) }
    fun updateLoopName(n: String) = _uiState.update { it.copy(loopName = n) }
    fun toggleView() = _uiState.update { it.copy(showSpectrogram = !it.showSpectrogram) }

    fun setUserSignature(num: Int, den: Int) {
        _uiState.update { it.copy(userSignature = num to den) }
        updateGrid()
    }

    fun clearUserSignature() {
        _uiState.update { it.copy(userSignature = null) }
        updateGrid()
    }

    fun setUserBars(bars: Int?) {
        _uiState.update { it.copy(userBars = bars) }
        updateGrid()
    }

    fun saveOrUpdate() = viewModelScope.launch {
        val s = _uiState.value;
        val d = s.recordingData ?: return@launch
        val sig = "${s.currentSignature.first}/${s.currentSignature.second}"
        if (s.activeLoop != null) {
            val u = s.activeLoop.copy(
                startSample = s.startSample,
                endSample = s.endSample,
                bpm = s.bpm,
                timeSignature = sig
            )
            repository.updateLoop(u); _uiState.update { it.copy(activeLoop = u) }
        } else {
            val v = repository.saveLoop(s.loopName, d, s.startSample, s.endSample, s.bpm, sig)
            _uiState.update { it.copy(activeLoop = v, showSaveDialog = false) }
        }
    }

    fun loadLoop(l: LoopMetadata) = viewModelScope.launch {
        val d = repository.loadLoopData(l); engine.loadData(d, l.startSample, l.endSample)
        _uiState.update {
            it.copy(
                activeLoop = l,
                startSample = l.startSample,
                endSample = l.endSample,
                screen = "looper",
                showSpectrogram = false
            )
        }
        analyze()
    }

    fun deleteLoop(l: LoopMetadata) = viewModelScope.launch {
        repository.deleteLoop(l); if (_uiState.value.activeLoop?.id == l.id) _uiState.update {
        it.copy(
            activeLoop = null
        )
    }; loadLoops()
    }

    private fun loadLoops() = viewModelScope.launch {
        val l = repository.getAllLoops(); _uiState.update { it.copy(loops = l) }
    }
}
