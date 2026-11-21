package tv.porch.app.ui.simple

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Text
import tv.porch.app.domain.OkHoldGuard
import tv.porch.app.domain.SimpleCode
import tv.porch.app.domain.SimpleCode.Outcome
import tv.porch.app.domain.SimpleCode.Step
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.backGoesBack
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.search.KeyCap
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** Asked by Back on the favourites list in simple mode. Focus starts on Stay, and Back means Stay. */
@Composable
fun LeaveCard(onStay: () -> Unit, onLeave: () -> Unit, onSimpleOff: () -> Unit) {
    val stay = remember { FocusRequester() }
    val guard = remember { OkHoldGuard() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        stay.tryFocus()
    }
    Dialog(onDismissRequest = onStay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A dialog's window dims everything behind it. DESIGN.md bans translucent layers, so the card sits solid with no dim.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize().backGoesBack(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(PorchSpace.ReminderCardWidth)
                    .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                    .padding(PorchSpace.PanelPad)
                    .onPreviewKeyEvent { event ->
                        // The Back press that opened the card can't pick anything; a held OK from the list can't either.
                        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                        ok && guard.swallows(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount)
                    },
            ) {
                Text("Leave Porch?", style = PorchType.Section, color = PorchColors.Text)
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text("The TV goes back to its home screen.", style = PorchType.Meta)
                Spacer(Modifier.height(PorchSpace.TileGap))
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                    Pill("Stay", modifier = Modifier.focusRequester(stay), onClick = onStay)
                    Pill("Leave", onClick = onLeave)
                }
                Spacer(Modifier.height(PorchSpace.TileGap))
                Pill("Simple mode off", plain = true, restColor = PorchColors.Muted, onClick = onSimpleOff)
            }
        }
    }
}

/**
 * The code pad. [turningOn] chooses a code and asks for it again; otherwise it asks for [saved] to leave simple mode.
 * A D-pad keypad, because a TV remote often has no number keys. The fourth digit submits.
 */
@Composable
fun SimpleCodeScreen(turningOn: Boolean, saved: String?, onTurnOn: (String) -> Unit, onTurnOff: () -> Unit) {
    var step by remember { mutableStateOf<Step>(if (turningOn) Step.Choose else Step.Unlock) }
    var entered by remember { mutableStateOf("") }
    var mistake by remember { mutableStateOf<String?>(null) }
    val one = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus) {
        if (hasFocus) return@LaunchedEffect
        withFrameNanos { }
        one.tryFocus()
    }

    fun press(key: String) {
        entered = SimpleCode.type(entered, key)
        if (key != SimpleCode.DELETE) mistake = null
        if (!SimpleCode.complete(entered)) return
        val typed = entered
        entered = ""
        when (val outcome = SimpleCode.submit(step, typed, saved)) {
            is Outcome.Next -> {
                step = outcome.step
                mistake = if (outcome.mismatch) "Those didn't match. Choose a code again." else null
            }
            is Outcome.TurnOn -> onTurnOn(outcome.code)
            Outcome.Wrong -> mistake = "That's not the code. Try again."
            Outcome.TurnOff -> onTurnOff()
        }
    }

    val (title, line) = when (step) {
        Step.Choose -> "Choose a code" to "Four numbers. You'll need them to turn simple mode off, so pick ones you'll remember."
        is Step.Confirm -> "Type it again" to "The same four numbers, to be sure."
        Step.Unlock -> "Type the code" to "The four numbers chosen when simple mode was turned on. If you forget them, Porch's data has to be cleared in the TV's settings. That removes the login and favourites."
    }

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        TopBar(title = "Simple mode", clock = formatHeaderClockLong(rememberNow()), tabs = emptyList(), current = null, onTab = {})
        // Centred on the height under the top bar, so the pad doesn't sit in the top half of the TV. The two columns
        // stay top-aligned inside, so a mistake line appearing doesn't move the boxes.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PorchSpace.Margin), contentAlignment = Alignment.CenterStart) { Row {
            Column(Modifier.width(PorchSpace.SearchLeft)) {
                Text(title, style = PorchType.ScreenTitle)
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                Text(line, style = PorchType.Body, color = PorchColors.TextSecondary)
                Spacer(Modifier.height(PorchSpace.HelpIntroGap))
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap)) {
                    repeat(SimpleCode.LENGTH) { i ->
                        Box(
                            Modifier.size(PorchSpace.SearchKey).background(PorchColors.SurfaceRaised, RoundedCornerShape(PorchRadius.Row)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i < entered.length) Text("•", style = PorchType.ScreenTitle, color = PorchColors.Text)
                        }
                    }
                }
                mistake?.let {
                    Spacer(Modifier.height(PorchSpace.TileGap))
                    Text(it, style = PorchType.Body, color = PorchColors.Text)
                }
            }
            Spacer(Modifier.width(PorchSpace.SearchGap))
            Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap)) {
                SimpleCode.KEYS.take(9).chunked(3).forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap)) {
                        keys.forEach { key ->
                            KeyCap(key, PorchSpace.SearchKey, if (key == "1") Modifier.focusRequester(one) else Modifier, onFocus = {}) { press(key) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap)) {
                    KeyCap("0", PorchSpace.SearchKey, Modifier, onFocus = {}) { press("0") }
                    KeyCap(SimpleCode.DELETE, PorchSpace.SearchWideKey, Modifier, onFocus = {}) { press(SimpleCode.DELETE) }
                }
            }
        } }
    }
}

/** One screen for the viewer, after simple mode is turned on: the three keys he needs. [onDone] also runs on Back. */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val start = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Straight after the code pad's last key, the TV hands focus to the pill before the pill is listening, so it is
    // focused but never draws it (seen on the emulator). Clearing and asking again gives it a focus it can see.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusManager.clearFocus(force = true)
        start.tryFocus()
    }
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Welcome to Porch", clock = formatHeaderClockLong(rememberNow()), tabs = emptyList(), current = null, onTab = {})
        Column(Modifier.padding(start = PorchSpace.Margin, end = PorchSpace.Margin, top = PorchSpace.HelpTop)) {
            Text("Three keys are all you need.", style = PorchType.GuideCell, color = PorchColors.Muted)
            Row(Modifier.padding(top = PorchSpace.HelpIntroGap)) {
                WELCOME.forEach { (title, line) ->
                    Column(Modifier.width(PorchSpace.HelpColumn)) {
                        Text(title, style = PorchType.ScreenTitle, color = PorchColors.Text)
                        Text(line, style = PorchType.Key, color = PorchColors.TextSecondary, modifier = Modifier.padding(top = PorchSpace.HelpTitleGap))
                    }
                }
            }
            Text("Start watching opens your channels.", style = PorchType.Key, color = PorchColors.TextSecondary, modifier = Modifier.padding(top = PorchSpace.HelpButtonGap))
            Pill("Start watching", modifier = Modifier.padding(top = PorchSpace.TileGap).focusRequester(start), onClick = onDone)
        }
    }
}

private val WELCOME = listOf(
    "Up and Down" to "Change the channel while you watch.",
    "OK" to "Shows what's on now. Press OK again to pause.",
    "Back" to "Goes to your channels.",
)
