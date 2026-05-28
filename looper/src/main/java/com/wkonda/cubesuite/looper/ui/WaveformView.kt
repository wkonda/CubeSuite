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
    startSample: Int,
    endSample: Int,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit,
    playbackPosition: Int = 0,
    isPlaying: Boolean = false,
    onsets: List<Int> = emptyList()
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
            .drawWithCache {
                val cacheSize = this.size
                val width = cacheSize.width
                val height = cacheSize.height
                if (width <= 0f || height <= 0f) {
                    onDrawBehind { }
                } else {
                    val bitmap = ImageBitmap(width.toInt(), height.toInt())
                    val canvas = Canvas(bitmap)
                    val drawScope = CanvasDrawScope()
                    drawScope.draw(this, this.layoutDirection, canvas, cacheSize) {
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

                        // Draw onsets
                        onsets.forEach { onsetSample ->
                            val onsetX = onsetSample * width / totalSamples
                            if (onsetX in 0f..width) {
                                drawLine(
                                    Color.Yellow.copy(alpha = 0.5f),
                                    Offset(onsetX, 0f),
                                    Offset(onsetX, height),
                                    2f
                                )
                            }
                        }
                    }
                    onDrawBehind {
                        drawImage(bitmap)
                    }
                }
            }
            .pointerInput(data.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture
                    val totalSamples = data.size.toFloat()
                    val startX = localStart * width / totalSamples
                    val endX = localEnd * width / totalSamples

                    val handleRadius = 40.dp.toPx()
                    selectedHandle = when {
                        !isPlaying && abs(down.position.x - startX) < handleRadius -> 0
                        !isPlaying && abs(down.position.x - endX) < handleRadius -> 1
                        else -> null
                    }
                    if (selectedHandle != null) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            val currentX = change.position.x
                            val newSample = (currentX * totalSamples / width).toInt()

                            if (selectedHandle == 0) {
                                localStart = newSample.coerceIn(0, localEnd - 100)
                                onStartChanged(localStart)
                            } else {
                                localEnd = newSample.coerceIn(localStart + 100, data.size)
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

            val totalSamples = data.size.toFloat()
            val startX = localStart * width / totalSamples
            val endX = localEnd * width / totalSamples

            val startColor = if (selectedHandle == 0) Color.White else CyanAccent
            val endColor = if (selectedHandle == 1) Color.White else CyanAccent

            if (startX in 0f..width) {
                drawLine(startColor, Offset(startX, 0f), Offset(startX, height), 2.dp.toPx())
            }
            if (endX in 0f..width) {
                drawLine(endColor, Offset(endX, 0f), Offset(endX, height), 2.dp.toPx())
            }
            if (isPlaying) {
                val cursorX = (localStart + playbackPosition) * width / totalSamples
                if (cursorX in 0f..width) drawLine(
                    Color.White,
                    Offset(cursorX, 0f),
                    Offset(cursorX, height),
                    1.dp.toPx()
                )
            }
        }
    }
}
