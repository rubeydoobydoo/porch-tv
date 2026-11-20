# Porch: design brief for Studio (Apple TV feel)

Written 29 Oct 2026. Ruben directs; Claude builds from the designs. Hand finished frames back as PNGs in `design/studio/` in the porch repo, or as a Notion page under Farabout Notes, with a short token list (colours, sizes, radii, focus scale) beside them.

## What Porch is

Porch is a personal IPTV player for Android TV, running on a Chromecast with Google TV plugged into a lounge-room TV. One person uses it, from the couch, with the small Google TV remote: Up, Down, Left, Right, OK, Back, and a mic button. No touch, no mouse, no keyboard. It plays live channels from a playlist the owner already has, shows a programme guide, and can rewind or play earlier shows (catch-up) where the provider allows it.

It is not a streaming service. There are no posters, no artwork and no channel logos: Porch draws a two-letter badge in a coloured square for each channel. There are about 50,000 channels and 280,000 guide entries in the real library, so every list is a slice of something big.

## The ask

Make it feel like the Apple TV home screen from across the room: large tiles that lift and brighten when focused, generous spacing, soft gradients behind rows, a calm frosted top bar, one bold title per screen, and the focused item always obvious at three metres. Apple's feel, not Apple's parts: no San Francisco font, no SF Symbols, no copied screens, no logos.

Function comes first. Every screen below has a job, and a design that looks good but slows down "what's on, play it" has failed.

## Hard rules

- Canvas 1920 x 1080. Everything readable from three metres: body text 24 px or larger, titles 40 px or larger.
- Dark only. Base colours today: background #0E1117, card #1B2130, focused card #243044, muted text #9AA6BA, faint text #6E7A8D, lines #313949, accent red #E23B3B. You may propose new values, as a list, but keep one dark family and keep red as the single accent, used sparingly. Red also means "live" in TV land, so do not use it for errors.
- Font is Inter, four weights (Regular, Medium, SemiBold, Bold). No other typeface.
- D-pad only. Every element is either focusable or decoration. Show the focused state on every frame: a visible focused tile, button or row. Focus must never be ambiguous.
- No channel logos, no posters, no stock photos, no AI-made people. Channel identity is the two-letter badge plus the name and number.
- No mascot, no illustrations of people.
- No 10-foot UI clichés from cable boxes: no neon grids, no glossy bevels, no scrolling tickers.
- Use placeholder channel names (for example "Harbour News", "Channel 204", "Sport Two"). Never real provider or channel names.

## Screens to design (in this order of importance)

### 1. Home
Job: within two seconds of opening, show what is on right now and let OK start it.
Content today: greeting "Hi Ruben" with the day; a row "What's on now" (programme cards: badge, channel name, programme title, progress bar across the show, favourites first); a row "Starting soon, next 30 min"; "Favourites"; "Recent" (channels you played); a nav line with Full guide, What's on now: all, Search, Settings.
Design questions for you: how a programme card looks with no artwork (badge-led? a soft colour wash per channel?); how the focused card lifts; how the top bar sits; what the loading state looks like while channels import (today it says "Loading your channels…" for minutes, and we are fixing that to show channels first then the guide filling in behind, so design a Home that is useful with channels but no guide yet).

### 2. Player and its overlay
Job: full-screen video with nothing on it. Press OK or Down and an overlay appears over the bottom third: channel badge and name, programme title, time left, a progress bar, "Next on this channel". Up and Down change channel. Left rewinds (catch-up). Right opens a side list of what else is on (favourites first, OK switches). An "Audio & subtitles" sheet lists tracks. Messages that can appear: "Reconnecting…", "The stream dropped. Press Retry to try again.", "This stream can't rewind.", "Starting catch-up…".
Design: the overlay, the right-hand "what else is on" panel, the audio sheet, and the three message states. Keep the overlay translucent so the picture stays visible.

### 3. Guide
Job: scan many channels by time and jump into one. Today it is a vertical list of channels, each a row of programme blocks on a time axis, with a filter line (All, Favourites, group names) and a "Jump to now" action. Past programmes with catch-up are playable; the focused programme shows "Starts 20:30" or "No catch-up on this channel".
Design: the grid with a focused cell, the now-line, the filter bar, a group with no channels ("No channels in this list.").

### 4. What's on now (the full version of Home's first row)
Shelves: "Live now", "Next hour", "Tonight", each showing five then a "more" tile. Groups selectable along the top. Hold OK to favourite.

### 5. Search
A typed search (on-screen keyboard, remote-friendly: letters in a grid, Space and Delete) and voice ("Hold mic and speak"). Results: "On now", "Later", then "Channels". Shows come first, channels last.

### 6. Settings and Setup
Settings: a list of sources (name, kind, channel count), Refresh, Add a source, Set up from your phone, Clean up channels, Guide offset (plus and minus minutes with a plain sentence explaining the effect), How Porch works. Setup: a form (Playlist URL or Xtream login: server, username, password; Guide URL; Name) that the D-pad lands on box by box, and a "Use your phone" option showing a web address to open on the same Wi-Fi. These can stay plain; they just need to match the family.

### 7. The one-page guide ("How Porch works")
Three short groups of lines explaining the remote, Home and the player, with one "Got it" button. Design it as a calm single page.

## What we will judge against

- From a photo of the TV at three metres, can you tell which item is focused and what will happen when OK is pressed?
- Does Home read as "what's on, press OK" in under two seconds?
- Does the overlay show channel, show, time left and next, without hiding the picture?
- Could every frame be built with rectangles, rounded corners, gradients, text and one badge per channel? (No bespoke imagery.)
- Is there anything a cable-box UI would do that this still does? Remove it.

## Deliverables

1. Three Home directions as single frames, each with a one-line idea. Ruben picks one.
2. For the chosen direction: Home, Player overlay (plus right panel and one message state), Guide, What's on now, Search, Settings, How Porch works. One frame each, focused state shown, 1920 x 1080 PNG.
3. A token list: background and surface colours, text colours, accent, tile radius, focus scale, focus glow or border, row gradient, type sizes for title, section, card title, card meta, body, hint.
4. Optional: one short note per screen on anything the remote needs that the frame cannot show (for example "Long press OK on a card toggles favourite").

Reference frames of today's screens (emulator, fake data, safe to look at) live in `review-shots/` in the porch repo: `4-home-fixed.png`, `6-guide.png`, `7-player.png`, `8-player-overlay-rewind.png`.
