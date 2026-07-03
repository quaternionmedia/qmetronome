package media.quaternion.qmetronome.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.ui.theme.PureWhite
import media.quaternion.qmetronome.ui.theme.QmNavy
import kotlin.math.cos
import kotlin.math.sin

/**
 * Two brand marks, each a small crisp vector shape with a few "pixel" accents integrated, rather
 * than the earlier full pixel-grid render - that read as too abstract at small scale. Long-press
 * either to open its GitHub page (not a plain tap, deliberately, so a stray tap can't bounce
 * someone out of this minimal app).
 *
 * [QmBrandMark] sits bottom-left, mirroring the settings gear's bottom-right placement.
 * [AppBrandMark] sits bottom-center - qMetronome's own mark deserves the more prominent spot, and
 * shares one canonical pose - triangle body, pivot near the base, arm swung to a fixed 45° - with
 * [media.quaternion.qmetronome.visualizers.MetronomeVisualizer]'s actual idle-frame render (the
 * Glyph Matrix's resting pose) and with `ic_launcher_foreground.xml`/`toy_preview.xml`. The
 * numbers (pivot at 50%/85%, body top at 30%, arm length 62%, 45°) are kept identical by hand
 * across all four representations - there's no way to literally share code between a Compose
 * Canvas, an Android vector drawable, and a pixel-brightness algorithm, so consistency here means
 * the same proportions, not the same source.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QmBrandMark(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .combinedClickable(
                onClick = {},
                onLongClick = { openUrl(context, "https://github.com/quaternionmedia") },
                onLongClickLabel = "Quaternion Media on GitHub",
            )
            .semantics { contentDescription = "Quaternion Media on GitHub" }
            .background(QmNavy, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "QM",
            color = PureWhite,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        // A few "nothing" pixels accenting the corner - the badge itself stays a legible
        // wordmark, same reasoning as the original text badge this replaced.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            repeat(3) {
                Box(Modifier.size(2.dp).background(PureWhite.copy(alpha = 0.55f)))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppBrandMark(modifier: Modifier = Modifier, sizeDp: Dp = 36.dp) {
    val context = LocalContext.current
    Canvas(
        modifier = modifier
            .size(sizeDp)
            .combinedClickable(
                onClick = {},
                onLongClick = { openUrl(context, "https://github.com/quaternionmedia/qmetronome") },
                onLongClickLabel = "qMetronome on GitHub",
            )
            .semantics { contentDescription = "qMetronome on GitHub" },
    ) {
        // Same canonical pose as MetronomeVisualizer's idle frame (and ic_launcher_foreground /
        // toy_preview) - pivot near the base, arm at a fixed 45° deflection (MetronomeVisualizer's
        // own idle angle: beatIndex=0, phase=0 is the triangle wave's peak), armLength 62% of the
        // icon's size. Body/arm/weight *sizes* deliberately don't copy MetronomeVisualizer's own
        // numbers, though - those are tuned for an animated beat-flash (a big flash reads as a
        // beat cue), which looks oversized/heavy-handed as a static logo. Bigger body, thinner
        // arm, smaller weight than the live visualizer instead.
        val w = size.width
        val h = size.height
        val pivot = Offset(w * 0.5f, h * 0.85f)
        val bodyTopY = h * 0.26f
        val bodyTopHalfWidth = w * 0.11f
        val bodyBottomHalfWidth = w * 0.30f

        val body = Path().apply {
            moveTo(pivot.x - bodyTopHalfWidth, bodyTopY)
            lineTo(pivot.x + bodyTopHalfWidth, bodyTopY)
            lineTo(pivot.x + bodyBottomHalfWidth, pivot.y)
            lineTo(pivot.x - bodyBottomHalfWidth, pivot.y)
            close()
        }
        drawPath(body, color = PureWhite)

        // The swinging arm at rest, off-center - an old-school triangle-metronome's wiper, not a
        // clock pendulum (see MetronomeVisualizer for the animated version of this same idea).
        val armLength = w * 0.62f
        val angle = (Math.PI / 4.0).toFloat() // 45 degrees - MetronomeVisualizer's own idle angle
        val tip = Offset(pivot.x + armLength * sin(angle), pivot.y - armLength * cos(angle))
        drawLine(color = QmNavy, start = pivot, end = tip, strokeWidth = w * 0.04f, cap = StrokeCap.Round)

        // One deliberate "nothing" pixel - the weight at the arm's tip is a blocky square rather
        // than a smooth circle, nodding to the Glyph Matrix without pixelating the whole mark.
        val pixel = w * 0.11f
        drawRect(
            color = QmNavy,
            topLeft = Offset(tip.x - pixel / 2f, tip.y - pixel / 2f),
            size = Size(pixel, pixel),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
