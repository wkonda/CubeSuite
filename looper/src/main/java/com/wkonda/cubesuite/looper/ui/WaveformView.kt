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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.audio.ChordRegion
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
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
    onsets: List<Int> = emptyList(),
    beatGrid: List<Int> = emptyList(),
    chords: List<ChordRegion> = emptyList(),
    correlationCurve: List<Pair<Int, Double>> = emptyList(),
    rhythmicCurve: List<Pair<Int, Double>> = emptyList()
) {
    if (data == null) return
    var size by remember { mutableStateOf(IntSize.Zero) };
    var handle by remember { mutableIntStateOf(-1) }
    val curS by rememberUpdatedState(start);
    val curE by rememberUpdatedState(end)
    val displayData = if (recording) {
        val windowSize = 5 * 48000; if (data.size > windowSize) data.copyOfRange(
            data.size - windowSize,
            data.size
        ) else data
    } else data

    Box(Modifier
        .fillMaxSize()
        .background(AppDarkBackground)
        .onSizeChanged { size = it }
        .drawWithCache {
            val (w, h) = this.size
            if (w <= 0 || h <= 0) onDrawBehind {} else {
                val bitmap = ImageBitmap(w.toInt(), h.toInt())
                CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), this.size) {
                    val midY = h / 2;
                    val step = max(1, displayData.size / w.toInt())
                    for (i in 0 until w.toInt()) {
                        val idx = i * step; if (idx >= displayData.size) break
                        val lh = (displayData[idx].toFloat() / Short.MAX_VALUE) * midY * 0.8f
                        drawLine(
                            CyanAccent.copy(0.4f),
                            Offset(i.toFloat(), midY - lh),
                            Offset(i.toFloat(), midY + lh)
                        )
                    }
                }
                onDrawBehind { drawImage(bitmap) }
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
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val n = (ch.position.x * data.size / w).toInt()
                        if (handle == 0) onStart(n.coerceIn(0, curE - 100)) else onEnd(
                            n.coerceIn(
                                curS + 100,
                                data.size
                            )
                        )
                        ch.consume()
                    }
                    handle = -1
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat();
            val h = size.height.toFloat(); if (w <= 0 || h <= 0 || recording) return@Canvas
            val sX = curS * w / data.size;
            val eX = curE * w / data.size
            val darkRed = Color(0xFF8B0000)

            drawLine(
                if (handle == 0) Color.White else darkRed,
                Offset(sX, 0f),
                Offset(sX, h),
                2.5.dp.toPx()
            )
            drawLine(
                if (handle == 1) Color.White else darkRed,
                Offset(eX, 0f),
                Offset(eX, h),
                2.5.dp.toPx()
            )

            if (playing) {
                val cX = (curS + pos) * w / data.size; if (cX in 0f..w) drawLine(
                    Color.White,
                    Offset(cX, 0f),
                    Offset(cX, h),
                    1.dp.toPx()
                )
            }

            beatGrid.forEach { b ->
                val bX = b * w / data.size; if (bX in 0f..w) drawLine(
                darkRed.copy(0.6f),
                Offset(bX, 0f),
                Offset(bX, h),
                1.dp.toPx()
            )
            }

            chords.forEach { c ->
                val cX = c.startSample * w / data.size
                if (cX in 0f..w) drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        c.label,
                        cX + 8f,
                        40f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE; textSize = 32f; isFakeBoldText =
                            true
                        })
                }
            }

            if (rhythmicCurve.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                rhythmicCurve.forEachIndexed { i, p ->
                    val x = p.first.toFloat() * w / data.size;
                    val y = h - (p.second.toFloat() * h * 0.4f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    Color.Blue.copy(0.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
            }

            if (correlationCurve.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path();
                val minSim = correlationCurve.minOf { it.second }.toFloat();
                val maxSim = correlationCurve.maxOf { it.second }.toFloat();
                val range = (maxSim - minSim).coerceAtLeast(0.01f)
                var lastX = -1f
                correlationCurve.forEach { p ->
                    val x = p.first.toFloat() * w / data.size;
                    val normY = (p.second.toFloat() - minSim) / range;
                    val y = (h * 0.1f) + (1f - normY) * (h * 0.5f)
                    if (lastX == -1f || abs(x - lastX) > (2048f * w / data.size)) path.moveTo(
                        x,
                        y
                    ) else path.lineTo(x, y)
                    lastX = x
                }
                drawPath(
                    path,
                    Color.Yellow,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(4.dp.toPx())
                    )
                )
            }
        }
    }
}
