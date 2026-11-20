package tv.porch.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import tv.porch.app.R

/** One full set of colours. Field names say what a colour is for, so a Focus fill may be dark in one look and light in another. */
class Palette(
    val Bg: Color,
    val Surface: Color,
    val SurfaceRaised: Color,
    val GuideElapsed: Color,
    val Text: Color,
    val TextSecondary: Color,
    val Muted: Color,
    val Faint: Color,
    val Line: Color,
    val Accent: Color,
    val OnAccent: Color,
    val Focus: Color,
    val OnFocus: Color,
    val Live: Color,
    val LiveOnDark: Color,
    val LiveOnLight: Color,
    val LogoPlate: Color,
)

val DarkPalette = Palette(
    Bg = Color(0xFF08090C),
    Surface = Color(0xFF14161C),
    SurfaceRaised = Color(0xFF1E2129),
    GuideElapsed = Color(0xFF2B2E38),
    Text = Color(0xFFF5F3EE),
    TextSecondary = Color(0xFFC8C6C0),
    Muted = Color(0xFFA8A6A0),
    Faint = Color(0xFF7E7C77),
    Line = Color(0xFF2A2C33),
    Accent = Color(0xFFF5A524),
    OnAccent = Color(0xFF0B0C10),
    Focus = Color(0xFFF5F3EE),
    OnFocus = Color(0xFF0B0C10),
    Live = Color(0xFFE23B3B),
    LiveOnDark = Color(0xFFFF6B6B),
    LiveOnLight = Color(0xFFB3261E),
    LogoPlate = Color(0xFFE8ECF2),
)

/** Warm stone with dark ink. In this look focus is the dark fill, so LiveOnLight, the word LIVE on focus, is the pale red. */
val LinenPalette = Palette(
    Bg = Color(0xFFDDD6C8),
    Surface = Color(0xFFE9E3D7),
    SurfaceRaised = Color(0xFFF3EEE4),
    GuideElapsed = Color(0xFFCDC5B4),
    Text = Color(0xFF1C1A16),
    TextSecondary = Color(0xFF3F3B33),
    Muted = Color(0xFF57524A),
    Faint = Color(0xFF625D54),
    Line = Color(0xFFC7BFB0),
    Accent = Color(0xFF7F4C00),
    OnAccent = Color(0xFFF5F3EE),
    Focus = Color(0xFF1C1A16),
    OnFocus = Color(0xFFF5F3EE),
    Live = Color(0xFFC62828),
    LiveOnDark = Color(0xFFA61B1B),
    LiveOnLight = Color(0xFFFF9A9A),
    LogoPlate = Color(0xFFF7F5F0),
)

/** Colour. DESIGN.md is the source: one amber accent, and red only ever means live. */
object PorchColors {
    /** The look in use. A plain field, not Compose state, so reading a colour costs nothing on a Chromecast; PorchApp rebuilds the screen when it changes. */
    var palette: Palette = DarkPalette

    val Bg get() = palette.Bg
    val Surface get() = palette.Surface
    val SurfaceRaised get() = palette.SurfaceRaised
    val GuideElapsed get() = palette.GuideElapsed
    val Text get() = palette.Text
    val TextSecondary get() = palette.TextSecondary
    val Muted get() = palette.Muted
    val Faint get() = palette.Faint
    val Line get() = palette.Line

    /** The current tab, a tick, a favourite star, See all when focused. */
    val Accent get() = palette.Accent
    val OnAccent get() = palette.OnAccent

    /** The fill of anything focused, and the text on it. */
    val Focus get() = palette.Focus
    val OnFocus get() = palette.OnFocus

    /** Live only: LIVE chip, live progress, guide now-line. Never buttons or errors. */
    val Live get() = palette.Live

    /** The word LIVE in small text: on a resting row, and on a focused one. */
    val LiveOnDark get() = palette.LiveOnDark
    val LiveOnLight get() = palette.LiveOnLight

    /** The solid plate a channel logo sits on, so a dark logo never vanishes. */
    val LogoPlate get() = palette.LogoPlate
    val LogoPlateDark = Color(0xFF2A2C33)

    /** Text on a LogoPlate card: the plate is light in every look, so its ink never changes. */
    val OnPlate = Color(0xFF0B0C10)

    /** Secondary text on a LogoPlate card. */
    val OnPlateMuted = Color(0xFF4A4C52)

    // The player still draws these translucent layers; its own piece replaces them.
    val SidePanel get() = Bg.copy(alpha = 0.86f)
    val Scrim get() = Bg
    val ScrimStops = listOf(0f, 0.78f, 0.90f)
    val Dim get() = Bg.copy(alpha = 0.60f)
}

/** The body face. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** The display face for screen titles and section headings. One variable font file holds every weight. */
@OptIn(ExperimentalTextApi::class)
val Display = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Corner radii. A pill is fully round; a plate is a quarter of its size. */
object PorchRadius {
    val Row = 6.dp
    val Tile = 9.dp
    val Panel = 12.dp
    val Pill = 50

    /** Plates round to a quarter of their size. */
    fun plate(size: Dp): Dp = size * 0.25f
}

/** Text styles. Numerals are tabular. Colour is left to the caller except Meta and Hint. */
object PorchType {
    private fun style(family: FontFamily, size: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = spacing.em,
        fontFeatureSettings = "tnum",
    )

    val ScreenTitle = style(Display, 22, FontWeight.ExtraBold, -0.02f)
    val Section = style(Display, 15, FontWeight.Bold)
    val RowTitle = style(Inter, 14, FontWeight.SemiBold)
    val GuideCell = style(Inter, 16, FontWeight.SemiBold)
    val GuideHeroTitle = style(Display, 24, FontWeight.ExtraBold, -0.02f)
    val TileTitle = style(Inter, 13, FontWeight.SemiBold)
    private val MetaBase = style(Inter, 12, FontWeight.Medium)
    val Meta: TextStyle get() = MetaBase.copy(color = PorchColors.Muted)
    val Tab = style(Inter, 14, FontWeight.SemiBold)
    private val HintBase = style(Inter, 12, FontWeight.Medium)
    val Hint: TextStyle get() = HintBase.copy(color = PorchColors.Muted)
    val LiveLabel = style(Inter, 11, FontWeight.Bold)
    val PlayerTitle = style(Display, 32, FontWeight.Bold)
    val PreviewTitle = style(Display, 28, FontWeight.ExtraBold, -0.02f).copy(lineHeight = 31.sp)

    val Body = style(Inter, 13, FontWeight.Normal)
    val Key = style(Inter, 18, FontWeight.SemiBold)
}

/** Focus look: a solid light fill, a small lift, and the tile outline. */
object PorchFocus {
    const val TileScale = 1.06f
    const val RowScale = 1.02f
    const val ButtonScale = 1.06f
    const val KeyScale = 1.10f
    val Ring = 2.dp
    val Hairline = 1.dp

    /** A tile's outline and the gap between it and the plate. */
    val TileOutline = 3.dp
    val TileOutlineGap = 2.dp
}

object PorchMotion {
    const val FocusMs = 180
    const val PanelMs = 200
}

object PorchSpace {
    val Margin = 32.dp
    val TileGap = 14.dp
    val RowGap = 25.dp
    val ListGap = 6.dp
    val ButtonPadX = 14.dp
    val ButtonPadY = 9.dp
    val ChipPadX = 6.dp
    val ChipPadY = 3.dp
    val TopBarHeight = 48.dp

    /** Top tabs: 12 each side inside the focus fill and no gap, so labels sit 24 apart. 24 between the clock and the first tab. */
    val TabPadX = 12.dp
    val TabsGap = 24.dp

    /** Home, from the frame at px / 2. */
    val HomeTileGap = 18.dp
    val HomeTilePad = 10.dp

    /** Home's four lists. A row is shorter than Live now's 42 dp, so a heading, four rows and "See all" fit a quarter of the screen. */
    val HomeRowHeight = 38.dp
    val HomeRowGap = 3.dp
    val HomeColumnGap = 16.dp

    /** Home's favourite tiles: the plate, and the gap between plates. */
    val HomeTileWidth = 118.dp
    val HomeTileHeight = 84.dp
    val HomeTileRowGap = 13.dp
    // The 24/7 grid: seven show posters across.
    val LoopPosterWidth = 116.dp
    val LoopPosterHeight = 164.dp
    val LoopPosterGap = 4.dp
    /** Home's preview pane. */
    val PreviewHeight = 165.dp
    val PreviewPictureWidth = 293.dp
    val PreviewGap = 18.dp
    val PreviewLogo = 72.dp
    val PreviewSmallLogo = 26.dp
    val PreviewLineGap = 4.dp
    val PreviewBelow = 12.dp
    /** The logo card: logo, then 12 to the name, then 4 to the channel number. */
    val LogoCardNameGap = 12.dp

    /** The programme details sheet. */
    val DetailPictureWidth = 400.dp
    val DetailPictureHeight = 225.dp

    /** The reminder card. */
    val ReminderCardWidth = 360.dp

    /** The bell on a programme with a reminder set: 16 tall. */
    val ReminderMark = 16.dp

    val HeroTitleAir = 4.dp

    /** Player, from the frames at px / 2. */
    val ScrimHeight = 260.dp
    val SidePanelWidth = 350.dp
    val SheetWidth = 320.dp
    val MessageCardWidth = 500.dp
    val PanelPad = 24.dp
    val CardPad = 32.dp
    val CardBadgeLeft = 40.dp
    val CardBadgeTop = 32.dp
    val NextGap = 10.dp
    val SheetRowPadY = 13.dp
    val PlayerBadge = 32.dp
    val PanelBadge = 28.dp
    val PanelBadgeSlot = 42.dp
    val ChipBadge = 24.dp
    val TrackDot = 10.dp

    /** Guide, direction B (DESIGN.md, Guide). */
    val GuideGap = 12.dp
    val GuideChipRow = 40.dp
    val GuideChannelColumn = 190.dp
    val GuideRowHeight = 40.dp
    val GuidePreviewHeight = 96.dp
    val GuidePreviewWidth = 170.dp
    val GuidePreviewLogo = 56.dp
    val GuideRowGap = 4.dp
    val GuideCellGap = 4.dp
    val GuideHeaderHeight = 22.dp
    val GuideCellPad = 9.dp
    val GuideMinTextCell = 60.dp
    val NowLineWidth = 1.dp
    val FilterGap = 8.dp

    /** A filter chip with or without an icon, so a row of them has one height. */
    val ChipHeight = 40.dp

    /** A row on the Sport tab, as tall as Live now's. */
    val SportRow = 42.dp

    /** What's on now, from the frame at px / 2. */
    val NowShelfGap = 10.dp

    /** Search, from the frame at px / 2. */
    val SearchLeft = 330.dp
    val SearchGap = 50.dp
    val SearchKey = 42.dp
    val SearchKeyGap = 6.dp
    val SearchWideKey = 90.dp
    val SearchRow = 46.dp
    val SearchBadge = 32.dp
    val SearchBadgeGap = 12.dp
    val SearchHintGap = 6.dp
    val SearchSpeakGap = 12.dp
    val SearchGridGap = 14.dp
    val SearchGroupGap = 20.dp
    val SearchMoreGap = 4.dp
    val SearchTrack = 90.dp
    val SearchTop = 12.dp

    /** Settings and Setup, from the frames at px / 2. */
    val SettingsList = 520.dp
    val SettingsCard = 310.dp
    val SettingsColumnGap = 50.dp
    val SettingsTop = 16.dp
    val SettingsRow = 38.dp
    val SettingsRowGap = 5.dp
    val SettingsGroupLabelGap = 6.dp
    val SettingsGroupGap = 12.dp
    val SettingsRowPadX = 13.dp
    val SetupList = 500.dp
    val SetupCard = 320.dp
    val SetupColumnGap = 60.dp
    val SetupFieldGap = 8.dp
    val SetupFieldPadY = 8.dp
    val SetupButtonGap = 10.dp
    val CardLineGap = 8.dp

    /** How Porch works, from the frame at px / 2. */
    val HelpTop = 90.dp
    val HelpIntroGap = 24.dp
    val HelpColumn = 310.dp
    val HelpTitleGap = 12.dp
    val HelpLineGap = 10.dp
    val HelpButtonGap = 60.dp

    /** Room above and below a row so a lifted tile is not clipped. */
    val FocusRoom = 8.dp
}

/** False when Android's animator duration scale is 0 (Animations Off): focus changes switch instantly. */
val LocalMotionEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/** The person this copy of Porch greets. Change this if someone else is watching. */
const val VIEWER_NAME = "Ruben"

@Composable
fun PorchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PorchColors.Bg,
            onBackground = PorchColors.Text,
            surface = PorchColors.Surface,
            onSurface = PorchColors.Text,
            surfaceVariant = PorchColors.Surface,
            onSurfaceVariant = PorchColors.Muted,
            primary = PorchColors.Accent,
            onPrimary = PorchColors.OnFocus,
            border = PorchColors.Focus,
        ),
        typography = Typography().withFont(Inter),
    ) {
        // Text with no colour of its own is near-white, not the toolkit's black: headings read from the couch.
        CompositionLocalProvider(
            LocalContentColor provides PorchColors.Text,
            LocalMotionEnabled provides motionEnabled,
            content = content,
        )
    }
}

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.use() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.use(),
        displayMedium = displayMedium.use(),
        displaySmall = displaySmall.use(),
        headlineLarge = headlineLarge.use(),
        headlineMedium = headlineMedium.use(),
        headlineSmall = headlineSmall.use(),
        titleLarge = titleLarge.use(),
        titleMedium = titleMedium.use(),
        titleSmall = titleSmall.use(),
        bodyLarge = bodyLarge.use(),
        bodyMedium = bodyMedium.use(),
        bodySmall = bodySmall.use(),
        labelLarge = labelLarge.use(),
        labelMedium = labelMedium.use(),
        labelSmall = labelSmall.use(),
    )
}
