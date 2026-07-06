package media.quaternion.qmetronome.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Local `ImageVector`s for the handful of icons `androidx.compose.material:material-icons-core`
 * doesn't include - `material-icons-extended` has all of them too, but as an ~87MB dependency
 * (confirmed via the Gradle cache) whose ~2000+ generated icon classes all get fully dexed in a
 * debug build (no R8 shrinking), which was the dominant cause of this app's oversized alpha APK
 * for a total of 8 icons actually used. Built from simple geometry (rectangles, triangles, and
 * trig-computed circles/stars) rather than hand-copied bezier path data - safer to get exactly
 * right than transcribing curve coordinates from memory, and consistent with this app's own
 * [media.quaternion.qmetronome.visualizers.GlyphCanvas]-style "simple primitives" drawing
 * philosophy elsewhere.
 */
object ExtraIcons {

    private const val VIEWPORT = 24f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply(block).build()

    /** Traces a regular polygon approximating a circle - safer than hand-tuned bezier/arc-flag
     * math for a shape this simple, and visually indistinguishable from a true circle at icon
     * sizes with enough segments. */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float, segments: Int = 24) {
        for (i in 0 until segments) {
            val angle = Math.toRadians(i * 360.0 / segments)
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    val Remove: ImageVector by lazy {
        icon("Remove") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 11f)
                lineTo(19f, 11f)
                lineTo(19f, 13f)
                lineTo(5f, 13f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f)
                lineTo(10f, 5f)
                lineTo(10f, 19f)
                lineTo(6f, 19f)
                close()
                moveTo(14f, 5f)
                lineTo(18f, 5f)
                lineTo(18f, 19f)
                lineTo(14f, 19f)
                close()
            }
        }
    }

    val ExpandMore: ImageVector by lazy {
        icon("ExpandMore") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 10f)
                lineTo(7.41f, 8.59f)
                lineTo(12f, 13.17f)
                lineTo(16.59f, 8.59f)
                lineTo(18f, 10f)
                lineTo(12f, 16f)
                close()
            }
        }
    }

    val ExpandLess: ImageVector by lazy {
        icon("ExpandLess") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 14f)
                lineTo(7.41f, 15.41f)
                lineTo(12f, 10.83f)
                lineTo(16.59f, 15.41f)
                lineTo(18f, 14f)
                lineTo(12f, 8f)
                close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 18f)
                lineTo(14.5f, 12f)
                lineTo(6f, 6f)
                close()
                moveTo(16f, 6f)
                lineTo(18f, 6f)
                lineTo(18f, 18f)
                lineTo(16f, 18f)
                close()
            }
        }
    }

    /** Two opposing arrows (not Google's exact loop-arrow glyph, a deliberately simpler original
     * design - see the file kdoc for why hand-copied curve data was avoided). */
    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 6.5f)
                lineTo(16f, 6.5f)
                lineTo(16f, 8.5f)
                lineTo(4f, 8.5f)
                close()
                moveTo(14f, 5f)
                lineTo(20f, 7.5f)
                lineTo(14f, 10f)
                close()
                moveTo(20f, 15.5f)
                lineTo(8f, 15.5f)
                lineTo(8f, 17.5f)
                lineTo(20f, 17.5f)
                close()
                moveTo(10f, 14f)
                lineTo(4f, 16.5f)
                lineTo(10f, 19f)
                close()
            }
        }
    }

    val StarBorder: ImageVector by lazy {
        icon("StarBorder") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                val center = 12f
                val outerRadius = 9f
                val innerRadius = 3.6f
                val points = (0 until 10).map { i ->
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                    Offset(
                        (center + radius * cos(angle)).toFloat(),
                        (center + radius * sin(angle)).toFloat(),
                    )
                }
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
        }
    }

    /** A fingertip dot with a ripple ring around it - not Google's hand-and-finger glyph, a
     * simpler original "tap point" design (see the file kdoc). */
    val TouchApp: ImageVector by lazy {
        icon("TouchApp") {
            path(fill = SolidColor(Color.Black)) {
                circle(cx = 12f, cy = 15f, r = 2.6f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
            ) {
                circle(cx = 12f, cy = 10.5f, r = 6.5f)
            }
        }
    }
}
