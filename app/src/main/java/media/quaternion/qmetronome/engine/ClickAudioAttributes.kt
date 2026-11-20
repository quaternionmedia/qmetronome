package media.quaternion.qmetronome.engine

import android.media.AudioAttributes

/**
 * The audio attributes every click `AudioTrack` is built with. The usage is media, so the click's
 * volume is the media stream's - the stream `MainActivity` points the hardware volume keys at.
 */
internal fun clickAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
