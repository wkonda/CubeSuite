package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wkonda.cubesuite.looper.data.LoopMetadata

@Composable
fun LoopListScreen(
    loops: List<LoopMetadata>,
    onLoopSelected: (LoopMetadata) -> Unit,
    onDeleteLoop: (LoopMetadata) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(loops) { loop ->
            ListItem(
                modifier = Modifier.clickable { onLoopSelected(loop) },
                headlineContent = { Text(loop.name) },
                supportingContent = { Text("${loop.totalSamples} samples") },
                trailingContent = {
                    Button(onClick = { onDeleteLoop(loop) }) {
                        Text("X")
                    }
                }
            )
            HorizontalDivider()
        }
    }
}
