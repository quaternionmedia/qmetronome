package media.quaternion.qmetronome.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.BeatPhase
import media.quaternion.qmetronome.engine.ClickSound
import media.quaternion.qmetronome.engine.ClickSpec
import media.quaternion.qmetronome.engine.ClickWaveform
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.DEFAULT_AUDIO_OFFSET_MS
import media.quaternion.qmetronome.engine.DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS
import media.quaternion.qmetronome.engine.DEFAULT_VISUAL_OFFSET_MS
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.engine.MidiBeatAction
import media.quaternion.qmetronome.engine.Phrase
import media.quaternion.qmetronome.engine.TimeSignature
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.midi.MidiClockSender
import media.quaternion.qmetronome.midi.UsbMidiConnector
import media.quaternion.qmetronome.ui.icons.ExtraIcons
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
 *
 * Every section is a [CollapsibleSection]: one line (title + the single most relevant control)
 * by default, expanding to the rest of that section's controls - keeps the whole screen scannable
 * on a phone without a wall of always-visible sliders/switches/explanations to scroll past.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onActivateToy: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val beat by MetronomeEngine.state.collectAsState()
    val visualizer by MetronomeEngine.visualizer.collectAsState()
    val clockStatus by MetronomeEngine.clockStatus.collectAsState()
    val clickEnabled by MetronomeEngine.clickEnabled.collectAsState()
    val clickSpecs by MetronomeEngine.clickSpecs.collectAsState()
    val clockOutEnabled by MidiClockSender.enabled.collectAsState()
    val midiActionsEnabled by MidiActionSender.enabled.collectAsState()
    val midiActions by MidiActionSender.actions.collectAsState()
    val visualOffsetMs by MetronomeEngine.visualOffsetMs.collectAsState()
    val audioOffsetMs by MetronomeEngine.audioOffsetMs.collectAsState()
    val firstBeatCountInCapMs by MetronomeEngine.firstBeatCountInCapMs.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val timeSignature by MetronomeEngine.timeSignature.collectAsState()
    val phrases by MetronomeEngine.phrases.collectAsState()
    val extendedBpmRangeEnabled by MetronomeEngine.extendedBpmRangeEnabled.collectAsState()
    var showBpmDialog by remember { mutableStateOf(false) }
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val unitSymbolsEnabled by MetronomeEngine.unitSymbolsEnabled.collectAsState()
    val muteProbability by MetronomeEngine.muteProbability.collectAsState()
    val progressiveMuteEnabled by MetronomeEngine.progressiveMuteEnabled.collectAsState()
    val progressiveMuteRampBars by MetronomeEngine.progressiveMuteRampBars.collectAsState()
    val queueOverlayEnabled by MetronomeEngine.queueOverlayEnabled.collectAsState()
    val phraseIndicatorEnabled by MetronomeEngine.phraseIndicatorEnabled.collectAsState()
    val visualizerEnabled by MetronomeEngine.visualizerEnabled.collectAsState()
    val persistentModeEnabled by MetronomeEngine.persistentModeEnabled.collectAsState()

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

            CollapsibleSection(
                title = "Tempo & Bars",
                summary = {
                    val summaryBpm = stagedBpm ?: beat.bpm
                    Text(
                        text = "${bpmDisplayValue(summaryBpm)} ${bpmDisplayUnit(summaryBpm)} · " +
                            "${beat.beatsPerBar}/${timeSignature.unitNoteValue}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            ) {
                // The exact same cluster driving the main screen (BPM/bars/TAP/play-stop/HOLD) -
                // guaranteed to live-sync since it reads the same StateFlows, not a second display
                // that could drift - and while this is open, it's the *only* composed instance
                // (see MainScreen's showControls handling).
                TempoTransportCluster(beat = beat, onShowBpmDialog = { showBpmDialog = true })
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extended range (BPH/BPS)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = extendedBpmRangeEnabled, onCheckedChange = MetronomeEngine::setExtendedBpmRangeEnabled)
                }
                Text(
                    text = "The same live tempo/meter/bar-queue/transport controls as the main screen - " +
                        "changes here apply immediately and vice versa, including which bar is currently " +
                        "active. Extended range unlocks tempos below ${MetronomeEngine.MIN_BPM.roundToInt()} " +
                        "BPM (shown as beats-per-hour) and above ${MetronomeEngine.MAX_BPM.roundToInt()} BPM " +
                        "(beats-per-second).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(4.dp))
                Text("Jump to unit", style = MaterialTheme.typography.bodyMedium)
                JumpToUnitChips()
                Text(
                    text = "Jumps straight to a representative tempo in that unit's own range - the same " +
                        "long-press-to-type dialog on the BPM number itself also lets you type an exact " +
                        "value in whichever unit you pick, converting it for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Random mute",
                summary = {
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
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Progressive start", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = progressiveMuteEnabled, onCheckedChange = MetronomeEngine::setProgressiveMuteEnabled)
                }
                if (progressiveMuteEnabled) {
                    SteppedSlider(
                        value = progressiveMuteRampBars.toFloat(),
                        onValueChange = { MetronomeEngine.setProgressiveMuteRampBars(it.roundToInt()) },
                        currentValue = { MetronomeEngine.progressiveMuteRampBars.value.toFloat() },
                        valueRange = MetronomeEngine.MIN_PROGRESSIVE_MUTE_RAMP_BARS.toFloat()..MetronomeEngine.MAX_PROGRESSIVE_MUTE_RAMP_BARS.toFloat(),
                        step = 1f,
                        defaultValue = MetronomeEngine.DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS.toFloat(),
                        dialogTitle = "Set ramp length (bars)",
                        valueLabel = { "${it.roundToInt()} bar ramp" },
                    )
                }
                Text(
                    text = "Randomly skips the click on some beats - a practice tool for internalizing " +
                        "tempo rather than leaning on every click. Progressive start ramps the chance up " +
                        "from 0% over the configured number of bars instead of starting at full strength - " +
                        "a shorter ramp reaches full strength sooner, a longer one eases in more gradually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Click",
                summary = {
                    Switch(checked = clickEnabled, onCheckedChange = MetronomeEngine::setClickEnabled)
                },
            ) {
                ClickSoundTabs(clickSpecs)
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Visualizer",
                summary = {
                    Switch(checked = visualizerEnabled, onCheckedChange = MetronomeEngine::setVisualizerEnabled)
                },
            ) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bar queue background", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = queueOverlayEnabled,
                        onCheckedChange = MetronomeEngine::setQueueOverlayEnabled,
                        modifier = Modifier.testTag("queue_overlay_switch"),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phrase indicator", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = phraseIndicatorEnabled,
                        onCheckedChange = MetronomeEngine::setPhraseIndicatorEnabled,
                        modifier = Modifier.testTag("phrase_indicator_switch"),
                    )
                }
                Text(
                    text = "Independent toggles - run any combination. The beat visualizer is the " +
                        "animated pattern above (pendulum, sweep, etc.); the bar queue background " +
                        "is the ambient per-bar/per-beat pattern baked into the Glyph Matrix (and " +
                        "its on-screen preview) when more than one bar is queued; the phrase " +
                        "indicator is a small dot per phrase around the matrix's outer rim when " +
                        "more than one phrase exists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Visual timing offset",
                summary = {
                    SteppedSlider(
                        value = visualOffsetMs,
                        onValueChange = MetronomeEngine::setVisualOffsetMs,
                        currentValue = { MetronomeEngine.visualOffsetMs.value },
                        valueRange = -500f..500f,
                        step = 10f,
                        defaultValue = DEFAULT_VISUAL_OFFSET_MS,
                        dialogTitle = "Set visual offset (ms)",
                        valueLabel = { "${it.roundToInt()} ms" },
                    )
                },
            ) {
                VisualOffsetDetails(bpm = beat.bpm)
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Audio timing offset",
                summary = {
                    SteppedSlider(
                        value = audioOffsetMs,
                        onValueChange = MetronomeEngine::setAudioOffsetMs,
                        currentValue = { MetronomeEngine.audioOffsetMs.value },
                        valueRange = -500f..500f,
                        step = 10f,
                        defaultValue = DEFAULT_AUDIO_OFFSET_MS,
                        dialogTitle = "Set audio offset (ms)",
                        valueLabel = { "${it.roundToInt()} ms" },
                    )
                },
            ) {
                AudioOffsetDetails(bpm = beat.bpm)
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "First beat count-in",
                summary = {
                    SteppedSlider(
                        value = firstBeatCountInCapMs,
                        onValueChange = MetronomeEngine::setFirstBeatCountInCapMs,
                        currentValue = { MetronomeEngine.firstBeatCountInCapMs.value },
                        valueRange = 0f..300f,
                        step = 10f,
                        defaultValue = DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS,
                        dialogTitle = "Set count-in cap (ms)",
                        valueLabel = { "${it.roundToInt()} ms" },
                    )
                },
            ) {
                Text(
                    text = "The very first beat of a session can't lead its own click the way every " +
                        "later beat does - there's no advance notice a play press is coming. This is " +
                        "the longest pause qMetronome may hold that first beat back by, to give it " +
                        "a real head start instead: 0 keeps the very first press instant, but that " +
                        "beat's click can trail the flash by roughly the audio offset above. Higher " +
                        "values trade a short, consistent pause for a first beat as precisely timed " +
                        "as the rest. Defaults to ${DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS.roundToInt()} " +
                        "ms, comfortably covering real hardware; double-tap the value above to reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Layout",
                summary = {
                    Switch(
                        checked = compactLandscape,
                        onCheckedChange = MetronomeEngine::setCompactLandscape,
                        modifier = Modifier.testTag("compact_landscape_switch"),
                    )
                },
            ) {
                Text(
                    text = "When on, landscape mode fits the preview and controls side-by-side " +
                        "within the screen instead of overflowing. Off keeps the full-size aesthetic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Symbol-only controls", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = symbolicControlsEnabled,
                        onCheckedChange = MetronomeEngine::setSymbolicControlsEnabled,
                        modifier = Modifier.testTag("symbol_only_controls_switch"),
                    )
                }
                Text(
                    text = "Drops words from the main screen's tempo/transport controls in favor of " +
                        "icons and dots only (TAP, HOLD, the BPM unit label, \"staged\" indicators) - " +
                        "a more unified, purely iconographic look that also needs no translation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unit symbols", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = unitSymbolsEnabled,
                        onCheckedChange = MetronomeEngine::setUnitSymbolsEnabled,
                        modifier = Modifier.testTag("unit_symbols_switch"),
                    )
                }
                Text(
                    text = "Shows a small secondary mark next to BPM, beat type, bar, and " +
                        "phrase controls, naming what each one is at a glance. On by default; turn " +
                        "off for a cleaner, symbol-free look.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            PersistentPlaybackSection(persistentModeEnabled)

            HorizontalDivider()

            CollapsibleSection(
                title = "Clock",
                summary = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (clockOutEnabled) {
                            Box(modifier = Modifier.size(6.dp).background(RecordingRed, CircleShape))
                        }
                        Text("Send clock", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = clockOutEnabled, onCheckedChange = MidiClockSender::setEnabled)
                    }
                },
            ) {
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
                                    imageVector = if (isStarred) Icons.Filled.Star else ExtraIcons.StarBorder,
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
                    text = "Other apps can also send clock straight to \"qMetronome Clock\" with no cable, and " +
                        "\"Send to\" above reaches USB gear directly - both repeat whatever tempo is currently " +
                        "playing, even if the clock above is following something external.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Spacer(Modifier.height(4.dp))
                Text("Outgoing clock feel", style = MaterialTheme.typography.bodyMedium)
                ClockFeelChips()
                Text(
                    text = "Mechanical actively corrects the outgoing clock for the truest, most locked-in " +
                        "beat. Organic skips that correction while repeating a followed clock, letting " +
                        "whatever natural timing variance really occurs come through instead - no fake " +
                        "randomness, just the honest imprecision of real hardware/scheduling. This only " +
                        "affects the MIDI clock bytes sent to other apps/gear - it has no effect on this " +
                        "app's own click or flash, so a difference is only audible on a device actually " +
                        "receiving this clock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "MIDI Actions",
                summary = {
                    Switch(
                        checked = midiActionsEnabled,
                        onCheckedChange = MidiActionSender::setEnabled,
                        modifier = Modifier.testTag("midi_actions_switch"),
                    )
                },
            ) {
                Text(
                    text = "Send a MIDI Note or CC message per beat type, over the same virtual/USB " +
                        "connections \"Send clock\" above already reaches - independent of the " +
                        "audible click, so these fire on their own schedule whether or not Click is " +
                        "on, or a beat happens to be randomly muted for practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                MidiActionTabs(midiActions)
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Beat Overrides",
                summary = {
                    val overrideCount = phrases.sumOf { phrase -> phrase.bars.sumOf { it.midiOverrides?.size ?: 0 } }
                    Text(
                        text = if (overrideCount == 0) "None set" else "$overrideCount set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
            ) {
                BeatOverridesSection(phrases = phrases, midiActions = midiActions)
            }

            HorizontalDivider()

            CollapsibleSection(
                title = "Phrase Actions",
                summary = {
                    val phraseActionCount = phrases.count { it.action.type != MidiActionType.NONE }
                    Text(
                        text = if (phraseActionCount == 0) "None set" else "$phraseActionCount set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
            ) {
                PhraseActionsSection(phrases = phrases)
            }

            HorizontalDivider()

            Spacer(Modifier.height(16.dp))
            Button(onClick = onActivateToy, modifier = Modifier.fillMaxWidth()) {
                Text("Activate as Glyph Toy")
            }
            Spacer(Modifier.height(24.dp))
            AppVersionFooter()
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showBpmDialog) {
        NumericEntryDialog(
            title = "Set BPM",
            initialValue = stagedBpm ?: beat.bpm,
            valueRange = if (extendedBpmRangeEnabled) {
                MetronomeEngine.EXTENDED_MIN_BPM..MetronomeEngine.EXTENDED_MAX_BPM
            } else {
                MetronomeEngine.MIN_BPM..MetronomeEngine.MAX_BPM
            },
            onConfirm = { bpm ->
                MetronomeEngine.setBpm(bpm)
                showBpmDialog = false
            },
            onDismiss = { showBpmDialog = false },
        )
    }
}

/** The very last thing in Settings - just enough to tell support/bug-report context apart
 * (which build a screenshot or log came from) without turning this into a real "About" screen. */
@Composable
private fun AppVersionFooter() {
    val context = LocalContext.current
    val versionText = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            "qMetronome ${info.versionName} (build ${info.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "qMetronome"
        }
    }
    Text(
        text = versionText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Click sounds ────────────────────────────────────────────────────────────

private fun MidiActionType.displayLabel(): String = when (this) {
    MidiActionType.NONE -> "None"
    MidiActionType.NOTE -> "Note"
    MidiActionType.CC -> "CC"
}

private fun ClickSound.displayLabel(): String = when (this) {
    ClickSound.BAR -> "Bar"
    ClickSound.REGULAR -> "Beat"
    ClickSound.ACCENT -> "Accent"
    ClickSound.STRONG_ACCENT -> "Strong Accent"
    ClickSound.CUSTOM -> "Custom"
}

/**
 * All five [ClickSound]s share the exact same control schema ([ClickSpec]: waveform, frequency,
 * duration) - tabbed rather than stacked five times over, since that's exactly the case where
 * tabs pay for themselves instead of just adding another layer of navigation.
 */
@Composable
private fun ClickSoundTabs(clickSpecs: Map<ClickSound, ClickSpec>) {
    var selected by remember { mutableStateOf(ClickSound.BAR) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickSound.entries.forEach { sound ->
                FilterChip(
                    selected = selected == sound,
                    onClick = { selected = sound },
                    label = { Text(sound.displayLabel()) },
                )
            }
        }
        ClickSoundControls(sound = selected, spec = clickSpecs.getValue(selected))
        Text(
            text = "Generated tones, no samples - tune each one's waveform, pitch and length. " +
                "Bar plays on beat 1 of every bar, Beat on every unmarked beat; Accent/Strong " +
                "Accent/Custom play on beats marked that way - long-press the beats-per-bar " +
                "number on the main screen to mark them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * All five [ClickSound]s share the exact same MIDI action schema ([MidiBeatAction]: type,
 * channel, number, value, duration) - tabbed the same way [ClickSoundTabs] already is, for the
 * same reason. Internal (not private) for the same reason as [ClockFeelChips]/[JumpToUnitChips]:
 * [HelpScreen] embeds this exact live control directly.
 */
@Composable
internal fun MidiActionTabs(actions: Map<ClickSound, MidiBeatAction>) {
    var selected by remember { mutableStateOf(ClickSound.BAR) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickSound.entries.forEach { sound ->
                FilterChip(
                    selected = selected == sound,
                    onClick = { selected = sound },
                    label = { Text(sound.displayLabel()) },
                )
            }
        }
        MidiActionControls(sound = selected, action = actions.getValue(selected))
    }
}

/** Thin wrapper over [MidiActionEditor] for the per-[ClickSound] type-default case - reads/writes
 * through [MidiActionSender.setAction]/[MidiActionSender.actions]. */
@Composable
private fun MidiActionControls(sound: ClickSound, action: MidiBeatAction) {
    MidiActionEditor(
        label = sound.displayLabel(),
        action = action,
        currentAction = { MidiActionSender.actions.value.getValue(sound) },
        onChange = { MidiActionSender.setAction(sound, it) },
    )
}

/**
 * The type/channel/number/value/duration controls shared by every [MidiBeatAction] editing
 * surface in this app - the per-[ClickSound] type-default tabs ([MidiActionControls]) and the
 * per-beat override editor ([BeatOverridesSection]) both call this instead of duplicating five
 * [SteppedSlider]s each. [currentAction] mirrors [SteppedSlider]'s own [action] param - a
 * caller backed by a `StateFlow` should read it fresh each call (not close over [action] itself),
 * the same "authoritative source, not a stale recomposition-time snapshot" fix
 * [BpmControls.currentBpm] established, needed here since [HoldRepeatButton]'s repeat loop can
 * fire several steps before recomposition catches up.
 */
@Composable
private fun MidiActionEditor(
    label: String,
    action: MidiBeatAction,
    currentAction: () -> MidiBeatAction,
    onChange: (MidiBeatAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MidiActionType.entries.forEach { type ->
                FilterChip(
                    selected = action.type == type,
                    onClick = { onChange(action.copy(type = type)) },
                    label = { Text(type.displayLabel()) },
                )
            }
        }
        if (action.type != MidiActionType.NONE) {
            SteppedSlider(
                value = (action.channel + 1).toFloat(),
                onValueChange = { onChange(action.copy(channel = it.roundToInt().coerceIn(1, 16) - 1)) },
                currentValue = { (currentAction().channel + 1).toFloat() },
                valueRange = 1f..16f,
                step = 1f,
                defaultValue = 1f,
                dialogTitle = "Set $label MIDI channel",
                valueLabel = { "Ch ${it.roundToInt()}" },
            )
            SteppedSlider(
                value = action.number.toFloat(),
                onValueChange = { onChange(action.copy(number = it.roundToInt().coerceIn(0, 127))) },
                currentValue = { currentAction().number.toFloat() },
                valueRange = 0f..127f,
                step = 1f,
                defaultValue = 60f,
                dialogTitle = if (action.type == MidiActionType.NOTE) "Set $label note number" else "Set $label CC number",
                valueLabel = { if (action.type == MidiActionType.NOTE) "Note ${it.roundToInt()}" else "CC ${it.roundToInt()}" },
            )
            SteppedSlider(
                value = action.value.toFloat(),
                onValueChange = { onChange(action.copy(value = it.roundToInt().coerceIn(0, 127))) },
                currentValue = { currentAction().value.toFloat() },
                valueRange = 0f..127f,
                step = 1f,
                defaultValue = 100f,
                dialogTitle = if (action.type == MidiActionType.NOTE) "Set $label velocity" else "Set $label CC value",
                valueLabel = { "${it.roundToInt()}" },
            )
            if (action.type == MidiActionType.NOTE) {
                SteppedSlider(
                    value = action.durationMs.toFloat(),
                    onValueChange = { onChange(action.copy(durationMs = it.roundToInt().coerceAtLeast(0))) },
                    currentValue = { currentAction().durationMs.toFloat() },
                    valueRange = 5f..500f,
                    step = 5f,
                    defaultValue = 20f,
                    dialogTitle = "Set $label note duration (ms)",
                    valueLabel = { "${it.roundToInt()} ms" },
                )
            }
        }
    }
}

/**
 * Authors a single beat's own [MidiBeatAction] override (see [TimeSignature.midiOverrides]) at an
 * explicitly chosen phrase/bar/beat - not just whichever bar the engine happens to be playing.
 * [PhraseQueueDots]/[BarQueueDots] (the *exact* pickers the main screen's own phrase/bar queues
 * use - "the graphic to choose" reused rather than a second one invented for this section) pick
 * which phrase and bar to browse, shown only once there's more than one to choose from; a beat-
 * index stepper (unchanged from before - simple, as originally asked) then picks the beat within
 * whichever bar that resolves to. [MetronomeEngine.setMidiOverride] takes that same explicit
 * triple, so an edit always lands on precisely the beat selected here - the fix for what used to
 * be an edit that could only ever reach "whichever bar is currently active," regardless of what
 * this section displayed.
 *
 * Selection here is deliberately local UI state, not engine navigation: tapping a phrase/bar dot
 * only updates [selectedPhraseIndex]/[selectedBarIndex], never calls
 * [MetronomeEngine.goToPhrase]/[MetronomeEngine.goToQueueBar] the way the identical-looking dots on
 * the main screen do - browsing here to author an override shouldn't also yank playback to a
 * different phrase/bar out from under a live set.
 *
 * Shows the selected beat's *effective* action (its own override if set, else its resolved
 * [ClickSound] type's own configured default) rather than a blank slate, so editing always starts
 * from what would actually fire - computed via [TimeSignature.clickSoundAt] on the *selected* bar
 * specifically, not [MetronomeEngine.beatTypeFor] (which only ever reads the *active* one).
 *
 * Takes [phrases]/[midiActions] as explicit parameters (not read internally from their own
 * `StateFlow`s) so this composable's recomposition is driven by normal Compose parameter-change
 * tracking - the same pattern [MidiActionTabs] already uses - rather than depending on whichever
 * ancestor scope happens to also read those flows.
 *
 * Internal (not private): [HelpScreen] embeds this exact live control under its own MIDI category,
 * the same "one shared instance, not a disconnected demo copy" pattern [MidiActionTabs]/
 * [ClockFeelChips]/[JumpToUnitChips] already establish there.
 */
@Composable
internal fun BeatOverridesSection(phrases: List<Phrase>, midiActions: Map<ClickSound, MidiBeatAction>) {
    var selectedPhraseIndex by remember { mutableStateOf(0) }
    var selectedBarIndex by remember { mutableStateOf(0) }
    var selectedBeatIndex by remember { mutableStateOf(0) }

    val phraseIndex = selectedPhraseIndex.coerceIn(0, (phrases.size - 1).coerceAtLeast(0))
    val phrase = phrases[phraseIndex]
    val barIndex = selectedBarIndex.coerceIn(0, (phrase.bars.size - 1).coerceAtLeast(0))
    val bar = phrase.bars[barIndex]
    val lastBeatIndex = (bar.beatCount - 1).coerceAtLeast(0)
    val beatIndex = selectedBeatIndex.coerceIn(0, lastBeatIndex)

    val sound = bar.clickSoundAt(beatIndex)
    val override = bar.midiOverrideAt(beatIndex)
    val effectiveAction = override ?: midiActions[sound] ?: MidiBeatAction()

    // Reads the authoritative state fresh, the same "don't close over a recomposition-time
    // snapshot" contract MidiActionEditor's own currentAction documents - phraseIndex/barIndex/
    // beatIndex themselves are fine to close over (they're this composable's own local selection,
    // not engine state that can change out from under it mid-gesture).
    fun currentEffectiveAction(): MidiBeatAction {
        val currentBar = MetronomeEngine.phrases.value.getOrNull(phraseIndex)?.bars?.getOrNull(barIndex) ?: return MidiBeatAction()
        return currentBar.midiOverrideAt(beatIndex) ?: MidiActionSender.actions.value[currentBar.clickSoundAt(beatIndex)] ?: MidiBeatAction()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Give one specific beat its own MIDI action, overriding its type's default " +
                "above for that beat only - browse any phrase and bar below, not just the one " +
                "currently playing. The main screen's own lightning-bolt button triggers " +
                "whatever's actually configured for the engine's live beat position.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (phrases.size > 1) {
            PhraseQueueDots(
                phrases = phrases,
                activeIndex = phraseIndex,
                onDotClick = { selectedPhraseIndex = it },
                onDotRemove = {},
            )
        }
        if (phrase.bars.size > 1) {
            BarQueueDots(
                queue = phrase.bars,
                activeIndex = barIndex,
                // A neutered beat - beatIndex -1 never matches a real 0-until-beatCount index - so
                // this picker never shows a misleading live pulse for a bar that isn't actually
                // the one playing. See BarQueueDots' own kdoc for this call site's contract.
                beat = BeatPhase.IDLE.copy(beatIndex = -1),
                onDotClick = { selectedBarIndex = it },
                onDotRemove = {},
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { selectedBeatIndex = (beatIndex - 1).coerceAtLeast(0) },
                modifier = Modifier.testTag("beat_override_prev_button"),
            ) {
                Icon(ExtraIcons.Remove, contentDescription = "Previous beat")
            }
            Text(
                text = "Beat ${beatIndex + 1} of ${bar.beatCount}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("beat_override_index_label"),
            )
            IconButton(
                onClick = { selectedBeatIndex = (beatIndex + 1).coerceAtMost(lastBeatIndex) },
                modifier = Modifier.testTag("beat_override_next_button"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Next beat")
            }
        }
        Text(
            text = if (override != null) "Override set" else "Following ${sound.displayLabel()}'s own default",
            style = MaterialTheme.typography.labelSmall,
            color = if (override != null) RecordingRed else MaterialTheme.colorScheme.secondary,
        )
        MidiActionEditor(
            label = "beat ${beatIndex + 1}",
            action = effectiveAction,
            currentAction = { currentEffectiveAction() },
            onChange = { MetronomeEngine.setMidiOverride(phraseIndex, barIndex, beatIndex, it) },
        )
        if (override != null) {
            TextButton(
                onClick = { MetronomeEngine.setMidiOverride(phraseIndex, barIndex, beatIndex, null) },
                modifier = Modifier.testTag("beat_override_clear_button"),
            ) {
                Text("Clear override")
            }
        }
    }
}

/**
 * Authors a single phrase's own [Phrase.action] (see [MetronomeEngine.setPhraseAction]) - the same
 * [PhraseQueueDots] picker [BeatOverridesSection] uses (shown only once there's more than one
 * phrase to choose from) plus the same [MidiActionEditor] every action-editing surface in this app
 * shares. Selection is local UI state, not engine navigation, the same "picking here never moves
 * playback" contract [BeatOverridesSection] follows - see its own kdoc. Fires once, automatically,
 * whenever [MetronomeEngine.goToPhrase] resolves to that phrase - no separate Trigger button here,
 * since jumping to the phrase *is* the trigger (tap its dot on the main screen, or arrive there via
 * the queue advancing).
 *
 * Internal (not private): [HelpScreen] embeds this exact live control under its own MIDI category -
 * see [BeatOverridesSection]'s own kdoc for why.
 */
@Composable
internal fun PhraseActionsSection(phrases: List<Phrase>) {
    var selectedPhraseIndex by remember { mutableStateOf(0) }
    val lastIndex = (phrases.size - 1).coerceAtLeast(0)
    val phraseIndex = selectedPhraseIndex.coerceIn(0, lastIndex)
    val action = phrases[phraseIndex].action

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Give a phrase its own MIDI action, fired once whenever you jump to it - " +
                "tapping its dot on the main screen, or arriving there automatically as the " +
                "queue advances.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (phrases.size > 1) {
            PhraseQueueDots(
                phrases = phrases,
                activeIndex = phraseIndex,
                onDotClick = { selectedPhraseIndex = it },
                onDotRemove = {},
            )
        }
        Text(
            text = "Phrase ${phraseIndex + 1} of ${phrases.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("phrase_action_index_label"),
        )
        MidiActionEditor(
            label = "phrase ${phraseIndex + 1}",
            action = action,
            currentAction = { MetronomeEngine.phrases.value.getOrElse(phraseIndex) { Phrase() }.action },
            onChange = { MetronomeEngine.setPhraseAction(phraseIndex, it) },
        )
    }
}

@Composable
private fun ClickSoundControls(sound: ClickSound, spec: ClickSpec) {
    val label = sound.displayLabel()
    val default = remember(sound) { ClickSpec.defaultFor(sound) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickWaveform.entries.forEach { waveform ->
                FilterChip(
                    selected = spec.waveform == waveform,
                    onClick = { MetronomeEngine.setClickSpec(sound, spec.copy(waveform = waveform)) },
                    label = { Text(waveform.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        SteppedSlider(
            value = spec.frequencyHz,
            onValueChange = { MetronomeEngine.setClickSpec(sound, spec.copy(frequencyHz = it)) },
            currentValue = { MetronomeEngine.clickSpecs.value.getValue(sound).frequencyHz },
            valueRange = 80f..4000f,
            step = 20f,
            defaultValue = default.frequencyHz,
            dialogTitle = "Set $label frequency (Hz)",
            valueLabel = { "${it.roundToInt()} Hz" },
        )
        SteppedSlider(
            value = spec.durationMs.toFloat(),
            onValueChange = { MetronomeEngine.setClickSpec(sound, spec.copy(durationMs = it.roundToInt())) },
            currentValue = { MetronomeEngine.clickSpecs.value.getValue(sound).durationMs.toFloat() },
            valueRange = 5f..300f,
            step = 5f,
            defaultValue = default.durationMs.toFloat(),
            dialogTitle = "Set $label duration (ms)",
            valueLabel = { "${it.roundToInt()} ms" },
        )
    }
}

// ── Persistent playback ─────────────────────────────────────────────────────

/**
 * Opt-in, off by default. Neither permission nudged below is required for this to do something
 * useful: declining the notification permission just means the foreground service runs silently
 * (Android still grants foreground scheduling priority regardless); declining the battery
 * exemption just means standard foreground-service protection instead of the strongest OEM-level
 * exemption. Never gate the feature on either being granted.
 */
@Composable
private fun PersistentPlaybackSection(persistentModeEnabled: Boolean) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Declining just means the notification won't show - the service still runs either way,
        // nothing else to do here.
    }

    fun enablePersistentMode() {
        MetronomeEngine.setPersistentModeEnabled(true)
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
            requestBatteryExemption(context)
        }
    }

    CollapsibleSection(
        title = "Playback",
        summary = {
            Switch(
                checked = persistentModeEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) enablePersistentMode() else MetronomeEngine.setPersistentModeEnabled(false)
                },
            )
        },
    ) {
        Text(
            text = "If you just want playback to survive the screen turning off, raising your " +
                "phone's own screen-timeout (or disabling screen-off) while keeping qMetronome " +
                "open works today with no extra permissions. This setting is for the cases that " +
                "doesn't cover - backgrounded, screen-locked, or switched away from the Glyph Toy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "When on, a quiet background notification keeps the metronome running - only " +
                "an explicit stop (here, the widget, or double-tapping the preview) stops it, not " +
                "unlocking the phone or switching Glyph Toys. Two prompts may appear when you turn " +
                "this on (a notification permission, a battery-optimization exemption) - both are " +
                "optional extras, not requirements; declining either still leaves this working.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (persistentModeEnabled) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val exempted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            if (!exempted) {
                OutlinedButton(onClick = { requestBatteryExemption(context) }) {
                    Text("Request battery exemption")
                }
            }
        }
    }
}

private fun requestBatteryExemption(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    context.startActivity(intent)
}

// ── Visual timing offset ───────────────────────────────────────────────────

private enum class OffsetUnit(val label: String) {
    Ms("ms"),
    Frames("frames @40fps"),
    BeatPct("beat %"),
}

/** The unit-switcher and explanation - the offset slider itself is the section's collapsed
 * [CollapsibleSection] summary, always visible, so it isn't repeated here. */
@Composable
private fun VisualOffsetDetails(bpm: Float) {
    var unit by remember { mutableStateOf(OffsetUnit.Ms) }
    val offsetMs by MetronomeEngine.visualOffsetMs.collectAsState()

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
        Text(
            text = when (unit) {
                OffsetUnit.Ms -> "${offsetMs.roundToInt()} ms"
                OffsetUnit.Frames -> "${"%.1f".format(offsetMs / FRAME_MS)} frames"
                OffsetUnit.BeatPct -> "${"%.1f".format(offsetMs / (60_000f / bpm) * 100)} % of beat"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Shifts visuals earlier (negative) or later (positive) relative to the beat timestamp. " +
                "Defaults to ${DEFAULT_VISUAL_OFFSET_MS.roundToInt()} ms - true zero, not a guessed " +
                "compensation, since no single hardcoded number matches every device's actual display lag. " +
                "If the flash feels early or late, drag to taste; double-tap the value above to reset to " +
                "that default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ── Audio timing offset ────────────────────────────────────────────────────

private enum class AudioOffsetUnit(val label: String) {
    Ms("ms"),
    BeatPct("beat %"),
}

/** Same idea as [VisualOffsetDetails], for the audible click. No "frames" unit here - that's tied
 * to the Glyph Matrix's own render rate, which has no equivalent for a one-shot sound. */
@Composable
private fun AudioOffsetDetails(bpm: Float) {
    var unit by remember { mutableStateOf(AudioOffsetUnit.Ms) }
    val offsetMs by MetronomeEngine.audioOffsetMs.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioOffsetUnit.entries.forEach { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = { unit = u },
                    label = { Text(u.label) },
                )
            }
        }
        Text(
            text = when (unit) {
                AudioOffsetUnit.Ms -> "${offsetMs.roundToInt()} ms"
                AudioOffsetUnit.BeatPct -> "${"%.1f".format(offsetMs / (60_000f / bpm) * 100)} % of beat"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Shifts the audible click earlier (negative) or later (positive) relative to the beat " +
                "timestamp. Zero or negative values use genuine lookahead scheduling to place the click " +
                "precisely - unlike the visual offset's phase-shifted decay curve, there's no equivalent " +
                "trick for a one-shot sound, so even zero benefits from a bit of a head start. Defaults to " +
                "${DEFAULT_AUDIO_OFFSET_MS.roundToInt()} ms - true zero, not a guessed compensation, since " +
                "no single hardcoded number matches every device's actual audio latency. Double-tap the " +
                "value above to reset to that default. Only ever delays (never leads) while following an " +
                "external MIDI clock, since there's nothing of your own to predict there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ── Jump to unit / clock feel chip rows ────────────────────────────────────

/** The "Jump to unit" chip row - internal (not private) so [HelpScreen] can embed this exact
 * live control rather than a static screenshot, the same "one shared live instance" pattern
 * [TempoTransportCluster] already establishes. Self-contained (reads its own engine state)
 * rather than taking parameters, so either call site can drop it in with no wiring. */
@Composable
internal fun JumpToUnitChips() {
    val beat by MetronomeEngine.state.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val currentBpmUnit = bpmUnitFor(stagedBpm ?: beat.bpm)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BpmUnit.entries.forEach { unit ->
            FilterChip(
                selected = currentBpmUnit == unit,
                onClick = {
                    if (unit != currentBpmUnit) {
                        if (unit != BpmUnit.BPM) MetronomeEngine.setExtendedBpmRangeEnabled(true)
                        MetronomeEngine.setBpm(bpmFromUnitValue(bpmDefaultUnitValue(unit), unit))
                    }
                },
                label = { Text(unit.label) },
                modifier = Modifier.testTag("jump_to_unit_${unit.name}"),
            )
        }
    }
}

/** The "Outgoing clock feel" chip row - internal (not private) for the same reason as
 * [JumpToUnitChips]: [HelpScreen] embeds this exact live control directly. */
@Composable
internal fun ClockFeelChips() {
    val clockOutTimingMode by MidiClockSender.timingMode.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockTimingMode.entries.forEach { mode ->
            FilterChip(
                selected = clockOutTimingMode == mode,
                onClick = { MidiClockSender.setTimingMode(mode) },
                label = { Text(if (mode == ClockTimingMode.MECHANICAL) "Mechanical" else "Organic") },
                modifier = Modifier.testTag("clock_feel_${mode.name}"),
            )
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

/**
 * The app's standard settings section: a title on one line with a single always-visible
 * [summary] control (a switch, a slider - whatever that section's single most relevant setting
 * is), expanding on tap to reveal [content], the rest of that section's controls. Keeps the whole
 * screen scannable at a glance on a phone instead of a long stack of always-expanded sliders and
 * explanations to scroll past.
 *
 * [summary] sits in a `weight(1f)` slot so a wide control (e.g. a [SteppedSlider], which fills
 * its own row) gets the actual remaining width rather than fighting the title for space - the
 * title/chevron side of the row is fixed-width instead. Tapping [summary]'s own control (a
 * `Switch`, a slider drag) is handled by that control itself and doesn't also toggle expansion -
 * Compose resolves nested pointer input by letting the innermost handler consume the gesture
 * first, the same reason a trailing switch works inside a clickable list row anywhere else.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    summary: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // A dedicated tag rather than relying on finding this row by its title text: that title
    // shares a merged semantics node with `summary`'s own control (e.g. a Switch), and a test
    // clicking by text risks resolving to the wrong one of the two actions living on that node.
    val headerTestTag = remember(title) { "section_header_${title.replace(" ", "_").replace("&", "and")}" }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(headerTestTag)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { summary() }
            Icon(
                modifier = Modifier.testTag("${headerTestTag}_chevron"),
                imageVector = if (expanded) ExtraIcons.ExpandLess else ExtraIcons.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

private const val FRAME_MS = 25f
