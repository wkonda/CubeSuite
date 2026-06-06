package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.LooperUiState
import com.wkonda.cubesuite.looper.LooperViewModel
import com.wkonda.cubesuite.looper.R
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.DarkBackground

@Composable
fun LibraryScreen(s: LooperUiState, vm: LooperViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.library),
                color = AccentCyan,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 4.dp)
            )
            IconButton({ vm.setScreen("looper") }, modifier = Modifier.size(36.dp)) {
                Icon(LooperIcons.Close, "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
        LoopListScreen(s.loops, { vm.loadLoop(it) }) { vm.deleteLoop(it) }
    }
}
