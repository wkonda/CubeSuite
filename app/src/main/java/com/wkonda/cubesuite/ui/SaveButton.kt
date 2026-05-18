package com.wkonda.cubesuite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import kotlinx.coroutines.delay

@Composable
fun SaveButton(isSuccess: Boolean?, onSave: () -> Unit) {
    var isSaving by remember { mutableStateOf(value = false) }
    var localSuccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isSuccess) {
        if (isSuccess != null) {
            isSaving = false
            localSuccess = isSuccess
            if (isSuccess) {
                delay(2000)
                localSuccess = null
            }
        }
    }

    Button(
        onClick = {
            isSaving = true
            localSuccess = null
            onSave()
        },
        enabled = (!isSaving) && (localSuccess == null),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (localSuccess == true) CyanAccent else ModTrackRed,
            disabledContainerColor = if (localSuccess == true) CyanAccent else ModTrackRed,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(vertical = 8.dp)
            .widthIn(min = 120.dp)
    ) {
        Text(
            text = when {
                isSaving -> "Saving..."
                localSuccess == true -> "Saved!"
                else -> "Save"
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Preview
@Composable
fun SaveButtonPreview() {
    SaveButton(isSuccess = null) {}
}
