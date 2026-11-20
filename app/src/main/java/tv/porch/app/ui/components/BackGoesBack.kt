package tv.porch.app.ui.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import tv.porch.app.domain.BackKey

/**
 * Compose's own answer to Back is to move focus out of the focused item, and that counts as handling the press, so the
 * Back handlers never hear it and the viewer has to press again. Put this around a window's content: a press nothing
 * below it took goes straight to the Back handlers. A dialog is its own window, so it needs its own.
 */
@Composable
fun Modifier.backGoesBack(): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return onKeyEvent { event ->
        if (event.key != Key.Back) return@onKeyEvent false
        if (BackKey.fires(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount)) dispatcher?.onBackPressed()
        true
    }
}
