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
}
