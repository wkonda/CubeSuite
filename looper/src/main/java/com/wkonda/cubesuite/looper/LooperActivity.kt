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
import androidx.compose.foundation.layout.ColumnScope
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
import com.wkonda.cubesuite.looper.ui.ChromaVisualizer
import com.wkonda.cubesuite.looper.ui.LoopListScreen
import com.wkonda.cubesuite.looper.ui.WaveformView
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.ui.theme.DarkBackground
import com.wkonda.cubesuite.ui.theme.SurfaceGray
import com.wkonda.cubesuite.ui.theme.TrackRed
import com.wkonda.cubesuite.ui.theme.White
import java.util.Locale

class LooperActivity : ComponentActivity() {
    private val vm by lazy { LooperViewModel(LooperEngine(), LoopRepository(this)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; hide(
            WindowInsetsCompat.Type.systemBars()
        )
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        setContent {
            CubeSuiteTheme {
                val s by vm.uiState.collectAsState(); Surface(
                color = DarkBackground,
                modifier = Modifier.fillMaxSize()
            ) { if (s.screen == "looper") LooperScreen(s, vm) else LibraryScreen(s, vm) }
            }
        }
    }
}

@Composable
fun LooperScreen(s: LooperUiState, vm: LooperViewModel) {
    var sigMenu by remember { mutableStateOf(false) };
    var barsMenu by remember { mutableStateOf(false) }
    Column(Modifier
        .fillMaxSize()
        .background(DarkBackground)
        .padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CUBE LOOPER", color = AccentCyan, fontWeight = FontWeight.Bold)
                s.bpm?.let { bpm ->
                    Text(
                        "  |  ${String.format(Locale.US, "%.1f", bpm)} BPM",
                        color = if (s.isRecording) TrackRed else Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (s.isRecording) FontWeight.Bold else FontWeight.Normal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "  |  ",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        MenuSelector(
                            "${s.bars} bars",
                            barsMenu,
                            { barsMenu = it }) {
                            (1..16).forEach { b ->
                                DropdownMenuItem(text = {
                                    Text(
                                        "$b bars",
                                        color = AccentCyan
                                    )
                                }, onClick = { vm.setUserBars(b); barsMenu = false })
                            }
                        }
                        Text(" of ", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        MenuSelector(
                            "${s.signature.first}/${s.signature.second}",
                            sigMenu,
                            { sigMenu = it }) {
                            listOf(
                                4 to 4,
                                3 to 4,
                                6 to 8,
                                2 to 4,
                                5 to 4
                            ).forEach { (n, d) ->
                                DropdownMenuItem(text = {
                                    Text(
                                        "$n/$d",
                                        color = AccentCyan
                                    )
                                }, onClick = { vm.setUserSignature(n, d); sigMenu = false })
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopBtn(if (s.showChromagram) "WAVE" else "CHROMA") { vm.toggleView() }
                TopBtn("LIBRARY") { vm.setScreen("library") }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, SurfaceGray, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
            if (s.showChromagram) ChromaVisualizer(
                s.chromagram,
                s.recordingData?.size ?: 0,
                s.startSample,
                s.endSample,
                { vm.updateStart(it) },
                { vm.updateEnd(it) },
                Modifier.fillMaxSize(),
                s.playbackPosition,
                s.isPlaying,
                s.totalBeats,
                s.correlationCurve,
                s.rhythmicCurve,
                s.signature
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
                s.totalBeats,
                s.correlationCurve,
                s.rhythmicCurve,
                s.signature
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
                s.recordingData != null && !s.isPlaying && !s.isRecording
            )
            Btn(
                if (s.activeLoop != null) "UPDATE" else "SAVE",
                { if (s.activeLoop != null) vm.saveOrUpdate() else vm.showSave() },
                Modifier.weight(1.5f),
                s.recordingData != null && !s.isPlaying && !s.isRecording
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
                colors = ButtonDefaults.buttonColors(contentColor = White)
            ) { Text("Save") }
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
fun MenuSelector(
    label: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Text(
            text = label,
            color = AccentCyan,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onToggle(true) })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onToggle(false) },
            modifier = Modifier.background(SurfaceGray),
            content = content
        )
    }
}

@Composable
fun TopBtn(text: String, onClick: () -> Unit) = Button(
    onClick,
    colors = ButtonDefaults.buttonColors(containerColor = SurfaceGray, contentColor = AccentCyan),
    shape = RoundedCornerShape(4.dp),
    contentPadding = PaddingValues(horizontal = 8.dp)
) { Text(text, style = MaterialTheme.typography.labelSmall) }

@Composable
fun LibraryScreen(s: LooperUiState, vm: LooperViewModel) {
    Column(Modifier
        .fillMaxSize()
        .background(DarkBackground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("LIBRARY", color = AccentCyan, fontWeight = FontWeight.Bold)
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
            containerColor = if (active) TrackRed else SurfaceGray,
            contentColor = if (active) White else AccentCyan,
            disabledContainerColor = SurfaceGray,
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
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(4.dp),
        color = SurfaceGray
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
            ) { Text("-", color = if (enabled) AccentCyan else Color.Gray) }
            Text(
                String.format(Locale.US, "%.3f", value.toDouble() / LooperConfig.SAMPLE_RATE),
                Modifier.weight(1f),
                if (enabled) AccentCyan else Color.Gray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(
                onClick = { onValueChange(value + step) },
                modifier = Modifier.size(24.dp),
                enabled = enabled
            ) { Text("+", color = if (enabled) AccentCyan else Color.Gray) }
        }
    }
}
