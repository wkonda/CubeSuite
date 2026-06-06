package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkonda.cubesuite.looper.R
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(loops) { loop ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelect(loop) },
                color = Color.Transparent,
                border = BorderStroke(1.dp, SurfaceGray.copy(alpha = 0.5f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            loop.name.uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val bpmStr = loop.bpm?.let {
                            String.format(
                                Locale.US,
                                "%.0f %s",
                                it,
                                stringResource(R.string.bpm)
                            )
                        } ?: "?? ${stringResource(R.string.bpm)}"
                        Text(
                            String.format(
                                Locale.US,
                                "%.1f%s | %s | %s",
                                loop.totalSamples.toDouble() / LooperConfig.SAMPLE_RATE,
                                stringResource(R.string.seconds_short),
                                loop.timeSignature,
                                bpmStr
                            ),
                            color = Color.Gray,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { onDelete(loop) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            LooperIcons.Delete,
                            stringResource(R.string.delete),
                            tint = TrackRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
