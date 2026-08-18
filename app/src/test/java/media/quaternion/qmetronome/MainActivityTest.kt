package media.quaternion.qmetronome

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @Test
    fun `hardware volume keys target media stream`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(AudioManager.STREAM_MUSIC, activity.volumeControlStream)
    }
}
