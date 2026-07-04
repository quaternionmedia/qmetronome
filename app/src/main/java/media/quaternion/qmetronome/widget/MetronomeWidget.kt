package media.quaternion.qmetronome.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import media.quaternion.qmetronome.MainActivity
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.ui.theme.UnlitGray
import kotlin.math.roundToInt

/**
 * BPM + play/stop, nothing else. A widget that tried to mirror the in-app preview's live
 * pulsing would fight the platform's update economics for a worse result than just looking at
 * the Glyph Matrix or the app - see docs/home-screen-widget.md for why that was ruled out
 * rather than attempted. Updates are event-driven: [media.quaternion.qmetronome.QMetronomeApp] calls
 * [updateAll] whenever bpm/play-state actually changes, and [ToggleMetronomeAction] only calls
 * [MetronomeEngine.toggle] - the resulting state change flows back through that same
 * [updateAll] path rather than the action updating the widget directly, so this never polls.
 */
class MetronomeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        MetronomeEngine.attach(context)
        provideContent {
            val beat by MetronomeEngine.state.collectAsState()
            val label = if (beat.isPlaying) "STOP" else "START"
            val bpm = beat.bpm.roundToInt()
            Log.d(TAG, "provideContent($id): rendering bpm=$bpm isPlaying=${beat.isPlaying} label=$label")
            WidgetContent(bpm = bpm, label = label)
        }
    }

    @Composable
    private fun WidgetContent(bpm: Int, label: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bpm.toString(),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "BPM",
                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 11.sp),
            )
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                // A plain clickable Text instead of Glance's Button - Button's RemoteViews
                // translation didn't reliably re-render its label text on update (round 2 found
                // toggling worked but the label was stuck); Text content updates do.
                modifier = GlanceModifier
                    .clickable(actionRunCallback<ToggleMetronomeAction>())
                    .background(UnlitGray)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

class ToggleMetronomeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "ToggleMetronomeAction.onAction($glanceId): toggling engine")
        MetronomeEngine.attach(context)
        MetronomeEngine.toggle()
    }
}

private const val TAG = "MetronomeWidget"
