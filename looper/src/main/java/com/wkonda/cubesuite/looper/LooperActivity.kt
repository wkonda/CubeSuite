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
    private val repo by lazy { LoopRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        setContent {
            CubeSuiteTheme {
                Surface(color = AppDarkBackground, modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf("looper") }
                    var active by remember { mutableStateOf<LoopMetadata?>(null) }
                    if (screen == "looper") {
                        LooperScreen(engine, repo, active, { screen = "library" }, { active = it })
                    } else {
                        var loops by remember { mutableStateOf(emptyList<LoopMetadata>()) }
                        LaunchedEffect(Unit) { loops = repo.getAllLoops() }
                        Column(Modifier
                            .fillMaxSize()
                            .background(AppDarkBackground)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text("LIBRARY", color = CyanAccent, fontWeight = FontWeight.Bold)
                                Text(
                                    "CLOSE",
                                    color = Color.Gray,
                                    modifier = Modifier.clickable { screen = "looper" })
                            }
                            LoopListScreen(loops, { loop ->
                                lifecycleScope.launch {
                                    engine.loadData(
                                        repo.loadLoopData(loop),
                                        loop.startSample,
                                        loop.endSample
                                    )
                                    active = loop; screen = "looper"
                                }
                            }, { loop ->
                                lifecycleScope.launch {
                                    repo.deleteLoop(loop)
                                    if (active?.id == loop.id) active = null; loops =
                                    repo.getAllLoops()
                                }
                            })
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
    repo: LoopRepository,
    loaded: LoopMetadata?,
    onLib: () -> Unit,
    onSave: (LoopMetadata?) -> Unit
) {
    val data by engine.recordingData.collectAsState()
    val pos by engine.playbackPosition.collectAsState()
    var isRec by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var start by remember { mutableIntStateOf(0) }
    var end by remember { mutableIntStateOf(0) }
    var bpm by remember { mutableDoubleStateOf(0.0) }
    var sig by remember { mutableStateOf("4/4") }
    var spec by remember { mutableStateOf(emptyList<List<Double>>()) }
    var showSpec by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("New Loop") }
    var showSave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val analyzer = remember { AudioAnalyzer() }

    LaunchedEffect(data) {
        data?.let {
            if (loaded != null) {
                start = loaded.startSample; end = loaded.endSample; showSpec = false
            } else {
                start = 0; end = it.size; showSpec = false
            }
            spec = analyzer.getSpectrogram(it, 82.41f, 329.63f, 400, 24)
        }
    }

    Column(Modifier
        .fillMaxSize()
        .background(AppDarkBackground)
        .padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CUBE LOOPER", color = CyanAccent, fontWeight = FontWeight.Bold)
                if (bpm > 0) Text(
                    "  |  ${String.format(Locale.US, "%.1f", bpm)} BPM ($sig)",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onLib,
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
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, TextGrayBox, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
            if (showSpec) FFTVisualizer(
                spec,
                data?.size ?: 0,
                start,
                end,
                { start = it },
                { end = it },
                Modifier.fillMaxSize(),
                pos,
                isPlaying
            )
            else WaveformView(data, start, end, { start = it }, { end = it }, pos, isPlaying)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            LooperControlButton(if (isRec) "STOP" else "REC", {
                if (isRec) {
                    engine.stopRecording(); isRec = false; onSave(null)
                } else scope.launch {
                    isRec = true; spec = emptyList(); showSpec = false; engine.startRecording()
                }
            }, Modifier.weight(1f), !isPlaying, isRec)
            LooperControlButton(if (isPlaying) "STOP" else "PLAY", {
                if (isPlaying) {
                    engine.stopPlayback(); isPlaying = false
                } else scope.launch {
                    engine.setLoopPoints(
                        start,
                        end
                    ); engine.startPlayback(); isPlaying = true
                }
            }, Modifier.weight(1f), !isRec, isPlaying)
            LooperControlButton("ANALYZE", {
                data?.let {
                    if (!showSpec) {
                        val r = analyzer.analyze(it); start = r.startSample; end =
                            r.endSample; bpm = r.bpm; sig = r.timeSignature; showSpec = true
                    } else {
                        val r = analyzer.snapToSeamlessLoop(it, start, end, bpm, sig); start =
                            r.startSample; end = r.endSample; bpm = r.bpm
                    }
                }
            }, Modifier.weight(1.5f), data != null && !isPlaying && !isRec)
            LooperControlButton(if (loaded != null) "UPDATE" else "SAVE", {
                if (loaded != null) scope.launch {
                    val u = loaded.copy(
                        startSample = start,
                        endSample = end
                    ); repo.updateLoop(u); onSave(u)
                }
                else showSave = true
            }, Modifier.weight(1.5f), data != null && !isPlaying && !isRec)
            if (data != null) {
                Adj(
                    "S",
                    start,
                    { start = it.coerceIn(0, end - 100) },
                    !isPlaying && !isRec,
                    Modifier.weight(2.5f)
                )
                Adj(
                    "E",
                    end,
                    { end = it.coerceIn(start + 100, data?.size ?: 0) },
                    !isPlaying && !isRec,
                    Modifier.weight(2.5f)
                )
            }
        }
    }

    if (showSave) AlertDialog(
        onDismissRequest = { showSave = false },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    data?.let {
                        onSave(
                            repo.saveLoop(
                                name,
                                it,
                                start,
                                end
                            )
                        )
                    }; showSave = false
                }
            }) { Text("Save") }
        },
        title = { Text("Save") },
        text = { TextField(name, { name = it }) })

    LaunchedEffect(isPlaying) {
        if (isPlaying && (end - start) > 0) while (isPlaying) {
            engine.updatePlaybackPosition(engine.getPlaybackHeadPosition() % (end - start)); delay(
                16
            )
        }
    }
}

@Composable
fun LooperControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) ModTrackRed else TextGrayBox,
            contentColor = if (active) Color.White else CyanAccent
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Adj(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(4.dp),
        color = TextGrayBox
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            IconButton(
                onClick = { onValueChange(value - 100) },
                modifier = Modifier.size(24.dp),
                enabled = enabled
            ) { Text("-", color = if (enabled) CyanAccent else Color.Gray) }
            Text(
                value.toString(),
                Modifier.weight(1f),
                if (enabled) CyanAccent else Color.Gray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(
                onClick = { onValueChange(value + 100) },
                modifier = Modifier.size(24.dp),
                enabled = enabled
            ) { Text("+", color = if (enabled) CyanAccent else Color.Gray) }
        }
    }
}
