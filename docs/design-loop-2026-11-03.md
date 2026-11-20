# Design loop, 3 Nov 2026: every screen on the Office Chromecast

Build on the device: main at 36078af (PRs 61 to 66), release, installed 3 Nov 3:26 pm. Screenshots are in `docs/shots-2026-11-03/` (not committed). The 24/7 shot is cropped above the grid because one filter chip may carry the provider's name.

Ruben's bar: a guest could use it without help, and would say "wow, app quality". Scores are 1 to 10 against that bar, from the couch. The TV draws 960 by 540 dp at 2x.

## Scores

| Screen | Shot | First impression | Hierarchy | Spacing | Contrast | Guest could use it | Brand fit | Wow |
|---|---|---|---|---|---|---|---|---|
| Home (with picture) | 01 | 8 | 8 | 7 | 9 | 8 | 9 | 8 |
| Home (no picture) | 01b | 6 | 7 | 6 | 9 | 8 | 8 | 6 |
| Full guide | 02 | 6 | 6 | 5 | 8 | 7 | 7 | 5 |
| What's on now | 03 | 7 | 7 | 7 | 9 | 8 | 8 | 7 |
| Now, Sport filter | 04 | 7 | 7 | 6 | 9 | 8 | 8 | 7 |
| Now, other copies panel | 04b | 6 | 6 | 7 | 7 | 7 | 6 | 5 |
| 24/7 | 05 | 9 | 8 | 8 | 9 | 8 | 9 | 9 |
| Search | 06 | 6 | 7 | 7 | 8 | 8 | 7 | 5 |
| Settings | 07 | 7 | 7 | 7 | 8 | 7 | 8 | 6 |
| Channels | 08 | 5 | 6 | 6 | 8 | 5 | 6 | 4 |
| Quick menu | 09 | 7 | 7 | 7 | 8 | 7 | 7 | 6 |
| Programme details | 10 | 6 | 8 | 6 | 8 | 8 | 7 | 6 |
| Player overlay | 11 | 8 | 8 | 8 | 9 | 8 | 8 | 8 |
| Reminder card | 12 | 8 | 8 | 8 | 7 | 8 | 8 | 7 |

## What I saw, screen by screen

- **Home.** Strong when the programme has a picture. Without one (about half the sport channels), the 293 by 165 block is a dark box with a small logo. Titles in the three columns cut short ("EFL Championship Hi...") while the bottom 170 px of the screen is empty. No sign on a row that a reminder is set.
- **Full guide.** The hero at the top repeats what the focused cell says and takes 300 px. With the Favourites filter on, four rows fill the top half and the bottom half is empty. The chip row is cut under the "Jump to now" pill ("Ki" showing). The red now-line runs over cell text. On first open the hero says "Nothing scheduled here" while programmes load (hero title, `GuideScreen.kt:622`; the only loaded flag means "channels arrived", not "programmes arrived").
- **What's on now.** Reads well. Chip row scrolls so "All" is half off the left edge once Sport is chosen. Raw provider text leaks: "Prime| CBS Sports Golazo Network", "Edge Of Alaska (?)". "395 shows · 47 filler hidden" is internal language. The other-copies panel dims the screen with a translucent scrim (DESIGN.md says solid fills only), turns the focused row grey, and titles itself with the raw programme name ("LIVE: SportsCenter - Tuesday 3 November 2026") and copies like "Ch 16655".
- **24/7.** The best screen. Posters, focus outline, name tiles for missing posters all work. Chip names are raw groups ("Movies/Actors", "Movies & Series").
- **Search.** The keyboard is fine; the right half says only "Type a show or channel." A guest sees nothing to pick from. The "Speak" pill is grey.
- **Settings.** Two columns work. The list clips at the top ("Actions" cut under the top bar when scrolled) and at the bottom (a row cut by the screen edge). "Xtream login" and "Sources" are provider words a guest will not know, but a guest rarely goes here.
- **Channels.** The only screen without the top bar. "v1 · Channels" sits bottom right like debug text. The list clips under the hint bar ("AZ 16"). Two-letter codes ("AFG", "AFR", "AL", "AM") and PPV group names look imported, not finished.
- **Quick menu.** Clear enough, but "Hide this group" never names the group or says how many channels go. The preview pane's logo turns grey behind the dim, which reads as broken.
- **Programme details.** Text column is good. Without a picture, a 400 by 225 dark block with a logo sits in a mostly empty left half. Synopses from the guide file start with the date.
- **Reminder card.** Fired at 5:55:20 for a 6:00 start, bottom right, logo, title, "Starts in 5 minutes on Bein Sports 2", Watch focused. Clear and on-model. The whole screen dims behind it (translucent layer), so the ESPN logo in the preview greys out, the same grey-out as the quick menu. Watch itself is the 8 s path (item 3 in the ranking).
- **Player overlay.** Clean and readable: LIVE chip, logo, number, title, times, progress bar, next programme, one pill, hint row.

## Outside eyes (Codex, same screenshots, no design direction given)

Codex's answer is in the ux-pack folder (`docs/ux-pack-2026-11-03/`). Its scores ran 3 to 7 for "guest could use it" and 4 to 6 for "polished store app". I checked each lead against the shots:

- REAL: "Hide this group" has no name and no count. "+3 channels" and three separate "NBA Basketball" rows are not explained. Titles truncate while space sits empty. Raw provider words reach the screen. Artwork fallback varies sharply (picture vs logo in a box). Synopsis starts with the date.
- NOT REAL: "no Mic button on the remote" (the Chromecast remote has one). "Three destinations answer the same question" is a product call Ruben already made (Home, guide, now are different jobs).
- LEAD, Ruben's call: rename "Your channels" to the rule that fills it ("Favourites and recent"). Put "Add to favourites" on the details sheet so no long press is needed.

The pack (zip, prompt and Codex answer) is ready for ChatGPT, Gemini and Grok if Ruben wants three more opinions.
