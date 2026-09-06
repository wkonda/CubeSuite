package com.wkonda.cubesuite.tuner.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wkonda.cubesuite.tuner.model.TunerFrame
import com.wkonda.cubesuite.tuner.ui.theme.TunerColors
import java.util.Locale
import kotlin.math.abs

private val stringNames = listOf("E2", "A2", "D3", "G3", "B3", "E4")

private fun FloatArray.atOrNaN(index: Int): Float {
    return if (index in indices) this[index] else Float.NaN
}

private fun BooleanArray.atOrFalse(index: Int): Boolean {
    return index in indices && this[index]
}

private data class TuningState(
    val label: String,
    val action: String,
    val hint: String
)

private fun tuningState(cents: Float, active: Boolean): TuningState {
    return when {
        !active || cents.isNaN() -> TuningState("NON DETECTEE", "ECOUTE", "corde")
        cents > 4f -> TuningState("TROP HAUTE", "DIMINUER", "la tension")
        cents < -4f -> TuningState("TROP BASSE", "AUGMENTER", "la tension")
        else -> TuningState("JUSTE", "NE RIEN CHANGER", "corde")
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = TunerColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Microphone permission required",
                color = TunerColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "This app needs microphone access to analyze pitch in real time.",
                color = TunerColors.TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRequestPermission) {
                Text(text = "Grant microphone access")
            }
        }
    }
}

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val latest by viewModel.latest.collectAsState()
    val history by viewModel.history.collectAsState()
    val capturePath by viewModel.capturePath.collectAsState()
    val context = LocalContext.current
    TunerScreenContent(
        latest = latest,
        history = history,
        capturePath = capturePath,
        onCapture = { viewModel.captureExample(context) }
    )
}

@Composable
fun TunerScreenContent(
    latest: TunerFrame,
    history: List<TunerFrame>,
    capturePath: String? = null,
    onCapture: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = TunerColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GUITAR TUNER",
                        color = TunerColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp
                    )
                    Text(
                        text = "${latest.activeCount}/6 strings detected",
                        color = TunerColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = if (latest.activeCount == 0) "Idle" else "Live",
                    color = if (latest.activeCount == 0) TunerColors.TextSecondary else TunerColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.size(12.dp))
                Button(onClick = onCapture) {
                    Text(text = "CAPTURE WAV")
                }
            }
            if (capturePath != null) {
                Text(
                    text = capturePath,
                    color = TunerColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stringNames.forEachIndexed { index, label ->
                    StringLane(
                        label = label,
                        cents = latest.cents.atOrNaN(index),
                        active = latest.active.atOrFalse(index),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "History",
                color = TunerColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            HistoryWaterfall(
                history = history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(TunerColors.PanelShape)
                    .background(TunerColors.Panel)
            )
        }
    }
}

@Composable
private fun HistoryWaterfall(
    history: List<TunerFrame>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (history.isEmpty()) {
            drawRect(color = TunerColors.Panel)
            return@Canvas
        }

        val frames = history.takeLast(120)
        val rows = frames.size.coerceAtLeast(1)
        val cols = 6
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        drawRect(color = TunerColors.Panel)

        frames.forEachIndexed { rowIndex, frame ->
            val y = size.height - (rowIndex + 1) * cellHeight
            val age = rowIndex / maxOf(1f, (rows - 1).toFloat())
            val alpha = 0.20f + age * 0.80f

            for (col in 0 until cols) {
                val active = frame.active.atOrFalse(col)
                val cents = frame.cents.atOrNaN(col)
                val color = when {
                    !active -> TunerColors.Inactive
                    abs(cents) <= 5f -> TunerColors.InTune
                    abs(cents) <= 10f -> TunerColors.Warm
                    else -> TunerColors.OffTune
                }.copy(alpha = alpha)

                drawRect(
                    color = color,
                    topLeft = Offset(col * cellWidth, y),
                    size = androidx.compose.ui.geometry.Size(cellWidth + 1f, cellHeight + 1f)
                )
            }
        }

        for (col in 1 until cols) {
            val x = col * cellWidth
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
private fun StringLane(
    label: String,
    cents: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val displayCents = if (cents.isNaN()) "--" else String.format(Locale.US, "%+.1f", cents)
    val state = tuningState(cents, active)
    val accent = when {
        !active -> TunerColors.Inactive
        abs(cents) <= 5f -> TunerColors.InTune
        abs(cents) <= 10f -> TunerColors.Warm
        else -> TunerColors.OffTune
    }

    Column(
        modifier = modifier
            .background(TunerColors.Panel, TunerColors.PanelShape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TunerColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.label,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val left = 14f
                val right = size.width - 14f
                val top = 12f
                val bottom = size.height - 14f
                val centerX = (left + right) / 2f
                val trackWidth = right - left
                val tickColor = TunerColors.Tick

                drawLine(
                    color = tickColor,
                    start = Offset(left, (top + bottom) / 2f),
                    end = Offset(right, (top + bottom) / 2f),
                    strokeWidth = 2f
                )
                for (step in -4..4) {
                    val x = centerX + trackWidth * 0.5f * (step / 4f)
                    drawLine(
                        color = tickColor.copy(alpha = if (step == 0) 0.8f else 0.4f),
                        start = Offset(x, top + if (step == 0) 4f else 8f),
                        end = Offset(x, bottom - if (step == 0) 4f else 8f),
                        strokeWidth = if (step == 0) 2f else 1f
                    )
                }
                if (!cents.isNaN()) {
                    val clamped = cents.coerceIn(-50f, 50f)
                    val markerX = centerX + (clamped / 50f) * (trackWidth / 2f)
                    drawCircle(
                        color = accent,
                        radius = 9f,
                        center = Offset(markerX, (top + bottom) / 2f)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.24f),
                        radius = 14f,
                        center = Offset(markerX, (top + bottom) / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                    )
                    drawLine(
                        color = accent,
                        start = Offset(markerX, top + 2f),
                        end = Offset(markerX, bottom - 2f),
                        strokeWidth = 4f
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (active) "$displayCents cents" else "--",
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = state.action,
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.hint,
            color = TunerColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(widthDp = 1200, heightDp = 520)
@Composable
private fun TunerScreenPreview() {
    val frames = List(72) { row ->
        TunerFrame(
            cents = FloatArray(6) { column ->
                when (column) {
                    0 -> (-12f + row * 0.15f).coerceIn(-20f, 20f)
                    1 -> (8f - row * 0.08f).coerceIn(-20f, 20f)
                    2 -> if (row % 11 == 0) Float.NaN else -4f + column
                    3 -> 0f
                    4 -> 7f
                    else -> -9f
                }
            },
            active = BooleanArray(6) { true }
        )
    }
    val latest = frames.last()
    MaterialTheme {
        TunerScreenContent(latest = latest, history = frames)
    }
}
