package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MetronomeSettingsTest {

    private val settings = MetronomeSettings(RuntimeEnvironment.getApplication())

    @Test
    fun `click is off by default - a silent visualizer is the safer first impression`() {
        assertFalse(settings.clickEnabled)
    }

    @Test
    fun `bpm, beats per bar, visualizer id and click enabled all round-trip`() {
        settings.bpm = 96f
        settings.beatsPerBar = 7
        settings.visualizerId = "ring_expand"
        settings.clickEnabled = true

        assertEquals(96f, settings.bpm)
        assertEquals(7, settings.beatsPerBar)
        assertEquals("ring_expand", settings.visualizerId)
        assertEquals(true, settings.clickEnabled)
    }

    @Test
    fun `bpm hint has not been shown by default, and round-trips once set`() {
        assertFalse(settings.hasShownBpmHint)

        settings.hasShownBpmHint = true

        assertEquals(true, settings.hasShownBpmHint)
    }

    @Test
    fun `persistent mode defaults off, and round-trips once set`() {
        assertFalse(settings.persistentModeEnabled)

        settings.persistentModeEnabled = true

        assertEquals(true, settings.persistentModeEnabled)
    }

    @Test
    fun `mute probability and progressive mute default off, and round-trip once set`() {
        assertEquals(0f, settings.muteProbability)
        assertFalse(settings.progressiveMuteEnabled)

        settings.muteProbability = 0.35f
        settings.progressiveMuteEnabled = true

        assertEquals(0.35f, settings.muteProbability)
        assertEquals(true, settings.progressiveMuteEnabled)
    }

    @Test
    fun `progressive mute ramp bars defaults and round-trips once set`() {
        assertEquals(MetronomeEngine.DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS, settings.progressiveMuteRampBars)

        settings.progressiveMuteRampBars = 16

        assertEquals(16, settings.progressiveMuteRampBars)
    }

    @Test
    fun `audio offset defaults to DEFAULT_AUDIO_OFFSET_MS, and round-trips once set`() {
        assertEquals(DEFAULT_AUDIO_OFFSET_MS, settings.audioOffsetMs)

        settings.audioOffsetMs = 15f

        assertEquals(15f, settings.audioOffsetMs)
    }

    @Test
    fun `first-beat count-in cap defaults to DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS, and round-trips once set`() {
        assertEquals(DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS, settings.firstBeatCountInCapMs)

        settings.firstBeatCountInCapMs = 0f

        assertEquals(0f, settings.firstBeatCountInCapMs)
    }

    @Test
    fun `extended bpm range defaults off, and round-trips once set`() {
        assertFalse(settings.extendedBpmRangeEnabled)

        settings.extendedBpmRangeEnabled = true

        assertEquals(true, settings.extendedBpmRangeEnabled)
    }

    @Test
    fun `symbolic controls default off, and round-trip once set`() {
        assertFalse(settings.symbolicControlsEnabled)

        settings.symbolicControlsEnabled = true

        assertEquals(true, settings.symbolicControlsEnabled)
    }

    @Test
    fun `unit symbols default on, and round-trip once set`() {
        assertEquals(true, settings.unitSymbolsEnabled)

        settings.unitSymbolsEnabled = false

        assertFalse(settings.unitSymbolsEnabled)
    }

    @Test
    fun `bar queue defaults to a single default bar, and round-trips once set`() {
        assertEquals(listOf(TimeSignature.DEFAULT), settings.queue)
        assertEquals(0, settings.queueIndex)
        assertEquals(MetronomeEngine.QueueMode.LOOP, settings.queueMode)

        val queue = listOf(
            TimeSignature(beatCount = 3, unitNoteValue = 4, bpm = 90f),
            TimeSignature(beatCount = 7, unitNoteValue = 8, bpm = 200f),
        )
        settings.queue = queue
        settings.queueIndex = 1
        settings.queueMode = MetronomeEngine.QueueMode.MANUAL

        assertEquals(queue, settings.queue)
        assertEquals(1, settings.queueIndex)
        assertEquals(MetronomeEngine.QueueMode.MANUAL, settings.queueMode)
    }

    @Test
    fun `a corrupt or empty persisted queue falls back to a single default bar`() {
        settings.queue = emptyList()
        assertEquals(listOf(TimeSignature.DEFAULT), settings.queue)
    }

    @Test
    fun `a bar's pinned visualizer round-trips through the queue encoding`() {
        val queue = listOf(
            TimeSignature(beatCount = 4, bpm = 90f, visualizerId = "pulse"),
            TimeSignature(beatCount = 6, bpm = 140f, visualizerId = null),
        )
        settings.queue = queue
        assertEquals(queue, settings.queue)
    }

    @Test
    fun `clock-out timing mode defaults to Mechanical, and round-trips once set`() {
        assertEquals(ClockTimingMode.MECHANICAL, settings.clockOutTimingMode)

        settings.clockOutTimingMode = ClockTimingMode.ORGANIC

        assertEquals(ClockTimingMode.ORGANIC, settings.clockOutTimingMode)
    }

    @Test
    fun `a queue encoded before visualizerId existed (3 fields, no trailing colon) decodes with a null visualizerId`() {
        settings.queue = listOf(TimeSignature(beatCount = 4, unitNoteValue = 4, bpm = 96f))
        // Simulate pre-this-feature data by writing the old 3-field row shape directly.
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queue", "4:4:96.0").apply()

        val decoded = settings.queue
        assertEquals(1, decoded.size)
        assertEquals(4, decoded[0].beatCount)
        assertEquals(96f, decoded[0].bpm)
        assertEquals(null, decoded[0].visualizerId)
    }

    @Test
    fun `a bar's accent pattern round-trips through the queue encoding`() {
        val queue = listOf(
            TimeSignature(
                beatCount = 4,
                bpm = 90f,
                accentPattern = listOf(BeatAccent.NONE, BeatAccent.ACCENT, BeatAccent.STRONG_ACCENT, BeatAccent.CUSTOM),
            ),
            TimeSignature(beatCount = 3, bpm = 120f, accentPattern = null),
        )
        settings.queue = queue
        assertEquals(queue, settings.queue)
    }

    @Test
    fun `a queue encoded before accentPattern existed (4 fields, no trailing colon) decodes with a null accentPattern`() {
        settings.queue = listOf(TimeSignature(beatCount = 4, unitNoteValue = 4, bpm = 96f, visualizerId = "pulse"))
        // Simulate pre-this-feature data by writing the old 4-field row shape directly.
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queue", "4:4:96.0:pulse").apply()

        val decoded = settings.queue
        assertEquals(1, decoded.size)
        assertEquals(4, decoded[0].beatCount)
        assertEquals("pulse", decoded[0].visualizerId)
        assertEquals(null, decoded[0].accentPattern)
    }

    @Test
    fun `an all-NONE accent pattern encodes the same as null and round-trips as null`() {
        settings.queue = listOf(TimeSignature(beatCount = 4, bpm = 90f, accentPattern = List(4) { BeatAccent.NONE }))
        assertEquals(null, settings.queue[0].accentPattern)
    }

    @Test
    fun `a bar's midi overrides round-trip through the queue encoding`() {
        val queue = listOf(
            TimeSignature(
                beatCount = 4,
                bpm = 90f,
                midiOverrides = mapOf(
                    2 to MidiBeatAction(type = MidiActionType.NOTE, channel = 0, number = 72, value = 110, durationMs = 15),
                    5 to MidiBeatAction(type = MidiActionType.CC, channel = 1, number = 20, value = 64, durationMs = 20),
                ),
            ),
            TimeSignature(beatCount = 3, bpm = 120f, midiOverrides = null),
        )
        settings.queue = queue
        assertEquals(queue, settings.queue)
    }

    @Test
    fun `a queue encoded before midiOverrides existed (5 fields, no trailing colon) decodes with null midiOverrides`() {
        settings.queue = listOf(TimeSignature(beatCount = 4, unitNoteValue = 4, bpm = 96f))
        // Simulate pre-this-feature data by writing the old 5-field row shape directly.
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queue", "4:4:96.0::1,2").apply()

        val decoded = settings.queue
        assertEquals(1, decoded.size)
        assertEquals(4, decoded[0].beatCount)
        assertEquals(listOf(BeatAccent.ACCENT, BeatAccent.STRONG_ACCENT), decoded[0].accentPattern)
        assertEquals(null, decoded[0].midiOverrides)
    }

    @Test
    fun `phrases default to a single default phrase, and round-trip once set`() {
        assertEquals(listOf(Phrase()), settings.phrases)
        assertEquals(0, settings.activePhraseIndex)
        assertEquals(MetronomeEngine.QueueMode.LOOP, settings.phraseQueueMode)

        val phrases = listOf(
            Phrase(bars = listOf(TimeSignature(beatCount = 3, bpm = 90f)), barQueueMode = MetronomeEngine.QueueMode.ONCE),
            Phrase(
                bars = listOf(TimeSignature(beatCount = 7, bpm = 140f), TimeSignature(beatCount = 5, bpm = 100f)),
                barQueueMode = MetronomeEngine.QueueMode.LOOP,
            ),
        )
        settings.phrases = phrases
        settings.activePhraseIndex = 1
        settings.phraseQueueMode = MetronomeEngine.QueueMode.MANUAL

        assertEquals(phrases, settings.phrases)
        assertEquals(1, settings.activePhraseIndex)
        assertEquals(MetronomeEngine.QueueMode.MANUAL, settings.phraseQueueMode)
    }

    @Test
    fun `phrases falls back to migrating the legacy single queue when no phrases have ever been persisted`() {
        settings.queue = listOf(TimeSignature(beatCount = 5, bpm = 110f), TimeSignature(beatCount = 3, bpm = 80f))
        settings.queueMode = MetronomeEngine.QueueMode.ONCE

        val migrated = settings.phrases

        assertEquals(1, migrated.size)
        assertEquals(settings.queue, migrated[0].bars)
        assertEquals(MetronomeEngine.QueueMode.ONCE, migrated[0].barQueueMode)
    }

    @Test
    fun `a corrupt persisted phrases value falls back to a single default phrase`() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("phrases", "not a valid encoding at all").apply()

        assertEquals(listOf(Phrase()), settings.phrases)
    }

    @Test
    fun `each phrase's own midi action round-trips through the phrase encoding`() {
        val phrases = listOf(
            Phrase(
                bars = listOf(TimeSignature(beatCount = 4, bpm = 90f)),
                action = MidiBeatAction(type = MidiActionType.NOTE, channel = 9, number = 72, value = 110, durationMs = 15),
            ),
            Phrase(bars = listOf(TimeSignature(beatCount = 3, bpm = 120f)), action = MidiBeatAction()),
        )
        settings.phrases = phrases
        assertEquals(phrases, settings.phrases)
    }

    @Test
    fun `phrases encoded before Phrase-action existed (2-part, no action segment) decode with a NONE action`() {
        // Simulate pre-this-feature data by writing the old "barQueueMode~bar1|bar2|..." shape
        // directly (no action segment - see MetronomeSettings.phrases' own kdoc for the
        // split-size-based discriminator this relies on).
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("phrases", "LOOP~4:4:96.0::").apply()

        val decoded = settings.phrases
        assertEquals(1, decoded.size)
        assertEquals(4, decoded[0].bars[0].beatCount)
        assertEquals(MidiBeatAction(), decoded[0].action)
    }

    @Test
    fun `midi actions are disabled by default, and round-trip once set`() {
        assertFalse(settings.midiActionsEnabled)

        settings.midiActionsEnabled = true

        assertEquals(true, settings.midiActionsEnabled)
    }

    @Test
    fun `a click sound's midi beat action defaults to NONE, and round-trips once set`() {
        assertEquals(MidiBeatAction(), settings.midiBeatAction(ClickSound.ACCENT))

        val action = MidiBeatAction(type = MidiActionType.NOTE, channel = 9, number = 42, value = 80, durationMs = 15)
        settings.setMidiBeatAction(ClickSound.ACCENT, action)

        assertEquals(action, settings.midiBeatAction(ClickSound.ACCENT))
        // A different sound's action is unaffected - each ClickSound gets its own pref key.
        assertEquals(MidiBeatAction(), settings.midiBeatAction(ClickSound.BAR))
    }

    @Test
    fun `a corrupt midi beat action falls back to the NONE default`() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("midi_action_bar", "not:a:valid:encoding").apply()

        assertEquals(MidiBeatAction(), settings.midiBeatAction(ClickSound.BAR))
    }
}
