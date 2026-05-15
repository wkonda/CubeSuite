package com.wkonda.cubesuite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.mvave.Module
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.mvave.Settings
import com.wkonda.cubesuite.ui.theme.ModTrackRed

@Preview
@Composable
fun SettingsScreenPreview() = SettingsScreen(
    Settings(
        general = Module.General(volume = 127, ampSw = 1, cabSw = 1, modSw = 0),
        cab = Module.CAB(ir = 8, reverb = 3),
        mod = Module.MOD(mix = 118, fb = 127, time = 31, mod = 15),
        amp = Module.AMP(tone = 15, gain = 7, type = 8)
    )
) { _,_->}
@Composable
fun SettingsScreen(settings: Settings, onAction: (Setting, Byte) -> Unit) {
    Column(Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .padding(top = 40.dp)) {
        Text("CUBE SUITE", color = ModTrackRed, fontSize = 48.sp, fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold, modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 20.dp))

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

