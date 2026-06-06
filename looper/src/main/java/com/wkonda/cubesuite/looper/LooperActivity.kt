package com.wkonda.cubesuite.looper

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopRepository
import com.wkonda.cubesuite.looper.ui.Adj
import com.wkonda.cubesuite.looper.ui.Btn
import com.wkonda.cubesuite.looper.ui.ChromaVisualizer
import com.wkonda.cubesuite.looper.ui.LibraryScreen
import com.wkonda.cubesuite.looper.ui.LooperIcons
import com.wkonda.cubesuite.looper.ui.MenuSelector
import com.wkonda.cubesuite.looper.ui.ViewTab
import com.wkonda.cubesuite.looper.ui.WaveformView
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.ui.theme.DarkBackground
import com.wkonda.cubesuite.ui.theme.TrackRed
import java.util.Locale

class LooperActivity : ComponentActivity() {
    private val vm by lazy { LooperViewModel(LooperEngine(), LoopRepository(this)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        setContent {
            CubeSuiteTheme {
                val s by vm.uiState.collectAsState()
                Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
                    if (s.screen == "looper") LooperScreen(s, vm) else LibraryScreen(s, vm)
                }
            }
        }
    }
}

@Composable
fun LooperScreen(s: LooperUiState, vm: LooperViewModel) {
    var sigMenu by remember { mutableStateOf(false) }
    var barsMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.cube_looper),
                color = AccentCyan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                s.bpm?.let { bpm ->
                    Text(
                        String.format(Locale.US, "%.1f %s", bpm, stringResource(R.string.bpm)),
                        color = if (s.isRecording) TrackRed else Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val barLabel =
                        if (s.bars == 1) stringResource(R.string.bar) else stringResource(R.string.bars)
                    MenuSelector("${s.bars} $barLabel", barsMenu, { barsMenu = it }) {
                        (1..16).forEach { b ->
                            val bLabel =
                                if (b == 1) stringResource(R.string.bar) else stringResource(R.string.bars)
                            DropdownMenuItem(
                                text = { Text("$b $bLabel", color = AccentCyan) },
                                onClick = { vm.setUserBars(b); barsMenu = false })
                        }
                    }
                    MenuSelector(
                        "${s.signature.first}/${s.signature.second}",
                        sigMenu,
                        { sigMenu = it }) {
                        listOf(
                            4 to 4,
                            3 to 4,
                            2 to 4,
                            6 to 8,
                            12 to 8,
                            5 to 4,
                            7 to 8,
                            9 to 8,
                            2 to 2,
                            3 to 8
                        ).forEach { (n, d) ->
                            DropdownMenuItem(
                                text = { Text("$n/$d", color = AccentCyan) },
                                onClick = { vm.setUserSignature(n, d); sigMenu = false })
                        }
                    }
                }
            }
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                ViewTab(
                    stringResource(R.string.wave),
                    !s.showChromagram
                ) { if (s.showChromagram) vm.toggleView() }
                ViewTab(
                    stringResource(R.string.chroma),
                    s.showChromagram
                ) { if (!s.showChromagram) vm.toggleView() }
            }
            IconButton(
                onClick = { if (s.isRecording) vm.stopRecording() else vm.startRecording() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    LooperIcons.Mic,
                    null,
                    tint = if (s.isRecording) TrackRed else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = { vm.setScreen("library") }, modifier = Modifier.size(32.dp)) {
                Icon(LooperIcons.Library, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = s.isRecording) { vm.stopRecording() }) {
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

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Btn(
                if (s.isPlaying) stringResource(R.string.stop) else stringResource(R.string.play),
                if (s.isPlaying) LooperIcons.Stop else LooperIcons.Play,
                { vm.togglePlayback() },
                Modifier.weight(0.8f),
                !s.isRecording && s.recordingData != null,
                s.isPlaying
            )
            Btn(
                stringResource(R.string.analyze),
                LooperIcons.Analyze,
                { vm.analyze() },
                Modifier.weight(1f),
                s.recordingData != null && !s.isPlaying && !s.isRecording && !s.isAnalyzing
            )
            Btn(
                if (s.activeLoop != null) stringResource(R.string.update) else stringResource(R.string.save),
                LooperIcons.Save,
                { if (s.activeLoop != null) vm.saveOrUpdate() else vm.showSave() },
                Modifier.weight(1f),
                s.recordingData != null && !s.isPlaying && !s.isRecording && !s.isAnalyzing
            )
            Adj(
                stringResource(R.string.start),
                s.startSample,
                { vm.updateStart(it) },
                s.recordingData != null && !s.isPlaying && !s.isRecording,
                Modifier.weight(1.1f)
            )
            Adj(
                stringResource(R.string.end),
                s.endSample,
                { vm.updateEnd(it) },
                s.recordingData != null && !s.isPlaying && !s.isRecording,
                Modifier.weight(1.1f)
            )
        }
    }

    if (s.showSaveDialog) AlertDialog(
        onDismissRequest = { vm.hideSave() },
        confirmButton = { Button(onClick = { vm.saveOrUpdate() }) { Text(stringResource(R.string.save)) } },
        title = { Text(stringResource(R.string.save_loop), fontSize = 16.sp) },
        text = {
            TextField(
                value = s.loopName,
                onValueChange = { vm.updateLoopName(it) },
                singleLine = true
            )
        }
    )
}
