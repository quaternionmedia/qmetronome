package media.quaternion.qmetronome

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import media.quaternion.qmetronome.ui.MainScreen
import media.quaternion.qmetronome.ui.theme.QMetronomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QMetronomeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(onActivateToy = ::openGlyphToysManager)
                }
            }
        }
    }

    /** Deep links into the system's Glyph Toys manager, per the Glyph Matrix Developer Kit's
     * recommended way to guide users to enable a freshly installed toy. */
    private fun openGlyphToysManager() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
            )
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Glyph Toys manager isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
