package com.wkonda.cubesuite.looper

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.wkonda.cubesuite.looper.audio.AudioAnalyzer
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopMetadata
import com.wkonda.cubesuite.looper.data.LoopRepository
import com.wkonda.cubesuite.looper.ui.FFTVisualizer
import com.wkonda.cubesuite.looper.ui.LoopListScreen
import com.wkonda.cubesuite.looper.ui.WaveformView
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class LooperActivity : ComponentActivity() {
    private val engine = LooperEngine()
    private val repository by lazy { LoopRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)

        setContent {
            CubeSuiteTheme {
                Surface(color = AppDarkBackground, modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("looper") }
                    var activeLoopMetadata by remember { mutableStateOf<LoopMetadata?>(null) }

                    if (currentScreen == "looper") {
                        LooperScreen(
                            engine = engine,
                            repository = repository,
                            loadedMetadata = activeLoopMetadata,
                            onOpenLibrary = { currentScreen = "library" },
                            onLoopSaved = { activeLoopMetadata = it }
                        )
                    } else {
                        var loops by remember { mutableStateOf(emptyList<LoopMetadata>()) }
                        LaunchedEffect(Unit) {
                            loops = repository.getAllLoops()
                        }

                        Column {
                            Button(
                                onClick = { currentScreen = "looper" },
                                modifier = Modifier.padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Back to Looper", color = Color.Black)
                            }
                            LoopListScreen(
                                loops = loops,
                                onLoopSelected = { loop ->
                                    lifecycleScope.launch {
                                        val data = repository.loadLoopData(loop)
                                        engine.loadData(data, loop.startSample, loop.endSample)
                                        activeLoopMetadata = loop
                                        currentScreen = "looper"
                                    }
                                },
                                onDeleteLoop = { loop ->
                                    lifecycleScope.launch {
                                        repository.deleteLoop(loop)
                                        if (activeLoopMetadata?.id == loop.id) activeLoopMetadata =
                                            null
                                        loops = repository.getAllLoops()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LooperScreen(
    engine: LooperEngine,
    repository: LoopRepository,
    loadedMetadata: LoopMetadata?,
    onOpenLibrary: () -> Unit,
    onLoopSaved: (LoopMetadata?) -> Unit
) {
    val recordingData by engine.recordingData.collectAsState()
    val playbackPosition by engine.playbackPosition.collectAsState()
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var startSample by remember { mutableStateOf(0) }
    var endSample by remember { mutableStateOf(0) }
    var detectedBpm by remember { mutableDoubleStateOf(0.0) }
    var spectrogramData by remember { mutableStateOf(emptyList<List<Double>>()) }
    var showSpectrogram by remember { mutableStateOf(false) }
    var loopName by remember { mutableStateOf("New Loop") }
    var showSaveDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val analyzer = remember { AudioAnalyzer() }

    LaunchedEffect(recordingData) {
        recordingData?.let {
            if (loadedMetadata != null) {
                startSample = loadedMetadata.startSample
                endSample = loadedMetadata.endSample
                loopName = loadedMetadata.name
                showSpectrogram = false
            } else {
                startSample = 0
                endSample = it.size
                showSpectrogram = false
            }
            spectrogramData = analyzer.getSpectrogram(it, 82f, 330f, 400, 40)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LOOPER", style = MaterialTheme.typography.headlineLarge, color = ModTrackRed)
                if (detectedBpm > 0) {
                    Text(
                        "BPM: ${String.format(Locale.US, "%.1f", detectedBpm)}",
                        color = CyanAccent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Button(
                onClick = onOpenLibrary,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Library", color = Color.Black)
            }
        }

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            if (showSpectrogram) {
                FFTVisualizer(
                    spectrogram = spectrogramData,
                    totalSamples = recordingData?.size ?: 0,
                    startSample = startSample,
                    endSample = endSample,
                    onStartChanged = { startSample = it },
                    onEndChanged = { endSample = it },
                    playbackPosition = playbackPosition,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                WaveformView(
                    data = recordingData,
                    startSample = startSample,
                    endSample = endSample,
                    onStartChanged = { startSample = it },
                    onEndChanged = { endSample = it },
                    playbackPosition = playbackPosition,
                    isPlaying = isPlaying
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        engine.stopRecording()
                        isRecording = false
                        onLoopSaved(null)
                    } else {
                        scope.launch {
                            isRecording = true
                            spectrogramData = emptyList()
                            showSpectrogram = false
                            engine.startRecording()
                        }
                    }
                },
                enabled = !isPlaying,
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) ModTrackRed else CyanAccent)
            ) {
                Text(
                    if (isRecording) "Stop Record" else "Record",
                    color = if (isRecording) Color.White else Color.Black
                )
            }

            Button(
                onClick = {
                    if (isPlaying) {
                        engine.stopPlayback()
                        isPlaying = false
                    } else {
                        scope.launch {
                            engine.setLoopPoints(startSample, endSample)
                            engine.startPlayback()
                            isPlaying = true
                        }
                    }
                },
                enabled = !isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) ModTrackRed else CyanAccent)
            ) {
                Text(
                    if (isPlaying) "Stop Play" else "Play",
                    color = if (isPlaying) Color.White else Color.Black
                )
            }

            Button(
                onClick = {
                    recordingData?.let {
                        val result = analyzer.analyze(it)
                        startSample = result.startSample
                        endSample = result.endSample
                        detectedBpm = result.bpm
                        showSpectrogram = true
                    }
                },
                enabled = recordingData != null && !isPlaying && !isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Analyze", color = Color.Black)
            }

            Button(
                onClick = {
                    if (loadedMetadata != null) {
                        scope.launch {
                            val updated = loadedMetadata.copy(
                                startSample = startSample,
                                endSample = endSample
                            )
                            repository.updateLoop(updated)
                            onLoopSaved(updated)
                        }
                    } else {
                        showSaveDialog = true
                    }
                },
                enabled = !isPlaying && !isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text(
                    if (loadedMetadata != null) "Update" else "Save",
                    color = if (isPlaying || isRecording) Color.Gray else Color.Black
                )
            }
        }

        if (recordingData != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { startSample = maxOf(0, startSample - 100) },
                    enabled = !isPlaying && !isRecording
                ) {
                    Text("-", color = if (isPlaying || isRecording) Color.Gray else CyanAccent)
                }
                Text(
                    "Start: $startSample",
                    color = if (isPlaying || isRecording) Color.Gray else Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(
                    onClick = { startSample = minOf(endSample, startSample + 100) },
                    enabled = !isPlaying && !isRecording
                ) {
                    Text("+", color = if (isPlaying || isRecording) Color.Gray else CyanAccent)
                }
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = { endSample = maxOf(startSample, endSample - 100) },
                    enabled = !isPlaying && !isRecording
                ) {
                    Text("-", color = if (isPlaying || isRecording) Color.Gray else CyanAccent)
                }
                Text(
                    "End: $endSample",
                    color = if (isPlaying || isRecording) Color.Gray else Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = {
                    endSample = minOf(recordingData?.size ?: 0, endSample + 100)
                }, enabled = !isPlaying && !isRecording) {
                    Text("+", color = if (isPlaying || isRecording) Color.Gray else CyanAccent)
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Loop") },
            text = { TextField(value = loopName, onValueChange = { loopName = it }) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        recordingData?.let {
                            val saved = repository.saveLoop(loopName, it, startSample, endSample)
                            onLoopSaved(saved)
                        }
                        showSaveDialog = false
                    }
                }) { Text("Save") }
            }
        )
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val loopLength = endSample - startSample
            if (loopLength > 0) {
                while (isPlaying) {
                    engine.updatePlaybackPosition(engine.getPlaybackHeadPosition() % loopLength)
                    delay(16)
                }
            }
        }
    }
}
