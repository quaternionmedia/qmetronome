package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.MetronomeEngine
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Small drawing helpers over a flat brightness array so new [GlyphVisualizer]s can be written
 * without hand-rolling index math or bounds checks.
 *
 * [initial], when given, seeds [pixels] with a *copy* of that frame (never the same array
 * instance) so drawing on top of an already-rendered frame - see [QueueOverlay] - never mutates
 * the caller's array in place. That matters because the caller's array may already be published
 * on a `StateFlow`; mutating a previously-emitted array instead of producing a new one would
 * silently break collector notification.
 *
 * With no [initial] (the common case - every visualizer's own `render()` call starts from a blank
 * canvas), [pixels] comes from [BufferPool] instead of a fresh `IntArray(size * size)` - this is
 * called 40×/sec while playing, and that allocation was the dominant source of per-frame garbage
 * in the render path. Safe to reuse *this* array (unlike the StateFlow-published one [initial]
 * copies away from) precisely because it's never handed further than the caller of this
 * constructor - `MetronomeEngine.renderFrame()`/`emitIdleFrame()` still make their own one
 * defensive copy before anything reaches `_frame`'s `StateFlow` or the Glyph Matrix hardware SDK.
 */
class GlyphCanvas(val size: Int, initial: IntArray? = null) {

    val pixels = initial?.copyOf() ?: BufferPool.acquire(size)

    val center: Float = (size - 1) / 2f

    /** Sets a pixel's brightness (0..255), silently ignoring out-of-bounds coordinates. */
    fun set(x: Int, y: Int, brightness: Int) {
        if (x < 0 || x >= size || y < 0 || y >= size) return
        pixels[y * size + x] = brightness.coerceIn(0, 255)
    }

    /** Adds brightness to a pixel, capping at 255 - useful for overlapping strokes. */
    fun add(x: Int, y: Int, brightness: Int) {
        if (x < 0 || x >= size || y < 0 || y >= size) return
        val index = y * size + x
        pixels[index] = (pixels[index] + brightness).coerceIn(0, 255)
    }

    /** Sets a pixel to whichever is brighter: its current value or [brightness]. Unlike [add],
     * this never pushes a pixel *past* the brighter of the two inputs - the right primitive for
     * layering a background structure under already-rendered content (see [QueueOverlay]) without
     * visibly capping or oversaturating whatever's already there. */
    fun max(x: Int, y: Int, brightness: Int) {
        if (x < 0 || x >= size || y < 0 || y >= size) return
        val index = y * size + x
        pixels[index] = maxOf(pixels[index], brightness.coerceIn(0, 255))
    }

    fun fill(brightness: Int) {
        pixels.fill(brightness.coerceIn(0, 255))
    }

    /** Filled circle centered at ([cx], [cy]) with the given [radius], anti-aliased at the edge. */
    fun filledCircle(cx: Float, cy: Float, radius: Float, brightness: Int) {
        val minX = (cx - radius).toInt().coerceAtLeast(0)
        val maxX = (cx + radius).toInt().coerceAtMost(size - 1)
        val minY = (cy - radius).toInt().coerceAtLeast(0)
        val maxY = (cy + radius).toInt().coerceAtMost(size - 1)
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                val distance = sqrt(dx * dx + dy * dy)
                val edge = (radius - distance + 0.5f).coerceIn(0f, 1f)
                if (edge > 0f) add(x, y, (brightness * edge).toInt())
            }
        }
    }

    /** A ring outline centered at ([cx], [cy]) with the given [radius] and [thickness]. */
    fun ring(cx: Float, cy: Float, radius: Float, thickness: Float, brightness: Int) {
        val minX = (cx - radius - thickness).toInt().coerceAtLeast(0)
        val maxX = (cx + radius + thickness).toInt().coerceAtMost(size - 1)
        val minY = (cy - radius - thickness).toInt().coerceAtLeast(0)
        val maxY = (cy + radius + thickness).toInt().coerceAtMost(size - 1)
        val halfThickness = thickness / 2f
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                val distance = sqrt(dx * dx + dy * dy)
                val edge = (halfThickness - kotlin.math.abs(distance - radius) + 0.5f).coerceIn(0f, 1f)
                if (edge > 0f) add(x, y, (brightness * edge).toInt())
            }
        }
    }

    /** A straight stroke from ([x0],[y0]) to ([x1],[y1]), sampled densely enough not to leave gaps at matrix resolution. */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, brightness: Int) {
        val steps = (hypot(x1 - x0, y1 - y0) * 2f).roundToInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            add((x0 + (x1 - x0) * t).roundToInt(), (y0 + (y1 - y0) * t).roundToInt(), brightness)
        }
    }

    fun toIntArray(): IntArray = pixels

    /**
     * Round-robins between 2 pre-allocated buffers per matrix size, instead of a fresh
     * `IntArray(size * size)` on every blank-canvas [GlyphCanvas]. Two (not one) so a genuinely
     * concurrent caller - `emitIdleFrame()` can run on whatever thread a Settings toggle callback
     * fires on, while the render loop runs on the engine's own dedicated dispatcher - never gets
     * handed the exact same in-flight buffer another caller is mid-draw on; `acquire` itself is
     * synchronized so handing out a buffer is atomic even if two callers race to acquire at once.
     * Each acquired buffer is zeroed before handoff so it still reads as a blank canvas.
     */
    private object BufferPool {
        private val buffersBySize = mutableMapOf<Int, Array<IntArray>>()
        private val nextIndexBySize = mutableMapOf<Int, Int>()

        @Synchronized
        fun acquire(size: Int): IntArray {
            val buffers = buffersBySize.getOrPut(size) { arrayOf(IntArray(size * size), IntArray(size * size)) }
            val index = nextIndexBySize.getOrDefault(size, 0)
            nextIndexBySize[size] = (index + 1) % buffers.size
            return buffers[index].also { it.fill(0) }
        }
    }
}

/** Eases [phase] (0..1) so beat pulses decay quickly instead of fading linearly. */
fun decayEase(phase: Float): Float = (1f - phase).coerceIn(0f, 1f).let { it * it }

/**
 * A bar's own [bpm], as a continuous 0..1 size fraction of the fixed [MetronomeEngine.MIN_BPM]/
 * [MetronomeEngine.MAX_BPM] span - independent of whatever else happens to be queued. **Slower
 * reads as bigger** (1 at [MetronomeEngine.MIN_BPM], 0 at [MetronomeEngine.MAX_BPM]) - a deliberate
 * choice, not an arbitrary one: it reads like a wave's own period, a longer/slower cycle drawn
 * larger, rather than tying visual size to onset speed. Used by both
 * [media.quaternion.qmetronome.ui] `BarQueueDots` (on-screen row height) and [QueueOverlay] (glyph
 * row thickness), so the two surfaces read as one consistent visual language rather than drifting
 * apart - see either call site's own kdoc for the fuller "why" (queue-relative scaling was tried
 * first for both and degenerates badly with sparse data or with extended-range bars mixed into a
 * normal-range queue).
 *
 * Deliberately restrains *all* size variation to the plain BPM span - extended-range bpms (below
 * [MetronomeEngine.MIN_BPM] or above [MetronomeEngine.MAX_BPM] - see
 * [MetronomeEngine.extendedBpmRangeEnabled]) clip flat to 0/1 and stay there, rather than
 * stretching the visible range to cover them too. That matters because BPH/BPS bpms can be
 * *enormously* far from the everyday range (down to ~0.0017 bpm, up to 12000): a linear,
 * queue-relative scale sharing a queue between a 0.5 bpm bar, an 8000 bpm bar, and several 120 bpm
 * bars would compress those 120 bpm bars into a barely-distinguishable sliver near one end, since
 * linear normalization has no notion of "which values are actually common." Log-scaling *within*
 * the fixed plain-BPM span (rather than clipping there too) is what keeps everyday tempos spread
 * out relative to each other, while rare BPH/BPS bars simply peg the extreme instead of dominating
 * the whole visible range.
 */
fun bpmSizeFraction(bpm: Float): Float {
    val logMin = ln(MetronomeEngine.MIN_BPM)
    val logMax = ln(MetronomeEngine.MAX_BPM)
    val logValue = ln(bpm.coerceAtLeast(0.0001f))
    val fasterFraction = ((logValue - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    return 1f - fasterFraction
}
