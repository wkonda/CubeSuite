package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    playing: Boolean = false
) {
    if (data == null) return
    var size by remember { mutableStateOf(IntSize.Zero) }
    var handle by remember { mutableStateOf<Int?>(null) }
    var lStart by remember { mutableIntStateOf(start) }
    var lEnd by remember { mutableIntStateOf(end) }

    LaunchedEffect(start, end) { lStart = start; lEnd = end }

    Box(Modifier
        .fillMaxSize()
        .background(AppDarkBackground)
        .onSizeChanged { size = it }
        .drawWithCache {
            val (w, h) = this.size
            if (w <= 0 || h <= 0) onDrawBehind {} else {
                val bitmap = ImageBitmap(w.toInt(), h.toInt())
                CanvasDrawScope().draw(this, this.layoutDirection, Canvas(bitmap), this.size) {
                    val midY = h / 2
                    val step = max(1, (data.size / w).toInt())
                    for (i in 0 until w.toInt()) {
                        val idx = i * step
                        if (idx >= data.size) break
                        val lh = (data[idx].toFloat() / Short.MAX_VALUE) * midY * 0.8f
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
        .pointerInput(data.size) {
            awaitEachGesture {
                val down = awaitFirstDown();
                val w = size.width.toFloat(); if (w <= 0) return@awaitEachGesture
                val sX = lStart * w / data.size;
                val eX = lEnd * w / data.size
                handle = when {
                    !playing && abs(down.position.x - sX) < 40.dp.toPx() -> 0
                    !playing && abs(down.position.x - eX) < 40.dp.toPx() -> 1
                    else -> null
                }
                if (handle != null) {
                    while (true) {
                        val ev = awaitPointerEvent();
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val n = (ch.position.x * data.size / w).toInt()
                        if (handle == 0) {
                            lStart = n.coerceIn(0, lEnd - 100); onStart(lStart)
                        } else {
                            lEnd = n.coerceIn(lStart + 100, data.size); onEnd(lEnd)
                        }
                        ch.consume()
                    }
                    handle = null
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat();
            val h = size.height.toFloat()
            if (w <= 0 || h <= 0) return@Canvas
            val sX = lStart * w / data.size;
            val eX = lEnd * w / data.size
            if (sX in 0f..w) drawLine(
                if (handle == 0) Color.White else CyanAccent,
                Offset(sX, 0f),
                Offset(sX, h),
                2.dp.toPx()
            )
            if (eX in 0f..w) drawLine(
                if (handle == 1) Color.White else CyanAccent,
                Offset(eX, 0f),
                Offset(eX, h),
                2.dp.toPx()
            )
            if (playing) {
                val cX = (lStart + pos) * w / data.size
                if (cX in 0f..w) drawLine(Color.White, Offset(cX, 0f), Offset(cX, h), 1.dp.toPx())
            }
        }
    }
}
