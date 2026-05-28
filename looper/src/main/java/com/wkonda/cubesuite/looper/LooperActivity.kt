package com.wkonda.cubesuite.looper

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.wkonda.cubesuite.ui.theme.TextGrayBox
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

                        Column(modifier = Modifier
                            .fillMaxSize()
                            .background(AppDarkBackground)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("LIBRARY", color = CyanAccent, fontWeight = FontWeight.Bold)
                                Text(
                                    "CLOSE",
                                    color = Color.Gray,
                                    modifier = Modifier.clickable { currentScreen = "looper" },
                                    style = MaterialTheme.typography.labelLarge
                                )
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
    var startSample by remember { mutableIntStateOf(0) }
    var endSample by remember { mutableIntStateOf(0) }
    var detectedBpm by remember { mutableDoubleStateOf(0.0) }
    var detectedSignature by remember { mutableStateOf("4/4") }
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
            spectrogramData = analyzer.getSpectrogram(it, 82.41f, 329.63f, 400, 24)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDarkBackground)
            .padding(8.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CUBE LOOPER",
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (detectedBpm > 0) {
                    Text(
                        "  |  ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                detectedBpm
                            )
                        } BPM ($detectedSignature)",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = onOpenLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextGrayBox,
                    contentColor = CyanAccent
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("LIBRARY", style = MaterialTheme.typography.labelSmall)
            }
        }

        // --- Visualization ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, TextGrayBox, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
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

        // --- Simple Controls Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LooperControlButton(
                text = if (isRecording) "STOP" else "REC",
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
                isActive = isRecording,
                enabled = !isPlaying,
                modifier = Modifier.weight(1f)
            )

            LooperControlButton(
                text = if (isPlaying) "STOP" else "PLAY",
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
                isActive = isPlaying,
                enabled = !isRecording,
                modifier = Modifier.weight(1f)
            )

            LooperControlButton(
                text = "ANALYZE",
                onClick = {
                    recordingData?.let { data ->
                        if (!showSpectrogram) {
                            val result = analyzer.analyze(data)
                            startSample = result.startSample
                            endSample = result.endSample
                            detectedBpm = result.bpm
                            detectedSignature = result.timeSignature
                            showSpectrogram = true
                        } else {
                            // Refine existing selection
                            val result = analyzer.snapToSeamlessLoop(
                                data,
                                startSample,
                                endSample,
                                detectedBpm,
                                detectedSignature
                            )
                            startSample = result.startSample
                            endSample = result.endSample
                            detectedBpm = result.bpm
                        }
                    }
                },
                enabled = recordingData != null && !isPlaying && !isRecording,
                modifier = Modifier.weight(1.5f)
            )

            LooperControlButton(
                text = if (loadedMetadata != null) "UPDATE" else "SAVE",
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
                enabled = recordingData != null && !isPlaying && !isRecording,
                modifier = Modifier.weight(1.5f)
            )

            if (recordingData != null) {
                SampleAdjustmentGroup(
                    label = "S",
                    value = startSample,
                    onValueChange = { startSample = it.coerceIn(0, endSample - 100) },
                    enabled = !isPlaying && !isRecording,
                    modifier = Modifier.weight(2.5f)
                )
                SampleAdjustmentGroup(
                    label = "E",
                    value = endSample,
                    onValueChange = {
                        endSample = it.coerceIn(startSample + 100, recordingData?.size ?: 0)
                    },
                    enabled = !isPlaying && !isRecording,
                    modifier = Modifier.weight(2.5f)
                )
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

@Composable
fun LooperControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) ModTrackRed else TextGrayBox,
            contentColor = if (isActive) Color.White else CyanAccent,
            disabledContainerColor = TextGrayBox,
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SampleAdjustmentGroup(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TextGrayBox,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )

            IconButton(
                onClick = { onValueChange(value - 100) },
                enabled = enabled,
                modifier = Modifier.size(24.dp)
            ) {
                Text("-", color = if (enabled) CyanAccent else Color.Gray, fontSize = 16.sp)
            }

            Text(
                value.toString(),
                color = if (enabled) CyanAccent else Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { onValueChange(value + 100) },
                enabled = enabled,
                modifier = Modifier.size(24.dp)
            ) {
                Text("+", color = if (enabled) CyanAccent else Color.Gray, fontSize = 16.sp)
            }
        }
    }
}
