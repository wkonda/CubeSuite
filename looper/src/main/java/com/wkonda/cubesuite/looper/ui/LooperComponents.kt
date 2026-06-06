package com.wkonda.cubesuite.looper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wkonda.cubesuite.looper.audio.LooperConfig
import com.wkonda.cubesuite.ui.theme.AccentCyan
import com.wkonda.cubesuite.ui.theme.SurfaceGray
import com.wkonda.cubesuite.ui.theme.TrackRed
import com.wkonda.cubesuite.ui.theme.White
import java.util.Locale

@Composable
fun ViewTab(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Text(
            text,
            color = if (active) AccentCyan else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Black else FontWeight.Bold
        )
        if (active) Box(
            Modifier
                .width(16.dp)
                .height(2.dp)
                .background(AccentCyan, RoundedCornerShape(1.dp))
        ) else Spacer(Modifier.height(2.dp))
    }
}

@Composable
fun MenuSelector(
    label: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Text(
            label,
            color = AccentCyan,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onToggle(true) })
        DropdownMenu(
            expanded,
            { onToggle(false) },
            modifier = Modifier.background(SurfaceGray),
            content = content
        )
    }
}

@Composable
fun Btn(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    Button(
        onClick,
        modifier.height(40.dp),
        enabled,
        colors = ButtonDefaults.buttonColors(
            if (active) TrackRed.copy(0.5f) else Color.Transparent,
            if (active) White else AccentCyan,
            Color.Transparent,
            Color.DarkGray
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        border = if (active) null else BorderStroke(1.dp, SurfaceGray.copy(0.5f))
    ) {
        Row(Modifier, Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Adj(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val step = (LooperConfig.SAMPLE_RATE * 0.002).toInt()
    Surface(
        modifier.height(40.dp),
        RoundedCornerShape(4.dp),
        Color.Transparent,
        border = BorderStroke(1.dp, SurfaceGray.copy(0.5f))
    ) {
        Row(Modifier.padding(horizontal = 4.dp), Arrangement.Center, Alignment.CenterVertically) {
            Text(
                label,
                color = if (enabled) Color.Gray else Color.DarkGray,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                { onValueChange((value - step).coerceAtLeast(0)) },
                Modifier.size(24.dp),
                enabled
            ) {
                Icon(
                    LooperIcons.ChevronLeft,
                    null,
                    tint = if (enabled) AccentCyan else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                String.format(Locale.US, "%.3f", value.toDouble() / LooperConfig.SAMPLE_RATE),
                color = if (enabled) AccentCyan else Color.DarkGray,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            IconButton({ onValueChange(value + step) }, Modifier.size(24.dp), enabled) {
                Icon(
                    LooperIcons.ChevronRight,
                    null,
                    tint = if (enabled) AccentCyan else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
