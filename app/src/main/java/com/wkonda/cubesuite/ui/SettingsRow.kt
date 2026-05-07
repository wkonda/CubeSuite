package com.wkonda.cubesuite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModThumbGray
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import com.wkonda.cubesuite.ui.theme.TextGrayBox


@Composable
@Preview
fun SettingsRowPreview() = SettingsRow("Label", 40, type=Setting.TYPE,enabled = true) { _,_ ->}
@Composable
@Preview
fun SettingsRowPreviewDisabled() = SettingsRow("Label", 60, type=Setting.TYPE,enabled = false) { _,_ ->}
@Composable
fun SettingsRow(
    label: String, value: Byte, type: Setting,
    maxRange: Float = 127f, enabled: Boolean = true, onAction: (Setting, Byte) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CyanAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), fontSize = 14.sp)

        Slider(
            value = value.toFloat(),
            onValueChange = { onAction(type, it.toInt().toByte()) },
            valueRange = 0f..maxRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                disabledThumbColor = ModThumbGray,
                disabledActiveTrackColor = ModTrackRed,
                disabledInactiveTrackColor = ModTrackRed
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )

        val color = if (enabled) CyanAccent else ModThumbGray
        Row(Modifier.width(56.dp).border(1.dp, color).background(TextGrayBox).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(value.toString(), color = if (enabled) Color.LightGray else ModThumbGray, fontSize = 12.sp)
            Column {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = color, modifier = Modifier.size(12.dp))
                Icon(Icons.Default.KeyboardArrowDown, null, tint = color, modifier = Modifier.size(12.dp))
            }
        }
    }
}