package media.quaternion.qmetronome.midi

import android.media.midi.MidiReceiver
import media.quaternion.qmetronome.engine.BeatAccent
import media.quaternion.qmetronome.engine.ClickSound
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.engine.MidiBeatAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MidiActionSenderTest {

    private val receivedMessages = mutableListOf<List<Int>>()
    private val fakeDestination = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            receivedMessages.add((offset until offset + count).map { msg[it].toInt() and 0xFF })
        }
    }

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        receivedMessages.clear()
        MidiActionSender.addDestination(fakeDestination)
    }

    @After
    fun tearDown() {
        MidiActionSender.removeDestination(fakeDestination)
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `fireForBeat sends nothing while disabled`() {
        MidiActionSender.setAction(ClickSound.BAR, MidiBeatAction(type = MidiActionType.NOTE))

        MidiActionSender.fireForBeat(ClickSound.BAR, System.nanoTime())
        Thread.sleep(50)

        assertTrue(receivedMessages.isEmpty())
    }

    @Test
    fun `fireForBeat sends nothing for a beat type configured as NONE`() {
        MidiActionSender.setEnabled(true)
        // ClickSound.ACCENT defaults to MidiBeatAction() - type NONE - left unset.

        MidiActionSender.fireForBeat(ClickSound.ACCENT, System.nanoTime())
        Thread.sleep(50)

        assertTrue(receivedMessages.isEmpty())
    }

    @Test
    fun `a NOTE action sends Note On promptly and Note Off after durationMs`() {
        MidiActionSender.setEnabled(true)
        // A duration comfortably longer than dispatch latency, so there's a real window where
        // Note On has arrived but Note Off - deliberately delayed - hasn't yet.
        MidiActionSender.setAction(
            ClickSound.BAR,
            MidiBeatAction(type = MidiActionType.NOTE, channel = 0, number = 60, value = 100, durationMs = 200),
        )

        MidiActionSender.fireForBeat(ClickSound.BAR, System.nanoTime())
        // The actual send happens off the caller's thread (see fireForBeat's own kdoc - this
        // must never block MetronomeEngine's calling thread), so this call returns before the
        // Note On necessarily has been sent yet - a short wait is required, unlike a same-thread
        // synchronous call.
        Thread.sleep(50)

        assertEquals(1, receivedMessages.size)
        assertEquals(listOf(0x90, 60, 100), receivedMessages[0])

        Thread.sleep(300)

        assertEquals(2, receivedMessages.size)
        assertEquals(listOf(0x80, 60, 0), receivedMessages[1])
    }

    @Test
    fun `a CC action sends a single 3-byte message with no follow-up`() {
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(
            ClickSound.ACCENT,
            MidiBeatAction(type = MidiActionType.CC, channel = 1, number = 7, value = 64),
        )

        MidiActionSender.fireForBeat(ClickSound.ACCENT, System.nanoTime())
        Thread.sleep(100)

        assertEquals(1, receivedMessages.size)
        assertEquals(listOf(0xB1, 7, 64), receivedMessages[0])
    }

    @Test
    fun `channel is encoded in the low nibble of the status byte`() {
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(
            ClickSound.BAR,
            MidiBeatAction(type = MidiActionType.NOTE, channel = 15, number = 1, value = 1, durationMs = 0),
        )

        MidiActionSender.fireForBeat(ClickSound.BAR, System.nanoTime())
        Thread.sleep(50) // send happens off the caller's thread - see fireForBeat's own kdoc

        assertEquals(0x9F, receivedMessages[0][0])
    }

    @Test
    fun `a NOTE velocity of 0 is floored to 1, never sent as a de-facto note-off`() {
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(
            ClickSound.BAR,
            MidiBeatAction(type = MidiActionType.NOTE, value = 0, durationMs = 0),
        )

        MidiActionSender.fireForBeat(ClickSound.BAR, System.nanoTime())
        Thread.sleep(50) // send happens off the caller's thread - see fireForBeat's own kdoc

        assertEquals(1, receivedMessages[0][2])
    }

    @Test
    fun `a removed destination receives nothing further`() {
        MidiActionSender.removeDestination(fakeDestination)
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(ClickSound.BAR, MidiBeatAction(type = MidiActionType.CC))

        MidiActionSender.fireForBeat(ClickSound.BAR, System.nanoTime())
        Thread.sleep(50)

        assertTrue(receivedMessages.isEmpty())
    }

    @Test
    fun `real playback fires the configured action for each beat's resolved beat type`() {
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(ClickSound.BAR, MidiBeatAction(type = MidiActionType.CC, number = 1, value = 1))
        MidiActionSender.setAction(ClickSound.ACCENT, MidiBeatAction(type = MidiActionType.CC, number = 2, value = 2))
        MetronomeEngine.setBeatsPerBar(2)
        MetronomeEngine.setAccentPattern(listOf(BeatAccent.NONE, BeatAccent.ACCENT))
        MetronomeEngine.setBpm(MetronomeEngine.MAX_BPM) // fast - several beats in a short sleep

        MetronomeEngine.start()
        Thread.sleep(500)

        assertTrue("expected BAR's CC (number 1)", receivedMessages.any { it == listOf(0xB0, 1, 1) })
        assertTrue("expected ACCENT's CC (number 2)", receivedMessages.any { it == listOf(0xB0, 2, 2) })
    }

    @Test
    fun `real playback still fires MIDI actions when the audible click is disabled`() {
        // See MetronomeEngine.beatTypeFor's own kdoc - MIDI actions are deliberately independent
        // of clickEnabled, unlike the audible click itself.
        MidiActionSender.setEnabled(true)
        MidiActionSender.setAction(ClickSound.BAR, MidiBeatAction(type = MidiActionType.CC, number = 5, value = 5))
        MetronomeEngine.setClickEnabled(false)
        MetronomeEngine.setBpm(MetronomeEngine.MAX_BPM)

        MetronomeEngine.start()
        Thread.sleep(300)

        assertTrue(receivedMessages.any { it == listOf(0xB0, 5, 5) })
    }

    @Test
    fun `goToPhrase fires the target phrase's own action`() {
        MidiActionSender.setEnabled(true)
        MetronomeEngine.addPhrase() // phrase 1, active
        MetronomeEngine.setPhraseAction(1, MidiBeatAction(type = MidiActionType.CC, number = 9, value = 42))

        MetronomeEngine.goToPhrase(0) // phrase 0 has no action configured (NONE) - nothing yet
        Thread.sleep(50)
        assertTrue(receivedMessages.isEmpty())

        MetronomeEngine.goToPhrase(1)
        Thread.sleep(50)

        assertTrue(receivedMessages.any { it == listOf(0xB0, 9, 42) })
    }

    @Test
    fun `re-entering the already-active phrase fires its action again`() {
        MidiActionSender.setEnabled(true)
        MetronomeEngine.setPhraseAction(0, MidiBeatAction(type = MidiActionType.CC, number = 3, value = 7))
        MetronomeEngine.goToPhrase(0)
        Thread.sleep(50)
        receivedMessages.clear()

        MetronomeEngine.goToPhrase(0) // same index, still active - fires again, not just on transitions
        Thread.sleep(50)

        assertTrue(receivedMessages.any { it == listOf(0xB0, 3, 7) })
    }
}
