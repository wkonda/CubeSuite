package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.DarkBackground
import com.wkonda.cubesuite.ui.theme.White
import kotlin.math.abs

@Composable
fun ChromaVisualizer(
    chromagram: List<List<Double>>,
    totalSamples: Int,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pos: Int = 0,
    playing: Boolean = false,
    totalBeats: Int = 16,
    correlationCurve: List<Pair<Int, Double>> = emptyList(),
    rhythmicCurve: List<Pair<Int, Double>> = emptyList(),
    signature: Pair<Int, Int> = 4 to 4
) {
    var size by remember { mutableStateOf(IntSize.Zero) };
    var handle by remember { mutableIntStateOf(-1) }
    val curS by rememberUpdatedState(start);
    val curE by rememberUpdatedState(end);
    val notes = remember { listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B") }

    Box(modifier
        .background(DarkBackground)
        .onSizeChanged { size = it }
        .drawWithCache {
            val (w, h) = this.size; if (chromagram.isEmpty() || w <= 0 || h <= 0) onDrawBehind {} else {
            val bitmap = ImageBitmap(w.toInt(), h.toInt()); CanvasDrawScope().draw(
                this,
                layoutDirection,
                Canvas(bitmap),
                this.size
            ) {
                val ch = h / chromagram[0].size;
                val cw = w / chromagram.size.toFloat()
                chromagram.forEachIndexed { t, mags ->
                    mags.forEachIndexed { f, intensity ->
                        if (intensity > 0.05f) {
                            val color = when {
                                intensity < 0.3f -> lerp(
                                    DarkBackground,
                                    AccentCyan.copy(0.4f),
                                    intensity.toFloat() / 0.3f
                                ); intensity < 0.7f -> lerp(
                                    AccentCyan.copy(0.4f),
                                    AccentCyan,
                                    (intensity.toFloat() - 0.3f) / 0.4f
                                ); else -> lerp(
                                    AccentCyan,
                                    White,
                                    (intensity.toFloat() - 0.7f) / 0.3f
                                )
                            }
                            drawRect(
                                color,
                                Offset(t * cw, h - (f + 1) * ch),
                                Size(cw + 1f, ch + 1f)
                            )
                        }
                    }
                }
            }; onDrawBehind { drawImage(bitmap) }
        }
        }
        .pointerInput(totalSamples, playing) {
            if (totalSamples <= 0 || playing) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown();
                val w = size.width.toFloat(); if (w <= 0) return@awaitEachGesture
                val sX = curS.toFloat() * w / totalSamples;
                val eX = curE.toFloat() * w / totalSamples
                handle = when {
                    abs(down.position.x - sX) < 48.dp.toPx() -> 0; abs(down.position.x - eX) < 48.dp.toPx() -> 1; else -> -1
                }
                if (handle != -1) {
                    while (true) {
                        val ev = awaitPointerEvent();
                        val ch = ev.changes.firstOrNull() ?: break; if (!ch.pressed) break
                        val n = (ch.position.x * totalSamples / w).toInt()
                        if (handle == 0) onStart(
                            n.coerceIn(
                                0,
                                curE - 100
                            )
                        ) else onEnd(n.coerceIn(curS + 100, totalSamples))
                        ch.consume()
                    }; handle = -1
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat();
            val h = size.height.toFloat(); if (w <= 0 || h <= 0) return@Canvas
            drawLoopGrid(curS, curE, totalSamples, totalBeats, signature, handle)
            if (playing) drawPlaybackLine(pos, curS, totalSamples)
            drawAnalysisCurves(rhythmicCurve, correlationCurve, totalSamples)
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE; alpha = 200; textSize = 28f; textAlign =
                    android.graphics.Paint.Align.LEFT; isFakeBoldText = true
                }
                val ch = h / 12f; for (i in 0..12) {
                val lineY = h - (i * ch); drawLine(
                    White.copy(0.15f),
                    Offset(0f, lineY),
                    Offset(w, lineY),
                    if (i == 0 || i == 12) 1.dp.toPx() else 0.5.dp.toPx()
                )
            }
                notes.forEachIndexed { i, name ->
                    if (!name.contains("#")) c.nativeCanvas.drawText(
                        name,
                        8f,
                        h - (i + 0.5f) * ch + 10f,
                        p
                    )
                }
            }
        }
    }
}
