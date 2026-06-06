package com.wkonda.cubesuite.looper.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.wkonda.cubesuite.looper.LooperScreen
import com.wkonda.cubesuite.looper.LooperUiState
import com.wkonda.cubesuite.looper.LooperViewModel
import com.wkonda.cubesuite.looper.audio.LooperEngine
import com.wkonda.cubesuite.looper.data.LoopRepository
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme

@Preview(showBackground = true)
@Composable
fun LooperScreenPreview() {
    val context = LocalContext.current
    val vm = LooperViewModel(LooperEngine(), LoopRepository(context))
    CubeSuiteTheme {
        LooperScreen(LooperUiState(recordingData = ShortArray(1000)), vm)
    }
}
