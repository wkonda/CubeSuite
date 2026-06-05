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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.DarkBackground
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WaveformView(
    data: ShortArray?,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    pos: Int = 0,
    playing: Boolean = false,
    recording: Boolean = false,
    totalBeats: Int = 16,
    correlationCurve: List<Pair<Int, Double>> = emptyList(),
    rhythmicCurve: List<Pair<Int, Double>> = emptyList(),
    signature: Pair<Int, Int> = 4 to 4
) {
    if (data == null) return
    var size by remember { mutableStateOf(IntSize.Zero) };
    var handle by remember { mutableIntStateOf(-1) }
    val curS by rememberUpdatedState(start);
    val curE by rememberUpdatedState(end)
    val windowSize = 5 * LooperConfig.SAMPLE_RATE;
    val isWindowed = recording && data.size > windowSize
    val dataOffset = if (isWindowed) data.size - windowSize else 0;
    val displaySize = if (isWindowed) windowSize else data.size

    Box(Modifier
        .fillMaxSize()
        .background(DarkBackground)
        .onSizeChanged { size = it }
        .drawWithCache {
            val (w, h) = this.size; if (w <= 0 || h <= 0) onDrawBehind {} else {
            val bitmap = ImageBitmap(w.toInt(), h.toInt()); CanvasDrawScope().draw(
                this,
                layoutDirection,
                Canvas(bitmap),
                this.size
            ) {
                val midY = h / 2;
                val step = max(1, displaySize / w.toInt())
                for (i in 0 until w.toInt()) {
                    val idx = dataOffset + i * step; if (idx >= data.size) break
                    val lh = (data[idx].toFloat() / Short.MAX_VALUE) * midY * 0.8f
                    drawLine(
                        AccentCyan.copy(0.4f),
                        Offset(i.toFloat(), midY - lh),
                        Offset(i.toFloat(), midY + lh)
                    )
                }
            }; onDrawBehind { drawImage(bitmap) }
        }
        }
        .pointerInput(data.size, playing, recording) {
            if (playing || recording) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown();
                val w = size.width.toFloat(); if (w <= 0) return@awaitEachGesture
                val sX = curS.toFloat() * w / data.size;
                val eX = curE.toFloat() * w / data.size
                handle = when {
                    abs(down.position.x - sX) < 48.dp.toPx() -> 0; abs(down.position.x - eX) < 48.dp.toPx() -> 1; else -> -1
                }
                if (handle != -1) {
                    while (true) {
                        val ev = awaitPointerEvent();
                        val ch = ev.changes.firstOrNull() ?: break; if (!ch.pressed) break
                        val n = (ch.position.x * data.size / w).toInt()
                        if (handle == 0) onStart(
                            n.coerceIn(
                                0,
                                curE - 100
                            )
                        ) else onEnd(n.coerceIn(curS + 100, data.size))
                        ch.consume()
                    }; handle = -1
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat();
            val h = size.height.toFloat(); if (w <= 0 || h <= 0 || recording) return@Canvas
            drawLoopGrid(curS, curE, data.size, totalBeats, signature, handle)
            if (playing) drawPlaybackLine(pos, curS, data.size)
            drawAnalysisCurves(rhythmicCurve, correlationCurve, data.size)
        }
    }
}
