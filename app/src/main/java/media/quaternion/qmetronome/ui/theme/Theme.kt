package media.quaternion.qmetronome.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Quaternion Media navy - reserved for QM brand chrome only (e.g. the footer credit). Never
 * used in the glyph-matching content area, which stays strictly black and white to match the
 * Glyph Matrix itself.
 */
val QmNavy = Color(0xFF0B1F3A)

val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)
val MidGray = Color(0xFF8A8A8A)

/** Matches the Glyph Matrix's own unlit-LED gray (see the kit's preview icon convention). */
val UnlitGray = Color(0xFF1C1C1C)

/**
 * Reserved for transient state/activity indicators only - latch, staged changes, external clock
 * activity - a studio tally-light accent, not a persistent theme color. Same restraint as
 * [QmNavy]: never used in the glyph-matching content area otherwise.
 */
val RecordingRed = Color(0xFFE0303A)

private val MonochromeColors = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    secondary = MidGray,
    onSecondary = PureBlack,
    tertiary = QmNavy,
    onTertiary = PureWhite,
    background = PureBlack,
    onBackground = PureWhite,
    surface = PureBlack,
    onSurface = PureWhite,
    surfaceVariant = UnlitGray,
    onSurfaceVariant = PureWhite,
    outline = MidGray,
)

/**
 * Sharp, blocky corners in the spirit of the Glyph Matrix's pixel grid - no soft
 * Material-default rounding.
 */
private val BlockyShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/**
 * Always black-on-white-on-black, regardless of system theme or wallpaper-derived dynamic
 * color - matching Nothing's own OS design language, which is deliberately monochrome rather
 * than themed. The single exception is [QmNavy], used sparingly for QM's own brand chrome.
 */
@Composable
fun QMetronomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonochromeColors,
        shapes = BlockyShapes,
        typography = QMetronomeTypography,
        content = content,
    )
}
