package com.example.qmetronome.engine

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
}
