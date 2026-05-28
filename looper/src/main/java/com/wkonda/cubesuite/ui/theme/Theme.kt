package com.wkonda.cubesuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ModTrackRed,
    secondary = CyanDark,
    tertiary = ModThumbGray,
    background = AppDarkBackground,
)

@Composable
fun CubeSuiteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme, typography = Typography, content = content
    )
}