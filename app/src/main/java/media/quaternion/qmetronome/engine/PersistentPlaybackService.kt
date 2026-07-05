package media.quaternion.qmetronome.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import media.quaternion.qmetronome.MainActivity
import media.quaternion.qmetronome.R
import kotlin.math.roundToInt

/**
 * A quiet foreground service, started/stopped only while [MetronomeEngine.persistentModeEnabled]
 * is on and [MetronomeEngine.state] is actually playing (see the collector in `QMetronomeApp`) -
 * this is what keeps the engine running through backgrounding/screen-lock/Glyph Toy unbind
 * instead of implicitly stopping (see `MetronomeGlyphService.performOnServiceDisconnected`).
 *
 * `START_NOT_STICKY`: if the process is killed outright, restarting just this Service without the
 * rest of the app's `attach()`-driven state would be a half-restored, confusing result - full
 * resilience against process death is a larger follow-up, not this pass.
 */
class PersistentPlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.persistent_playback_channel_name),
            NotificationManager.IMPORTANCE_LOW, // no sound/heads-up - a quiet "still running" cue
        ).apply {
            description = getString(R.string.persistent_playback_channel_description)
        }
        manager.createNotificationChannel(channel)
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MetronomeEngine.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val state = MetronomeEngine.state.value
        val contentText = if (state.isPlaying) {
            "${state.bpm.roundToInt()} BPM - playing"
        } else {
            "Ready"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, PersistentPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "persistent_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "media.quaternion.qmetronome.action.STOP_PERSISTENT_PLAYBACK"
    }
}
