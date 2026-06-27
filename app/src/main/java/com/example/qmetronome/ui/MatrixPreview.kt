package com.example.qmetronome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Renders the same brightness grid the real Glyph Matrix would show, so the visualizer can be
 * tuned and demoed without a physical Nothing device attached.
 */
@Composable
fun MatrixPreview(matrixSize: Int, frame: IntArray, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black),
    ) {
        if (matrixSize <= 0 || frame.size < matrixSize * matrixSize) return@Canvas
        val cell = size.minDimension / matrixSize
        val dotRadius = cell * 0.38f
        for (row in 0 until matrixSize) {
            for (col in 0 until matrixSize) {
                val brightness = frame[row * matrixSize + col].coerceIn(0, 255)
                if (brightness <= 0) continue
                val center = Offset(
                    x = cell * col + cell / 2f,
                    y = cell * row + cell / 2f,
                )
                drawCircle(
                    color = Color.White.copy(alpha = brightness / 255f),
                    radius = dotRadius,
                    center = center,
                )
            }
        }
    }
}
