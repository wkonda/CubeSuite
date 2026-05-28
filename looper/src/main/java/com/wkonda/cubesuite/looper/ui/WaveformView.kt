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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WaveformView(
    data: ShortArray?,
    startSample: Int,
    endSample: Int,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit,
    playbackPosition: Int = 0,
    isPlaying: Boolean = false
) {
    if (data == null) return
    var size by remember { mutableStateOf(IntSize.Zero) }
    var selectedHandle by remember { mutableStateOf<Int?>(null) }
    var localStart by remember { mutableIntStateOf(startSample) }
    var localEnd by remember { mutableIntStateOf(endSample) }

    LaunchedEffect(startSample, endSample) {
        localStart = startSample
        localEnd = endSample
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDarkBackground)
            .onSizeChanged { size = it }
            .pointerInput(data.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture
                    val totalSamples = data.size.toFloat()
                    val startX = localStart * width / totalSamples
                    val endX = localEnd * width / totalSamples
                    selectedHandle = when {
                        !isPlaying && abs(down.position.x - startX) < 150f -> 0
                        !isPlaying && abs(down.position.x - endX) < 150f -> 1
                        else -> null
                    }
                    if (selectedHandle != null) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val panX = change.position.x - change.previousPosition.x
                            val deltaSamples = (panX * totalSamples / width).toInt()
                            if (selectedHandle == 0) {
                                localStart = (localStart + deltaSamples).coerceIn(0, localEnd)
                                onStartChanged(localStart)
                            } else {
                                localEnd = (localEnd + deltaSamples).coerceIn(localStart, data.size)
                                onEndChanged(localEnd)
                            }
                            change.consume()
                        }
                        selectedHandle = null
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            if (width == 0f || height == 0f) return@Canvas
            val centerY = height / 2
            val totalSamples = data.size.toFloat()
            val step = max(1, (totalSamples / width).toInt())
            for (i in 0 until width.toInt()) {
                val sampleIdx = (i * step)
                if (sampleIdx >= data.size) break
                val value = data[sampleIdx].toFloat() / Short.MAX_VALUE
                val lineHeight = value * centerY * 0.8f
                drawLine(
                    CyanAccent.copy(alpha = 0.4f),
                    Offset(i.toFloat(), centerY - lineHeight),
                    Offset(i.toFloat(), centerY + lineHeight),
                    1f
                )
            }
            val startX = localStart * width / totalSamples
            val endX = localEnd * width / totalSamples
            val alpha = if (isPlaying) 0.5f else 1.0f
            val startColor =
                (if (selectedHandle == 0) Color.White else ModTrackRed).copy(alpha = alpha)
            val endColor =
                (if (selectedHandle == 1) Color.White else ModTrackRed).copy(alpha = alpha)

            if (startX in -50f..width + 50f) {
                drawLine(startColor, Offset(startX, 0f), Offset(startX, height), 15f)
                drawCircle(startColor, 60f, Offset(startX, height - 150f))
                drawCircle(startColor, 60f, Offset(startX, 150f))
            }
            if (endX in -50f..width + 50f) {
                drawLine(endColor, Offset(endX, 0f), Offset(endX, height), 15f)
                drawCircle(endColor, 60f, Offset(endX, height - 150f))
                drawCircle(endColor, 60f, Offset(endX, 150f))
            }
            if (isPlaying) {
                val cursorX = (localStart + playbackPosition) * width / totalSamples
                if (cursorX in 0f..width) drawLine(
                    Color.Cyan,
                    Offset(cursorX, 0f),
                    Offset(cursorX, height),
                    5f
                )
            }
        }
    }
}
