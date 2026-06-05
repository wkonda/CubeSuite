package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.looper.data.LoopMetadata
import com.wkonda.cubesuite.ui.theme.DarkBackground
import com.wkonda.cubesuite.ui.theme.SurfaceGray
import com.wkonda.cubesuite.ui.theme.TrackRed
import java.util.Locale

@Composable
fun LoopListScreen(
    loops: List<LoopMetadata>,
    onSelect: (LoopMetadata) -> Unit,
    onDelete: (LoopMetadata) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(loops) { loop ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceGray)
                    .clickable { onSelect(loop) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        loop.name.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        String.format(
                            Locale.US,
                            "%.2fS",
                            loop.totalSamples.toDouble() / LooperConfig.SAMPLE_RATE
                        ), color = Color.Gray, style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    "DELETE",
                    Modifier.clickable { onDelete(loop) },
                    color = TrackRed,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
