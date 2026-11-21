# Porch design

The look Ruben picked on 2 Nov 2026: A and C mixed. Calm near-black, flat rows, one amber accent, and big logo tiles only where a real picture earns the space. The mock-up is the board "A and C mixed" in the couch rebuild artifact.

Judged on a Chromecast from the couch. The TV draws 960 by 540 dp at 2x, so 1 dp is 2 screen pixels. Every value below is in dp or sp unless it says otherwise.

Read this before touching UI. Never invent a colour, size or radius. If a value is missing, add it here first.

## Hard limits

- **D-pad only.** Everything is either focusable or decoration. Focus is never ambiguous: focus is a solid light fill, on every screen.
- **Solid fills only.** No gradients, blur, shadows or translucent layers. The Chromecast is GPU-bound, and these are what cost frames.
- **One exception to solid fills: the live overlay's scrim.** Video needs a readable band under the channel name, title and hints, so `Overlay` draws one vertical gradient over the bottom `PorchSpace.ScrimHeight` of the picture. It is `PorchColors.Scrim` (the `Bg` colour) at alpha `PorchColors.ScrimStops` (0, 0.78, 0.90) at 0%, 45% and 100% of the height, top to bottom. The only other alpha in the player is `PorchColors.SidePanel` (0.86) behind the side panel and `PorchColors.Dim` (0.60) behind the quick menu and sheets. Nothing else may use a gradient or alpha.
- **Squares hold a real picture or they aren't squares.** A channel with no logo in a list gets a row, not a tile.
- **Readable from three metres.** Text is 12 sp or larger. The one exception is the 11 sp LIVE label, which is bold.

## Colour

One accent: amber. Red only ever means live.

| Token | Value | Use |
|---|---|---|
| `Bg` | #08090C | Every screen's background |
| `Surface` | #14161C | Panels, dialogs, a resting pill, a resting field |
| `SurfaceRaised` | #1E2129 | An open item, whose details are showing |
| `GuideElapsed` | #2B2E38 | The part of an on-air guide cell that has already played, from its left edge to the now-line |
| `Text` | #F5F3EE | Titles |
| `TextSecondary` | #C8C6C0 | Text that must be read, but isn't the title |
| `Muted` | #A8A6A0 | Channel names, times, hints |
| `Faint` | #7E7C77 | Hidden items, and decoration only |
| `Line` | #2A2C33 | Guide grid lines, nothing else |
| `Accent` | #F5A524 | The current tab, a tick, a favourite star, the reminder bell, See all when focused |
| `OnAccent` | #0B0C10 | Text on an amber fill |
| `Focus` | #F5F3EE | The fill of anything focused |
| `OnFocus` | #0B0C10 | Text on a focused fill |
| `Live` | #E23B3B | Live chip fill, progress bar, guide now-line |
| `LiveOnDark` | #FF6B6B | The word LIVE on a dark row |
| `LiveOnLight` | #B3261E | The word LIVE on a focused row |
| `LogoPlate` | #E8ECF2 | Behind every logo |
| `LogoPlateDark` | #2A2C33 | Behind a logo that is nearly all white |
| `OnPlateMuted` | #4A4C52 | Secondary text on a LogoPlate card |

Channel letter badges keep their own colour set, because they're identity, not theme.

## Linen look

A second look Ruben picked on 5 Nov 2026: warm stone with dark ink. A choice in Settings; Dark is the default. The player stays dark over video in both. Same token names, so focus is the dark fill here.

| Token | Value |
|---|---|
| `Bg` | #DDD6C8 |
| `Surface` | #E9E3D7 |
| `SurfaceRaised` | #F3EEE4 |
| `GuideElapsed` | #CDC5B4 |
| `Text` | #1C1A16 |
| `TextSecondary` | #3F3B33 |
| `Muted` | #57524A |
| `Faint` | #625D54 |
| `Line` | #C7BFB0 |
| `Accent` | #7F4C00 |
| `OnAccent` | #F5F3EE |
| `Focus` | #1C1A16 |
| `OnFocus` | #F5F3EE |
| `Live` | #C62828 |
| `LiveOnDark` (LIVE on a resting row) | #A61B1B |
| `LiveOnLight` (LIVE on focus) | #FF9A9A |
| `LogoPlate` | #F7F5F0 |

## Type

Display: Manrope, for screen titles and section headings. Body: Inter, for everything else. Numbers are tabular.

| Token | Font | Size | Weight | Use |
|---|---|---|---|---|
| `ScreenTitle` | Display | 22 | 800 | Top of a screen |
| `Section` | Display | 15 | 700 | A list's heading |
| `RowTitle` | Body | 14 | 600 | A programme in a row |
| `TileTitle` | Body | 13 | 600 | Under a logo tile |
| `Meta` | Body | 12 | 500 | Channel name, time, count |
| `Tab` | Body | 14 | 600 | Top tabs |
| `Hint` | Body | 12 | 500 | Hint bar |
| `LiveLabel` | Body | 11 | 700 | The word LIVE |
| `PlayerTitle` | Display | 32 | 700 | Player overlay |
| `PreviewTitle` | Display | 28 | 800 | The programme in Home's preview pane, up to 2 lines, 31 between lines |
| `Body` | Body | 13 | 400 | Paragraphs and setting text. Carried over from the old app until each screen's piece lands |
| `Key` | Body | 18 | 600 | Search keys and typed text, until the Search piece |

## Space

A 4 dp grid: 4, 8, 12, 16, 24, 32, 40. `Margin` is 32 at the screen's edge. The top bar is 48 tall. There's no max content width, because a TV screen is the width.

## Shape

| Token | Radius |
|---|---|
| `Row` | 6 |
| `Pill` | Fully round |
| `Tile` | 9 |
| `Plate` | A quarter of its size |
| `Panel` | 12 |

## Rows and tiles

- **Row:** no fill at rest. Height 38 in Home's lists, 42 on Live now, Sport and Channels. Logo plate, title, then channel, then the time at the right edge.
- **Focused row:** `Focus` fill, `OnFocus` text, scale 1.02.
- **Reminder mark:** a small bell (`ic_bell`, a vector), 16 tall (`ReminderMark`), in `Accent`, on a programme with a reminder set: Home's Starting soon rows (before the time) and the guide's cell for that programme (at its right edge). On a focused row or cell it is `OnFocus`.
- **Player panel rows:** "What else is on" and the audio and subtitles sheet use the same focus as every row: the `Focus` fill with `OnFocus` text, no ring. The progress bar on a focused "What else is on" row keeps the `Live` fill on a `Line` track.
- **Logo tile:** 84 tall and about 118 wide on Home, 13 apart. `LogoPlate` fill, logo fitted inside; with no logo, the channel name in `TileTitle`, dark, on the plate. A title and a channel name below. Focused: a 3 dp `Focus` outline with a 2 dp gap, scale 1.06.
- **Poster tile:** on 24/7, a portrait show poster 116 wide and 164 tall, `Tile` radius, seven across, 14 between posters at rest (the tiles' outline room plus a 4 dp grid gap). Cropped to fill, `Surface` until it loads, no crossfade. The show's name sits below in `TileTitle`, one line. Focus is the same as a Movies tile: the `Focus` fill as a card of 5 (`TilePad`) round the poster and its name (the name turns `OnFocus`), scale 1.06. No outline.
- **Poster tile, no poster:** while TVmaze hasn't answered, and when it has no poster, a solid `Surface` tile with the show's name in `TileTitle`, centred, up to 3 lines. Never the generic 24/7 logo. A channel's own logo (one that fewer than 5 channels share) goes on a `LogoPlate` tile of the same size instead.
- **Poster credit:** "Posters from TVmaze" in `Hint`, at the right of the 24/7 hint bar; "Posters from TVmaze and RPDB" while a rating key is saved. TVmaze's data is CC BY-SA. "Back goes to the filters" shows in that bar only while the grid has focus.
- **Rating poster:** with the viewer's RPDB key, the same 116 by 164 tile shows RPDB's poster (rating drawn on it) in place of TVmaze's. While it loads, a solid `Surface` block. If RPDB has nothing usable, TVmaze's poster, then the name tile, as before.
- **Poster ratings screen (Settings):** laid out like Backup guides. Left: one line of explanation (`Body`, `TextSecondary`), the last result (`TileTitle`), a "Remove key" pill. Right: the phone card with the address ending `/posters`. The key is never shown. The Settings row trails "On" or "Off".
- **Logo tile:** `LogoTile` (`LogoPlate` fill, logo fitted inside; with no logo, the channel name in `TileTitle`, dark, on the plate) is kept for a 24/7 channel that has its own logo. Focused: a 3 dp `Focus` outline with a 2 dp gap, scale 1.06.
- **Other copies panel:** the `WatchOnPanel` side panel: the channel's name in `ScreenTitle`, "Other copies" under it, and one full-width pill per copy, best first, saying its quality (when the name gives one) and channel number: "FHD   Ch 512". Opened from "Other copies (3)" in the hold-OK menu in the guide and the player, below the favourite row, so focus doesn't move if the count arrives after the menu opens.
- **Logo card:** what a programme with no picture shows, and what shows while a picture loads, so a slow picture never looks broken. `LogoPlate` fill, `Tile` radius. The channel logo at 72 in the middle (a channel with no logo shows its letter badge at 72), 12 below it the channel name in `TileTitle` in `OnFocus`, 4 below that "Channel 424" in `Meta` in `OnPlateMuted`. 293 by 165 in Home's and the guide's preview pane, 400 by 225 in the details sheet. The name is 10 (`HomeTilePad`) in from the card's sides.
- **Filter chip:** a pill 40 tall, with or without an icon, so a row of chips has one height. The row starts at the margin and scrolls off the screen's edge, never off a cut line inside the margin.
- **Settings and Search rows:** resting `Surface`, 6 radius; focused is the `Focus` fill with `OnFocus` text, like every row. No ring. Secondary text on a focused row turns `OnFocus` too. Settings group labels are `Section` in `TextSecondary`.
- **Search before typing:** with nothing typed, the right side shows "Your channels" in `Section` over up to 7 of Home's Your channels rows (the same `LiveRowItem`). Right from the keyboard enters it, Left returns. The first letter replaces it with results. With no favourites or recents it is the plain line "Type a show or channel." There is no voice hint text; the Speak pill is `Text` on `Surface` like any pill.
- **Search field:** the typed text sits on `SurfaceRaised`, so it reads apart from the keys, which rest on `Surface`.
- **Top tabs:** Up from a screen lands on the tab you are on. The ends wrap: a fresh Left on the first tab goes to the last (Home to Settings), a fresh Right on the last goes to the first. A held Left or Right stops at the end. Each label has 12 each side inside its focus fill (`TabPadX`) and no gap between tabs, so labels sit 24 apart; 24 between the clock and the first tab (`TabsGap`).
- **Top bar fit:** the tabs always show whole. When the bar is short of room the title side gives way: the clock drops its day ("10:38 pm"), then hides, and last the title shortens with an ellipsis.
- **Pill at rest:** `Surface` fill, `Text`.
- **Pill chosen:** `Surface` fill, an amber tick, `Text`. A chosen item never relies on colour alone.
- **Group state:** every row says it in words. Showing: resting pill, amber tick, the word Showing. Hidden: no fill, `Faint` text, the word Hidden.

## Home preview pane

The top of Home, under the top bar, showing whatever row has focus. It isn't focusable, and nothing on it moves: no alpha, no gradient, no scale.

- **Size:** 165 tall, across the width between the margins.
- **Picture:** 293 by 165, `Tile` radius, cropped to fill. 18 from the text. Until a picture loads, and whenever there isn't one, the logo card (Rows and tiles). A missing picture looks the same as a slow one, so it never reads as broken.
- **Text, top to bottom, 4 apart:** logo at 26 and channel name in `Meta`; title in `PreviewTitle`; status in `Meta`, with the word LIVE in `LiveOnDark`; description in `Body`, up to 3 lines, in `TextSecondary`.
- **12 below the pane** before the lists start.

Home's budget at 540 tall, with the out-of-date line showing: top bar 48, line 28, pane 165, gap 12, heading 30, four rows and See all 202, hint 32. That's 517, so rows stay at 38.

## Guide

Picked 9 Nov 2026 (Guide 2 on the mock-up). Three hours across, marks every half hour, up to 6 channels.

- **Top card:** 96 tall. `LogoPlate` card 170 wide with the channel logo at 56 and no words, or the programme's picture. Beside it the title (`GuideHeroTitle`: Display, 24, 800, one line), the status in `Meta`, one line of description in `Body`.
- **Rows:** 40 tall, 4 apart. Programme titles are `GuideCell` (Body, 16, 600).

## Programme details sheet

A full-screen sheet about one programme, over whatever screen opened it. Back closes it and the screen below keeps its focus. Solid `Bg` over the whole window, 32 (`Margin`) all round, 32 between the two columns.

- **Left column, 400 wide, centred on the screen's height with the right column:** the picture, 400 by 225, `Tile` radius, cropped to fill. Until it loads, and whenever there isn't one, the logo card at 400 by 225, as on Home's preview pane.
- **Right column:** channel logo at 26 and channel name in `Meta`; the title in `ScreenTitle`, up to 2 lines; the when line in `Meta`, with the word Live in `LiveOnDark`; the description in `Body`, `TextSecondary`, up to 7 lines. No description means no line, never placeholder text. Then a row of `Pill`s, 14 apart (`TileGap`); the first takes focus.
- **Gaps, top to bottom:** 4 between the channel row, title and when line (`HeroTitleAir`), 14 before the description and before the pills (`TileGap`), 32 (`Margin`) before "Next on <channel>" in `Section` with up to 3 programmes in `Meta`, under the pills. Decoration only.

## Guide

Ruben picked direction B on 3 Nov 2026: Home's preview pane on top, then the chips, then a grid of five rows. Top to bottom, inside the 32 margin:

- **Top bar:** 48.
- **Preview pane:** 165 tall, the same pane as Home's, showing the programme under the focused cell. The description is up to 2 lines here, not 3. It follows focus with Home's settle delay. While programmes load it shows the channel's logo and name only.
- **12 gap, then the chip row:** 40 tall. "Jump to now" first, then the filters. A chip here doesn't lift, because the row is exactly one chip tall. Focus is still the `Focus` fill.
- **12 gap, then the time header:** 22 tall. The day in `Meta` over the channel column, then times every 30 minutes in `Meta`. The current time is a pill: `Live` fill, `OnAccent` text, `LiveLabel`.
- **Grid:** rows 38 tall, 4 between. The channel column is 190 wide: logo plate at 26, the name in `RowTitle`, the number and "catch-up" in `Meta`. Cells: `Surface` fill, `Row` radius, `RowTitle` text, 4 between. The focused cell is the `Focus` fill with `OnFocus` text, no scale. 2 hours across the cell area.
- **Now pill text:** `OnAccent`, dark on the `Live` fill.
- **Now-line:** 1 wide, `Live`, drawn under the cells so it never crosses text. The pill in the header stays on top.
- **Loading:** cells are plain `Surface` blocks with no text until programmes arrive.
- **Hint bar:** 32.

Budget at 540 tall: 48 + 165 + 12 + 40 + 12 + 22 + five rows 206 + 32 = 537. With "All", favourites come first, then the rest in the guide's order.

## Movies tab

Ruben picked layout A on 10 Nov 2026 (`design/movies-v1/movies-mockup.html`). A tab between 24/7 and Search.

- **Top to bottom, inside the 32 margin:** top bar 48; "Continue watching" in `Section`; a row of poster tiles (the 24/7 poster tile, 116 by 164, 14 apart) with a resume bar under each; the category chips (filter chips, 40 tall, the chosen one with an amber tick); the chosen category's posters, seven across, scrolling down. With nothing started, the shelf and its heading are gone and the chips sit under the top bar.
- **Resume bar:** 4 tall, 2 radius, `TextSecondary` fill on a `Line` track, 5 under the poster, then the title. Red stays for live, amber for chosen.
- **No poster:** the 24/7 name tile (`Surface`, title centred in `TileTitle`, up to 3 lines).
- **Focus:** a poster tile here (Movies and Series, not 24/7) takes the same focus as every row: the `Focus` fill as a card of 5 (`TilePad`) round the poster, its resume bar and its name (the name turns `OnFocus`), scale 1.06. No outline. A frame round the poster alone read as an outline, so the name sits inside the fill. Ruben's rule that focus is a solid light fill on every screen (19 Nov walk) wins over the 24/7 poster tile's outline.
- **Hint bar:** "OK opens the film".

## Film details sheet

Picked 10 Nov 2026 (sheet 1). The programme details sheet with a film's poster.

- **Left column:** the poster, 260 by 366, `Tile` radius, cropped to fill, centred on the screen's height with the right column. No poster: the name tile at that size.
- **Right column, gaps as the programme sheet:** genre, year, length and rating in `Meta` ("Drama · 2021 · 1 h 52 min · Rated 7.4", leaving out what the provider doesn't give); the title in `ScreenTitle`, up to 2 lines; the plot in `Body`, `TextSecondary`, up to 7 lines (no plot, no line); for a started film the resume bar 300 wide and "41 min in · 1 h 11 min left" in `Meta`; then the pills, 14 apart: "Resume from 41:12" (focus) and "Play from the start", or "Play" alone for a film not started.
- **Hint bar:** "OK plays · Left and Right skip 10 s while it plays · Back returns to Movies".

## Series tab

Ruben asked for it on 10 Nov 2026. The Movies tab's layout A for TV shows, as a tab between Movies and Search.

- **Same as Movies:** top bar, "Continue watching" shelf, category chips, posters seven across, the name tile when there is no poster.
- **Chip order:** drama and crime first, then comedy and sitcoms, then lifestyle, then the rest in provider order (`Shows.genreRank`).
- **Continue watching:** one tile per show, the show's poster. The resume bar shows how far into the episode you are up to; a show waiting on its next episode shows the empty track.
- **Hint bar:** "OK opens the show".

## Show page

A full-screen window like the film sheet, over the screen that opened it. Back closes it.

- **Top, inside the 32 margin:** the poster at the 24/7 tile size (116 by 164, `Tile` radius), then 32 (`Margin`) across, a column of: genre, year, seasons and rating in `Meta` ("Crime · 2019 · 3 seasons · Rated 7.4"); 4 (`HeroTitleAir`); the title in `ScreenTitle`, 1 line; 14 (`TileGap`); the plot in `Body`, `TextSecondary`, up to 3 lines.
- **Season chips:** 14 below, filter chips 40 tall, 8 apart, "Season 1", "Season 2"; the chosen one has the amber tick. Selection follows focus. It opens on the season you are up to.
- **Episodes:** 14 below, a list of rows the size of a Search result (46 tall, `Row` radius, `Surface`, `Focus` fill when focused), 5 apart (`SettingsRowGap`). Row text, 14 in from the left: "3  Episode title" in `TileTitle`, then the length in `Meta` ("52 min"). The episode you are up to adds "12 min in" (or "Up next") to that line and the resume bar 300 wide at the row's right, 14 in.
- **Focus:** lands on the episode you are up to, else the first episode. Up from the first episode goes to the chosen season chip.
- **Hint bar:** "OK plays · Back returns to Series" (or "to Search").

Budget at 540 tall: 32 + 164 + 14 + 40 + 14 + four rows 204 + hint 32 = 500.

## Sport tab

Ruben asked for it on 16 Nov 2026. A tab after What's on now: the next 14 days of fixtures from ESPN, in the TV's time zone, then the last 7 days of results and the ladders, each hidden until OK.

- **Top to bottom, inside the 32 margin:** top bar 48; the view chips (Coming up, Results, Ladders); the sport filter chips (40 tall, the chosen one with the amber tick, selection follows focus); then the list, scrolling down.
- **Day heading:** "Today", "Tomorrow", "Sat 22 Nov" in `Section`, 12 above (`GuideGap`).
- **Row:** 42 tall (`SportRow`) like Sport on What's on now, `Row` radius, no fill at rest, `Focus` fill and `OnFocus` text with scale 1.02 when focused. Title in `RowTitle`, league in `Meta` beside it, then the channel name in `Meta` after a "·" when the guide shows the game on a channel (a team game only; UFC, F1, golf and tennis never have one). Time at the right edge in `Meta`. LIVE in `LiveOnDark` (`LiveOnLight` focused).
- **Following:** a game the viewer follows says "Following" in `Meta` and `Accent` (`OnFocus` when focused) between the channel and the time, `TileGap` either side. Followed games come first on their day.
- **Bell:** a later game with a reminder set shows `ReminderBell` (16, `Accent` at rest, `OnFocus` focused) just left of the time, `TileGap` either side. Same bell as the guide.
- **Views (added 16 Nov, PR 3):** a row of three filter chips above the sport chips: "Coming up", "Results", "Ladders". Same chip as the sport chips (40 tall, `FilterGap` apart, amber tick on the chosen one, selection follows focus). Up from it goes to the tab; up from the sport chips goes to it. The sport chips filter all three views.
- **Results:** the last 7 days, newest day first ("Today", "Yesterday", "Sat 8 Nov" in `Section`, 12 above). **Ladders:** one section per table, headed by the league ("Premier League", or "NBA · Eastern Conference"); rows in position order, and a followed row does not move. On Results, followed names come first. Both show the "Following" label.
- **Score row:** the same 42 row as a fixture (`SportRow`, `Row` radius, `Focus` fill and scale 1.02 focused). Title in `RowTitle`, league in `Meta` beside it on Results. On Results, at the right edge "Hidden" in `Meta` and `Faint` (`OnFocus` focused). OK replaces it with the result ("North Town 3, South City 1") in `Meta` and `Text` (`OnFocus` focused). It stays shown until Porch closes; nothing is saved. A ladder row is never hidden and OK does nothing: a position column at the left, 44 wide (`LadderPositionWidth`), the number in `Meta` (`Muted`), then the name in `RowTitle`, then "Following" when followed, then the stats ("27 pts · 10 played") at the right edge in `Meta` and `Text`, in position order.
- **Hint bar on Results and Ladders:** Results: "OK shows the result" · "Back goes to the filters"; a shown row, and every ladder row, says only "Back goes to the filters". At the right "Results from ESPN · updated 10 min ago" (or Ladders).
- **No score or winner shows anywhere until OK on its own row. Ladders always show. Coming up never shows a score.**
- **Hint bar, left,** from the focused row: "OK plays" (on now or about to start), "OK sets a reminder", "OK removes the reminder", or "No channel in your guide for this game." (a team game only; an event row says nothing about OK). Then " · Back goes to the filters". At the right "Schedule from ESPN · updated 10 min ago" in `Hint`.

## Reminder card

A small card at the bottom right, over whatever is on screen, 360 wide, 32 (`Margin`) from the screen's edges. `Surface` fill, `Panel` radius, 24 (`PanelPad`) inside. It is its own window, so it takes the remote. The window's dim is set to none, so the picture or Home behind it keeps its colour.

- **Top:** the channel logo at 28 (`PanelBadge`) and the programme title in `Section`, up to 2 lines, 14 apart (`TileGap`).
- **Under it:** "Starts in 5 minutes on <channel>", or "Starting now on <channel>" under a minute, in `Meta`, up to 2 lines.
- **Pills, 14 apart, 14 below:** "Watch" takes focus, then "Not now". Back also dismisses. It dismisses itself after 60 seconds.

## Simple mode

Goal 3, 20 Nov 2026 ("Dad's TV"): Porch for a viewer who only wants his channels. Option B, "One list". Turned on in Settings with a 4-digit code; only that code turns it off. Existing tokens only.

- **Top bar everywhere:** only the Home and Full guide tabs.
- **Favourites list (Home):** top bar titled "Favourites"; "Your channels" in `Section`, then every favourite in channel order as the Live now row (42 tall, `ListGap` apart, the logo, programme, channel and "ends 9:30 pm", as Home's Your channels), scrolling, inside `Margin` with `FocusRoom` above and below. No preview pane, no See all. Up from the first row goes to the Home tab. Hint bar: the OK keycap, "plays   ·   Back leaves Porch".
- **No favourites:** "No favourites yet. Hold OK on a channel in the Full guide to add one." in `Body`, `TextSecondary`, then 14 (`TileGap`) and an "Open Full guide" pill, which takes focus.
- **Leave card:** "Leave Porch?" over the list, the reminder card's look centred on the screen: 360 wide (`ReminderCardWidth`), `Surface`, `Panel` radius, 24 inside (`PanelPad`). The title in `Section`, 6 (`ListGap`) then "The TV goes back to its home screen." in `Meta`, then 14 (`TileGap`) and the pills "Stay" (focus) and "Leave", 14 apart; 14 below them a plain pill, "Simple mode off". Back means Stay.
- **Code pad:** a full screen. Top bar titled "Simple mode", no tabs. Left column 330 wide (`SearchLeft`), the two columns centred on the height under the top bar: the step in `ScreenTitle` ("Choose a code", "Type it again", "Type the code"), 8 (`CardLineGap`), one line in `Body`, `TextSecondary`, 24 (`HelpIntroGap`), four code boxes 42 square (`SearchKey`), 6 apart (`SearchKeyGap`), `SurfaceRaised` fill, `Row` radius, a filled box shows "•" in `ScreenTitle`. 14 (`TileGap`) under them a mistake line in `Body`, `Text` ("Those didn't match. Choose a code again." or "That's not the code. Try again."). 50 across (`SearchGap`), the keypad: 42 keys (the Search key cap), 6 apart, 1 to 9 three across, then "0" and a 90-wide (`SearchWideKey`) "Delete". Focus starts on 1. The fourth digit submits.
- **Welcome card:** one full screen, no tabs, top bar titled "Welcome to Porch". Laid out like How Porch works (`HelpTop`, `HelpIntroGap`, three `HelpColumn` columns, `HelpTitleGap`, `HelpLineGap`, `HelpButtonGap`): "Three keys are all you need." in `Body`, `Muted`; columns "Up and Down" / "OK" / "Back" in `ScreenTitle` (the keys are what Dad reads from the couch) with their lines in `Body`, `TextSecondary`; a "Start watching" pill, which takes focus.

## Motion

Focus moves take 180 ms, ease out, on scale and fill only. Screens swap without a transition. Nothing animates on its own, except the player's overlay fading out, and everything is still once focus stops.

## Lessons

- A ring is not focus here. Settings and Search wore a dark fill with a ring and read as flat and unfinished beside Home's solid fill; anything that must show focus uses the `Focus` fill. A row of chips needs one fixed height, or an icon makes some taller than others.
- A screen that opens from another screen's key press can get focus before its pill is listening, so the pill is focused but draws no fill. Clear focus and ask again after a frame (simple mode's welcome card, goal 3).
