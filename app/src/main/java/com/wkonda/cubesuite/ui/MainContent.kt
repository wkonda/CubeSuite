package com.wkonda.cubesuite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.R
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.mvave.Settings
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.ModTrackRed

@Composable
fun MainContent(
    allSettings: Map<Preset, Settings>?,
    activePreset: Preset,
    isSuccess: Boolean?,
    onPresetSelected: (Preset) -> Unit,
    onSave: () -> Unit,
    onAction: (Setting, Byte) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(), color = AppDarkBackground
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                color = ModTrackRed,
                fontSize = 48.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 20.dp)
            )

            if (allSettings != null) {
                SettingsScreen(
                    activePreset = activePreset,
                    settings = allSettings[activePreset]!!,
                    isSuccess = isSuccess,
                    onPresetSelected = onPresetSelected,
                    onSave = onSave,
                    onAction = onAction
                )
            } else {
                Text(
                    stringResource(R.string.no_device_found),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 100.dp)
                        .padding(horizontal = 24.dp)
                )
            }
        }
    }
}