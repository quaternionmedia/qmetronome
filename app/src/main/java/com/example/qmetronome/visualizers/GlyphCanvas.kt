package com.example.qmetronome.visualizers

import kotlin.math.sqrt

/**
 * Small drawing helpers over a flat brightness array so new [GlyphVisualizer]s can be written
 * without hand-rolling index math or bounds checks.
 */
class GlyphCanvas(val size: Int) {

    val pixels = IntArray(size * size)

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

    fun toIntArray(): IntArray = pixels
}

/** Eases [phase] (0..1) so beat pulses decay quickly instead of fading linearly. */
fun decayEase(phase: Float): Float = (1f - phase).coerceIn(0f, 1f).let { it * it }
