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
import com.wkonda.cubesuite.looper.audio.ChordRegion
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
import kotlin.math.abs
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
    playing: Boolean = false,
    beatGrid: List<Int> = emptyList(),
    chords: List<ChordRegion> = emptyList(),
    correlationCurve: List<Pair<Int, Double>> = emptyList(),
    rhythmicCurve: List<Pair<Int, Double>> = emptyList(),
) {
    var size by remember { mutableStateOf(value = IntSize.Zero) }
    var handle by remember { mutableIntStateOf(-1) }
    val curS by rememberUpdatedState(newValue = start)
    val curE by rememberUpdatedState(newValue = end)

    val notes = remember {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        (36..84).map { m ->
            Triple(names[m % 12] + ((m / 12) - 1), 0f, !names[m % 12].contains("#"))
        }
    }

    Box(
        modifier
            .background(AppDarkBackground)
            .onSizeChanged { size = it }
            .drawWithCache {
                val (w, h) = this.size
                if (spec.isEmpty() || w <= 0 || h <= 0) onDrawBehind {} else {
                    val bitmap = ImageBitmap(w.toInt(), h.toInt())
                    CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), this.size) {
                        val ch = h / spec[0].size
                        val cw = w / 600f

                        spec.forEachIndexed { t, mags ->
                            val colMax = mags.maxOrNull() ?: -100.0
                            val range = 30.0 // Even tighter range for extreme contrast
                            val floor = colMax - range

                            mags.forEachIndexed { f, m ->
                                // Peak Picking: only draw if it's a local maximum in frequency
                                // This drastically reduces vertical smearing.
                                val isPeak =
                                    (f > 0 && f < mags.size - 1 && m >= mags[f - 1] && m >= mags[f + 1]) ||
                                            (f == 0 && m >= mags[f + 1]) ||
                                            (f == mags.size - 1 && m >= mags[f - 1])

                                val rawIntensity = ((m - floor) / range).toFloat().coerceIn(0f, 1f)
                                // Cubing the intensity for "super-sharpening"
                                val intensity = rawIntensity.pow(3f)

                                // If it's a peak, we give it a boost; if not, we dim it heavily
                                val finalIntensity = if (isPeak) intensity else intensity * 0.3f

                                if (finalIntensity > 0.05f && m > -80.0) {
                                    val color = when {
                                        finalIntensity < 0.3f -> lerp(
                                            AppDarkBackground,
                                            CyanAccent.copy(alpha = 0.4f),
                                            finalIntensity / 0.3f
                                        )

                                        finalIntensity < 0.7f -> lerp(
                                            CyanAccent.copy(alpha = 0.4f),
                                            CyanAccent,
                                            (finalIntensity - 0.3f) / 0.4f
                                        )

                                        else -> lerp(
                                            CyanAccent,
                                            Color.White,
                                            (finalIntensity - 0.7f) / 0.3f
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
                            if (handle == 0) onStart(n.coerceIn(0, curE - 100)) else onEnd(
                                n.coerceIn(
                                    curS + 100,
                                    total
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
            val h = size.height.toFloat()
            if (w <= 0 || h <= 0) return@Canvas
            val sX = curS * w / total;
            val eX = curE * w / total

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
                val cX = (curS + pos) * w / total; if (cX in 0f..w) drawLine(
                    Color.White,
                    Offset(cX, 0f),
                    Offset(cX, h),
                    1.dp.toPx()
                )
            }
            beatGrid.forEach { b ->
                val bX = b * w / total
                if (bX in 0f..w) drawLine(
                    darkRed.copy(0.6f),
                    Offset(bX, 0f),
                    Offset(bX, h),
                    1.dp.toPx()
                )
            }

            if (rhythmicCurve.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                rhythmicCurve.forEachIndexed { i, p ->
                    val x = p.first.toFloat() * w / total
                    val y = h - (p.second.toFloat() * h * 0.4f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    Color(0xFF44AAFF),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }

            if (correlationCurve.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                val minSim = correlationCurve.minOf { it.second }.toFloat()
                val maxSim = correlationCurve.maxOf { it.second }.toFloat()
                val range = (maxSim - minSim).coerceAtLeast(0.01f)
                var lastX = -1f
                correlationCurve.forEach { p ->
                    val x = p.first.toFloat() * w / total
                    val sim = p.second.toFloat()
                    val normY = (sim - minSim) / range
                    val y = (h * 0.1f) + (1f - normY) * (h * 0.5f)

                    val expectedMaxGap = (2048f * w / total)
                    if (lastX == -1f || abs(x - lastX) > expectedMaxGap) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    lastX = x
                }
                drawPath(
                    path,
                    Color.Yellow,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }

            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(160, 255, 255, 255); textSize =
                    24f; textAlign = android.graphics.Paint.Align.LEFT
                }
                val cp = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE; textSize = 32f; isFakeBoldText = true
                }
                var lastLabelX = -1000f
                chords.forEach { ch ->
                    val cX = ch.startSample * w / total
                    // Only draw label if it's far enough from the last one to be readable
                    if (cX in 0f..w && abs(cX - lastLabelX) > 80f) {
                        c.nativeCanvas.drawText(ch.label, cX + 8f, 40f, cp)
                        lastLabelX = cX
                    }
                }
                val ch = h / notes.size
                notes.forEachIndexed { i, (name, _, isWhite) ->
                    val y = h - (i + 0.5f) * ch
                    if (isWhite) {
                        c.nativeCanvas.drawText(name, 10f, y + 8f, p)
                        drawLine(
                            Color.White.copy(0.15f),
                            Offset(0f, h - (i + 1) * ch),
                            Offset(w, h - (i + 1) * ch),
                            1.dp.toPx()
                        )
                    } else drawLine(
                        Color.White.copy(0.05f),
                        Offset(0f, h - (i + 1) * ch),
                        Offset(w, h - (i + 1) * ch),
                        0.5.dp.toPx()
                    )
                }
            }
        }
    }
}
