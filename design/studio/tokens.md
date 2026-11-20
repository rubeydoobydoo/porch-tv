# Porch tokens, direction A (Spotlight)

Canvas 1920x1080, outer margin 80 px. Dark only. Inter only (400/500/600/700), tabular numerals.

## Colour
| Token | Value | Use |
|---|---|---|
| bg | #0E1117 | screen |
| surface | #1B2130 | tiles, rows, buttons, fields |
| surface-focused | #243044 | focused tile/row/field |
| surface-past | #161B26 | guide cells that have ended |
| line | #313949 | "more" tile outline, dividers |
| text | #F2F4F8 | titles, primary |
| text-secondary | #C9D1DE | section labels, meta on focus |
| text-muted | #9AA6BA | meta (7.7:1 on bg) |
| text-faint | #7D899C | hints (changed from #6E7A8D, which was 4.35:1) |
| accent-live | #E23B3B | LIVE chip, live progress fill, guide now-line. Nothing else. Never errors. |
| top bar | rgba(14,17,23,.55) + 24 px blur | frosted bar |
| overlay scrim | bg at 0 → 78% → 90% over the bottom 520 px | player overlay |
| side panel | rgba(14,17,23,.86) + 24 px blur | What else is on, Audio |

Badge colours: HN #3A6FD8, S2 #2E9A5B, NG #B3761A (darkened from #C7851F for 3:1), FM #D2602E, C2 #2A8C9A, VR #B9507A, DC #A9773A, WF #3E8FC4, MR #6C7FA8. Any generated badge colour must give at least 3:1 with white bold letters.

## Shape
Tile radius 18 · button/field 14 · guide cell 12 · panel/card 24 · badge 25% of its size.

## Focus (one rule per element type)
- Tiles, rows, cells, fields: 4 px white ring + surface-focused fill + shadow 0 28 56 rgba(0,0,0,.55); scale 1.08 tiles (1.10 Home row), 1.02 to 1.04 rows and cells.
- Buttons and keyboard keys: white fill with #0E1117 text, scale 1.06 (keys 1.10).
- Current tab or filter (selected, not focused): white text / surface-focused fill, no ring.
- Tile wash: channel colour 26% → surface at 155°; focused 50% → surface-focused.

## Gradients
Hero wash: linear 115°, focused channel colour at 28–42% → transparent by 60%, faded to bg by 72% of height. It follows focus on Home, Guide and What's on now.

## Type (px / weight)
Screen title 44/700 (−2%) · Home hero title 80/700 · Player title 64/700 · Panel/empty title 40/700 · Section 30/600 · Card title 26–28/600 · Card meta 24/500 · Body 26–28/400–500 · Hint 24/500 faint. Nothing under 24 px except the 22 px LIVE chip and key-cap labels (bold, caps).

## Spacing
4 px unit. Tile gap 26–36, label to row 50, list gap 10–14, row-to-row 50+.

## Motion
Focus lift 150 ms ease-out (scale + shadow). Overlay fades in 200 ms; side panels slide from the right in 200 ms. With Animations Off: no scaling animation; ring and fill switch instantly.
