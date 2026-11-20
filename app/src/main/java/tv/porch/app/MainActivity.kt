package tv.porch.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tv.porch.app.domain.LauncherRow
import tv.porch.app.ui.PorchApp
import tv.porch.app.ui.components.backGoesBack
import tv.porch.app.ui.theme.PorchTheme

class MainActivity : ComponentActivity() {
    /** The channel a launcher card asked for, until PorchApp has acted on it. */
    private var openChannel by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as PorchApplication).graph
        // Read before the first screen is built, so a start from a card never resumes the last channel first.
        // A recreated activity still holds the old intent; that card was already opened.
        if (savedInstanceState == null) openChannel = channelFrom(intent)
        setContent {
            PorchTheme {
                Box(Modifier.fillMaxSize().backGoesBack()) {
                    PorchApp(graph, openChannel = openChannel, onOpenHandled = { openChannel = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        channelFrom(intent)?.let { openChannel = it }
    }

    override fun onStop() {
        super.onStop()
        (application as PorchApplication).graph.updateLauncherRow()
    }

    private fun channelFrom(intent: Intent?): Long? =
        if (intent?.action == Intent.ACTION_VIEW) LauncherRow.channelIdFrom(intent.dataString) else null
}
