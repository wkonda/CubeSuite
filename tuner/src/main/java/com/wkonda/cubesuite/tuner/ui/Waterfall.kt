package com.wkonda.cubesuite.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.wkonda.cubesuite.tuner.model.TunerFrame
import com.wkonda.cubesuite.tuner.ui.theme.TunerColors
import kotlin.math.abs

private fun FloatArray.atOrNaN(index: Int): Float {
    return if (index in indices) this[index] else Float.NaN
}

private fun BooleanArray.atOrFalse(index: Int): Boolean {
    return index in indices && this[index]
}

@Composable
fun Waterfall(
    history: List<TunerFrame>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (history.isEmpty()) {
            drawRect(color = TunerColors.Panel)
            return@Canvas
        }

        val frames = history.takeLast(120)
        val rows = frames.size.coerceAtLeast(1)
        val cols = 6
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        drawRect(color = TunerColors.Panel)

        frames.forEachIndexed { rowIndex, frame ->
            val y = size.height - (rowIndex + 1) * cellHeight
            val age = rowIndex / maxOf(1f, (rows - 1).toFloat())
            val alpha = 0.20f + age * 0.80f

            for (col in 0 until cols) {
                val active = frame.active.atOrFalse(col)
                val cents = frame.cents.atOrNaN(col)
                val color = when {
                    !active -> TunerColors.Inactive
                    abs(cents) <= 5f -> TunerColors.InTune
                    abs(cents) <= 10f -> TunerColors.Warm
                    else -> TunerColors.OffTune
                }.copy(alpha = alpha)

                drawRect(
                    color = color,
                    topLeft = Offset(col * cellWidth, y),
                    size = Size(cellWidth + 1f, cellHeight + 1f)
                )
            }
        }

        for (col in 1 until cols) {
            val x = col * cellWidth
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
    }
}
