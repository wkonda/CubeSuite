package com.wkonda.cubesuite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.ui.theme.CyanAccent
import com.wkonda.cubesuite.ui.theme.ModTrackRed
import kotlinx.coroutines.delay

private enum class State { IDLE, SAVING, SAVED }

@Composable
fun SaveButton(isSuccess: Boolean?, onSave: () -> Unit) {
    val state = remember { mutableStateOf(State.IDLE) }

    LaunchedEffect(isSuccess) {
        if (isSuccess == true) {
            state.value = State.SAVED
            delay(2000)
            state.value = State.IDLE
        } else if (isSuccess == false) {
            state.value = State.IDLE
        }
    }

    Button(
        onClick = { state.value = State.SAVING; onSave() },
        enabled = state.value == State.IDLE,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (state.value == State.SAVED) CyanAccent else ModTrackRed,
            disabledContainerColor = if (state.value == State.SAVED) CyanAccent else ModTrackRed,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(vertical = 8.dp)
            .widthIn(min = 120.dp)
    ) {
        Text(
            text = when (state.value) {
                State.SAVING -> "Saving..."
                State.SAVED -> "Saved!"
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
