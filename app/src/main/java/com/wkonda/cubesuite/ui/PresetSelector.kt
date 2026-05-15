package com.wkonda.cubesuite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import com.wkonda.cubesuite.ui.theme.TextGrayBox


@Composable
@Preview
fun PresetSelectorPreview() {
    PresetSelector(Preset.B) { }
}

@Composable
fun PresetSelector(activePreset: Preset, onPresetSelected: (Preset) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
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
                    .padding(vertical = 6.dp),
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
