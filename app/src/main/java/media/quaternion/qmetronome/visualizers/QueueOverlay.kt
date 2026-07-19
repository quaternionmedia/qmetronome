package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.TimeSignature
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An ambient "which bar, which beat" background baked directly into the Glyph Matrix frame,
 * mirroring the on-screen `BarQueueDots` row's underlying idea (see `MainScreen.kt`) without
 * competing with it - `BarQueueDots` stays the precise tap-to-jump/long-press-to-remove control
 * surface, while this is a passive cue visible on the physical glyph and its on-screen preview
 * mirror alike. Applied as a post-processing pass over an already-rendered frame (see
 * [media.quaternion.qmetronome.engine.MetronomeEngine]'s render loop) so every visualizer gets it
 * "for free" without any of them needing to know the queue exists.
 *
 * A no-op when [queue] has one entry - the common case (no queue in use) costs nothing and every
 * visualizer's appearance is completely unchanged.
 *
 * Loosely emulates a sheet-music layout: each bar gets its own **horizontal row**, stacked
 * top-to-bottom in queue order, and within a row its beats tick **left to right** - the rotated
 * counterpart of an earlier version of this overlay that stacked bars as side-by-side columns
 * with beats ticking top-to-bottom (the rotation was a direct ask: read bars down the matrix and
 * beats across, the way a line of notation reads).
 *
 * A row's *thickness* - and therefore the size of every tick drawn in it - scales with that bar's
 * own tempo relative to [minBpm]/[maxBpm] (faster reads thicker/bigger, matching the on-screen bar
 * row's own tempo axis) - the caller passes the *queue's own* observed bpm range, not a fixed
 * absolute one, so a bar's row is always sized relative to what's actually queued next to it
 * rather than silently clipping at some boundary most bars never approach (e.g. the extended BPM
 * range's own 0.1-12000 span). This is a deliberate, static property of the bar, not tied to the
 * live beat animation - the earlier
 * version only conveyed tempo through the active tick's transient brightness pulse, which decays
 * over a fixed fraction of *each beat's own duration* regardless of bpm: sampled at a fixed frame
 * rate, a fast bar's pulse is almost always caught already-decayed (it has so little of *this*
 * beat's total pulse-window left by the next rendered frame) while a slow bar's pulse lingers
 * large for many more frames - so apparent size ends up anti-correlated with tempo instead of
 * tracking it. A static, bpm-driven row thickness sidesteps that sampling artifact entirely; the
 * live pulse (still present, via [decayEase]) layers on top as a "beat is happening now" cue
 * rather than being the only way tempo shows up at all.
 *
 * Blended in via [GlyphCanvas.max] rather than overwritten - the whole point is for this to sit
 * *behind* whatever the main visualizer is doing and interact with it, not clip it: wherever the
 * visualizer is already bright, its pixels win untouched; wherever it's dark, the tick structure
 * shows through instead of a hard black cutout.
 *
 * The real Glyph Matrix's LEDs are wired in a circle, not a filled square - the SDK masks the
 * square pixel grid's corners at the hardware layer with a curve app code can't query. Every
 * candidate pixel is individually checked against a conservative inner circle
 * ([USABLE_RADIUS_FRACTION] of the nominal radius) before being touched, since row/tick content
 * spans the whole matrix and there's no single safe baseline to round inward from.
 *
 * [phraseCount]/[activePhraseIndex] additionally draw a small radial dot per phrase around the
 * matrix's outer rim (just outside the per-bar-row content's own [USABLE_RADIUS_FRACTION], still
 * inside the SDK's circular mask) when there's more than one phrase - independent of the per-bar
 * rows above: a single-bar active phrase (`queue.size <= 1`) still gets its radial dots drawn if
 * multiple phrases exist, and a multi-bar phrase still gets its rows drawn even with only one
 * phrase. Toggled independently by the caller (see
 * [media.quaternion.qmetronome.engine.MetronomeEngine.phraseIndicatorEnabled]) the same way
 * [media.quaternion.qmetronome.engine.MetronomeEngine.queueOverlayEnabled] already gates the
 * per-bar rows - a caller with the toggle off simply passes `phraseCount = 1` regardless of the
 * real count, reusing this function's own no-op guard rather than adding a second one.
 */
object QueueOverlay {

    fun apply(
        frame: IntArray,
        matrixSize: Int,
        queue: List<TimeSignature>,
        activeIndex: Int,
        beatIndex: Int,
        phase: Float,
        minBpm: Float,
        maxBpm: Float,
        phraseCount: Int = 1,
        activePhraseIndex: Int = 0,
    ): IntArray {
        if (queue.size <= 1 && phraseCount <= 1) return frame

        val canvas = GlyphCanvas(matrixSize, initial = frame)
        val center = canvas.center
        val usableRadius = matrixSize / 2f * USABLE_RADIUS_FRACTION

        if (queue.size > 1) {
            val startY = center - usableRadius
            val totalHeight = usableRadius * 2f
            val leftX = center - usableRadius
            val rightX = center + usableRadius

            // Row thickness is weighted, not a raw fraction, so even the slowest bar in the queue
            // still gets a visibly non-zero row rather than shrinking toward nothing.
            val weights = queue.map { bar ->
                val tempoFraction = ((bar.bpm - minBpm) / (maxBpm - minBpm).coerceAtLeast(1f)).coerceIn(0f, 1f)
                MIN_ROW_WEIGHT + (1f - MIN_ROW_WEIGHT) * tempoFraction
            }
            val totalWeight = weights.sum()
            val clampedActive = activeIndex.coerceIn(0, queue.lastIndex)

            var cumulative = 0f
            queue.forEachIndexed { slot, bar ->
                val rowTop = startY + (cumulative / totalWeight) * totalHeight
                cumulative += weights[slot]
                val rowBottom = startY + (cumulative / totalWeight) * totalHeight
                val rowHeight = rowBottom - rowTop
                val inset = rowHeight * (1f - TICK_HEIGHT_FRACTION) / 2f
                val topPx = (rowTop + inset).roundToInt()
                val bottomPx = (rowBottom - inset).roundToInt().coerceAtLeast(topPx)
                val tickHalfWidth = (matrixSize / 15).coerceIn(0, 1)
                val isActiveBar = slot == clampedActive

                repeat(bar.beatCount) { beat ->
                    val tickX = (leftX + (beat + 0.5f) / bar.beatCount * (rightX - leftX)).roundToInt()
                    val brightness = if (isActiveBar && beat == beatIndex) {
                        (BASE_TICK_BRIGHTNESS + (255 - BASE_TICK_BRIGHTNESS) * decayEase(phase)).roundToInt()
                    } else {
                        BASE_TICK_BRIGHTNESS
                    }
                    for (y in topPx..bottomPx) {
                        for (x in (tickX - tickHalfWidth)..(tickX + tickHalfWidth)) {
                            if (hypot(x - center, y - center) <= usableRadius) {
                                canvas.max(x, y, brightness)
                            }
                        }
                    }
                }
            }
        }

        if (phraseCount > 1) {
            val dotRadius = (matrixSize / 30f).coerceAtLeast(1f)
            // filledCircle anti-aliases half a pixel past its own radius (see GlyphCanvas), so the
            // farthest a lit pixel can land from the matrix center is indicatorRadius + dotRadius +
            // 0.5 - cap indicatorRadius so that stays at or inside matrixSize/2, the SDK's own
            // circular mask (readme/requirements.md's phrase-indicator requirement), rather than
            // trusting PHRASE_INDICATOR_RADIUS_FRACTION alone, which spills past it at small matrix
            // sizes where dotRadius's 1px floor is proportionally larger.
            val indicatorRadius = (matrixSize / 2f * PHRASE_INDICATOR_RADIUS_FRACTION)
                .coerceAtMost(matrixSize / 2f - dotRadius - 0.5f)
            val clampedActivePhrase = activePhraseIndex.coerceIn(0, phraseCount - 1)
            repeat(phraseCount) { i ->
                val angle = Math.toRadians(-90.0 + i * 360.0 / phraseCount)
                val x = center + indicatorRadius * cos(angle).toFloat()
                val y = center + indicatorRadius * sin(angle).toFloat()
                val brightness = if (i == clampedActivePhrase) 255 else BASE_TICK_BRIGHTNESS
                canvas.filledCircle(x, y, dotRadius, brightness)
            }
        }

        return canvas.toIntArray()
    }

    private const val USABLE_RADIUS_FRACTION = 0.85f
    private const val PHRASE_INDICATOR_RADIUS_FRACTION = 0.95f
    private const val MIN_ROW_WEIGHT = 0.4f
    private const val TICK_HEIGHT_FRACTION = 0.7f
    private const val BASE_TICK_BRIGHTNESS = 70
}
