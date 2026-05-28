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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import kotlin.math.abs

@Composable
fun FFTVisualizer(
    spectrogram: List<List<Double>>,
    totalSamples: Int,
    startSample: Int,
    endSample: Int,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playbackPosition: Int = 0,
    isPlaying: Boolean = false
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var selectedHandle by remember { mutableStateOf<Int?>(null) }
    var localStart by remember { mutableIntStateOf(startSample) }
    var localEnd by remember { mutableIntStateOf(endSample) }

    LaunchedEffect(startSample, endSample) {
        localStart = startSample
        localEnd = endSample
    }

    val maxMag = remember(spectrogram) {
        spectrogram.flatten().maxOrNull()?.coerceAtLeast(0.01) ?: 1.0
    }

    Box(
        modifier = modifier
            .background(AppDarkBackground)
            .onSizeChanged { size = it }
            .drawWithCache {
                val cacheSize = this.size
                val width = cacheSize.width
                val height = cacheSize.height
                if (spectrogram.isEmpty() || width <= 0f || height <= 0f) {
                    onDrawBehind { }
                } else {
                    val bitmap = ImageBitmap(width.toInt(), height.toInt())
                    val canvas = Canvas(bitmap)
                    val drawScope = CanvasDrawScope()
                    drawScope.draw(
                        this,
                        this.layoutDirection,
                        canvas,
                        cacheSize
                    ) {
                        val numWindows = spectrogram.size
                        val numBins = spectrogram[0].size
                        val cellWidth = width / numWindows
                        val cellHeight = height / numBins

                        spectrogram.forEachIndexed { tIdx, magnitudes ->
                            magnitudes.forEachIndexed { fIdx, mag ->
                                val intensity = (mag / maxMag).coerceIn(0.0, 1.0).toFloat()
                                val color = lerp(AppDarkBackground, CyanAccent, intensity)
                                drawRect(
                                    color = color,
                                    topLeft = Offset(
                                        tIdx.toFloat() * cellWidth,
                                        height - (fIdx + 1).toFloat() * cellHeight
                                    ),
                                    size = Size(cellWidth + 1f, cellHeight + 1f)
                                )
                            }
                        }
                    }
                    onDrawBehind {
                        drawImage(bitmap)
                    }
                }
            }
            .pointerInput(totalSamples) {
                if (totalSamples <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture

                    val startX = localStart * width / totalSamples
                    val endX = localEnd * width / totalSamples

                    val handleRadius = 150f
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
                                localEnd = newSample.coerceIn(localStart + 100, totalSamples)
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

            // Draw selection handles
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
                if (cursorX in 0f..width) {
                    drawLine(Color.Cyan, Offset(cursorX, 0f), Offset(cursorX, height), 5f)
                }
            }

            // Draw note labels
            val notes = listOf(
                "E2" to 82.41f, "F2" to 87.31f, "G2" to 98.00f, "A2" to 110.00f, "B2" to 123.47f,
                "C3" to 130.81f, "D3" to 146.83f, "E3" to 164.81f, "F3" to 174.61f, "G3" to 196.00f,
                "A3" to 220.00f, "B3" to 246.94f, "C4" to 261.63f, "D4" to 293.66f, "E4" to 329.63f
            )

            val fromHz = 82f
            val toHz = 330f
            val logFrom = kotlin.math.log2(fromHz)
            val logTo = kotlin.math.log2(toHz)

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 255, 255, 255)
                    textSize = 32f
                }
                notes.forEach { (name, freq) ->
                    val logF = kotlin.math.log2(freq)
                    val ratio = (logF - logFrom) / (logTo - logFrom)
                    val y = height - ratio * height

                    canvas.nativeCanvas.drawText(name, 10f, y, paint)
                    drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, y), Offset(width, y), 1f)
                }
            }
        }
    }
}

@Preview
@Composable
fun SpectrogramPreview() {
    val numWindows = 100
    val numBins = 64
    val mockSpectrogram = List(numWindows) { t ->
        List(numBins) { f ->
            // Simulate a sliding sine wave
            val targetF = (numBins * 0.2 + numBins * 0.6 * (t.toDouble() / numWindows)).toInt()
            if (abs(f - targetF) < 3) 1.0 else Math.random() * 0.1
        }
    }

    FFTVisualizer(
        spectrogram = mockSpectrogram,
        totalSamples = 44100,
        startSample = 5000,
        endSample = 35000,
        onStartChanged = {},
        onEndChanged = {},
        modifier = Modifier.fillMaxSize()
    )
}
