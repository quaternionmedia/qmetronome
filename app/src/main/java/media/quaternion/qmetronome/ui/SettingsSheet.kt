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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.ClickSound
import media.quaternion.qmetronome.engine.ClickSpec
import media.quaternion.qmetronome.engine.ClickWaveform
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.DEFAULT_AUDIO_OFFSET_MS
import media.quaternion.qmetronome.engine.DEFAULT_VISUAL_OFFSET_MS
import media.quaternion.qmetronome.engine.MetronomeEngine
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
    val clockOutTimingMode by MidiClockSender.timingMode.collectAsState()
    val visualOffsetMs by MetronomeEngine.visualOffsetMs.collectAsState()
    val audioOffsetMs by MetronomeEngine.audioOffsetMs.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val timeSignature by MetronomeEngine.timeSignature.collectAsState()
    val extendedBpmRangeEnabled by MetronomeEngine.extendedBpmRangeEnabled.collectAsState()
    var showBpmDialog by remember { mutableStateOf(false) }
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val muteProbability by MetronomeEngine.muteProbability.collectAsState()
    val progressiveMuteEnabled by MetronomeEngine.progressiveMuteEnabled.collectAsState()
    val progressiveMuteRampBars by MetronomeEngine.progressiveMuteRampBars.collectAsState()
    val queueOverlayEnabled by MetronomeEngine.queueOverlayEnabled.collectAsState()
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
                    Switch(checked = queueOverlayEnabled, onCheckedChange = MetronomeEngine::setQueueOverlayEnabled)
                }
                Text(
                    text = "Independent toggles - run either, both, or neither. The beat visualizer " +
                        "is the animated pattern above (pendulum, sweep, etc.); the bar queue " +
                        "background is the ambient per-bar/per-beat pattern baked into the Glyph " +
                        "Matrix (and its on-screen preview) when more than one bar is queued.",
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
                title = "Layout",
                summary = {
                    Switch(checked = compactLandscape, onCheckedChange = MetronomeEngine::setCompactLandscape)
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
                    Switch(checked = symbolicControlsEnabled, onCheckedChange = MetronomeEngine::setSymbolicControlsEnabled)
                }
                Text(
                    text = "Drops words from the main screen's tempo/transport controls in favor of " +
                        "icons and dots only (TAP, HOLD, the BPM unit label, \"staged\" indicators) - " +
                        "a more unified, purely iconographic look that also needs no translation.",
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockTimingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = clockOutTimingMode == mode,
                            onClick = { MidiClockSender.setTimingMode(mode) },
                            label = { Text(if (mode == ClockTimingMode.MECHANICAL) "Mechanical" else "Organic") },
                        )
                    }
                }
                Text(
                    text = "Mechanical actively corrects the outgoing clock for the truest, most locked-in " +
                        "beat. Organic skips that correction while repeating a followed clock, letting " +
                        "whatever natural timing variance really occurs come through instead - no fake " +
                        "randomness, just the honest imprecision of real hardware/scheduling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
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

private fun ClickSound.displayLabel(): String = when (this) {
    ClickSound.BAR -> "Bar"
    ClickSound.REGULAR -> "Beat"
    ClickSound.ACCENT -> "Accent"
}

/**
 * Bar/Beat/Accent share the exact same control schema ([ClickSpec]: waveform, frequency,
 * duration) - tabbed rather than stacked three times over, since that's exactly the case where
 * tabs pay for themselves instead of just adding another layer of navigation.
 */
@Composable
private fun ClickSoundTabs(clickSpecs: Map<ClickSound, ClickSpec>) {
    var selected by remember { mutableStateOf(ClickSound.BAR) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ClickSound.BAR, ClickSound.REGULAR, ClickSound.ACCENT).forEach { sound ->
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
                "Bar plays on beat 1 of every bar, Beat on every other beat. Accent is wired in " +
                "but not reachable yet - nothing marks extra beats within a bar accented today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
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
                "Defaults to ${DEFAULT_VISUAL_OFFSET_MS.roundToInt()} ms to compensate for typical human " +
                "reaction/perception plus system display lag - if the flash still feels late, drag further " +
                "left; double-tap the value above to reset to that default.",
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
                "timestamp. A negative value uses genuine lookahead scheduling to actually pre-trigger the " +
                "click - unlike the visual offset's phase-shifted decay curve, there's no equivalent trick " +
                "for a one-shot sound. Defaults to ${DEFAULT_AUDIO_OFFSET_MS.roundToInt()} ms; double-tap " +
                "the value above to reset to that default. Only ever delays (never leads) while following " +
                "an external MIDI clock, since there's nothing of your own to predict there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
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

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
