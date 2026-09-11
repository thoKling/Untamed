package dev.survivaloverhaul.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.survivaloverhaul.launcher.ui.theme.Palette

/** A raised panel. Everything on a screen sits in one of these. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(Palette.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint,
        modifier = modifier,
    )
}

/** A small outlined caption, used for the unofficial-launcher marker. */
@Composable
fun Pill(text: String, colour: Color = Palette.TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        modifier = Modifier
            .border(1.dp, colour.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * One line of installation status: a dot whose colour carries the state, the
 * thing being reported, and the detail behind it.
 */
@Composable
fun StatusLine(
    label: String,
    detail: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(tone, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

/**
 * The one button the launcher is built around. Disabled it still says what it
 * is waiting for, because a dead Play button with no explanation is the worst
 * screen a launcher can show.
 */
@Composable
fun PlayButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(
                if (enabled) Palette.playButton else SolidColor(Palette.SurfaceRaised),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFF1A0C03) else Palette.TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 3.sp,
        )
    }
}

/**
 * The download bar.
 *
 * It is drawn from two boxes rather than a Material progress indicator so the
 * fill uses the same ember gradient as the Play button, and so the track stays
 * visible at zero: a bar that disappears when nothing has been downloaded yet
 * reads as a failure rather than as a start.
 */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Palette.SurfaceRaised, RoundedCornerShape(999.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Palette.playButton, RoundedCornerShape(999.dp)),
        )
    }
}

/** A flat, quiet button. Used for navigation and for secondary actions. */
@Composable
fun QuietButton(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = when {
        !enabled -> Palette.TextFaint
        selected -> Palette.Ember
        else -> Palette.TextSecondary
    }
    Box(
        modifier = modifier
            .background(
                if (selected) Palette.SurfaceRaised else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, color = foreground, style = MaterialTheme.typography.titleMedium)
    }
}

/** A labelled row in the settings screen: what it is, why, and its control. */
@Composable
fun SettingRow(
    label: String,
    description: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
        control()
    }
}
