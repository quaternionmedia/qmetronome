package media.quaternion.qmetronome.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiClockSender
import media.quaternion.qmetronome.midi.UsbMidiConnector
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
import kotlin.math.roundToInt

/**
 * Every control that isn't "look at the beat" or "start/stop/tap" lives here: one dismissable
 * grouping instead of a permanently visible wall of switches and pickers competing with the
 * matrix preview for attention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onActivateToy: () -> Unit) {
    val beat by MetronomeEngine.state.collectAsState()
    val visualizer by MetronomeEngine.visualizer.collectAsState()
    val clockStatus by MetronomeEngine.clockStatus.collectAsState()
    val clickEnabled by MetronomeEngine.clickEnabled.collectAsState()
    val clockOutEnabled by MidiClockSender.enabled.collectAsState()

    val context = LocalContext.current
    val usbMidi = remember { UsbMidiConnector(context.applicationContext) }
    val usbDevices by usbMidi.availableDevices.collectAsState()
    val followingUsbDeviceId by usbMidi.followingDeviceId.collectAsState()
    val sendingUsbDeviceId by usbMidi.sendingDeviceId.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            SettingsSection(title = "Beats per bar") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { MetronomeEngine.setBeatsPerBar(beat.beatsPerBar - 1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Fewer beats per bar")
                    }
                    Text(beat.beatsPerBar.toString(), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { MetronomeEngine.setBeatsPerBar(beat.beatsPerBar + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "More beats per bar")
                    }
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
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { usbMidi.refreshDevices() }) {
                            Text("Scan USB MIDI")
                        }
                    }
                    usbDevices.forEach { device ->
                        val isFollowing = device.id == followingUsbDeviceId
                        val isSending = device.id == sendingUsbDeviceId
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(usbMidi.displayName(device), style = MaterialTheme.typography.bodyMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { if (isFollowing) usbMidi.disconnectFollowing() else usbMidi.connectForFollowing(device) },
                                ) {
                                    Text(if (isFollowing) "Stop following" else "Follow")
                                }
                                OutlinedButton(
                                    onClick = { if (isSending) usbMidi.disconnectSending() else usbMidi.connectForSending(device) },
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

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
