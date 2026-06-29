package media.quaternion.qmetronome.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Numeric/technical readouts (BPM, the headline) use a monospace face to read like a digital
 * counter, echoing the Glyph Matrix's own dot-matrix display without claiming to reproduce
 * Nothing's proprietary "Ndot" font, which isn't available for redistribution outside their
 * SDK's internal use.
 */
val QMetronomeTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    )
}
