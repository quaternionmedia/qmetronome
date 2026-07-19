package media.quaternion.qmetronome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.tutorial.TutorialCategory
import media.quaternion.qmetronome.tutorial.TutorialTopic
import media.quaternion.qmetronome.tutorial.TutorialTopics

/**
 * An in-app counterpart to `docs/user-guide/`, reading the same [TutorialTopics] content - but
 * rather than a static screenshot per topic, every category embeds the *real, live* production
 * composable(s) it's about (the same shared-instance pattern [SettingsSheet] already established
 * for [TempoTransportCluster]: one composed instance, wired to the actual [MetronomeEngine], not a
 * disconnected demo copy) - richer than an image, automatically theme-correct, and never able to
 * drift from what the main screen actually looks like. Trying a control here really does change
 * your tempo/settings, exactly like opening Settings and touching one there would.
 *
 * [TutorialCategory.MIDI] is the one category whose five topics don't all live on a single shared
 * control (unlike, say, every [TutorialCategory.GLYPH_TOY] gesture living on the one preview), so
 * its `when` branch below stacks all five: [ClockFeelChips] (clock feel), an inline MIDI Actions
 * switch plus [MidiActionTabs] (per-type defaults), [BeatOverridesSection] and
 * [PhraseActionsSection] (per-beat/per-phrase overrides, each reusing the exact same
 * [PhraseQueueDots]/[BarQueueDots] pickers the main screen's own queues use), and
 * [TapTriggerButton] paired with a [HoldButton] so latching HOLD to try the Trigger swap doesn't
 * require leaving this screen. [TapTriggerButton] specifically (not the fuller
 * [TempoTransportCluster] the other three MIDI-adjacent categories share) - see its own kdoc for
 * why pulling in the whole cluster here would duplicate `testTag`s already on screen from the
 * TEMPO/TIME_SIGNATURE/BAR_QUEUE categories.
 *
 * A full-screen overlay reached the same way as [SettingsSheet] (see [MainScreen]'s help icon
 * next to the settings gear) - same translucent-backdrop, explicit-close treatment, for the same
 * reason: nothing to tap-to-dismiss behind it.
 */
@Composable
fun HelpScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val beat by MetronomeEngine.state.collectAsState()
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val midiActionsEnabled by MidiActionSender.enabled.collectAsState()
    val midiActions by MidiActionSender.actions.collectAsState()
    val phrases by MetronomeEngine.phrases.collectAsState()
    val topicsByCategory = TutorialTopics.all.groupBy { it.category }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
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
                Text(text = "Help", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close help")
                }
            }

            HorizontalDivider()

            topicsByCategory.forEach { (category, topics) ->
                CategorySection(category = category, topics = topics) {
                    when (category) {
                        // Shown once, under TEMPO only - TIME_SIGNATURE/BAR_QUEUE share the exact
                        // same cluster (it already covers both), so giving each its own copy would
                        // mount three simultaneously-live instances of the same testTag-bearing
                        // controls on one screen, not just visual repetition. See CategorySection's
                        // own kdoc: "rendered once via demo" was always the intent here.
                        TutorialCategory.TEMPO -> TempoTransportCluster(beat = beat, onShowBpmDialog = {})
                        TutorialCategory.TIME_SIGNATURE, TutorialCategory.BAR_QUEUE -> {}
                        TutorialCategory.GLYPH_TOY -> {
                            val frame by MetronomeEngine.frame.collectAsState()
                            PreviewBox(previewSize = previewSizeFor(frame), frame = frame, onShowSettings = {}, modifier = Modifier.fillMaxWidth())
                        }
                        TutorialCategory.MIDI -> {
                            // Five distinct sub-features share this one category (unlike, say,
                            // GLYPH_TOY's gestures, which really do all live on one control) - a
                            // light divider between each keeps the stack legible as one scrolls,
                            // lining up with each one's own title/description immediately below
                            // in CategorySection's own topic list, rather than reading as one
                            // undifferentiated wall of controls.
                            val midiDividerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            ClockFeelChips()
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = midiDividerColor)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("MIDI Actions", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = midiActionsEnabled, onCheckedChange = MidiActionSender::setEnabled)
                            }
                            MidiActionTabs(midiActions)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = midiDividerColor)
                            BeatOverridesSection(phrases = phrases, midiActions = midiActions)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = midiDividerColor)
                            PhraseActionsSection(phrases = phrases)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = midiDividerColor)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // A distinct testTag from TapTriggerButton's own default - TEMPO's
                                // cluster above already puts one "tap_trigger_button" node on this
                                // same screen (demoing tap-tempo itself), so this second, standalone
                                // instance (demoing the Trigger swap specifically) needs its own to
                                // stay uniquely queryable.
                                TapTriggerButton(testTag = "help_midi_tap_trigger_button")
                                HoldButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                            }
                        }
                        TutorialCategory.SETTINGS -> {
                            JumpToUnitChips()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Compact landscape layout", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = compactLandscape, onCheckedChange = MetronomeEngine::setCompactLandscape)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Symbol-only controls", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = symbolicControlsEnabled, onCheckedChange = MetronomeEngine::setSymbolicControlsEnabled)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** One category's block: its display name, its live demo control (rendered once via [demo], not
 * once per topic - several topics in a category often share the same underlying control, e.g.
 * every [TutorialCategory.GLYPH_TOY] gesture lives on the one preview), then each of its topics'
 * title + description as plain text underneath. */
@Composable
private fun CategorySection(
    category: TutorialCategory,
    topics: List<TutorialTopic>,
    demo: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            demo()
        }
        topics.forEach { topic ->
            Text(
                text = topic.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = topic.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
