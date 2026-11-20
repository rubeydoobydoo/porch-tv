package tv.porch.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.withFrameNanos
import tv.porch.app.domain.ReturnTile
import tv.porch.app.domain.SimpleMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import tv.porch.app.domain.Tabs
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.porch.app.domain.Airing
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelBadge
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.HeaderClockFit
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import tv.porch.app.domain.Kind
import tv.porch.app.domain.formatHeaderClock
import tv.porch.app.domain.formatHourMinute
import tv.porch.app.domain.formatLeft
import tv.porch.app.domain.progressOf
import tv.porch.app.ui.theme.LocalMotionEnabled
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val CardShape = RoundedCornerShape(PorchRadius.Tile)
private val ChipShape = RoundedCornerShape(PorchRadius.Pill)
private val PillShape = RoundedCornerShape(PorchRadius.Pill)

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    return now
}

@Composable
fun ScreenHeader(title: String, withDay: Boolean, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = PorchType.ScreenTitle)
        Text(formatHeaderClock(now, withDay), style = PorchType.Meta)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = PorchType.Section)
        if (!subtitle.isNullOrBlank()) {
            Text(
                "   $subtitle",
                style = PorchType.Meta,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** The outlined "OK" key in a hint line. [label] swaps in another key, such as "Mic". */
@Composable
fun OkKeycap(label: String = "OK") {
    Box(
        Modifier
            .border(PorchFocus.Hairline, PorchColors.Line, RoundedCornerShape(PorchRadius.Row))
            .padding(horizontal = PorchSpace.ChipPadX, vertical = PorchSpace.ChipPadY),
    ) {
        Text(label, style = PorchType.LiveLabel, color = PorchColors.Text)
    }
}

@Composable
fun HintBar(text: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = PorchType.Hint)
        Text(trailing, style = PorchType.Hint)
    }
}

@Composable
fun SyncLine(text: String?, failed: Boolean) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        color = if (failed) PorchColors.Text else PorchColors.Muted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun Badge(
    text: String,
    color: Color,
    size: Dp = 24.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    fixedWidth: Boolean = false,
) {
    Box(
        Modifier
            .then(if (fixedWidth) Modifier.size(size) else Modifier.defaultMinSize(minWidth = size, minHeight = size))
            .background(color, RoundedCornerShape(PorchRadius.plate(size)))
            .padding(horizontal = if (fixedWidth) 0.dp else 6.dp, vertical = if (fixedWidth) 0.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, fill: Color = PorchColors.Live, track: Color = PorchColors.Faint) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(fill),
        )
    }
}

@Composable
fun porchCardColors() = CardDefaults.colors(
    containerColor = PorchColors.Surface,
    contentColor = PorchColors.Text,
    focusedContainerColor = PorchColors.SurfaceRaised,
    focusedContentColor = PorchColors.Text,
    pressedContainerColor = PorchColors.SurfaceRaised,
    pressedContentColor = PorchColors.Text,
)

val FocusRing get() = androidx.compose.foundation.BorderStroke(PorchFocus.Ring, PorchColors.Focus)

@Composable
fun porchCardBorder(shape: Shape = CardShape) = CardDefaults.border(
    focusedBorder = Border(border = FocusRing, shape = shape),
    pressedBorder = Border(border = FocusRing, shape = shape),
)

/** Lift on focus. Tiles use [PorchFocus.TileScale], rows [PorchFocus.RowScale]. No lift when animations are off. */
@Composable
fun porchCardScale(focused: Float = PorchFocus.TileScale): androidx.tv.material3.CardScale {
    val lift = if (LocalMotionEnabled.current) focused else 1f
    return CardDefaults.scale(scale = 1f, focusedScale = lift, pressedScale = if (lift > 1f) 1.01f else 1f)
}

@Composable
fun clickColors(
    container: Color = PorchColors.Surface,
    content: Color = PorchColors.Text,
    focused: Color = PorchColors.SurfaceRaised,
) = ClickableSurfaceDefaults.colors(
    containerColor = container,
    contentColor = content,
    focusedContainerColor = focused,
    focusedContentColor = content,
    pressedContainerColor = focused,
    pressedContentColor = content,
)

@Composable
fun clickBorder(shape: Shape) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(border = FocusRing, shape = shape),
)

@Composable
fun clickScale(focused: Float = PorchFocus.RowScale): androidx.tv.material3.ClickableSurfaceScale {
    val lift = if (LocalMotionEnabled.current) focused else 1f
    return ClickableSurfaceDefaults.scale(focusedScale = lift)
}

/** Coming back from the player puts focus back on the tile it left. */
val LocalReturnTile = staticCompositionLocalOf { ReturnTile() }

@Composable
fun Modifier.landsOnReturn(): Modifier {
    val back = LocalReturnTile.current
    val tile = currentCompositeKeyHash
    val tracked = onFocusChanged { if (it.hasFocus) back.focused(tile) }
    if (!back.isFor(tile)) return then(tracked)
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        back.landed()
    }
    return then(tracked).focusRequester(requester)
}

/** "FLSP 003 · 1234" or "TR · 1234": the catalogue code or tag, then the channel number when it has one. */
fun channelMetaLine(channel: Channel): String =
    listOfNotNull(channel.code ?: channel.tag, channel.number.takeIf { it > 0 }?.toString()).joinToString(" · ")

@Composable
fun SoonCard(
    airing: Airing,
    offsetMinutes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val start = GuideClock.shift(airing.programme.startUtc, offsetMinutes)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(92.dp),
        shape = CardDefaults.shape(shape = CardShape),
        colors = porchCardColors(),
        border = porchCardBorder(),
        scale = porchCardScale(PorchFocus.RowScale),
        glow = CardDefaults.glow(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatHourMinute(start),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(72.dp),
            )
            Column {
                Text(
                    airing.programme.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    airing.channel.shownTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorchColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ChannelMeta(channel: Channel, showMeta: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(channel, Color(ChannelBadge.color(channel.name, channel.group)))
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.favourite) {
                    FavouriteStar()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    channel.shownTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorchColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = channelMetaLine(channel)
            if (showMeta && meta.isNotEmpty()) {
                Text(meta, style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChannelChip(channel: Channel, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        colors = clickColors(),
        border = clickBorder(ChipShape),
        scale = clickScale(PorchFocus.ButtonScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel, Color(ChannelBadge.color(channel.name, channel.group)))
            Spacer(Modifier.width(8.dp))
            Text(channel.shownTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/**
 * The kind row: an all pill plus the kinds the list has. Hidden when the list has none.
 * [firstFocus] lands on the first pill (the row above sends Down there); [upTo] is where Up goes.
 */
@Composable
fun KindPills(
    kinds: List<Kind>,
    selected: Kind?,
    onSelect: (Kind?) -> Unit,
    allLabel: String = "All",
    firstFocus: FocusRequester? = null,
    upTo: FocusRequester? = null,
) {
    if (kinds.isEmpty()) return
    // Focus targets read their own Up, so it goes on every pill rather than on the row.
    val pill = if (upTo != null) Modifier.focusProperties { up = upTo } else Modifier
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(
            allLabel,
            selected = selected == null,
            modifier = if (firstFocus != null) pill.focusRequester(firstFocus) else pill,
        ) { onSelect(null) }
        kinds.forEach { kind -> Pill(kind.label, selected = selected == kind, modifier = pill, icon = kind.iconRes()) { onSelect(kind) } }
    }
}

/**
 * The button rule. Resting: Surface fill. Focused: Focus fill, dark text, lift. [selected] (the current filter or a
 * picked group) adds an amber tick that stays when focused, and never relies on colour alone.
 * A [plain] pill is a tab: no fill at rest, and the current one is amber text with no tick.
 * [open] is the item whose details are showing: a raised fill and no tick, because nothing was chosen.
 * [hidden] is a group that is switched off: no fill, Faint text, and the caller says the word Hidden.
 * [accent] is kept so callers compile; it draws as a normal button.
 * [restColor] is the resting label colour, for quieter buttons such as the other tabs.
 */
@Composable
fun Pill(
    label: String,
    selected: Boolean = false,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PorchType.TileTitle,
    restColor: Color = PorchColors.Text,
    plain: Boolean = false,
    open: Boolean = false,
    hidden: Boolean = false,
    @DrawableRes icon: Int? = null,
    /** A filter chip: always [PorchSpace.ChipHeight] tall, whether or not it has an icon. */
    chip: Boolean = false,
    /** False in a row exactly one pill tall, where a lifted pill would be clipped. Focus is still the solid fill. */
    lift: Boolean = true,
    /** Space each side of the label. The top tabs use the narrower [PorchSpace.TabPadX]. */
    padX: Dp = PorchSpace.ButtonPadX,
    onClick: () -> Unit,
) {
    val container = when {
        plain -> Color.Transparent
        open -> PorchColors.SurfaceRaised
        hidden -> Color.Transparent
        else -> PorchColors.Surface
    }
    val content = when {
        plain && selected -> PorchColors.Accent
        hidden -> PorchColors.Faint
        else -> restColor
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(if (lift) PorchFocus.ButtonScale else 1f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(
            Modifier
                .then(if (chip) Modifier.heightIn(min = PorchSpace.ChipHeight) else Modifier)
                .padding(horizontal = padX, vertical = PorchSpace.ButtonPadY),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            if (selected && !plain) Tick()
            Text(label, style = textStyle, maxLines = 1)
        }
    }
}

/** A small tick drawn as a vector: amber at rest, the dark focused-text colour once its pill is focused. */
@Composable
fun Tick(modifier: Modifier = Modifier) {
    // The surface sets the content colour to OnFocus while focused, so this follows it without any state of its own.
    val onFocus = LocalContentColor.current == PorchColors.OnFocus
    val color = if (onFocus) PorchColors.OnFocus else PorchColors.Accent
    Canvas(modifier.size(14.dp)) {
        val stroke = 2.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            lineTo(size.width * 0.40f, size.height * 0.82f)
            lineTo(size.width * 0.90f, size.height * 0.20f)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** True while simple mode is on (Dad's TV). PorchApp provides it. */
val LocalSimpleMode = androidx.compose.runtime.compositionLocalOf { false }

/** Top bar for the Studio screens: title and clock on the left, tabs on the right. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TopBar(
    title: String,
    clock: String,
    tabs: List<String>,
    current: String?,
    onTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Lets a screen put focus on the selected tab when it has no row to give it to. */
    selectedTab: FocusRequester? = null,
) {
    // Simple mode shows only Home and the guide, on every screen's bar.
    val shown = SimpleMode.tabs(tabs, LocalSimpleMode.current)
    Row(
        modifier
            .fillMaxWidth()
            .height(PorchSpace.TopBarHeight)
            .background(PorchColors.Bg)
            .padding(horizontal = PorchSpace.Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted, so the Row measures it after the tabs: the tabs always show whole and the clock gives way.
        TitleAndClock(title, clock, Modifier.weight(1f))
        if (shown.isNotEmpty()) Spacer(Modifier.width(PorchSpace.TabsGap))
        // Up from a screen lands on the tab you are on, not on whichever tab sits nearest.
        val own = selectedTab ?: remember { FocusRequester() }
        val landing = current != null && current in shown
        Row(
            Modifier.focusProperties { enter = { if (landing) own else FocusRequester.Default } }.focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The ends wrap (Home to Settings in one press); the rule, held keys included, is in Tabs.wrap.
            val tabRequesters = remember(shown) { shown.associateWith { FocusRequester() } }
            for (tab in shown) {
                Pill(
                    tab,
                    selected = tab == current,
                    modifier = Modifier
                        .focusRequester(tabRequesters.getValue(tab))
                        .let { if (tab == current) it.focusRequester(own) else it }
                        .onPreviewKeyEvent { event ->
                            val direction = when (event.key) {
                                Key.DirectionLeft -> -1
                                Key.DirectionRight -> 1
                                else -> return@onPreviewKeyEvent false
                            }
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val target = Tabs.wrap(shown, tab, direction, event.nativeKeyEvent.repeatCount) ?: return@onPreviewKeyEvent false
                            runCatching { tabRequesters.getValue(target).requestFocus() }.isSuccess
                        },
                    textStyle = PorchType.Tab,
                    restColor = PorchColors.Muted,
                    plain = true,
                    padX = PorchSpace.TabPadX,
                ) { onTab(tab) }
            }
        }
    }
}

/** The title, then the clock in full, without its day, or not at all: whichever fits ([HeaderClockFit]). */
@Composable
private fun TitleAndClock(title: String, clock: String, modifier: Modifier) {
    Layout(
        content = {
            Text(title, style = PorchType.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock, style = PorchType.Meta, maxLines = 1)
            Text(HeaderClockFit.dropDay(clock), style = PorchType.Meta, maxLines = 1)
        },
        modifier = modifier,
    ) { parts, constraints ->
        val titleP = parts[0].measure(constraints.copy(minWidth = 0))
        val fullP = parts[1].measure(Constraints())
        val shortP = parts[2].measure(Constraints())
        val gap = PorchSpace.TileGap.roundToPx()
        val clockP = when (HeaderClockFit.pick(constraints.maxWidth, titleP.width, gap, fullP.width, shortP.width)) {
            HeaderClockFit.Shown.Full -> fullP
            HeaderClockFit.Shown.TimeOnly -> shortP
            HeaderClockFit.Shown.None -> null
        }
        val height = maxOf(titleP.height, clockP?.height ?: 0)
        layout(constraints.maxWidth, height) {
            titleP.placeRelative(0, (height - titleP.height) / 2)
            clockP?.placeRelative(titleP.width + gap, (height - clockP.height) / 2)
        }
    }
}

/** A card on the right of Settings and Setup: Surface fill, Panel radius. */
@Composable
fun DetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
            .padding(PorchSpace.PanelPad),
    ) { content() }
}

/** The red LIVE label. Red means live and nothing else. */
@Composable
fun LiveChip() {
    Box(
        Modifier
            .background(PorchColors.Live, RoundedCornerShape(PorchRadius.Row))
            .padding(horizontal = PorchSpace.ChipPadX, vertical = PorchSpace.ChipPadY),
    ) {
        Text("LIVE", style = PorchType.LiveLabel, color = PorchColors.Text)
    }
}

@Composable
fun <T> FittingRow(
    items: List<T>,
    extra: Int,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    key: (T) -> Any,
    more: @Composable (Modifier) -> Unit,
    item: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraintsWidth(modifier) { maxWidth ->
        val shown = items
        val slots = shown.size + if (extra > 0) 1 else 0
        if (slots == 0) return@BoxWithConstraintsWidth
        val gap = 12.dp
        val width = ((maxWidth - gap * (slots - 1)) / slots).coerceAtLeast(96.dp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(shown, key = key) { value -> item(value, Modifier.width(width).height(rowHeight)) }
            if (extra > 0) {
                item { more(Modifier.width(width).height(rowHeight)) }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsWidth(modifier: Modifier = Modifier, content: @Composable (Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        content(maxWidth)
    }
}

@Composable
fun TvField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
    placeholder: String? = null,
) {
    // The D-pad lands on the box, not the text, so passing through never opens the keyboard.
    // OK starts typing. Up or Down while typing leaves the box. The keyboard's action key
    // moves to the next box, or runs onDone on the last one.
    val focusManager = LocalFocusManager.current
    val textFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(editing) { if (editing) textFocus.requestFocus() }
    Column(modifier) {
        Text(label, style = PorchType.Meta)
        Spacer(Modifier.height(PorchSpace.SettingsGroupLabelGap))
        Box(
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus }
                .onKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (ok && event.type == KeyEventType.KeyUp && !editing) {
                        editing = true
                        true
                    } else {
                        ok
                    }
                }
                .focusable()
                .background(if (focused) PorchColors.SurfaceRaised else PorchColors.Surface, RoundedCornerShape(PorchRadius.Row))
                .border(if (focused) PorchFocus.Ring else 1.dp, if (focused) PorchColors.Focus else PorchColors.Line, RoundedCornerShape(PorchRadius.Row))
                .padding(horizontal = 14.dp, vertical = PorchSpace.SetupFieldPadY),
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = PorchType.Key.copy(fontWeight = FontWeight.Normal), color = PorchColors.Muted, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                // Most boxes hold addresses. The keyboard must not suggest or learn the password.
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    // Step off the box first, so the keyboard closes and an error line can be seen.
                    onDone = {
                        focusManager.moveFocus(FocusDirection.Down)
                        onDone?.invoke()
                    },
                ),
                textStyle = PorchType.Key.copy(color = PorchColors.Text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(PorchColors.Text),
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocus)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { if (!it.isFocused) editing = false }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    },
            )
        }
    }
}

@Composable
fun MicGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(22.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.33f, size.height * 0.06f),
            size = Size(size.width * 0.34f, size.height * 0.46f),
            cornerRadius = CornerRadius(size.width, size.width),
        )
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
            size = Size(size.width * 0.68f, size.height * 0.46f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(size.width / 2f, size.height * 0.74f),
            end = Offset(size.width / 2f, size.height * 0.9f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.32f, size.height * 0.9f),
            end = Offset(size.width * 0.68f, size.height * 0.9f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun GuideGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(16.dp)) {
        val y = listOf(0.2f, 0.5f, 0.8f)
        y.forEach { fraction ->
            drawLine(
                color = color,
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ColumnScope.EmptyLine(text: String) {
    Text(text, color = PorchColors.Muted, style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun BoxScope.CenterMessage(text: String) {
    Text(text, modifier = Modifier.align(Alignment.Center), color = PorchColors.Muted)
}
