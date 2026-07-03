package media.quaternion.qmetronome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiClockSender
import media.quaternion.qmetronome.midi.UsbMidiConnector
import media.quaternion.qmetronome.ui.theme.PureBlack
import media.quaternion.qmetronome.ui.theme.RecordingRed
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
import kotlin.math.roundToInt

/**
 * Every control that isn't "look at the beat" or "start/stop/tap" lives here: one dismissable
 * grouping instead of a permanently visible wall of switches and pickers competing with the
 * matrix preview for attention.
 *
 * A full-screen overlay rather than a half-open bottom sheet - the backdrop is translucent
 * (not 100% solid) so the metronome's bright visualizer flashes still glow through dimly behind
 * it, but opaque enough to read as a deliberate full "window" rather than a half-dismissed peek
 * at the main screen. Since there's no visible area behind it to tap-to-dismiss, closing is
 * explicit: the close button, or the system back gesture via [BackHandler].
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onActivateToy: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val beat by MetronomeEngine.state.collectAsState()
    val visualizer by MetronomeEngine.visualizer.collectAsState()
    val clockStatus by MetronomeEngine.clockStatus.collectAsState()
    val clickEnabled by MetronomeEngine.clickEnabled.collectAsState()
    val clockOutEnabled by MidiClockSender.enabled.collectAsState()
    val visualOffsetMs by MetronomeEngine.visualOffsetMs.collectAsState()
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val muteProbability by MetronomeEngine.muteProbability.collectAsState()
    val progressiveMuteEnabled by MetronomeEngine.progressiveMuteEnabled.collectAsState()

    val usbDevices by UsbMidiConnector.availableDevices.collectAsState()
    val followingUsbDeviceId by UsbMidiConnector.followingDeviceId.collectAsState()
    val sendingUsbDeviceId by UsbMidiConnector.sendingDeviceId.collectAsState()
    val starredKeys by UsbMidiConnector.starredDeviceKeys.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.94f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close settings")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Random mute") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteppedSlider(
                        value = muteProbability,
                        onValueChange = MetronomeEngine::setMuteProbability,
                        currentValue = { MetronomeEngine.muteProbability.value },
                        valueRange = 0f..1f,
                        step = 0.05f,
                        defaultValue = 0f,
                        dialogTitle = "Set mute probability (%)",
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        dialogValueLabel = { "${(it * 100).roundToInt()}" },
                        dialogParse = { it.toFloatOrNull()?.div(100f) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progressive start", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = progressiveMuteEnabled, onCheckedChange = MetronomeEngine::setProgressiveMuteEnabled)
                    }
                    Text(
                        text = "Randomly skips the click on some beats - a practice tool for internalizing " +
                            "tempo rather than leaning on every click. Progressive start ramps the chance up " +
                            "from 0% over the first few bars instead of starting at full strength.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Click") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Audible click", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = clickEnabled, onCheckedChange = MetronomeEngine::setClickEnabled)
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Visualizer") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualizerRegistry.all.forEach { candidate ->
                        FilterChip(
                            selected = candidate.id == visualizer.id,
                            onClick = { MetronomeEngine.setVisualizer(candidate) },
                            label = { Text(candidate.displayName) },
                        )
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Visual timing offset") {
                VisualOffsetControls(
                    offsetMs = visualOffsetMs,
                    bpm = beat.bpm,
                    onOffsetChanged = MetronomeEngine::setVisualOffsetMs,
                )
            }

            HorizontalDivider()

            SettingsSection(title = "Layout") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Compact landscape layout", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = compactLandscape, onCheckedChange = MetronomeEngine::setCompactLandscape)
                    }
                    Text(
                        text = "When on, landscape mode fits the preview and controls side-by-side " +
                            "within the screen instead of overflowing. Off keeps the full-size aesthetic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Clock") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (val status = clockStatus) {
                            is MetronomeEngine.ClockStatus.Internal -> "Internal"
                            is MetronomeEngine.ClockStatus.Midi -> {
                                val bpmText = status.measuredBpm?.let { "${it.roundToInt()} BPM" } ?: "waiting for clock…"
                                val sourceText = status.source?.let { " ($it)" } ?: ""
                                "Following MIDI clock$sourceText · $bpmText"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (clockStatus is MetronomeEngine.ClockStatus.Midi) RecordingRed else Color.Unspecified,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { UsbMidiConnector.refreshDevices() }) {
                            Text("Scan USB MIDI")
                        }
                    }
                    usbDevices.forEach { device ->
                        val isFollowing = device.id == followingUsbDeviceId
                        val isSending = device.id == sendingUsbDeviceId
                        val isStarred = UsbMidiConnector.deviceKey(device) in starredKeys
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { UsbMidiConnector.toggleStar(device) }) {
                                    Icon(
                                        imageVector = if (isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (isStarred) {
                                            "Unstar - stop auto-reconnecting this device"
                                        } else {
                                            "Star for auto-reconnect"
                                        },
                                    )
                                }
                                Text(UsbMidiConnector.displayName(device), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (isFollowing) UsbMidiConnector.disconnectFollowing() else UsbMidiConnector.connectForFollowing(device)
                                    },
                                ) {
                                    Text(if (isFollowing) "Stop following" else "Follow")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (isSending) UsbMidiConnector.disconnectSending() else UsbMidiConnector.connectForSending(device)
                                    },
                                ) {
                                    Text(if (isSending) "Stop sending" else "Send to")
                                }
                            }
                            if (isFollowing && isSending) {
                                Text(
                                    text = "Following and sending to the same device - fine on most gear, but if this " +
                                        "device has MIDI Thru/echo enabled it could loop its own clock back to itself.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (isStarred) {
                                Text(
                                    text = "Starred - will auto-reconnect and restore this connection whenever it's " +
                                        "unplugged and plugged back in.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Other apps can also send clock straight to \"qMetronome Clock\" with no cable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Send MIDI clock", style = MaterialTheme.typography.bodyMedium)
                        if (clockOutEnabled) {
                            Box(modifier = Modifier.size(6.dp).background(RecordingRed, CircleShape))
                        }
                        Switch(checked = clockOutEnabled, onCheckedChange = MidiClockSender::setEnabled)
                    }
                    Text(
                        text = "Other apps can pick \"qMetronome Clock\" as a MIDI input to follow this tempo, and " +
                            "\"Send to\" above reaches USB gear directly - both repeat whatever tempo is currently " +
                            "playing, even if the clock above is following something external.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            HorizontalDivider()

            Spacer(Modifier.height(16.dp))
            Button(onClick = onActivateToy, modifier = Modifier.fillMaxWidth()) {
                Text("Activate as Glyph Toy")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Visual timing offset ───────────────────────────────────────────────────

private enum class OffsetUnit(val label: String) {
    Ms("ms"),
    Frames("frames @40fps"),
    BeatPct("beat %"),
}

@Composable
private fun VisualOffsetControls(offsetMs: Float, bpm: Float, onOffsetChanged: (Float) -> Unit) {
    var unit by remember { mutableStateOf(OffsetUnit.Ms) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OffsetUnit.entries.forEach { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = { unit = u },
                    label = { Text(u.label) },
                )
            }
        }

        SteppedSlider(
            value = offsetMs,
            onValueChange = onOffsetChanged,
            currentValue = { MetronomeEngine.visualOffsetMs.value },
            valueRange = -500f..500f,
            step = 10f,
            defaultValue = 0f,
            dialogTitle = "Set visual offset (ms)",
            valueLabel = { ms ->
                when (unit) {
                    OffsetUnit.Ms -> "${ms.roundToInt()} ms"
                    OffsetUnit.Frames -> "${"%.1f".format(ms / FRAME_MS)} frames"
                    OffsetUnit.BeatPct -> "${"%.1f".format(ms / (60_000f / bpm) * 100)} % of beat"
                }
            },
            dialogValueLabel = { "${it.roundToInt()} ms" },
        )

        Text(
            text = "Shifts visuals earlier (negative) or later (positive) relative to the beat timestamp. " +
                "Use negative values to compensate for display latency — if the flash feels late, drag left. " +
                "Reset to 0 ms if in doubt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private const val FRAME_MS = 25f
