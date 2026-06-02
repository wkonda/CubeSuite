package com.wkonda.cubesuite.looper

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopRepository
import com.wkonda.cubesuite.looper.ui.FFTVisualizer
import com.wkonda.cubesuite.looper.ui.LoopListScreen
import com.wkonda.cubesuite.looper.ui.WaveformView
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import com.wkonda.cubesuite.ui.theme.TextGrayBox
import java.util.Locale

class LooperActivity : ComponentActivity() {
    private val vm by lazy { LooperViewModel(LooperEngine(), LoopRepository(this)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        setContent {
            CubeSuiteTheme {
                val s by vm.uiState.collectAsState()
                Surface(color = AppDarkBackground, modifier = Modifier.fillMaxSize()) {
                    if (s.screen == "looper") LooperScreen(s, vm) else LibraryScreen(s, vm)
                }
            }
        }
    }
}

@Composable
fun LooperScreen(s: LooperUiState, vm: LooperViewModel) {
    var sigMenuExpanded by remember { mutableStateOf(value = false) }
    var barsMenuExpanded by remember { mutableStateOf(value = false) }
    val sigs = listOf(4 to 4, 3 to 4, 6 to 8, 2 to 4, 5 to 4)

    Column(
        Modifier
            .fillMaxSize()
            .background(AppDarkBackground)
            .padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CUBE LOOPER", color = CyanAccent, fontWeight = FontWeight.Bold)
                val currentBpm = s.bpm
                if (currentBpm != null && currentBpm > 0) {
                    Text(
                        "  |  ${String.format(Locale.US, "%.1f", currentBpm)} BPM",
                        color = if (s.isRecording) ModTrackRed else Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (s.isRecording) FontWeight.Bold else FontWeight.Normal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "  |  ",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Box {
                            Text(
                                text = "${s.bars} bars",
                                color = CyanAccent,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { barsMenuExpanded = true })
                            DropdownMenu(
                                expanded = barsMenuExpanded,
                                onDismissRequest = { barsMenuExpanded = false },
                                modifier = Modifier.background(TextGrayBox)
                            ) {
                                (1..16).forEach { b ->
                                    DropdownMenuItem(text = {
                                        Text(
                                            "$b bars",
                                            color = CyanAccent
                                        )
                                    }, onClick = { vm.setUserBars(b); barsMenuExpanded = false })
                                }
                            }
                        }
                        Text(" of ", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Box {
                            Text(
                                text = "${s.signature.first}/${s.signature.second}",
                                color = CyanAccent,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { sigMenuExpanded = true })
                            DropdownMenu(
                                expanded = sigMenuExpanded,
                                onDismissRequest = { sigMenuExpanded = false },
                                modifier = Modifier.background(TextGrayBox)
                            ) {
                                sigs.forEach { (n, d) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$n/$d",
                                                color = CyanAccent
                                            )
                                        },
                                        onClick = {
                                            vm.setUserSignature(n, d); sigMenuExpanded = false
                                        })
                                }
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    { vm.toggleView() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextGrayBox,
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        if (s.showSpectrogram) "WAVE" else "SPECTRO",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Button(
                    { vm.setScreen("library") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextGrayBox,
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("LIBRARY", style = MaterialTheme.typography.labelSmall)
                }
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
            if (s.showSpectrogram) FFTVisualizer(
                s.spectrogram,
                s.recordingData?.size ?: 0,
                s.startSample,
                s.endSample,
                { vm.updateStart(it) },
                { vm.updateEnd(it) },
                Modifier.fillMaxSize(),
                s.playbackPosition,
                s.isPlaying,
                s.beatGrid,
                s.chords,
                s.correlationCurve,
                s.rhythmicCurve
            )
            else WaveformView(
                s.recordingData,
                s.startSample,
                s.endSample,
                { vm.updateStart(it) },
                { vm.updateEnd(it) },
                s.playbackPosition,
                s.isPlaying,
                s.isRecording,
                s.beatGrid,
                s.chords,
                s.correlationCurve,
                s.rhythmicCurve
            )
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Btn(
                if (s.isRecording) "STOP" else "REC",
                { if (s.isRecording) vm.stopRecording() else vm.startRecording() },
                Modifier.weight(1f),
                !s.isPlaying,
                s.isRecording
            )
            Btn(
                if (s.isPlaying) "STOP" else "PLAY",
                { vm.togglePlayback() },
                Modifier.weight(1f),
                !s.isRecording,
                s.isPlaying
            )
            Btn(
                "ANALYZE",
                { vm.analyze() },
                Modifier.weight(1.5f),
                (s.recordingData != null && (!s.isPlaying) && (!s.isRecording))
            )
            Btn(
                if (s.activeLoop != null) "UPDATE" else "SAVE",
                { if (s.activeLoop != null) vm.saveOrUpdate() else vm.showSave() },
                Modifier.weight(1.5f),
                (s.recordingData != null && (!s.isPlaying) && (!s.isRecording))
            )
            if (s.recordingData != null) {
                Adj(
                    "S",
                    s.startSample,
                    { vm.updateStart(it) },
                    !s.isPlaying && !s.isRecording,
                    Modifier.weight(2.5f)
                )
                Adj(
                    "E",
                    s.endSample,
                    { vm.updateEnd(it) },
                    !s.isPlaying && !s.isRecording,
                    Modifier.weight(2.5f)
                )
            }
        }
    }
    if (s.showSaveDialog) AlertDialog(
        onDismissRequest = { vm.hideSave() },
        confirmButton = {
            Button(
                onClick = { vm.saveOrUpdate() },
                colors = ButtonDefaults.buttonColors(contentColor = Color.White)
            ) {
                Text("Save")
            }
        },
        title = { Text("Save") },
        text = {
            TextField(
                value = s.loopName,
                onValueChange = { vm.updateLoopName(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.saveOrUpdate() })
            )
        })
}

@Composable
fun LibraryScreen(s: LooperUiState, vm: LooperViewModel) {
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
                modifier = Modifier.clickable { vm.setScreen("looper") })
        }
        LoopListScreen(s.loops, { vm.loadLoop(it) }) { vm.deleteLoop(it) }
    }
}

@Composable
fun Btn(
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
            contentColor = if (active) Color.White else CyanAccent,
            disabledContainerColor = TextGrayBox,
            disabledContentColor = Color.Gray
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
    val step = (LooperConfig.SAMPLE_RATE * 0.002).toInt()
    val seconds = value.toDouble() / LooperConfig.SAMPLE_RATE
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
                onClick = { onValueChange((value - step).coerceAtLeast(0)) },
                modifier = Modifier.size(24.dp),
                enabled = enabled
            ) { Text("-", color = if (enabled) CyanAccent else Color.Gray) }
            Text(
                String.format(Locale.US, "%.3f", seconds),
                Modifier.weight(1f),
                if (enabled) CyanAccent else Color.Gray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(
                onClick = { onValueChange(value + step) },
                modifier = Modifier.size(24.dp),
                enabled = enabled
            ) { Text("+", color = if (enabled) CyanAccent else Color.Gray) }
        }
    }
}
