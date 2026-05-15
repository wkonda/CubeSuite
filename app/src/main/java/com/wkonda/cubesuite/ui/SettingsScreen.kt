package com.wkonda.cubesuite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.mvave.Module
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.mvave.Settings
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import com.wkonda.cubesuite.ui.theme.TextGrayBox

@Preview
@Composable
fun SettingsScreenPreview() {
    val mockSettings = Settings(
        general = Module.General(volume = 127, ampSw = 1, cabSw = 1, modSw = 0),
        cab = Module.CAB(ir = 8, reverb = 3),
        mod = Module.MOD(mix = 118, fb = 127, time = 31, mod = 15),
        amp = Module.AMP(tone = 15, gain = 7, type = 8)
    )
    SettingsScreen(
        presets = List(Preset.entries.size) { mockSettings },
        activePreset = Preset.A,
        onPresetSelected = {},
        onAction = { _, _ -> }
    )
}

@Composable
fun SettingsScreen(
    presets: List<Settings>,
    activePreset: Preset,
    onPresetSelected: (Preset) -> Unit,
    onAction: (Setting, Byte) -> Unit
) {
    val settings = presets[activePreset.index]
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp)
    ) {
        Text(
            "CUBE SUITE", color = ModTrackRed, fontSize = 48.sp, fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold, modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 20.dp)
        )

        PresetSelector(activePreset, onPresetSelected)

        SettingsRow("VOLUME", settings.general.volume, Setting.VOLUME, onAction = onAction)

        val cabOn = settings.general.cabSw > 0
        Section(Setting.CAB_SW, cabOn, onAction) {
            SettingsRow("IR CAB", settings.cab.ir, Setting.IR, cabOn, onAction)
            SettingsRow("REVERB", settings.cab.reverb, Setting.REVERB, cabOn, onAction)
        }

        val modOn = settings.general.modSw > 0
        Section(Setting.MOD_SW, modOn, onAction) {
            SettingsRow("MIX", settings.mod.mix, Setting.MIX, modOn, onAction)
            SettingsRow("FB", settings.mod.fb, Setting.FB, modOn, onAction)
            SettingsRow("TIME", settings.mod.time, Setting.TIME, modOn, onAction)
            SettingsRow("MOD", settings.mod.mod, Setting.MOD, modOn, onAction)
        }

        val ampOn = settings.general.ampSw > 0
        Section(Setting.AMP_SW, ampOn, onAction) {
            SettingsRow("TONE", settings.amp.tone, Setting.TONE, ampOn, onAction)
            SettingsRow("GAIN", settings.amp.gain, Setting.GAIN, ampOn, onAction)
            SettingsRow("TYPE", settings.amp.type, Setting.TYPE, ampOn, onAction)
        }
    }
}

@Composable
fun PresetSelector(activePreset: Preset, onPresetSelected: (Preset) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Preset.entries.forEach { preset ->
            val isSelected = activePreset == preset
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ModTrackRed else TextGrayBox)
                    .clickable { onPresetSelected(preset) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.name,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
