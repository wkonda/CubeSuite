package com.wkonda.cubesuite.looper.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LooperIcons {
    val Mic = ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 14f)
        curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
        lineTo(15f, 5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
        curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
        lineTo(9f, 11f)
        curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
        close()
        moveTo(17f, 11f)
        curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
        curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
        lineTo(5f, 11f)
        curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.93f)
        lineTo(11f, 21f)
        lineTo(13f, 21f)
        lineTo(13f, 17.93f)
        curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
        lineTo(17f, 11f)
        close()
    }.build()

    val Library = ImageVector.Builder(
        name = "Library",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 13.5f)
        lineTo(12f, 7f)
        horizontalLineTo(16f)
        verticalLineTo(9f)
        horizontalLineTo(14f)
        verticalLineTo(13.5f)
        curveTo(14f, 14.88f, 12.88f, 16f, 11.5f, 16f)
        curveTo(10.12f, 16f, 9f, 14.88f, 9f, 13.5f)
        curveTo(9f, 12.12f, 10.12f, 11f, 11.5f, 11f)
        curveTo(11.69f, 11f, 11.85f, 11.02f, 12f, 11.06f)
        verticalLineTo(13.5f)
        close()
        moveTo(20f, 4f)
        verticalLineTo(16f)
        horizontalLineTo(8f)
        verticalLineTo(4f)
        horizontalLineTo(20f)
        close()
        moveTo(22f, 2f)
        horizontalLineTo(6f)
        verticalLineTo(18f)
        horizontalLineTo(22f)
        verticalLineTo(2f)
        close()
        moveTo(4f, 6f)
        horizontalLineTo(2f)
        verticalLineTo(22f)
        horizontalLineTo(18f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
    }.build()

    val Play = ImageVector.Builder(
        name = "Play",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 5f)
        verticalLineTo(19f)
        lineTo(19f, 12f)
        lineTo(8f, 5f)
        close()
    }.build()

    val Stop = ImageVector.Builder(
        name = "Stop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 6f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        verticalLineTo(6f)
        close()
    }.build()

    val Analyze = ImageVector.Builder(
        name = "Analyze",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 3f)
        horizontalLineTo(5f)
        curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        verticalLineTo(5f)
        curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
        close()
        moveTo(9f, 17f)
        horizontalLineTo(7f)
        verticalLineTo(10f)
        horizontalLineTo(9f)
        verticalLineTo(17f)
        close()
        moveTo(13f, 17f)
        horizontalLineTo(11f)
        verticalLineTo(7f)
        horizontalLineTo(13f)
        verticalLineTo(17f)
        close()
        moveTo(17f, 17f)
        horizontalLineTo(15f)
        verticalLineTo(13f)
        horizontalLineTo(17f)
        verticalLineTo(17f)
        close()
    }.build()

    val Save = ImageVector.Builder(
        name = "Save",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 3f)
        horizontalLineTo(5f)
        curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        verticalLineTo(7f)
        lineTo(17f, 3f)
        close()
        moveTo(12f, 19f)
        curveTo(10.34f, 19f, 9f, 17.66f, 9f, 16f)
        curveTo(9f, 14.34f, 10.34f, 13f, 12f, 13f)
        curveTo(13.66f, 13f, 15f, 14.34f, 15f, 16f)
        curveTo(15f, 17.66f, 13.66f, 19f, 12f, 19f)
        close()
        moveTo(15f, 9f)
        horizontalLineTo(5f)
        verticalLineTo(5f)
        horizontalLineTo(15f)
        verticalLineTo(9f)
        close()
    }.build()

    val ChevronLeft = ImageVector.Builder(
        name = "ChevronLeft",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(15.41f, 7.41f)
        lineTo(14f, 6f)
        lineTo(8f, 12f)
        lineTo(14f, 18f)
        lineTo(15.41f, 16.59f)
        lineTo(10.83f, 12f)
        lineTo(15.41f, 7.41f)
        close()
    }.build()

    val ChevronRight = ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(10f, 6f)
        lineTo(8.59f, 7.41f)
        lineTo(13.17f, 12f)
        lineTo(8.59f, 16.59f)
        lineTo(10f, 18f)
        lineTo(16f, 12f)
        lineTo(10f, 6f)
        close()
    }.build()

    val Delete = ImageVector.Builder(
        name = "Delete",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 19f)
        curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
        horizontalLineTo(16f)
        curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
        verticalLineTo(7f)
        horizontalLineTo(6f)
        verticalLineTo(19f)
        close()
        moveTo(19f, 4f)
        horizontalLineTo(15.5f)
        lineTo(14.5f, 3f)
        horizontalLineTo(9.5f)
        lineTo(8.5f, 4f)
        horizontalLineTo(5f)
        verticalLineTo(6f)
        horizontalLineTo(19f)
        verticalLineTo(4f)
        close()
    }.build()

    val Close = ImageVector.Builder(
        name = "Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 6.41f)
        lineTo(17.59f, 5f)
        lineTo(12f, 10.59f)
        lineTo(6.41f, 5f)
        lineTo(5f, 6.41f)
        lineTo(10.59f, 12f)
        lineTo(5f, 17.59f)
        lineTo(6.41f, 19f)
        lineTo(12f, 13.41f)
        lineTo(17.59f, 19f)
        lineTo(19f, 17.59f)
        lineTo(13.41f, 12f)
        lineTo(19f, 6.41f)
        close()
    }.build()
}
