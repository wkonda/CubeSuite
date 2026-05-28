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
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CyanAccent
import kotlin.math.abs
import kotlin.math.pow

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

    val notes = remember {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        (40..64).map { midi ->
            val octave = (midi / 12) - 1
            val baseName = names[midi % 12]
            val name = baseName + octave
            val freq = 440f * 2.0.pow((midi - 69.0) / 12.0).toFloat()
            val isWhite = !baseName.contains("#")
            Triple(name, freq, isWhite)
        }
    }

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
                if (cursorX in 0f..width) {
                    drawLine(Color.White, Offset(cursorX, 0f), Offset(cursorX, height), 1.dp.toPx())
                }
            }

            // Draw note labels
            val fromHz = 82.41f
            val toHz = 329.63f
            val logFrom = kotlin.math.log2(fromHz)
            val logTo = kotlin.math.log2(toHz)

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(100, 255, 255, 255)
                    textSize = 24f
                }
                notes.forEach { (name, freq, isWhite) ->
                    val logF = kotlin.math.log2(freq)
                    val ratio = (logF - logFrom) / (logTo - logFrom)
                    val y = height - ratio * height

                    if (isWhite) {
                        canvas.nativeCanvas.drawText(name, 10f, y, paint)
                        drawLine(
                            Color.White.copy(alpha = 0.15f),
                            Offset(0f, y),
                            Offset(width, y),
                            1f
                        )
                    } else {
                        drawLine(
                            Color.White.copy(alpha = 0.05f),
                            Offset(0f, y),
                            Offset(width, y),
                            1f
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SpectrogramPreview() {
    val numWindows = 100
    val numBins = 24
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
