package com.wkonda.cubesuite.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed

@Composable
fun SaveButton(isSuccess: Boolean?, onSave: () -> Unit) {
    var isSaving by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isSuccess) { if (isSuccess != null) isSaving = false }

    val scale by animateFloatAsState(
        targetValue = when {
            isSaving -> 0.97f
            isSuccess == true -> 1.05f
            isPressed -> 0.95f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val color by animateColorAsState(
        targetValue = if (isSuccess == true) CyanAccent else ModTrackRed,
        label = "color"
    )

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(
                enabled = !isSaving,
                interactionSource = interactionSource,
                indication = null
            ) {
                isSaving = true
                onSave()
            }
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .widthIn(min = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = when {
                isSaving -> "Saving..."
                isSuccess == true -> "Saved!"
                else -> "Save"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "text"
        ) { targetText ->
            Text(targetText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}