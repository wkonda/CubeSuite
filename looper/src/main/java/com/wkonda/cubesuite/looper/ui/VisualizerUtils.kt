package com.wkonda.cubesuite.looper.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.ui.theme.AnalysisBlue
import com.wkonda.cubesuite.ui.theme.AnalysisYellow
import com.wkonda.cubesuite.ui.theme.TrackRed
import com.wkonda.cubesuite.ui.theme.White
import kotlin.math.abs

fun DrawScope.drawLoopGrid(
    start: Int,
    end: Int,
    totalSamples: Int,
    totalBeats: Int,
    signature: Pair<Int, Int>,
    handle: Int = -1
) {
    val w = size.width;
    val h = size.height; if (w <= 0 || h <= 0 || totalSamples <= 0) return
    val sX = start.toFloat() * w / totalSamples;
    val eX = end.toFloat() * w / totalSamples
    drawLine(if (handle == 0) White else TrackRed, Offset(sX, 0f), Offset(sX, h), 2.5f)
    drawLine(if (handle == 1) White else TrackRed, Offset(eX, 0f), Offset(eX, h), 2.5f)
    for (i in 1 until totalBeats) {
        val b = start + (i * (end - start).toDouble() / totalBeats).toInt()
        val bX = b.toFloat() * w / totalSamples
        if (bX in 0f..w) {
            val isDownbeat = i % signature.first == 0; drawLine(
                if (isDownbeat) White.copy(0.8f) else TrackRed.copy(
                    0.6f
                ), Offset(bX, 0f), Offset(bX, h), if (isDownbeat) 2f else 1f
            )
        }
    }
}

fun DrawScope.drawPlaybackLine(pos: Int, start: Int, totalSamples: Int) {
    val w = size.width;
    val h = size.height;
    val cX = (start + pos) * w / totalSamples
    if (cX in 0f..w) drawLine(White, Offset(cX, 0f), Offset(cX, h), 1.dp.toPx())
}

fun DrawScope.drawAnalysisCurves(
    rhythmic: List<Pair<Int, Double>>,
    correlation: List<Pair<Int, Double>>,
    totalSamples: Int
) {
    val w = size.width;
    val h = size.height
    if (rhythmic.isNotEmpty()) {
        val path = Path(); rhythmic.forEachIndexed { i, p ->
            val x = p.first.toFloat() * w / totalSamples;
            val y = h - (p.second.toFloat() * h * 0.4f); if (i == 0) path.moveTo(
            x,
            y
        ) else path.lineTo(x, y)
        }
        drawPath(path, AnalysisBlue, style = Stroke(width = 1f))
    }
    if (correlation.isNotEmpty()) {
        val path = Path();
        val minS = correlation.minOf { it.second }.toFloat();
        val maxS = correlation.maxOf { it.second }.toFloat();
        val r = (maxS - minS).coerceAtLeast(0.01f);
        var lx = -1f
        correlation.forEach { p ->
            val x = p.first.toFloat() * w / totalSamples;
            val ny = (p.second.toFloat() - minS) / r;
            val y =
                (h * 0.1f) + (1f - ny) * (h * 0.5f); if (lx == -1f || abs(x - lx) > (2048f * w / totalSamples)) path.moveTo(
            x,
            y
        ) else path.lineTo(x, y); lx = x
        }
        drawPath(
            path,
            AnalysisYellow,
            style = Stroke(
                width = 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(4.dp.toPx())
            )
        )
    }
}
