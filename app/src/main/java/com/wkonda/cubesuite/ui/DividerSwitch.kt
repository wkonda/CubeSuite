package com.wkonda.cubesuite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.ui.theme.CyanAccent

@Composable
fun DividerSwitch(
    isOn: Boolean, onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashedLine(modifier = Modifier.weight(1f))

        // Le bouton circulaire (Plein ou Vide)
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isOn) CyanAccent else Color.Transparent)
                .border(2.dp, CyanAccent, CircleShape)
                .clickable { onToggle() })

        DashedLine(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DashedLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = CyanAccent,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
            strokeWidth = 2f
        )
    }
}