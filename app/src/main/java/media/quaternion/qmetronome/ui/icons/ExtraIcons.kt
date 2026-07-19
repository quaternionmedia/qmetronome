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
 * for what was originally a handful of icons actually used, now 15 and still growing incrementally
 * rather than reconsidering the tradeoff. Built from simple geometry (rectangles, triangles, and
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

    /** An open (unclosed) arc, the same trig-segment approach as [circle] - used for [Help]'s
     * question-mark hook, where a full closed shape isn't what's wanted. Degrees, not radians,
     * to keep the call site's sweep readable. */
    private fun PathBuilder.arc(cx: Float, cy: Float, r: Float, startDeg: Double, endDeg: Double, segments: Int = 16) {
        for (i in 0..segments) {
            val angle = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments)
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
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

    /** A ring with a question-mark hook + stem + dot inside - not Google's exact glyph, built
     * from the same [arc]/[circle] trig primitives as everything else here (see the file kdoc). */
    val Help: ImageVector by lazy {
        icon("Help") {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f) {
                circle(cx = 12f, cy = 12f, r = 9f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                arc(cx = 12f, cy = 9.5f, r = 3f, startDeg = -160.0, endDeg = 90.0)
                lineTo(12f, 13.5f)
            }
            path(fill = SolidColor(Color.Black)) {
                circle(cx = 12f, cy = 17f, r = 1.2f)
            }
        }
    }

    /** Three stacked horizontal bars - the "add a phrase" affordance in `BeatsPerBarControls`
     * (`MainScreen.kt`). Deliberately distinct from [Add][androidx.compose.material.icons.Icons.Filled.Add]
     * (already used right next to it for "add a bar") since the two mean different things at
     * different scopes - this reads as "phrases," not another generic plus. */
    val Phrases: ImageVector by lazy {
        icon("Phrases") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 7f)
                lineTo(4f, 7f)
                close()
                moveTo(4f, 10.5f)
                lineTo(20f, 10.5f)
                lineTo(20f, 13.5f)
                lineTo(4f, 13.5f)
                close()
                moveTo(4f, 17f)
                lineTo(20f, 17f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
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

    /** A small upward triangle, evoking a physical metronome's body - the BPM unit-symbol mark
     * (see [MetronomeEngine.unitSymbolsEnabled][media.quaternion.qmetronome.engine.MetronomeEngine.unitSymbolsEnabled]).
     * This and the four icons below it are deliberately tiny/subtle - a label, not a control. */
    val UnitBpm: ImageVector by lazy {
        icon("UnitBpm") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4f)
                lineTo(19f, 20f)
                lineTo(5f, 20f)
                close()
            }
        }
    }

    /** A ">" accent mark - the same notation musicians write above an accented note - the
     * beat-type unit-symbol mark. */
    val UnitBeatType: ImageVector by lazy {
        icon("UnitBeatType") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 5f)
                lineTo(18f, 12f)
                lineTo(6f, 19f)
            }
        }
    }

    /** A lightning bolt - the main screen's manual MIDI Trigger button (see `TransportRow` in
     * `MainScreen.kt`), a universally-recognized "fire this now" symbol rather than a text label,
     * matching the icon-first look of the rest of the transport row. Straight-edged, built from
     * plain [lineTo]s rather than the curved official Material glyph (see the file kdoc). */
    val Trigger: ImageVector by lazy {
        icon("Trigger") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 2f)
                lineTo(7f, 13f)
                lineTo(11f, 13f)
                lineTo(9f, 22f)
                lineTo(17f, 10f)
                lineTo(13f, 10f)
                close()
            }
        }
    }

    /** A single vertical barline, the same mark that ends a measure in real notation - the bar
     * unit-symbol mark. */
    val UnitBar: ImageVector by lazy {
        icon("UnitBar") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 4f)
                lineTo(13f, 4f)
                lineTo(13f, 20f)
                lineTo(11f, 20f)
                close()
            }
        }
    }

    /** A double vertical barline, the same mark musicians use to close a song-form section - the
     * phrase unit-symbol mark. */
    val UnitPhrase: ImageVector by lazy {
        icon("UnitPhrase") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 20f)
                lineTo(8f, 20f)
                close()
                moveTo(14f, 4f)
                lineTo(16f, 4f)
                lineTo(16f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }
    }
}
