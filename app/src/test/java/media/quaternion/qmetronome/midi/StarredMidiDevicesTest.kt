package media.quaternion.qmetronome.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StarredMidiDevicesTest {

    private lateinit var store: StarredMidiDevices

    @Before
    fun setUp() {
        store = StarredMidiDevices(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a device is not starred by default`() {
        assertFalse(store.isStarred("1:2:test"))
    }

    @Test
    fun `starring adds the key, unstarring removes it`() {
        store.setStarred("1:2:test", true)
        assertTrue(store.isStarred("1:2:test"))

        store.setStarred("1:2:test", false)
        assertFalse(store.isStarred("1:2:test"))
    }

    @Test
    fun `desired follow and send default to false`() {
        assertFalse(store.desiredFollow("1:2:test"))
        assertFalse(store.desiredSend("1:2:test"))
    }

    @Test
    fun `desired follow and send persist independently`() {
        store.setDesiredFollow("1:2:test", true)
        assertTrue(store.desiredFollow("1:2:test"))
        assertFalse(store.desiredSend("1:2:test"))

        store.setDesiredSend("1:2:test", true)
        assertTrue(store.desiredSend("1:2:test"))
    }

    @Test
    fun `unstarring forgets the desired follow and send state`() {
        store.setStarred("1:2:test", true)
        store.setDesiredFollow("1:2:test", true)
        store.setDesiredSend("1:2:test", true)

        store.setStarred("1:2:test", false)

        assertFalse(store.desiredFollow("1:2:test"))
        assertFalse(store.desiredSend("1:2:test"))
    }

    @Test
    fun `multiple starred devices are tracked independently`() {
        store.setStarred("a", true)
        store.setStarred("b", true)
        assertEquals(setOf("a", "b"), store.starredKeys())

        store.setStarred("a", false)
        assertEquals(setOf("b"), store.starredKeys())
    }
}
