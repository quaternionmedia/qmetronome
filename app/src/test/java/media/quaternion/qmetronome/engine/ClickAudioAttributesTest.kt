package media.quaternion.qmetronome.engine

import android.media.AudioAttributes
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClickAudioAttributesTest {

    @Test
    fun `the click is played as media`() {
        assertEquals(AudioAttributes.USAGE_MEDIA, clickAudioAttributes().usage)
    }

    @Test
    fun `the click's volume is the media stream's`() {
        assertEquals(AudioManager.STREAM_MUSIC, clickAudioAttributes().volumeControlStream)
    }
}
