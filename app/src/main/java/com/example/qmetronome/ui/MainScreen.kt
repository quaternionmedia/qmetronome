package com.example.qmetronome.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.qmetronome.engine.MetronomeEngine
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Glyph Matrix preview is the focal point by design - it's a 1:1 stand-in for what's
 * actually showing on the hardware, and the rest of the screen exists to support it, not
 * compete with it. Everything that isn't "look at the beat" or "start/stop/tap" lives behind
 * the settings button in a dismissable sheet (see [SettingsSheet]), so the main screen stays
 * down to one functional grouping at a time. Two affordances don't rely on that small button:
 * long-pressing the preview/BPM readout also opens settings (the button can end up crowded
 * against the system status bar depending on device chrome), and swiping the preview
 * left/right cycles visualizers without leaving the main screen at all.
 */
@Composable
fun MainScreen(onActivateToy: () -> Unit, modifier: Modifier = Modifier) {
    val beat by MetronomeEngine.state.collectAsState()
    val frame by MetronomeEngine.frame.collectAsState()
    val previewSize = if (frame.isNotEmpty()) sqrt(frame.size.toDouble()).roundToInt() else 25

    var showSettings by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Long-press anywhere on the preview/BPM readout as a second way into settings -
            // the top-right gear can end up close to (or visually crowded by) the system status
            // bar depending on device/launcher chrome, so this is a deliberately large, easy
            // to hit fallback target rather than the only way in.
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showSettings = true })
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MatrixPreview(
                    matrixSize = previewSize,
                    frame = frame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            // Local to this gesture-detection coroutine, not Compose state - it
                            // only needs to live for one drag's duration, and resetting it on
                            // every pixel of movement has no reason to trigger recomposition.
                            var dragAccumulatedPx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccumulatedPx = 0f },
                                onDragEnd = {
                                    when {
                                        dragAccumulatedPx <= -swipeThresholdPx -> MetronomeEngine.nextVisualizer()
                                        dragAccumulatedPx >= swipeThresholdPx -> MetronomeEngine.previousVisualizer()
                                    }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragAccumulatedPx += dragAmount
                            }
                        },
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = beat.bpm.roundToInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { MetronomeEngine.tapTempo() }) {
                    Text("TAP")
                }
                FilledIconButton(
                    onClick = MetronomeEngine::toggle,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = if (beat.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (beat.isPlaying) "Stop" else "Start",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }

        BrandFooter(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false }, onActivateToy = onActivateToy)
    }
}
