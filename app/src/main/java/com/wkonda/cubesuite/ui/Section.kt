package com.wkonda.cubesuite.ui

import androidx.compose.runtime.Composable
import com.wkonda.cubesuite.mvave.Setting

@Composable
fun Section(type: Setting, isOn: Boolean, onAction: (Setting, Byte) -> Unit, content: @Composable () -> Unit) {
    DividerSwitch(isOn) { onAction(type, if (isOn) 0 else 1) }
    content()
}