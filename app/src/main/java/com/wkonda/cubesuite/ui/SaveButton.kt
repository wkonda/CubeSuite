package com.wkonda.cubesuite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.wkonda.cubesuite.ui.theme.ModTrackRed

@Preview
@Composable
fun SaveButtonPreview() {
    SaveButton { }
}

@Composable
fun SaveButton(onSave: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ModTrackRed)
            .clickable { onSave() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Save",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}