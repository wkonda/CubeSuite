package com.wkonda.cubesuite.tuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TunerColorScheme = darkColorScheme(
    primary = TunerColors.Accent,
    secondary = TunerColors.Warm,
    tertiary = TunerColors.OffTune,
    background = TunerColors.Background,
    surface = TunerColors.Panel
)

@Composable
fun TunerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TunerColorScheme,
        content = content
    )
}
