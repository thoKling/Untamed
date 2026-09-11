package dev.survivaloverhaul.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The launcher's own look: night, and firelight.
 *
 * The palette is ours, not Minecraft's. Nothing here imitates Mojang's
 * branding, which is a requirement rather than a preference for an unofficial
 * launcher, and it is also the reason the product reads as its own thing.
 */
object Palette {
    val NightDeep = Color(0xFF0C0A09)
    val Night = Color(0xFF13100E)
    val Surface = Color(0xFF1B1714)
    val SurfaceRaised = Color(0xFF241E1A)
    val Outline = Color(0xFF352C25)

    val Ember = Color(0xFFFF8A3D)
    val EmberDeep = Color(0xFFDD5F26)
    val EmberGlow = Color(0x33FF8A3D)

    val TextPrimary = Color(0xFFF3EBE2)
    val TextSecondary = Color(0xFFA69A8D)
    val TextFaint = Color(0xFF6E635A)

    val Ready = Color(0xFF79C97C)
    val Pending = Color(0xFFE3B457)
    val Missing = Color(0xFFD9604E)
    val Idle = Color(0xFF5F564E)

    /** The window background: a cold night with a fire somewhere below it. */
    val windowBackground = Brush.verticalGradient(listOf(NightDeep, Night, Color(0xFF191310)))

    val playButton = Brush.horizontalGradient(listOf(Ember, EmberDeep))
}

private val colours = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Color(0xFF1A0C03),
    secondary = Palette.EmberDeep,
    background = Palette.Night,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceRaised,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Outline,
    error = Palette.Missing,
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

@Composable
fun SurvivalOverhaulTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, typography = typography, content = content)
}
