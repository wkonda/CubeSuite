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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow

@Composable
fun FFTVisualizer(
    spec: List<List<Double>>,
    total: Int,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pos: Int = 0,
    playing: Boolean = false
) {
    var size by remember { mutableStateOf(IntSize.Zero) };
    var handle by remember { mutableIntStateOf(-1) }
    val curS by rememberUpdatedState(start);
    val curE by rememberUpdatedState(end)
    val notes = remember {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        (LooperConfig.MIDI_E2..LooperConfig.MIDI_E4).map { m ->
            Triple(
                names[m % 12] + ((m / 12) - 1),
                440f * 2.0.pow((m - 69.0) / 12.0).toFloat(),
                !names[m % 12].contains("#")
            )
        }
    }
    val maxM = remember(spec) { (spec.flatten().maxOrNull() ?: 1.0).coerceAtLeast(0.001) }
    val logF = log2(LooperConfig.FREQ_E2.toDouble());
    val logT = log2(LooperConfig.FREQ_E4.toDouble())

    Box(modifier
        .background(AppDarkBackground)
        .onSizeChanged { size = it }
        .drawWithCache {
            val (w, h) = this.size
            if (spec.isEmpty() || w <= 0 || h <= 0) onDrawBehind {} else {
                val bitmap = ImageBitmap(w.toInt(), h.toInt())
                CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), this.size) {
                    val cw = w / spec.size;
                    val ch = h / spec[0].size
                    spec.forEachIndexed { t, mags ->
                        mags.forEachIndexed { f, m ->
                            val intensity =
                                (log10(m.coerceAtLeast(1e-12) + 1.0) / log10(
                                    maxM.coerceAtLeast(
                                        1e-12
                                    ) + 1.0
                                )).toFloat()
                                    .pow(4f).coerceIn(0f, 1f)
                            drawRect(
                                lerp(
                                    AppDarkBackground,
                                    CyanAccent,
                                    (intensity * 1.5f).coerceIn(0f, 1f)
                                ), Offset(t * cw, h - (f + 1) * ch), Size(cw + 1f, ch + 1f)
                            )
                        }
                    }
                }
                onDrawBehind { drawImage(bitmap) }
            }
        }
        .pointerInput(total, playing) {
            if (total <= 0 || playing) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown();
                val w = size.width.toFloat(); if (w <= 0) return@awaitEachGesture
                val sX = curS.toFloat() * w / total;
                val eX = curE.toFloat() * w / total
                handle = when {
                    abs(down.position.x - sX) < 48.dp.toPx() -> 0; abs(down.position.x - eX) < 48.dp.toPx() -> 1; else -> -1
                }
                if (handle != -1) {
                    while (true) {
                        val ev = awaitPointerEvent();
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val n = (ch.position.x * total / w).toInt()
                        if (handle == 0) onStart(
                            n.coerceIn(
                                0,
                                curE - 100
                            )
                        ) else onEnd(n.coerceIn(curS + 100, total))
                        ch.consume()
                    }
                    handle = -1
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat();
            val h = size.height.toFloat(); if (w <= 0 || h <= 0) return@Canvas
            val sX = curS * w / total;
            val eX = curE * w / total
            drawLine(
                if (handle == 0) Color.White else CyanAccent,
                Offset(sX, 0f),
                Offset(sX, h),
                2.dp.toPx()
            )
            drawLine(
                if (handle == 1) Color.White else CyanAccent,
                Offset(eX, 0f),
                Offset(eX, h),
                2.dp.toPx()
            )
            if (playing) {
                val cX = (curS + pos) * w / total; if (cX in 0f..w) drawLine(
                    Color.White,
                    Offset(cX, 0f),
                    Offset(cX, h),
                    1.dp.toPx()
                )
            }
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(100, 255, 255, 255); textSize = 24f
                }
                notes.forEach { (n, f, white) ->
                    val y = h - ((log2(f.toDouble()) - logF) / (logT - logF) * h).toFloat()
                    if (white) {
                        c.nativeCanvas.drawText(n, 10f, y, p); drawLine(
                            Color.White.copy(0.15f),
                            Offset(0f, y),
                            Offset(w, y)
                        )
                    } else drawLine(Color.White.copy(0.05f), Offset(0f, y), Offset(w, y))
                }
            }
        }
    }
}
