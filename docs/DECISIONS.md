# Decisions

Small calls made without Ruben, with the reason. Newest first.

## 2 Nov 2026: Manrope (Ruben)

- **Manrope for headings, downloaded from Google Fonts, with Ruben's yes.** Inter stays for everything else.
- **`PorchTypeTest` changed, with Ruben's yes.** It pinned Inter on every style, and listed names the new tokens removed. It now checks headings use the display face and the rest use Inter.

## 2 Nov 2026: Channel logos (my calls)

- **Coil 2.7.0 (`coil-compose`), the one new dependency.** It builds with the project's Compose BOM. OkHttp and Okio come with it. Debug APK grew by about 1.0 MB (13.85 to 14.85 MB), unminified.
- **Letter badge always underneath.** The logo plate is drawn only once the image has loaded, so a slow or dead logo never leaves a hole.
- **One loader, built by the Application.** 20 MB memory cache, 100 MB disk cache in `cacheDir/logos`, no crossfade, cache headers ignored, 5 s connect and read timeouts, no retries, 4 requests at once (OkHttp dispatcher). No logger is set, so no address is ever written to the log.
- **Decoded at the drawn size.** The pixel size comes from the badge's dp size minus padding at the device density, so a 26 dp badge decodes at roughly 44 px on the TV, never the full image. Hardware bitmaps left on; nothing needed them off.
- **Remembered per address and size.** The request is keyed on the address, so focus changes and recomposition don't restart it. A row that scrolls out of composition drops its painter, which cancels a request still running.
- **Address filter runs once, at read.** `LogoAddress.clean` in `Channel` mapping: only http or https with something after the scheme, else null (letter badge). Cleartext is already allowed by the network security config, same as streams.
- **Channel list query now reads `logoUrl`.** No schema change. It is the only long text column in the fast query. Not measured on the Chromecast: roughly 49,000 short strings, a few MB of heap. If the first-open time or memory shows a cost, the alternative is to load the address per visible row by id.
- **Known gap:** a logo that fails is not remembered, so scrolling back to a dead one tries once more. One short attempt, 5 s at most.

## 2 Nov 2026: Home and What's on now, review fixes (my calls)

- **A channel is listed once per row** (`LiveRows.fold` drops repeats), and the zap lists sent to the player are de-duplicated. The guide can repeat a programme, and the Watch on panel crashed on the repeat.
- **Home has no Right panel.** Right always moves to the next column, even on a folded row, and OK plays the row's first channel. The panel stays on What's on now, so the other channels are reached through the Sport list behind See all.
- **Home rows are keyed by channel**, so focus and the return from the player follow the channel when Recent reorders.
- **A "See all" chip is applied once per visit** (saved with the route). The list is held until the view model answers that chip, with a 1.5 s limit so it can never hang.
- **Focus recovery** (`RowFocus`): if the row focus was on is gone, focus goes to the nearest row in the same list, else the first row, else the selected chip (Now) or tab (Home). With no rows at all it starts on the chip or tab. Start-up focus never takes focus from the top bar or chips.
- **Favourites "See all" counts only rows What's on now will show** (channels with a real programme). Starting soon sends the All chip.
- **Sport costs less:** the chip must rest 200 ms before queries restart; the 3-hour window refreshes every 5 minutes; and the start-window query returns no descriptions (nothing on these screens shows them; no schema change).
- **Loading line:** while a refresh runs and there are no channels or nothing is on now, Home says "Loading your channels and guide…" in place of the out-of-date line.
- **Dead code removed:** ProgrammeCard, QuietChannelCard, MoreTile, tileWash, four unused sizes, and `NowUi.now` and `offsets` (a changing `now` made every tick's state distinct).
- **Known gap, left on purpose:** SportMatch accepts looser titles ("Rocky V - Extended"). Revisit when real guide data shows it matters.

## 2 Nov 2026: Home as four lists (my calls)

- **Home is a 2 by 2 grid of lists, no hero.** Live sport and Recent on the left, favourites on now and Starting soon on the right. The hero, tile shelves, category shelves and the "No channels found" line are gone. `heroWash` stays because the Full guide still uses it. The old tile shelves in `HomeTiles.kt` are deleted; `channelColor` stays.
- **Home rows are 34 dp, not 42.** A heading, four rows and "See all" must fit a quarter of 540 dp, with the out-of-date line showing. This is a measured budget, not yet seen on the TV.
- **The channel name sits after the title only if all of it fits** (a small custom layout), and is left out when the title already is the channel's name. The title is never cut to make room.
- **Reading order decides first focus:** Live sport, favourites, Recent, Starting soon. Up, Down, Left and Right use Compose's normal geometry; there are no focus traps because every list stays in place even when empty.
- **Live sport lists matches only** (not other programmes on sport channels), favourites first. See all opens What's on now on the Sport chip.
- **Recent holds the last 8 played channels**, same as the old shelf. Its See all opens the Full guide.
- **Starting soon is one programme per channel**, so a channel with short shows cannot fill the list. Filler is dropped before the first one is picked. Its See all opens What's on now, keeping the chip the viewer last chose.
- **A favourite or recent channel whose show is filler, or that has no guide data, shows its channel name with no time.**
- **"Guide out of date" is not shown while a refresh is running**, since the guide is half-loaded then. It counts the viewer's own channels that are matched to the guide.
- **`Route.Now` takes an optional chip.** Null leaves the viewer's last choice; "See all" sets one. `CategoryScreen`, `Route.Category` and the `only`/`group` options on `NowViewModel` are removed; nothing else used them.

## 2 Nov 2026: Live now rows, after seeing them

- **One line per row, 42 dp.** The first build was 76 dp with two lines, and only 4.5 rows fit. The mock-up's sizes were in pixels at 1920, and the TV draws at 2x. Now 7 or more fit.
- **An end time within 5 minutes of the common one stays blank.** Real guides drift a minute or two, and "ends 8:01" beside "most shows end 8:00" is noise.
- **The chip row scrolls.** With icons, the last chip ran off the edge.
- **Known gap (Codex):** a channel whose first show in the next hour is filler drops out of Next hour, even if a real show follows in that hour. It shows once that filler has passed. Left for now, since the fix sits in shelf code with existing tests.

## 2 Nov 2026: Sport list (my calls)

- **The Sport chip is the Sport list.** No new tab or route. Sections are Live now and Next 3 hours; Tonight is not shown.
- **Next 3 hours asks the database for three hours only while the Sport chip is on.** Other chips still ask for one. It lists every programme on each sport channel, not just the next one, because a channel's next show is often not its next match.
- **A folded row is a match if any of its channels reads as Sport.** Excluded words are whole words, so "reviews" is not caught by "review".
- **League check is a prefix on whole words.** "UFC 300" counts, "UFCs of the world" doesn't. A "LIVE:" prefix on a title is not stripped (not in the brief), so such titles only match through "X v Y".
- **"Nothing on for any channel" means any sport channel.** The sport list is all this screen knows. If none has anything on, the usual empty line shows; if they do but nothing is a match or non-filler, "No sport in the guide right now."
- **Sport rows get a wider time slot (150 dp)** so "LIVE" and "ends 9:30 pm" fit; the other chips keep 92 dp.

## 2 Nov 2026: Live now as rows (my calls)

- **Filler is dropped from Next hour and Tonight too**, with the same rule, but only the Live section counts it in "n filler hidden". The brief said "left out of this screen".
- **Rule 3 needs no extra code.** All filler is already left out of Live, so a channel whose filler is about to give way to a real show is simply not listed now. It still counts as hidden.
- **The commonest end time is judged to the minute**, and the time shown is a real row's end, so a few seconds of drift in the guide do not split the vote.
- **Right opens the panel only on a folded row.** On a single-channel row Right does nothing, as before.
- **The "Watch on" panel is a Dialog window with a solid fill**, like the quick menu: the row keeps its focus underneath, and the Chromecast gets no translucent layer.
- **Starting focus yields to the player return.** If the viewer is coming back from the player, the row they left takes focus (`landsOnReturn`), not the first row.
- **The full-list screen (`CategoryScreen`) stays** because Home's group "more" tile still opens it. Its Next hour and Tonight paths are now unreachable from What's on now; trimming them is a follow-up.

## 2 Nov 2026: icons (Ruben asked for Canva)

- **Canva set the look; the app uses vectors drawn to match.** The Canva connector only hands back 200 px previews of its images (the 1264 px originals stay in Canva). Vectors stay crisp at any size, take the focus colour, and add no dependency.
- **The sport icon is an oval ball.** Canva's set had one, and it reads as footy in Australia.
- **New launcher icon and banner:** an amber awning over a play button.
## 2 Nov 2026: backup guide final review

- **A new guide offset shifts the stored backup rows,** so their real times hold. They're stored with the offset taken off.
- **Known gaps left:** no size cap on a backup download (same as the provider's); the progress count can include rows from a file that then fails; `forMatching()` still builds its list on every refresh with no backups, which costs milliseconds.

## 2 Nov 2026: backup guide

- **Ruben's call: a favourite in a Hidden group keeps showing.** Confirmed, so the note under "Channels final review" stands.
- **Ruben's call: build a backup guide.** Outside XMLTV files, with addresses typed on the phone page and kept on the TV only. Measured: about 2,600 of 49,283 channels on their own, against 10,587 for the provider's guide when it works. So it's a fallback, not a swap.
- **Backup rows go in under the source's live generation.** That's generation 0 for a source with no good guide yet, and the queries join on it, so the rows show with no query change.
- **The guide offset is taken off each backup row as it's stored.** Every screen adds the source's offset to stored times, and backup times are already real UTC, so they land on the right time with no query changed. If the viewer changes the offset, backup rows sit wrong until the next refresh. That's the least bad option.
- **Matches are written in place, not through `saveChannels`.** `saveChannels` deletes and re-adds every channel, which would lose a favourite set during the import. The provider's late match already works in place for the same reason.
- **New backup rows wait out of sight, then swap in one transaction with their matches.** They sit in a hidden generation (-1), not under a temporary key as first suggested. A temporary key in the live generation would still be read by What's on and by search, whose limit of 80 it could crowd out. No query joins generation -1, and the existing clean-up removes anything a crash leaves. So a dead address, an empty file or a crash leaves the working backup as it was.
- **A backup match goes only after a clean run:** every file loaded and at least one channel matched. A failed file might still hold that channel, and we don't record which file gave which channel, so one failed file keeps all the old matches nothing renewed. They go at the next clean run, and they run out on their own meanwhile.
- **A provider success carries backup rows into the new generation,** and a channel the provider doesn't match stays on its backup while the provider's guide comes in. A channel the provider does match takes that match, and rows nothing uses go at the swap.
- **Backup keys start `porch-backup:`** (`BackupGuide.KEY_PREFIX`). A provider's id can be almost anything, including `x:`, and clean-up must never touch one. Devices that ran the first build keep their `x:` rows until a provider guide works or they run out.
- **A file that fails part way takes back what it added,** so the next file can claim those ids. A file with no programmes counts as failed.
- **Speed, after a 9-minute Chromecast run.** A programme for an unwanted channel is now skipped without being read, and the XML reader takes chars from its own block. Each channel's names are cleaned once across files, and the name regex is compiled once. Backup inserts commit per 2,000 wanted rows, because each commit wakes every screen's programme query. The step holds three columns per channel, not whole rows. The two reader changes are shared with the provider's guide, and the existing parser tests cover them. The "expected" count stays: one index scan per refresh, not what's slow.
- **Progress says "Backup guide 2 of 3: 62,053 programmes for your channels",** not "Guide filling in", because those rows aren't on screen until the swap and most of a file is skipped.
- **A phone POST with no addresses field changes nothing.** Only an empty field clears the list.
- **A channel listed in a file but with no programmes isn't matched,** so it doesn't show as covered with nothing on it, and it isn't counted in "channels matched".
- **Remove all cleans up once.** A small per-source flag says backup rows may exist, guarded so two refreshes can't lose it (none can run together today, as imports share one thread). With no addresses and no flag, a refresh runs no new queries.
- **A source with no guide address also uses the backups,** treated as a failed provider guide. Otherwise a playlist-only source couldn't use the feature.
- **Remove all asks twice,** like removing a source, because saved addresses can't be read back. **The last result also says how many addresses failed,** because a quiet partial failure would look like a full load.
- **The server on this screen offers only the guides page, at /guides and any other path.** A source can't be added from it.
- **Known gaps:** the last result shows the source refreshed last, not a total. A channel moved off a run-out provider key loses that key's past programmes until the provider's guide works. A file that lists channels after their programmes loses those programmes. No size cap on a downloaded file, same as the provider's guide.

## 2 Nov 2026: Channels final review

- **Older TVs with picks but no reserved row read the way Home does:** if every pick has gone, it counts as no picks, on the screen and in the switch.
- **A language filter that rejected every old pick keeps what the filter allowed.** Home showed that before; removing the picks would have emptied it.
- **Not changed, on purpose:** a favourite or played channel in a Hidden group still shows. That's the existing "favourites always show" rule, and a Codex finding asked to remove it. It also shows after an old language filter is converted.
- **A channel already moved to another group stays there,** with no way to undo it now the button is gone. Ruben doesn't use moves (2 Nov), and the emulator's copy of his data has none.

## 2 Nov 2026: Channels screen and starting pick

Fix round 1 (two reviews agreed: "no picks" meant "show everything", so losing the last pick, or every pick going stale on a refresh, flooded the source). This replaces the first version of the entry below, including its "hides everything else" line, which was wrong: hiding the last group now reads "Keeping 0" and writes no hidden rows.

- **A reserved group name marks a restricted source.** `GroupChoice.RESTRICTED` (starts with U+0001) is saved in chosen_groups. A source with any chosen row is restricted: only picked groups show, even with none left. `GroupChoice.live` keeps the row; `filter` and `inChosenGroup` are unchanged, since no channel has that group. Readers (Home, Guide, What's on now) only pass the set on to those functions, and the Channels screen is the only list, so the row is never shown or counted. Sources with no rows behave as before.
- **The Channels screen never writes hidden rows.** Hiding from "everything" turns the source into a pick list (every showing group except the target, plus the reserved row). Hiding when restricted removes the pick. Showing removes the hidden row and, when restricted, adds the pick. Old hidden rows still count.
- **Any change on a source with picks but no reserved row adds the row.** That upgrades an older install the first time the viewer touches it, so it also stops flooding.
- **The change is worked out inside one transaction from the saved rows** (`LibraryRepository.setGroupsShowing`). The screen sends the source, the groups and, for one group, the state its row asks for; a whole language sends "switch" and the saved rows decide. Quick repeated presses cannot flip-flop.
- **The starting pick always writes the reserved row, even if no group is kept** (that source then shows nothing until the viewer switches something on). Groups of only dividers are left out.
- **Starting pick works on the label before `|` or `:`** (letters, digits, / or +, 2 to 8 long): AU, NZ, UK, US, EN, GB, the TV's country and content labels (VOD, PPV, REPLAY, CATCHUP, SERIES, MOVIES, SPORT, SPORTS, NEWS, KIDS, MUSIC, DOCS, HD, FHD, UHD, 4K, SD, SKY, BBC) are kept; any other label is dropped (ASIA, EXYU, AR, FR, DE). A "24/7" label is kept like a 24/7 start. With no label, a 24/7 start is kept, a language word ("Hindi Movies") is judged by its tag, and anything else is kept. Radio is dropped first. Known gap: the existing word map sends `ukrainian` to UK, so "Ukrainian ..." is kept; not changed here.
- **Old language filter becomes picks.** Unrestricted: picks are the groups the filter allowed, plus the reserved row. Restricted: picks the filter rejects are dropped. Then the filter is cleared. Two side effects: a favourite or played channel in a rejected group now shows (picks never hide those), and new provider groups stay hidden until switched on.
- **Channels screen details.** The counts are tagged with their source and nothing shows until they match. The language in focus changes at once, the group list swaps after 120 ms of rest, and Right lands on the row last in focus or the first (`focusRestorer`). The "New groups" hint shows only when the source is restricted. The open language row carries a trailing arrow because the tick fill hides the open fill.

## 2 Nov 2026: Channels screen, first version

- **The first-save check runs inside the channel-save transaction, before the old channels are deleted.** So the early save and the fallback save in one refresh can't both pick, and a source with channels is never picked for. Country comes from `Locale.getDefault().country`, passed into `LibraryRepository` as a function.
- **A group row is one Pill reading "Showing  name  (count)".** Status leads so a long name can't cut it off. Names are shown as the provider wrote them, so "EN:" and "EN |" twins stay apart.
- **Left from the group list goes back to the language row in focus**, and Right from a language goes to the first group, rather than the nearest row, so moving sideways never changes the language.
- **"Settings > Clean up channels" is still the wording in the Hide this group message** (`QuickMenu`). `QuickMenuTest` asserts that text, so it stays until Ruben OKs a test change.

## 2 Nov 2026: what chosen looks like, and what Ruben answered

- **Selected look A: a tick and a blue fill.** Ruben left the pick open and it's cheap to change, so I took my recommendation. The tick is what matters: focus is white in both states, so colour alone can't say chosen. Amber (C) was out because it means favourite.
- **Clean up says the state, not the action.** "Showing" or "Hidden", and OK switches it. The old "Show" and "Hide" read the same as a state.
- **The group whose channels are open gets a lighter fill and no tick.** Nothing was chosen, so a tick would lie.
- **Logos: yes, after Home is rebuilt.** Needs an image library, which Ruben OKed (personal app, not published). Measure on the Chromecast.
- **On-demand: yes, after this rebuild.**
- **Moving a single channel goes.** Ruben doesn't use it. The button leaves with the Channels screen; the stored column stays, so nothing needs migrating.
- **"I kinda want it out of the box to work."** Porch should pick a sensible start by itself, not ask for setup. Plan for the Channels step changes to match.
- **The starting pick (Ruben):** AU, NZ, UK and US on; 24/7, replay and on-demand groups stay in. Radio stays out, my call: it isn't TV.

## 2 Nov 2026: couch rebuild picks (Ruben)

- **Yes to:** Home without the hero and six largest groups; Live now as rows with end times and no filler; Sport by match; one Channels screen; folding HD, SD and backup copies, after the rest. Plan: `docs/plan-couch-rebuild.md`.
- **Squares only when they hold a picture.** A letter badge doesn't count. Otherwise use rows. 48,976 of 49,299 channels have a logo address, so logos are possible, but drawing them needs an image library, which is a new dependency and needs Ruben's OK.
- **Multi-view is off the roadmap.** The plan allows 1 connection.
- **Moving one channel to another group stays for now.** Ruben's "4 yes" did not settle it, and moves are saved data, so it isn't dropped without asking.
- **Recording stays on the roadmap, not started.** Decision 6 only named multi-view.

## 1 Nov 2026: every channel in the guide

- **A picked group shows every channel in it, and every pickable group gets a chip.** The provider ships no guide for its 24/7 loop channels, so the switch hid nearly all of them. This narrows the 30 Oct "no-guide switch" decision: "Show channels without a guide" now only changes the All and Favourites lists. The rule is `GuideCoverage.forFilter`.
- **A channel with no guide match shows one bar all day, "<title> · on all day".** The row is never blank and OK still plays it live. It looks like a normal programme bar, not live-red. The hero reads the title and "On all day · OK plays" (`AllDayText`).

## 1 Nov 2026: daily-driver fixes

- **The guide's All chip now keeps to My groups (Ruben, from the main TV: "shows groups I didn't pick").** This reverses the 30 Oct "All shows the rest". Favourites and recently played stay exempt. To see everything, clear My groups or use Search.
- **Back from the guide grid goes to the current chip and keeps your row.** Up only walks one row at a time, which is no use around channel 50,000. Pressing OK on that chip goes to the top of the list. A second Back leaves the guide.
- **Back from the player keeps your place (Ruben: "loses my place").** Screens keep their scroll while they wait in the stack, and the tile you left takes focus again, for the tile on Home, What's on now and the full lists. If that tile has gone (a refresh, a re-sort), Home falls back to its first tile. Search keeps its scroll only.
- **Holding OK on a channel opens one shared quick menu, everywhere.** Home, What's on now, Search, the channel lists, the Guide and the Player all use it. A hold used to favourite silently (and pause in the player), so a mis-hold changed things with no sign. The rows are chosen by `QuickMenu.rows`; the menu is a Compose `Dialog` (a separate window, so the screen below keeps its focus), owned by PorchApp.
- **The hold's own key-up cannot pick the first row.** The menu swallows every OK event until it sees a fresh press (`OkHoldGuard`). Without it, letting go of the hold would pick "Add to Favourites".
- **The menu reads the channel afresh when it opens.** Search results and other built lists can be older than the last favourite change, and would show "Add" for a channel that is already a favourite.
- **"Hide this group" is in the menu.** Settings > Clean up channels lists hidden groups with a Show button, so the hide can be undone there, and the message after it says so. It hides the channel's effective group (the viewer's move, otherwise the playlist's).
- **Messages are a shared line at the bottom, 2 seconds for favourites and 4 for the hide line.** The hide line is longer, so it stays longer. The Guide's own message line is kept for its other messages.
- **The favourite star is amber (`PorchColors.Favourite`), a text glyph.** Red stays live-only. The Guide's old "Fav" word is replaced by the star.
- **On What's on now, a hold on a favourite no longer removes it straight away.** The old special case (wait for key-up, then move focus) is gone: the menu's "Remove from Favourites" does it. After removing from the Favourites chip, focus may need one arrow press to land again.
- **After favouriting on Home, the focused tile is scrolled back fully on screen** with the page margin as room (`bringIntoView`, two frames after the change, so the reorder has settled).
- **Cleaned channel names are stored at import, not worked out at every start.** Cleaning 49,279 names took 4.3 s on the Chromecast before Home could draw, and the result only changes when the raw name does. Old rows have no stored name (`divider` is null) and are cleaned at read until the next refresh rewrites them, so nothing looks different. Database version 4, an AutoMigration.
- **The TV runs the release build, signed with the local debug key.** On the Chromecast, Home took 37.5 s from a cold start on the debug build and 15.5 s on release, with no code change. Porch is sideloaded for one person, so no store key is needed. R8 shrinking stays off for now.
- **The full guide opens on All when no channel shown is a favourite.** Opening on an empty Favourites page read as broken. A Favourites pick made by the viewer is left alone, so removing the last favourite does not move them. The rule is `GuideFilterRule`.
- **A cold start opens straight on the last channel played, with Home under it.** Porch is a daily driver and a TV comes on to a picture. It takes the most recent `lastPlayedAt`; Up and Down zap through that channel's group, and Back goes to Home. No setting, by design. Nothing ever played, or no sources, starts as before. Coming back to a running app changes nothing.
## 1 Nov 2026: guide refresh cut off on the Chromecast

- **The guide is saved whole to a cache file, then parsed from the file.** On the Chromecast a refresh failed after about 8 minutes with "unexpected end of stream", a bit under halfway through the programmes. The same guide (about 94 MB) downloads on the Mac in 14 seconds, so the provider was cutting a connection Porch held open while it slowly stored programmes. The file is deleted as soon as it is open for reading.
- **One retry for a cut-off download, none for a refused login.** A second cut-off says "The guide download stopped part way. Try again later." instead of the raw error.

## 30 Oct 2026: Settings and Setup

- **For an Xtream login the guide address is optional; Porch uses the panel's own guide (`xmltv.php`).** The Studio frame shows it optional and every Xtream panel serves one. A playlist (M3U) still needs a guide address.
- **Phone setup stays opt-in.** The page has no password, so it starts only from "Start phone setup" or Settings' "Set up from your phone", and stops when Setup closes. The separate phone screen is folded into Setup.
- **The guide offset row moves every source by the same step and reads "Varies" when sources differ.** One row, as the frame shows; per-source offsets can come later if anyone runs several providers.

## 30 Oct 2026: What's on now

- **Chips choose what, shelves show when.** The chip row picks All, Favourites or a kind; the three shelves are Live now, Next hour and Tonight. One screen answers "what's on" without a filter to set first.
- **Live now is favourites first; Next hour and Tonight run by start time.** A "when" shelf out of time order reads as broken; favourites only break ties.
- **Each later shelf shows a channel's earliest start in its window,** so Tonight is not just the late-night tail, and drops shows that have already started.
- **What's on now lists programmes, so a channel with no guide data never appears here,** even with "Show channels without a guide" on. Those channels show in the guide and on Home. (Codex flagged it; kept on purpose.)

## 30 Oct 2026: one shared channel list

- **All screens share one channel list instead of each running its own query and name cleaning.** With about 49,000 channels the query plus cleaning costs about 3.5 s; four screens doing it each was most of the guide's 10-second first load (now under a second) and half of Home's.
- **Accepted:** a screen returning after 30 s idle first shows the last list it saw, then the fresh one a few seconds later; re-querying each time would cost those 3.5 s on every visit. The player keeps the list live while it is open, so its "what else is on" picks never offer a removed channel.
- **Not met:** Home's tiles still take 5 to 6 s from a cold launch on the emulator (target 3 s). Storing the cleaned names and kinds at import is the next step (plan item 4e).

## 30 Oct 2026: My groups

- **Chosen groups live in their own table, not as the inverse of hidden groups.** Hiding is "never show this"; choosing is "show me these by default, All shows the rest". Keeping them apart means Clear all cannot unhide, and the schema says what each row means. Room version 3 with an auto migration; the upgrade was checked over real data on the emulator.
- **The choice is per source, and favourites and recently played are exempt.** Picking groups on one source says nothing about another, so a source with no picks shows everything. A chosen group that later disappears (hidden, renamed, filtered) is ignored rather than left blanking the screen. You favourited or played it; it shows.
- **A channel with no guide match is hidden until "Show channels without a guide" is on.** The guide and What's on now have nothing to say about it; Home's "Your channels" mode still lists it while a guide is loading. A source with no guide URL at all is never hidden this way, and the guide's empty state names the switch.

## 29 Oct 2026: player keys on a remote with no play/pause

- **A short OK shows the overlay and then opens Audio & subtitles; a hold opens the quick menu (see 1 Nov 2026: daily-driver fixes).** The Chromecast remote has no play/pause, menu or info key, so every player action has to live on the D-pad. Menu and Info still open the sheet on remotes that have them. Hold OK used to pause and resume directly; Pause and Play are now the first row of the menu.
- **The overlay hides after 5 seconds, never while paused.**

## 29 Oct 2026: fast import

- **Channels are committed as soon as the guide's channel list is read, before the programmes stream.** Measured on the Chromecast: a 25-minute import left Home empty the whole time on first setup and on "Loading your channels…" for minutes after a relaunch. Now Home has channels within seconds and the guide fills in behind under a new generation, so nothing half-done is ever visible.
- **One import at a time, on one background-priority thread.** Two guide imports side by side would double the CPU load that made the box lag. A second source waits and says so. Codex and the reviewer flagged the serialisation; accepted on purpose for a TV box.
- **Home throttles rather than debounces while a refresh runs.** A debounce waits for a quiet gap, and an import has none, so the first version of this change could have left Home stale for the whole stream. Throttle-latest emits at once, then at most twice a second.
- **The on-now and soon queries carry ±12-hour bounds next to the exact offset terms.** Offsets are clamped to ±720 minutes, so the bounds are exact, and the (sourceId, generation, startUtc) index can bound the scan from above. No schema change.
- **Known gap:** between the channel commit and the generation flip, channels are matched to the new guide's ids while the old generation is still visible; a provider that changes its ids shows no programmes for that window.

## 29 Oct 2026: TV first, then one core, then phone

- **The Android TV app is finished to the Studio frames before the core extraction and the phone layout.** Ruben's call: nail the TV app first. The TV pass touches `ui/` only and the extraction touches `parse/`, `domain/` and `data/`, so the order costs nothing. `PLAN.md` holds the whole plan; `docs/plan-studio-ui.md` the TV pass.
- **Storage stays Room, as Room KMP.** Same annotations, same schema files, same `porch.db` on the Chromecast; SQLDelight would mean rewriting every data method and converting the existing database. Reasoning in `PLAN.md`.
- **Parser fixtures from iptv-org are sanitised.** Every host becomes `example.com`, a test enforces it, so the no-real-URL rule holds.
- **Tabs swap the screen; they do not stack.** Choosing a top-bar tab replaces the current screen, so Back from any tab goes to Home and Back from Home leaves the app.
- **"Animations off" follows Android's own remove-animations switch.** Porch gets no setting of its own.

## 28 Oct 2026: headings read from the couch

- **Text with no colour of its own is white.** The TV toolkit's default content colour is black, so every heading that set no colour ("Hi Ruben", "What's on now", screen titles, group names) was near-invisible on the dark background, on the emulator and on Ruben's Chromecast. One line in `PorchTheme` provides white as the default; nothing that already passes a colour changes.

## 26 Oct 2026: a one-page guide

- **One static page, shown once, rather than a step-by-step walkthrough.** A walkthrough fights the D-pad and costs several times more to build and keep right. The page also lives in Settings as "How Porch works".
- **The seen flag lives in SharedPreferences, not Room.** No schema change and no migration for one yes/no value.

## 26 Oct 2026: no destructive migration

- **The wipe-on-upgrade fallback is gone.** A forgotten migration used to delete every source, favourite and hidden group without a word. Now the app crashes at its first database read, which is loud, and the data file is left untouched. `SchemaGuardTest` catches a version with no migration before that; it cannot tell whether the schema JSON was committed, so that stays a written rule.
- **Downgrades still wipe.** Installing an older build over a newer one is a developer-only situation, and the older build cannot read the newer layout anyway.

## 26 Oct 2026: catch-up time

- **The stamp is written in the panel's own time zone.** Xtream panels read the `yyyy-MM-dd:HH-mm` stamp as their own local time, and the login reply says which zone that is. Confirmed on a real panel on 28 Oct 2026: a panel reporting Europe/Amsterdam served catch-up for a stamp 10 minutes in its past that was still 50 minutes in the future by UTC, and refused stamps ahead of its own clock. A panel that gives no zone is read as UTC, as before.
- **The guide offset is applied to catch-up.** The offset turns a guide time into the real time, and the archive holds the real time. Asking for the raw guide time would fetch the wrong show whenever an offset is set.
- **The zone is stored on the source and refreshed with the channels, not fetched on each press.** No wait when you press OK, it works offline, and it starts the pattern of changing the saved data with an automatic migration instead of wiping it. If the login reply fails during a refresh, the old zone stays.

## 26 Oct 2026: zipped and empty guides

- **Zero programmes means the guide failed.** Nothing cleverer, like "far fewer than last time", which could misfire when a provider really trims its guide.
- **Gzip is spotted from the first two bytes, not the name.** Providers serve gzipped guides under plain `.xml` names too. The same check now covers M3U playlists, since they share the reader.

## 26 Oct 2026: setup boxes and the remote

- **The D-pad lands on the box, and OK opens the keyboard.** Before, focus went straight into the text, which popped the keyboard open on every box you passed and trapped Up and Down. This is how TV apps usually behave.
- **The keyboard's tick on the guide box saves.** It's the last box, so the tick doing the obvious thing saves a trip down to the button.
- **Known gap:** closing the keyboard with Back instead of the tick leaves the first D-pad press after it unused. The second press works. Not worth a third attempt now.

## 26 Oct 2026: fake provider for development

- **Python standard library, not Kotlin.** It runs on the Mac with no Java and no installs, and it can't be pulled into the APK by Gradle.
- **server.py is about 680 lines, over the 550 planned.** It covers nine kinds of address, five fault switches and the big-guide mode. Cutting it would drop checks the review asked for. It's a dev tool, never shipped.
- **Test video is Big Buck Bunny** (Blender Foundation, CC BY 3.0), downloaded once by `fetch-video` into `~/.cache/porch-fakeiptv/`, never committed. About 21 MB, ten minutes, lowest quality variant.
- **The startup banner doesn't print the fake password.** Keeps the "never log a password" rule simple.

## 26 Oct 2026: frozen streams come back

- **Three kinds of drop, not one.** The player used to wait only for an error. Many servers close the connection cleanly instead, which ExoPlayer reports as the stream ending, or go quiet and leave it buffering. On the fake provider all three modes (`end20`, `stall20`, `drop20`) came back through the new wait with no ExoPlayer error in the log, so the old code would have frozen on all three.
- **15 seconds of buffering counts as a stall.** The review's suggestion. Short enough that a dead stream is back inside half a minute, long enough that a slow server gets a fair go. The number lives in `StreamHealth.STALL_LIMIT_MS`.
- **A catch-up programme reaching its end is left alone.** The picture holds on the last frame, as before. Where to send the viewer next (back to live, the next programme) is a product choice, not part of this fix.
- **The overlay opens when the player has something to say.** "Reconnecting…" only lives in the overlay, so without this the screen went black with no message while it reconnected. It closes itself six seconds after the message clears.
- **The stall rule is live only, and only while the player wants to play.** The reviewer caught both: a slow catch-up would have jumped back to the programme start, and a paused player sitting in buffering would have been un-paused by the retry. On catch-up only an error counts.
- **Known gap:** a stream that buffers 14 seconds, plays a second and buffers again never trips the stall rule, because the clock resets on each good frame. Acceptable for now.
- **Known gap:** the emulator can't show the 15-second stall path on its own. The fake provider's stall mode is caught by the clean-end path first, since the connection also closes; a server that trickles bytes is what the stall limit is for, and only the unit test covers it.


## 2 Nov 2026: Porch look, piece 1 (tokens and Home)

- **Tokens renamed, not just recoloured.** `PorchColors` now carries DESIGN.md's names (`Surface`, `SurfaceRaised`, `Focus`, `OnFocus`, `Live`, amber `Accent`). The old red `Accent` became `Live`, so a leftover red button can't hide. `PorchType` is `Meta`, `TileTitle`, `RowTitle`, `LiveLabel` and so on; `PorchRadius` is `Row`, `Tile`, `Panel`, `Pill`, `plate()`.
- **Two type tokens added to DESIGN.md, `Body` (13 regular) and `Key` (18 semibold).** The old app used them on 40-plus lines, and no DESIGN.md token was close. They are marked as carried over until each screen's piece lands. Other old styles mapped onto existing tokens: panel and hero titles became `ScreenTitle`, the hero channel line became `Meta`.
- **`Display` is Inter Bold for now.** `val Display` in Theme.kt is the one line to change when Manrope is approved.
- **Screens that aren't in this piece took the new colours and kept their layout.** Their focused fill is `SurfaceRaised` with light text (not the Focus fill) until each gets its piece, because their focused text is light and Focus is light. Guide past cells now match future cells and are told apart by muted text, as they already were. Resting borders on cards and fields are gone (`Line` is for the guide grid).
- **Player keeps its translucent layers** (`SidePanel`, `Scrim`, `Dim`). The brief leaves the player alone; its own piece makes them solid.
- **Glows removed.** DESIGN.md says no shadows, so cards and buttons use the default (no) glow.
- **Pill tick is a drawn vector.** Amber at rest. It reads the surface's content colour, so it turns dark when its pill is focused, with no state of its own. `ChoiceLabels.chosen` stays for its test; nothing in the app calls it now.
- **Groups on Channels.** Showing is the quiet default: Surface pill, no tick, no word. Hidden is no fill, `Faint` text and the word Hidden. A language row with any group showing gets the tick; one with none goes quiet in the same way as a hidden group (no fill, `Faint`).
- **Favourite tiles.** `HomeLists.favouriteTiles` puts favourites with a programme on now first (stable, so channel order holds inside each half). The old favourites list and its See all are gone; `favouritesOnNow` keeps its rules and tests. The tile slot is 128 dp wide with the 118 dp plate inset 5 dp, so the 3 dp outline and 2 dp gap fit without clipping; slots sit 3 dp apart for a 13 dp visual gap. `LogoTile` (not `ChannelLogo`, which is square) draws the plate; a failed or missing logo shows the channel name in dark `TileTitle` on it.
- **Home focus.** Down from a tile goes to the first row of the first list that has one. Up from a first row goes to the tile focus last rested on, then Up from a tile goes to the current tab. Both are handled in code, not by screen position, and fall back to the normal move when the target isn't there.
- **No guide yet.** When the three lists are all empty and channels exist, the middle shows one line and an "Open Full guide" pill, which takes first focus if there are no tiles.
- **Known conflict, kept to the letter of the brief:** DESIGN.md says See all is `Accent` when focused. Amber on the light Focus fill is unreadable, so a focused See all uses `OnFocus` like every other row. Ruben to say if he wants it otherwise.

## 3 Nov 2026: 24/7 tab (my calls)

- **Loop channels are found by group, not by name.** A channel belongs when its group starts with "24/7" and its name is not a divider. Picks narrow as on other screens (favourites and played channels always show; hidden groups are already gone from the channel list). The guide-coverage rule is skipped, since these never have guide data.
- **Genre chips come from group names:** "24/7", VIP, RAW, raised letters and a trailing 60 or 60fps come off, then `ChannelNames` title-cases it. Biggest genre first, ties A to Z, "All" at the front. A group with nothing left is "Other".
- **Show names reuse `ChannelNames.display`.** It already drops the provider label and "24/7". An all-caps name then gets small words (of, the, and, a, in...) lower-cased unless first, which `ChannelNames` does not do. Mixed-case names are left as written. Stored names are untouched.
- **The grid redraws 200 ms after focus settles on a chip**, as on What's on now. The tick moves at once.
- **Tile size is a new DESIGN.md entry:** 160 by 90, five across, 14 apart. It fits the 906 dp the grid has with the 5 dp outline room each side.
- **Recent needed no change.** A loop channel's Recent row already reads its cleaned name (for example "Gentleman Jack"), because `ChannelNames` drops "24/7" and the provider label.
- **Help text unchanged.** No line mentions the tabs.
## 3 Nov 2026: Full guide cleanup

- **One chip row, keys not group names.** Favourites, countries, Sport, News, Movies, Kids, All. The guide filters on `fav`, `all`, `cc:AU` or `kind:Sport` (`GuideChips`). Group chips are gone from the guide; the old group filter still works in `GuideCoverage.view` for its tests. Music and Docs have no chip: the brief named four kinds.
- **Country from the group's leading label only** (`ChannelCountry`, using `ChannelNames.group`, not `LanguageTags`, because "Ukrainian" reads as UK there). GB and USA fold into UK and US. Other labels, even big ones, get no chip.
- **TV country first** via `Locale.getDefault().country`; the rest keep US, UK, NZ, AU, CA, IE. Applied to chip order and to the All list only (favourites, country, number). Other chips keep the old order: favourites, on now, number.
- **Country and kind chips hide channels with no guide**, like All, so "Show channels without a guide" still works. A named group used to show them all; no group chips remain.
- **Noise words are capitals only.** "RAW" and "VIP" are removed only in capitals, and "60fps" in any case. The existing test keeps "WWE Raw" as a show name, so a case-blind rule would break it. A name that is only noise keeps it. "9NOW" now reads "9Now" (letters after leading digits are title-cased).
- **Rules live in `ChannelNames`; stored names are not touched.** The guide and the Channels screen call `ChannelNames.listTitle` and `groupRow` on the raw name at draw time (cleaned names are cached), so old rows look right before the next refresh. A new import stores the same cleaner's output.
- **Known gap:** two groups that clean to the same text (for example "EN| Sport" and "EN| Sport RAW") now read the same on the Channels screen. They stay separate rows. Folding duplicate channels is the next task.
## 3 Nov 2026: Home preview pane with programme pictures

- **Migration 4 to 5 is an `AutoMigration`,** the pattern versions 2 to 4 use. Room generates `ALTER TABLE programmes ADD COLUMN iconUrl TEXT DEFAULT NULL` and nothing else. In SQLite that's the same as no default. No destructive fallback was added.
- **Column order follows the mock-up:** Live sport, Your channels, Starting soon. Your channels takes Recent's middle place. First focus and the pane's first row are therefore Live sport's first row when there's sport on.
- **The no-guide state now needs Your channels empty too.** A favourite with no guide data gets a row, so a viewer with favourites sees their rows, not the "Open Full guide" line. Before this, tiles sat above that line and kept favourites reachable.
- **No row to show means no pane,** so the no-guide line and empty lists get the screen. While the first load runs, the plain block holds the space.
- **Times read "ends 9:45 pm",** from the shared `formatTime`, as on every other row, not the mock-up's bare "9:45".
- **A row carries `programmeId`** (0 means none), so the pane can fetch by id. A folded row uses the programme on its first channel, which is the one whose title it shows.
- **Pictures load with `imageLoader.execute`, not a painter.** A painter drops to its loading state when the address changes, which is the flash the old picture is there to avoid. A cancelled move cancels the download.
- **If the focused row has gone while focus is on the tabs,** the pane moves to the first row instead of showing a programme that has ended.
- **An icon address over 500 characters is dropped,** not cut, because a cut address leads nowhere. The first usable icon wins, so a `file:` one followed by a web one keeps the web one.
- **Pictures share the logo caches** (20 MB in memory, 100 MB on disk), as the brief asked for the shared loader. Watch for logos being pushed out on a long session.
- **Rows stay 38 dp.** With the out-of-date line showing, Home adds up to 517 of 540 dp (the budget is in DESIGN.md).
## 3 Nov 2026: Show posters on the 24/7 tab

- **Posters come from TVmaze's keyless search** (`singlesearch/shows`), credited in the hint bar ("Posters from TVmaze"; the data is CC BY-SA). Only the search text goes out. Nothing is logged, not even counts.
- **Search text drops** brackets, "24/7", HD/FHD/UHD/SD/4K, "channel", "marathon", a trailing "Season N" or "S N", and a trailing single digit. Two digits stay, because "Ben 10" would otherwise search for "Ben". "24" alone stays.
- **A poster is used only if TVmaze's show name matches the search** (accents, case, punctuation and a leading "The" ignored; or one name is the other plus a subtitle, when the shorter is at least 5 letters). The search is fuzzy, and a wrong show's poster is worse than a name tile. Only the `medium` image, only on https.
- **"No poster" is remembered; a failure is not.** A 404 or a show with no image is stored (in the `show_posters` preferences file) and never asked again. A network error, bad answer or 5xx is not stored and is not retried for 5 minutes. A 429 holds the whole line for 60 s, then the same request goes again.
- **The line is a fair mutex.** One request at a time, 500 ms apart. A tile that leaves the screen cancels its ask, and a cancelled waiter leaves the mutex, so only tiles on screen are served.
- **Own logo rule:** a logo address shared by 5 or more channels in the listed set is generic and never drawn on 24/7. A tile with a poster shows the poster; without one, a real own logo on the plate; otherwise the name on a `Surface` tile. Home, Your channels and the guide are unchanged.
- **Seven across, 116 by 164 dp.** The grid gap is 4 dp so the outline room (5 dp each side) makes 14 between posters. The old 160 by 90 tokens are gone.
- **Known cost:** the preferences file holds one entry per show asked about (about 250 KB at 2,800 shows), rewritten on each answer in the background.
## 3 Nov 2026: Optional RPDB rating posters on the 24/7 tab

- **Unverified:** the address shape `https://api.ratingposterdb.com/<key>/imdb/poster-default/<imdb>.jpg` and its behaviour have never been run with a real key, and RPDB's docs weren't reachable. A bad key is known to give 403. Nothing here was tested against the real service.
- **Off unless a key is saved.** With none saved the tab behaves as before. The key lives in the `porch` preferences on the TV, like the backup guide addresses. It comes in only from the phone page at `/posters` (blank removes it), and no page or screen ever shows it. Settings has "Poster ratings" (On or Off) and a small screen with the address, the last result and "Remove key".
- **The key can reach three places:** the poster address (in Coil's memory and disk cache keys, in the app's private cache), the preferences file, and the phone form. The app has no logging at all (checked: no `Log.`, `println` or Timber anywhere), no error text is shown from image loads, and `PosterRatings` / `Rpdb` never return the key to a screen. Removing the key doesn't clear the disk cache; those entries are unreachable once the key is gone.
- **A key is letters, digits, `-` and `_`, 3 to 64 long.** Anything else is refused on the phone page, so a key can't smuggle a path or a query into the address. An IMDb id must be `tt` and 6 to 10 digits.
- **TVmaze entries now keep the IMDb id** (`2|poster|imdb`). Entries saved before are used as they were, and asked again once only when a key is saved (and only those that had a poster), at the same 2 a second.
- **A rating poster is tried first; anything unusable falls back** to TVmaze's, then the name tile: 403, 404, a network error, a picture under 50 px wide, or one where 97% of sampled pixels sit within 24 of the average colour. Each failure is remembered per show for the session. Three 403s in a row stop asking for the session and the screen says "Key not accepted." A new key resets this. A solid block shows while the RPDB request runs, so the TVmaze poster doesn't flash and swap.
- **Rating posters decode at tile size through the shared loader** and sit on the disk cache, so scrolling back costs no request. They need a software bitmap to read the pixels once, which uses more memory than a hardware one.
- **Hint bar:** "Back goes to the filters" only while the grid has focus (on the chips, Back leaves the screen). Credit reads "Posters from TVmaze and RPDB" with a key.
- **Spaced-out names:** runs of two or more single letters are joined, in the shown name and the search ("A N T Farm" is "ANT Farm"). A lone "A" or "I" stays.

## 3 Nov 2026: Porch polish (instant Home, Back warning, last channel)

- **Home draws a saved copy first.** `HomeSnapshot` keeps what Home last showed in `home_snapshot.json` (app private files): 4 shown rows plus 3 spares per list, with the channel id, name, readable name, logo address, group, favourite flag, programme title and id, start, stop, match flag, and how many "See all" holds. The brief listed fewer fields; the extras keep the badge colour, the star and the preview description from jumping when the real lists replace it. Rows that ended are dropped on read, and so are starting-soon rows that have started. The saved copy is used only while the real lists are still loading, and never again after.
- **Write rule:** background thread, temp file then rename, at most one a minute. A write that arrives inside the minute waits and goes out when the minute is up (latest one wins), so a quick exit doesn't leave an old copy. Never written for an empty Home or while the first import runs. Read errors give nothing.
- **Deleted** when a source is removed in Settings, and when the source list goes empty. There is no "Start over" in the app today, so those are the two places.
- **OK works on the saved rows without a change to `play()`.** It sends channel ids, and the player loads the channels from the database by id; hold OK looks the channel up afresh too. So no press waits on the 48,000-channel list.
- **With no saved copy:** each of the three columns says "Loading your channels and guide…" under its heading until the real lists arrive.
- **Back on Home** warns for 2 seconds in the hint bar's place, and a second press in that time is passed to the system, so leaving behaves as it always did. Only Home with nothing under it is covered.
- **"Back to <channel>"** is the first row of the player's hold-OK menu, from `LastChannel`, kept for the app run (so it also works across leaving and re-entering the player). It plays the channel looked up afresh, which makes the current one the previous one, so it flips. Names over 24 characters are cut with an ellipsis because a pill doesn't wrap. `QuickMenu.rows` gained a defaulted `hasPrevious`, so the existing tests are untouched.

## 3 Nov 2026: Review fixes (Home, guide, 24/7 posters)

- **Guide filter fallback prefers the All chip.** The chip list starts with Favourites, so "first chip" could be an empty Favourites page. A filter that matches no chip now goes to Favourites if there are some, else All. The older "first group" fallback stays for a list with no All entry, so the existing test is unchanged.
- **"No guide yet" shows when no row in any list has a programme**, even with favourites. In that state the three lists are hidden, so focus order and the preview pane skip them.
- **Preview pane:** a failed description lookup shows the logo block; cancellation still ends the effect.
- **Genre names are cached by group name** (cleared past 2,000 names).
- **Under the UK chip a trailing "GB" is dropped like "UK"**, in the guide only.
- **Posters:** the saved TVmaze poster shows at once; ids are fetched after it and a failure doesn't take it away. An old empty entry (saved before ids were kept) is asked once with ids wanted, then saved as `2||`. After three 429s in one ask, that ask gives up for now (retry after 5 minutes) so it doesn't hold the line.
- **Full guide hint:** "Back goes to the filters" while the grid has focus, "Back goes to the lists" on the chips (it said "lists" always, found on the Chromecast).

## 3 Nov 2026: Combine copies of a channel

- **Keep a name's words unless they're on a short list.** The family key is the source, the country label and the name with only quality and copy words gone. So +1, East, North, a city or a number keeps channels apart without being listed, and a word we've never seen can only cause a missed fold, never a wrong one.
- **Families stay within one source.** The guide lists one source at a time, and a guide id means nothing across sources.
- **"Alt" and "BK" and "backup" count only at the end of a name.** "Alt Example" keeps its word. "Backup 2" is a copy; "Alt 1" and "Alt 2" stay apart, because the real list has separate feeds named that way. Added "BK" after seeing "HD(BK)" on the real list.
- **A number at the end written as a word is that number** ("Example Two" and "Example 2"). Not in the brief, added because the real list has both for one channel, and the guide-id rule still stops a wrong one. A mid-name word stays ("Example One North").
- **A raised 2 is a number only on its own.** The real list has "Example" with a raised "25fps" (it starts with the ordinary raised 2) and a raised "3840P", and those folded into the 2 and 3 channels until this rule. Found by reading the real list, now in the table test.
- **A name under 3 characters stays apart, as asked.** That leaves a two-character channel three times on the UK chip and a couple more on other chips, each with one guide id. Easy to relax for same-guide-id copies if Ruben wants it.
- **Copies come from every channel not hidden, not only My groups.** A copy in a group the viewer didn't pick can still be a fallback or an Other copies row. Hidden groups are out of the channel list, so never used.
- **Each list folds what it could show.** The guide and What's on now fold inside the chip, and Home's lists fold their own contents, so a family's overall best copy in another group can't remove a channel from Sport or Recent.
- **Marked as played: the listed channel, not the copy playing.** The representative order has "last played" in it, so marking a copy would quietly change the viewer's choice, against "playing one is for now". "Back to" also tracks the listed channel, so a fallback isn't a "previous channel".
- **Moving on gets a shorter retry.** The channel asked for gets the full `RetryPolicy` as before, about a minute. Each copy after that gets one reconnect, or nine copies would be ten minutes of waiting. The failed-copy set only grows until the viewer zaps or presses Retry, so it always ends. Retry starts over at the listed channel.
- **Catch-up:** never moves to another copy on failure. A programme whose best copy keeps no recordings plays from the first copy that does, so folding can't remove catch-up from the guide.
- **The guide's list now builds off the main thread.** The brief asked for the fold off the main thread, and it sits in the middle of filter, order and chips, so the whole rebuild moved. "Loaded" is set with the list in the same frame, so the empty panel can't flash.
- **Off counts:** the Settings panel still counts when the switch is off: "48,393 channels. On, they'd show as 43,263".
- **Not tuned:** "5action" and "5 action" stay apart, and copies whose guide ids differ stay apart. Both are missed folds, the safe side.

## Finish pass (3 Nov 2026)

- **Changes the "shorter retry" call above: with another copy waiting, the channel asked for gets 3 reconnects, not the full minute.** Seen on the Chromecast with a dead first copy: 75 seconds of "Reconnecting…" before "Trying another copy". With the change it is about 20 seconds (ExoPlayer retries each connect a few times itself). With no other copy the full `RetryPolicy` still applies, so a lone channel is as patient as before. `ChannelFold.ASKED_COPY_ATTEMPTS`.
- **Fake provider got `stream=dead1`, `dead` and `slow2`.** `drop20` can't test the fallback: each reconnect plays 20 clean seconds, so the reconnect count resets and the player never moves on. `dead1`: channel 1 answers 503. `dead`: every stream does. `slow2`: channel 1 dead and channel 2 waits 5 s, so the banner can be photographed.
- **Settings and Search rows use the Focus fill, like every other row, not a dark fill with a ring.** DESIGN.md already said so; these two screens were the exception, and looked flat beside Home. Secondary text turns `OnFocus` on a focused row. The Search field sits on `SurfaceRaised` so it reads apart from the keys. Settings group labels are `Section` in `TextSecondary`.
- **Filter chips are 40 tall, with or without an icon** (`PorchSpace.ChipHeight`). The guide's chip row now runs to the screen's edge, so a scrolled chip leaves the screen instead of being cut at a line inside the margin.
- **Up from a screen lands on the tab you are on** (the tab bar's focus group enters on the current tab). Before, Up from Search or Settings landed on Home, so OK took you somewhere you didn't mean to go.
- **Frames on the Chromecast (4K panel, UI at 1080p), warm, 8 rounds of Down, Right, Left, Up:** Home, What's on now and the guide each 21 ms median, 23 ms 95th. The GPU is the limit: about 16 ms of GPU time per frame on a full-screen list, so under 16 ms is not reachable without cutting what is drawn. The first run after an install is far worse (Home 95th 77 ms, guide 89 ms) because the code isn't compiled yet. A baseline profile would fix that but needs the profileinstaller dependency, so it is left for Ruben to decide.
### Fold copies: fix round 1

- **Lists that run on the guide fold only copies with guide data** (`ChannelFold.foldGuided`: Home Sport and Starting soon, search programmes, the player's Others, What's on now). The best copy overall can have no guide id, because favourite and last played rank above it, and it took the matched copy's whole family with it. Lists that show a channel with no guide (Favourites, Recent, the guide grid) still fold everything.
- **Your channels dedupes by family**, not by id: a favourite copy and a different recently played copy are one tile.
- **Failed copies get a fresh chance once one has played stably** (after `MARK_AFTER_MS`). A copy that keeps failing never gets there, so the walk is still bounded. This replaces "only grows" above.
- **After a fallback, the copy that played is the one marked as played**, not the dead channel asked for. Otherwise the dead copy ranks top and every later tune waits through its retries. This replaces "marked as played: the listed channel" for the fallback case only; a copy the viewer chose from Other copies still marks the listed channel.
- **The key now includes what the label and name didn't say:** a leading bracket the label didn't use ("(+1)", "(East)", "UK| [DE]"), and the cleaned group name when the channel has no label (so "Germany Sports" and "Italy Sports" stay apart). The cost is a missed fold for the same channel listed under two differently named unlabelled groups, the safe side.
- **A "#2" or "(2)" copy marker counts as a number for the events rule** ("PPV #1" and "PPV #2" are two events). Found by Codex, reproduced.
- **Zap fold is marked done only once it lands**, and re-runs if the list changes while it runs.
- **Tune finds a sibling copy already in the list** instead of appending a second entry. Hold OK during a fallback acts on the channel asked for, not the stand-in. The Guide's rebuild stops between stages once superseded. The 401 and 403 path clears the "Trying another copy" note.
- **Accepted, not fixed:** removing a favourite when two copies of one channel are favourited leaves the row under the other copy.

## Leaving the app stops the stream (3 Nov 2026)

- **Home, another app or the TV sleeping now ends the stream.** It used to keep playing behind the launcher, with sound, holding the provider's one connection. Reproduced and fixed on the Office Chromecast.
- **Coming back restarts it.** Live rejoins what is on now. Catch-up carries on from the same spot. A "stream dropped" card also gets one fresh try on return.

## Back goes straight back (3 Nov 2026)

- **Root cause of the swallowed Back:** Compose moves focus out of the focused item on Back and counts that as handled, so the Back handlers never hear the press. Found by counting presses from the player's audio sheet: 4, should be 3.
- **Fix in one place:** the app window and each dialog send an untaken Back straight to the Back handlers. The two patches in the player came out, and the counts held.
- **Holding Back steps back once**, not all the way out of the app.

## The phone page has a one-time address (3 Nov 2026)

- **Each time the page opens, its address gets a new six-letter code.** Anything without the code gets a 404. This stops other devices on the Wi-Fi, and web pages open on them, reading or posting to it while it's on screen. It takes logins and the poster ratings key.
- **A code in the address, not a password box,** so setup stays one address to type and nothing more. The page is still plain HTTP, so the "network you trust" line stays.

## The TV's refresh rate follows the channel (3 Nov 2026)

- **On by default**, with a Settings row to turn it off. Ruben wants it working out of the box, and judder on 25 and 50 frame sport is worse than a short blink.
- **Measured, not read from the stream:** Porch times about three seconds of pictures, because this provider's `.ts` streams don't state a rate reliably. It also means zapping past a channel never switches the TV.
- **Stays put when it already fits.** 59.94 and 60, or 23.976 and 24, count as the same. Only modes at the current resolution are used.
- **Stays at the channel's rate on Porch's own screens**, so going back to the same channel doesn't blink. Android restores the TV's normal rate on leaving the app.

## Programme details sheet (3 Nov 2026)

- **The sheet fetches its own extras** (guide offset, a copy that can play catch-up, what's next, the picture) when it opens, so screens only pass the channel, the programme and a watch callback. Pills appear once those are in, so the first pill never changes under focus. Waiting for the shared channel list is capped at 1.5 seconds.
- **Home and Now look the programme up by id** (`repository.programme`) on a hold, because their rows carry the id but not the programme. The menu opens when the lookup answers.
- **Home and Now gained an `onWatch` parameter** so "Watch from the start" can start catch-up. Their `onPlay` stays as it was.
- **In the player,** "Watch from the start" starts catch-up the way a catch-up opened from the guide does (same channel, or the copy that keeps the recording). "Watch now" while in catch-up goes back to live; on live it just closes.
- **"Next on" skips only the programme shown,** as briefed. For a programme that hasn't started it can list what is on before it.
- **Finished on another day reads `Ended Mon 2 Nov 9:00 pm`,** the same day format the guide uses.

## Match reminders (3 Nov 2026)

- **Reminders fire only while Porch is open.** A check runs when the app comes on screen and every 20 seconds after, with a plain `delay`. No WorkManager, no service, no new permission. A reminder set and then left while Porch is closed doesn't fire; one more than 15 minutes past its start is dropped.
- **Stored as JSON in the `porch` preferences** (`ReminderStore`), not in Room, so there is no schema bump. Times are guide-shifted when saved, so a later change to the source's guide offset doesn't move a saved reminder.
- **One reminder per channel and start time.** A second tap on the pill turns it off.
- **"Watch" on the card plays that channel alone** (`play(listOf(channel), channel)`), as there is no list on screen to zap through.
- **Already watching that channel** (the player is on top and its last channel is the same channel or another copy of it): the reminder is cleared without a card. Catch-up isn't detected, so watching a recording of that channel counts as watching it.
- **A reminder names its channel by source and identity,** the pair favourites use, because a refresh gives every channel a new id.
- **Card Watch replaces an open player** instead of stacking a second one, closes the details sheet and quick menu, and plays with the shared channel list so channel up and down work.
- **Details sheet buttons are keyed by action.** When the set changes, focus returns to the first one and an OK within 500 ms of the change is ignored.
- **The card's 60 seconds counts only while the app is on screen,** and starts again on return.
- **`ProgrammeCard.actions` takes `reminderOn: Boolean? = null`.** Null leaves the list exactly as before, so existing callers and tests are unchanged.

## Design loop walk (3 Nov 2026, evening)

- **Shared screenshots crop or cover anything that could be the provider's name.** The 24/7 chip row carries a group name that may be the provider's brand, so the shared shot is cropped above the grid until Ruben says which it is. Codex judged ten screens without it.
- **No forced relaunch of Porch on the Office Chromecast during a walk.** A relaunch resumes the last channel and takes the provider's one stream from the living room. Twice this session a stray press played a channel for 10 to 15 s. Cold-start checks wait for a time Ruben names.
- **Card Watch is slow because it rebuilds the player, not because of the network.** Read-only trace (`PorchApp.kt:338-353`, `PlayerScreen.kt:182-223`): the card pops the player and pushes a new one, so ExoPlayer is released and rebuilt, up to 201 channels reload, and there is a 1.5 s wait for the shared channel list. A zap reuses the player. The fix is to tell the open player to tune. Not yet built.
- **"Nothing scheduled here" while the guide loads is a missing flag.** `model.loaded` means the channel listing arrived, not the programmes. A `programmesLoaded` flag, set when `reload()` finishes, keeps the hero blank until then. Not yet built.

## Evening of 3 Nov 2026: polish, banner, launcher (Ruben away, "deploy if happy")

- **Guide direction B and the logo card are the build.** Ruben picked B (Home's preview pane on top, favourites first in All, Jump to now in the chip row) and pane option 2 (a logo card on the light plate when a programme has no picture) from the 3 Nov canvas. Issue #71.
- **The TV banner is the house with an amber door, not the wordmark.** Three Canva drafts were made; the wordmark-plus-amber-dot read like another brand to Ruben, so the house mark from draft 2 was drawn exactly in Manrope and DESIGN.md colours (#74, PR 75). The AI drafts stay in docs/icon-options-2026-11-03/ for reference only.
- **Card Watch tunes the open player** through a TuneRequest on PlaybackBus (#70, PR 76). A tune skips the 250 ms zap debounce (one deliberate press, not a run). A pending tune is dropped when a new player is pushed, so a new player never plays a tune meant for the old one. One logcat line "tune ready <n> ms", no URL or name, stays in for timing.
- **"Hide this group" names the group and counts raw channel rows,** not folded families, so the count can exceed what the list shows (#69). Accepted: it is the blast radius of the hide, which is what the viewer needs.
- **A label of up to 8 letters before `|` is a tag.** Widening from 3 letters means "Sports| X" and "Asia: X" clean too. Cleaned names are stored at import, so existing rows change on the next refresh, not at upgrade.
- **Projectivy on the living room TV, set by Claude at Ruben's say-so:** Porch ticked into the Home category, the Home row at size S so seven cards fit, the Stremio Continue Watching row at size M, and the Android "Watch Next" row hidden (it was the untitled second poster row). All reversible in Projectivy's menu.
- **Outside inspiration (Canvas bot, 3 Nov, docs/ux-pack-2026-11-03 and the research answer):** patterns adopted into the guide build: favourites first where focus lands, logo fallback on a flat tile, fixed-size placeholders while loading, one focus treatment. Not adopted yet: a "now and next" strip over video on Down (Channels DVR); logged as a later level-up.

## 4 Nov 2026: which look wins (Ruben)

- **DESIGN.md (the 2 Nov look) wins; design/studio guides layout only.** Ruben's note named design/studio and Inter only. Asked, he chose to keep near-black, amber and Manrope headings. The studio frames predate it and their frosted bar and wash break the no-blur, no-gradient rule.
- **Card Watch: rebuilding the player was not the main delay.** The tune-in-place build measured 7,843 ms on the Chromecast, with no retry or copy message on screen, so most of the time is the stream starting. The change stays (it keeps the same player and Back), but it is not the speed fix. Next step is measuring a zap to the same channel, not another guess.
- **Testing on the Chromecast is silent:** Porch's audio is denied with `cmd appops set tv.porch.app PLAY_AUDIO deny`, so the TV's own volume is left alone. Volume is fixed over HDMI on both TVs, so stream volume commands do nothing.

## 4 to 5 Nov 2026: guide fixes and merging at Ruben's word

- **Guide elapsed tone is `GuideElapsed` #2B2E38, flat and neutral, not red.** Red stays the thin now-line only. A focused on-air cell stays one solid fill, so the tone drops out while focused.
- **A row with a guide but nothing in the window gets a bar saying "Nothing scheduled here".** The same words as the hero. With no cell, focus on that row showed nothing, and it can be the first row a guest lands on.
- **Merged 79, 81 and 82 and installed on the living room while it slept, on Ruben's "merge and deploy" at bedtime.** Accepted a known gap: guide B moves slower than the old guide (see PLAN.md). Reason: it is the guide he picked, nothing crashes, and a sleeping install can be replaced.
- **PR 82 merged with CI green on its own branch, not on a merge with main.** A leftover edit in its old worktree blocked updating the branch. The local tests and the release build then passed on main itself (f8108b8) before any install.

## 9 Nov 2026: the guide updates itself (Ruben's Notion research page)

- **On the TV, not a Cloudflare Worker.** A server would hold the provider login and add a service to run. A background job every 3 hours refreshes any guide older than 6, so a guide is at most about 9 hours old.
- **Never starts while a player is on screen.** An import is heavy on a Chromecast. A refresh already running when someone presses play carries on. It starts when Porch comes on screen with no player, or when the player closes.
- **No warm zapping.** Providers often allow one connection, so a second stream could cut off the one being watched.
- **i.mjh.nz not added.** Its site publishes no terms, so Ruben decides. It can be pasted into Backup guides without a code change.

## 9 Nov 2026: Linen

- **Ruben picked A. Linen from the mock-up (5 Nov).** A Settings choice; Dark stays the default; the player stays dark over video.
- **The look is a plain field read by every colour, not Compose state.** Reading colours stays free on a Chromecast. `key()` in PorchApp rebuilds the screen when the look changes, so focus goes back to the top of Settings.
## 9 Nov 2026: guide speed was the install, not the guide

- **Compile after every adb install.** Measured on the Chromecast, worst guide frames went from about 170 ms to 85 to 105 ms, and the 90th percentile from 93 to 117 ms to about 70 (the old guide was 53 to 81). Recomposition counts per key press were already small, so no guide code changed. Measurements before this were all taken on uncompiled installs.

## 9 Nov 2026: free-to-air fallback guide

- **i.mjh.nz `au/all` is a default backup guide (Ruben, 9 Nov).** A TV that never touched its Backup guides gets it; a list someone saved, even an empty one, stays theirs. All of Australia, not one city, because the provider carries every city's channels. The site publishes no terms; Ruben accepted that.

## 9 Nov 2026: pick favourites after setup

- **Offered once, after the How Porch works guide, on a new install only.** It uses the same seen-once flag, so a TV already set up never sees it. Six of each kind, only channels with a guide and a logo, so every pick looks right from day one.
## 9 Nov 2026: search survives a typo

- **Close spellings are tried only when an exact search finds nothing.** A correct spelling costs nothing extra. Words of 4 to 5 letters allow one slip, longer ones two, and the first letter must be right, which keeps the scan quick on a Chromecast.
## 9 Nov 2026: bigger guide

- **Guide 2 from the mock-up (Ruben).** The big logo card from guide B becomes a 96-tall one, which frees room for 6 channels, 3 hours across and bigger cell text. Home keeps its large pane.

## 16 Nov 2026: Sport schedule (my calls)

- Tab name "Sport", placed after What's on now.
- Football is one chip for the Premier League, Champions League and A-League; Golf one chip for PGA, DP World and LIV; Tennis one chip for ATP and WTA.
- All ten chips always show, so focus never jumps when a sport is off-season.
- Home team first, then "v", the Australian way.
- F1 shows qualifying, sprint and race sessions, not practice.
- Golf and tennis dates are ESPN's US Eastern dates; a tournament in Asia can read a day off.
- Team leagues are asked day by day because ESPN refuses a date range for them (16 Nov 2026). Off-season leagues are asked once.
- Known gap: a later knockout fixture can show who won an earlier round. That is in any schedule.
- A saved "live" flag counts for at most 6 hours after the start, so an old copy never shows LIVE for ever.
- Each league's copy is refreshed on its own when over an hour old, so one failing league is retried next time the tab opens.
- Sport row beside-the-title league label follows DESIGN.md (beside, not under); added `PorchSpace.SportRow` (42) for it.
- A game saved as not started stays listed for 3 hours after its start, so it doesn't vanish at kickoff before the next refresh.
- Each league is shown and saved as soon as it loads; the first load is about 65 requests, one at a time.
- Golf and tennis ignore ESPN's "finished" mark and go by their end date: on 16 Nov 2026 ESPN marked the running ATP Finals and LP Open "post".
- Left as is (small): the "updated" age is the newest league's; the TV zone is read once while the tab is open; in US summer a tennis end date can read a day late.
- Every day of a team league failing, with nothing in the plain reply, counts as a failed league (named, retried at the next open), not an empty fresh one (found by the fix check).
- Known gap, seen once on the emulator (16 Nov walk): on a fresh load, while leagues were still arriving, the first Back from the list went to Home instead of the chips. Not reproduced in four later tries (Back from a focused row went to the chosen chip each time). Watch for it on the Chromecast.
- Known gap (Codex, MEDIUM, kept on purpose): when only some days of a team league fail, the league keeps the days that loaded, drops the rest of its old copy, and counts as fresh for an hour. Chosen so one bad day does not hide a whole league (final review F2, locked by `one_failing_day_does_not_fail_the_league`). Closed in Sport PR 2: a failed day now keeps the old copy's games for that day only (`a_failed_day_keeps_the_old_copys_games_for_that_day_only`).

## 16 Nov 2026: Sport PR 3, results and ladders (my calls)

Ruben OK'd the contract in session (16 Nov): three views, each result and ladder row hidden until OK, A to Z ladders, tennis top 20.

- Ladders sort A to Z, followed names first, so the order leaks nothing. Position and points show only after OK on that row.
- A shown row is kept in memory (the Sport view model) and forgotten when Porch closes. Nothing is saved, so the next open starts hidden.
- UFC result: the main event, read as the last fight ESPN lists. Tennis: men's and women's singles finals only, once marked finished (ESPN marks running events "post"). Golf: the lowest `order` once the end day is over in US Eastern time. F1: the Race session only.
- Standings come from `site.api.espn.com/apis/v2/.../standings` (the `site/v2` path gives only a link). Position is ESPN's `rank`, else `playoffSeed` (NFL and NBA, per conference). Tennis rankings from `site/v2/.../rankings`, top 20.
- A standings reply with no tables counts as failed (ESPN changed shape) and the last copy stays. An empty results reply is believed (off season).
- Results and ladders use their own saved files (`sport_results.json`, `sport_ladders.json`) and their own types; the schedule's `SportFixture` still has no score field.
- Results and ladders load only when their view is opened, at most once an hour per league.
- Final review fixes (Opus and Codex, 16 Nov): golf now waits until the day after the end date, as written above (the code let the last day through, and ESPN can mark a running event finished). A ladder table with no name or a repeated name gets "Table N", and a team listed twice shows once, because a repeated list key would crash the screen. The Sport tab goes back to Coming up before its first frame, so an old Results list never takes focus. A long shown result shortens itself instead of hiding the title. Claude's calls.

## 16 Nov 2026: Sport PR 2, channel links (my calls)

Ruben approved the contract in session (16 Nov).

- Only team games ("A v B") link to a channel. UFC, F1, golf and tennis titles are too loose to match without playing the wrong thing.
- A guide programme links when it is on a channel Porch reads as Sport, starts within 30 minutes of the game, and has one team in each half of its "v" (also vs, @, a dash), either order. A team name can match by its full name or by a word of 4+ letters that is not shared by many clubs (city, united, town and the like). Ended programmes are ignored.
- "Man City" in the guide against "Manchester City" from ESPN may miss. A miss shows no channel, which is safer than the wrong one.
- If several channels show a game, the lowest channel number plays and the rest go in the zap list. No "Watch on" panel in this PR.
- The reminder for a later game is for the first channel's start time, using the guide's own title for the time and the game's title for the name.
- OK with no channel does nothing, and the hint bar says why for a team game. For an event (UFC, F1, golf, tennis), which can never have a channel, the hint bar says nothing about OK.
- Following uses the same name matching as Home's Following list. A followed game comes first on its day.
- The guide read is a new lean query without descriptions, for Sport channels only, from 3 hours ago to 15 days on. No schema change.
- A failed day of a team league keeps that day's games from the old copy; other days come from the new load.
- The guide is read each time the Sport tab opens (`SportViewModel.loadGuide`), not just when the model is made, because the model lives as long as the activity. Codex found this (MEDIUM, 16 Nov): reopening Sport after a refresh kept old programmes and channel ids. Known gap: a guide refresh while the tab is open shows after the next open.
- Known gap: HD and SD copies of a channel are both channels, so the lowest channel number wins, whichever it is.

## 10 Nov 2026: channel changes

- **Where 4 seconds goes.** On the Chromecast, about 0.35 s to start after the key (a 250 ms wait for fast zapping), then about 3.2 s from asking for the stream to the first picture, then up to a second before sound and picture are ready. Starting from non-keyframe pictures measured no better, so it was dropped. The rest looks like the provider.
- **The player says "Starting…" until the picture arrives,** which also keeps the channel name on screen. Before, the overlay could time out and leave a black screen.

## 10 Nov 2026: Following

- **Third list on Home, mock-up option 1 (Ruben).** Following replaces Starting soon only when something followed is on now or in the next 7 days. Otherwise Starting soon comes back.
- **What you can follow.** The sheet offers names split from the title (on "v", "vs", ":", dashes and commas, 3+ letters, at most 3) plus "Whole title". A title that doesn't split follows in one press. Pressing "Following ..." again unfollows, like Reminder on.
- **Whole words only, close spelling for 6+ letters.** A short follow like "USA" or "Fox" must not catch "Jerusalem" or "Box". Longer names still survive a slip ("Matildas" finds "Matilda's").
- **Later rows show the guide's day and date** ("Sat 3 Jan 9:30 pm"), the same as the guide, because a 7-day window can reach the same weekday as today.
- **Following loads after the rest of Home,** on its own, so it can't slow Home's first lists. It is not in Home's saved start-up copy yet.

## 10 Nov 2026: On-demand v1 (movies)

- **Movies first, series next PR (Ruben).** A new Movies tab beside 24/7. Home stays live TV for now.
- **First screen (Ruben):** Continue watching, then your categories (a cleaned-up short list, English on by default, like the live starting pick), and Search with the typo-proof matching.
- **Artwork (Ruben):** the provider's posters and plots for now. TMDB later, with Ruben's OK and a free key.
- **Contract:** the tab opens in under 2 s on the Chromecast with 199,589 films (one category fetched at a time, never the whole library); the details sheet shows poster, year, length and plot; OK plays or resumes; Left and Right skip; D-pad, OK and Back only; live TV stays as fast. Tests 1 to 5.
- **My calls:** play from the standard Xtream movie address; "started" means more than 2 minutes watched and under 95% of the film; resume points stay on the TV in Porch's own storage.

### 10 Nov 2026: On-demand v1, calls while planning (issue 109)

- **Left and Right skip 10 s in the film player; holding keeps skipping.** Small and easy to change. Mock-up: `design/movies-v1/movies-mockup.html`.
- **How other players do it (ideas only, MIT: vantaflow/ultra-tv, PirateChuck/Vodder):** categories first, then `get_vod_streams&category_id=X` for one category; read the JSON as a stream, never as one string; one forgiving reader for every field (number or text, blank is empty); play from `/movie/{user}/{pass}/{stream_id}.{ext}`, `mp4` when the extension is missing; plot, length and year come from `get_vod_info` when the film sheet opens, stored back on the film's row. Never hold a database write open while downloading.
- **Picked by Ruben (10 Nov):** Movies tab layout A (Continue watching shelf, category chips, poster grid seven across) and film sheet 1 (like the programme sheet, portrait poster 260 by 366). Resume bar in `TextSecondary` on a `Line` track.
- **Search (Ruben said yes, 10 Nov):** Xtream has no search call, so Porch fetches the slim title list of all films once in the background after the tab is usable, refreshed daily. The tab never waits for it. This is the one exception to "one category at a time".
- **Build calls (10 Nov, Claude):** one branch and one PR for issue 109, since the screens need the data layer to be walked. A category view shows up to 500 films, newest first. A category's on or off choice survives a refresh. A category counts as fresh for 24 h after a full, clean download. A download that is already running finishes even if you leave the tab (rare, and the next open is then instant). Films get their own player screen, so live playback code is untouched. Search shows films as a "Films" group in the existing Search tab.

### 10 Nov 2026: Movies first focus and lazy chips (issue 109)
First focus on Movies goes to the first Continue watching tile when there is one, so the shelf is in view; otherwise the first poster. Ruben approved (10 Nov).
The category chips are a lazy row. With 362 categories the plain row built every chip on open and froze the screen about 1.2 s on the Chromecast (measured with atrace, 10 Nov).
When the starter pick chooses no film category, Movies shows them all rather than an empty tab (review finding, 10 Nov).

### 10 Nov 2026: Pick your channels by topic
Ruben chose the topics in an interview (10 Nov): one or two AFL and NRL, EPL, Champions League, A-League, F1, NFL, UFC, NBA, some golf and tennis, WWE and AEW, free-to-air, world news, kids and entertainment. No cricket. Up to 5 per topic. Picks become favourites only; nothing is hidden.
A channel counts as Australian when its name tag is AU or its group has the word AU, AUS or Australia. Free-to-air cues ("Nine", "Seven", "10") only match Australian channels, so they don't catch other countries' channels.
The old six-kinds picker code (`FavouritePicks`) stays, unused, because removing it means deleting its tests, which needs Ruben's OK.
Liverpool and the Vikings are left to the existing Following screen, not built into the picker.
Checked on the Office Chromecast (10 Nov): this provider's Australian sport (Fox Sports 502 and 504) has no guide match, so the guide rule dropped AFL and NRL. Australian channels no longer need a guide, a topic with nothing guided offers the rest, and pay-per-view event slots (group with the word PPV or events) come last, so Fox Sports 504 leads AFL instead of "AFL TV 00".
Ruben asked mid-build for more than sport (10 Nov): Entertainment became Movies, TV shows (E!, comedy, drama, lifestyle and the like), Docs and Music.

### 10 Nov 2026: TV shows split into Drama and crime, Comedy, Lifestyle
Ruben approved (10 Nov): the picker's TV shows group becomes Drama and crime, Comedy and Lifestyle (food, renovation, travel, home), with E! and general entertainment left in a small TV shows group. Up to 5 each, same ranking as the other topics.
Order in the picker: Movies, Drama and crime, Comedy, Lifestyle, TV shows, Docs. A channel goes to the first group that wants it, so "Crime Comedy" counts as drama.
Cue calls (mine, small and reversible): FX, AMC, Showcase, Universal, 13th Street and Alibi count as drama; Dave as comedy; TLC and HGTV as lifestyle; UKTV, Fox8, Fox One, Sky One and Sky Max stay in TV shows. A "Discovery Home" channel lands in Lifestyle before Docs.
Ruben and Rads share one favourites list. No profiles (Ruben, 10 Nov). This replaces "separate lists for Rads and Ruben" in the plan.

### 10 Nov 2026: Top bar keeps every tab whole (Series, #113)
With 8 tabs, Home's top bar cut "Settings" to "Setti" (seen on the Office Chromecast and the emulator, both 960 by 540 dp). The bar measured the tabs last, so they got whatever the title and clock left.
Now the tabs are measured first, and the title side gives way: the clock drops its day, then hides. Tab labels went from 14 each side plus 6 between to 12 each side and no gap (24 apart, on the 4 dp grid), so Home keeps "Wednesday 10:34 pm". Longer titles such as "What's on now" show the time without the day. Claude's call, small and easy to change; values added to DESIGN.md.

### 11 to 12 Nov 2026: Series and Movies categories (#113)
Ruben's provider starts every category with a bar ("|FR| NETFLIX CRIME | MYSTERE"), so the starter pick found no language label and kept all of them (237 of 237 in Series, 368 of 368 in Movies). Proven from the living room TV's database, not Android's regex or the release build. Both tabs now trim leading bars before the pick (Ruben OK'd Movies, 12 Nov).
A picked and an unpicked category with the same cleaned title stay on separate shelves, so "|FR| NETFLIX" can't put French titles into the English "Netflix" chip. Only picked shelves show, so the French one stays hidden.
Series and Movies have no category chooser, so each category refresh picks again with the current rule (the old "keep stored picks" code is gone). The category cache keys moved to v2 so picks stored by older builds refresh on the first open after an update. Claude's call.
Series folds two or more picked kids, cartoon, family or animation shelves into one "Kids & cartoons" chip (Ruben asked, 11 Nov; Arthur sat in "English Kids", not "Cartoons"). A lone kids shelf keeps its own name, which an existing test expects. Anime stays separate. Movies does not merge kids shelves; not asked.
Episodes are fetched when a show opens and kept in memory, not stored in Room. A show's category row stores its shelf position after the genre sort, so reading back keeps drama, comedy, lifestyle order. The film player's shared parts became `VodPlayer`, used by films and episodes. Claude's calls during the Series build.
Known gaps: the shows table is keyed by series id, so a show the provider lists in two categories appears under one only (fixed 16 Nov, see "A film or show in two categories"; films had the same gap). Shows the provider drops are not removed (Codex, 12 Nov), the same as films; one pruning follow-up for both.
Codex review (12 Nov) fixes: season chips scroll so later seasons are reachable, and a show's episode list is fetched again after an hour, so a new episode appears without restarting Porch. Claude's calls on the limit.

### 12 Nov 2026: Movies and Series drop what the provider withdraws
After a category download finishes whole, films or shows in that category with an older listing stamp are deleted. A failed, cancelled or cut-off download throws first and deletes nothing. A reply with zero items deletes nothing, the same as an empty playlist. The full background title list does not prune; only category refreshes do. Claude's call, kept narrow on purpose.
Continue watching hides a film or show the provider dropped, because it can no longer play. The resume point or show progress stays, so the tile comes back in its place if the provider lists the item again (for example after moving it to another category). Claude's call.
A refresh keeps the newer of the stored and new listing stamps, so a slower full-list write landing after a category write can't make a listed row look dropped. No table changed, so no Room version bump.
An empty `[]` category list keeps the stored Movies and Series categories and asks again on the next open (Fable, 12 Nov). A category that downloads a valid empty list now counts as fetched for a day, so its chip stops re-downloading on every visit; an error reply still does not count. Claude's calls.
Codex review (12 Nov) fixes: a title the provider still lists but that Porch can't read this time (no name, or a field that fails) keeps its stored row instead of being pruned. Movies and Series read Continue watching again when a category finishes loading, so a dropped title's tile goes in the same visit. Claude's calls.
Sport tab freeze (16 Nov ANR): channel links now arrive a moment after the rows on a big guide, because the guide is read off the main thread instead of inside a frame. The rows show at once without a channel, then the channel fills in. Results and ladders mark follows the same way as before, with each name read once. Claude's call.

### 16 Nov 2026: playback robustness
Both players are built with `DefaultRenderersFactory(context).setEnableDecoderFallback(true)`, so if a hardware decoder fails to start, ExoPlayer tries the next one down (software) before reporting an error. Claude's call, from a checked idea (10 Nov). It only helps decoder start-up failures, not a stream that is broken.
`VodPlayer` now works out the stream address in an effect keyed on the player and the title, and loads it in a second effect keyed on the player and that address, so a new film or episode on the same player loads its own stream (Fable review LOW, PR 114). A new title also clears an earlier failure. Claude's call.
Emulator test of network loss uses `cmd connectivity set-package-networking-enabled` with the OEM deny chain on, because the emulator image can't go root. No retry rule changed: the 1, 2, 4, 8, 16, 30 s loop recovered a short outage and gave up with Retry on a long one.
Review fix (lean reviewer, 16 Nov): when the title changes, VodPlayer first stops and clears the old stream, and resets the address, position and length, so the old film cannot keep playing or save its position under the new title while the new address is worked out. A title changing to null shows the failure text. Left alone: keying on the title text, not a request token (episode titles include season and number). Claude's call.

### 16 Nov 2026: A film or show in two categories shows in both chips
Some panels list a title in several categories but name only the first in `category_id`, even when asked for the second. Porch stored one category per film or show, so the title was missing from the other chip. Films had the same gap as shows, with the same fix, so both changed.
New tables `film_listings` and `show_listings` hold one row per category a title is listed under, next to `films` and `shows` (not a re-key of either). Room goes to version 8; the AutoMigration copies one listing per stored title from its stored category, so every title stays in the chip it was in. Show progress, resume points, Continue tiles and search read `films` and `shows` as before. Claude's call: the smallest schema change.
A category download files each title under the category asked for and under its own named one (`VodRefresh.listedUnder`). The background full list files it under its own category only. Porch does not read the panel's `category_ids` list: a chip's own download already lists the title there. Claude's call.
Pruning (PR 115) now works per listing: a finished category download deletes that category's stale listings, and deletes a title only when that was its last listing. A title dropped from one category but still in another stays. The `categoryId` column on `films` and `shows` is still written but no longer decides chips or pruning. Claude's call.
The fake provider lists one film (Copper Summit, 21 and 23) and one show (Harbour Detectives, 31 and 32) in two categories, the way such panels send it.
The per-category "fetched today" keys moved to v2 (`movies_cat_at_v2_`, `shows_cat_at_v2_`), so after the update each chip downloads again on its next open and a title in two categories shows at once instead of up to a day later (Opus review, 16 Nov). The full list gives a title with no category a blank listing only when it has no listing at all, and a category download removes that blank listing, so a title a category has filed can still be pruned (Opus and Codex, 16 Nov). Claude's calls.
Known gap (Opus review, 16 Nov): no committed test runs the prune SQL or the 7 to 8 migration. JVM Room tests need a new test dependency (Robolectric or similar), which is Ruben's call. The SQL and migration were checked once with a Python SQLite script built from the 7 and 8 schema JSON, and on the emulator upgrade walk.


### 19 Nov 2026: player without a long press (Goal 1)
OK with the overlay up now pauses or plays (it used to open Audio & subtitles). The overlay has a Pause or Play button that holds focus; Audio & subtitles is in the hold menu and on Menu or Info (the old Audio pill was never reachable by D-pad, per Codex). Up or Down at the end of a zap list shows "End of this list" for 2 s (not over a drop or a start message). The overlay's programme is shown only for the channel it was loaded for, which fixes the old programme under the new name during a tune. Claude's calls.
### 19 Nov 2026: Launch push goals
Launch means "sideload for dad plus clean repo" (Ruben, 19 Nov). No store listing, no money. Five goals for 19 to 21 Nov are in PLAN.md "Launch push", picked from the Fable usability walk (19 Nov) and web and X research. Claude's calls, under Ruben's blessing for the push: pause gets a non-long-press path; sources get Edit; a simple mode with a code for Dad; README and an install guide for a public repo.
No new release signing key in this push: the living room TV has builds signed with the local debug key, and a new key would force an uninstall that wipes its login and favourites. Claude's call.

### 19 Nov 2026: Goal 4, on-demand polish
Movies and Series poster tiles now focus with the `Focus` fill (a 5 dp frame round the poster), not an outline; DESIGN.md Movies tab says so. A stream with no length saves its position with length 0, `Films.started` accepts length 0, and the overlay shows just the position. Claude's call.
Codex review (20 Nov): a stream with no length that plays to the end now saves its last position as its length (`Films.endMark`), so it counts as finished: it leaves Continue watching and a series moves to the next episode. Claude's call.

### 19 Nov 2026: Goal 5, public repo and Dad guide
History audit passed (403 commits, all refs): no real secrets, provider hosts or public IPs; only fake test values. Findings are in the coordinator notes, not in the repo. Nothing was rewritten. Claude's call.
Untracked local files (TiViMate PDF, `REVIEW-claude.md`, `review-shots/`, `screenshots-local/`) are now in `.gitignore`. Nothing was deleted. Claude's call.
README now says the sideloaded APK is the release build (CLAUDE.md already did), and phone setup uses a typed address with a one-time code, not a QR code. `docs/INSTALL.md` adds a Dad set-up list; its step to turn USB debugging off afterwards is optional advice. PR 27 left open for Ruben to close.

### 19 Nov 2026: hide a group in a few presses (PLAN step 3)
The 19 presses (counted 4 Nov) were the route to Settings > Channels, not the picking: Up to the bar, Right 8 times to Settings (the last of 9 tabs), OK, Down 3 from Refresh to Channels, OK, Right, Down to the group, OK. From code that is 16 + k, where k is the Downs to the group. Hold OK > "Hide <group>" is 4 to 5 presses but is a long press, and long presses are shortcuts, never the only way (`docs/quality-brief.md`).
Options weighed: (A) the tab bar wraps at its ends and Settings opens on the Channels row, about 6 + k, all short presses; (B) the Channels screen opens inside the group list on the first showing group, saves 1 to a few, leaves the 9-tab walk; (C) a "Hide group" button on the programme sheet, only reachable for programmes that haven't started, because OK on a live one plays it. Picked A: only the tab walk and the Settings depth were big enough to reach "a few". Claude's call.
It works when:
- From Home, with no long press, a named group is hidden in 6 + k presses (Up, Left, OK, OK, Right, Down k, OK), down from 16 + k. Measured on the emulator before and after with the same fake group.
- A fresh Left on the first top tab moves to the last tab, and a fresh Right on the last moves to the first, on every screen with the top bar. A held Left or Right stops at the end, so holding a key can't spin round the bar. The rule works on whatever tab list it is given (simple mode may drop tabs).
- Settings opens with focus on Channels instead of Refresh. Back from a Settings sub-screen still lands on the row you left.
- Hold OK > Hide <group> is unchanged.
- No new write, no schema change, no new setting: the Channels screen keeps `setGroupsShowing`.

### 19 Nov 2026: editing a source, plain failure words (launch push goal 2)
Edit updates the same source row (only the form's columns, by a targeted UPDATE, so a refresh writing the generation at the same moment is not undone). The kind can't change, and phone setup is not offered in edit mode because it would send the password over plain HTTP. Claude's calls.
An M3U playlist address can be edited, but channel identity stays `m3u:` plus the stream URL, with no name-based fallback: a fuzzy carry-over in refresh could move a favourite onto the wrong channel. The edit screen says a new address can lose favourites on channels whose stream address changed. Xtream identities are stream ids, so a new server, username or password keeps them. Claude's call, after an advisor check.
An Xtream login's own guide address (which carries the password) shows as an empty guide box and is rebuilt from the new login on save. A typed guide address is shown and kept.
Saving an edit while that source is importing queues one more refresh after the current one, because the import is blocking IO and can't be cancelled cleanly.
Failure words: the guide part of a refresh is tagged, so a playlist 404 never blames the guide. Only Porch's own sentences pass through as detail; a parser's message could quote the reply. The last failure per source is kept in preferences, because a failed guide still marks the source refreshed and nothing tries again for six hours; it is cleared by a good refresh or by removing the source. Removing a source also clears its status, which had kept Home from saying an import was running (walk shot 28). Claude's calls.
Home with no guide keeps "Your channels" (favourites, then recent) beside the no-guide line, and the line is the failure when there is one. The import line on Home reads the refresh status in its own small composable, so progress ticks don't redraw Home. The channel count shows once the playlist is read ("Found 20,000 channels. Fetching the guide…"); there is no running count while it downloads. Claude's calls.
Codex review (19 Nov): an edit that changes an Xtream server or username forgets when Movies and Series were last fetched for that source, so the next visit asks the new account and its category refresh prunes the old titles. Resume points stay. A show's episode list held in memory still lasts up to its hour. Claude's call.
Known gap, left on purpose (Codex, 19 Nov): Edit is for a typo or a new password, not for moving to another provider. After a server or username change, Movies and Series screens already open keep their shelves until reopened, cached film details are keyed by stream id, and categories the new account lacks are never pruned. The Xtream edit card says to add another provider as a new source. A full catalogue reset on account change is a follow-up if Ruben wants it.
Edit and Add forms: Down from the last field goes to the Save button, not Cancel, because a blind OK on Cancel threw an edit away on the emulator. The fake provider (dev only) now serves `/xmltv.php` so an Xtream login's rebuilt guide address can be tested; a wrong login gets 403. Claude's calls.
Emulator check (20 Nov): a 5 dp `Focus` frame round the poster alone looked like the old outline. The fill now holds the poster, the resume bar and the name, which turns `OnFocus`. Claude's call, to meet "solid light fill, not a white outline".

### 20 Nov 2026: Goal 4, Setup polish
The Pick channels screen is titled "Pick channels" (the Settings row stays "Pick your channels"): the longer title ran into the tabs and was cut to "Pick your chann...". On Add a source, the phone panel keeps one pill: "Start phone setup" becomes "Stop phone setup", so focus stays in the panel after Start instead of jumping to the form, and Stop closes the plain-HTTP page early. Claude's call.

### 20 Nov 2026: Goal 3, simple mode for Dad's TV
Home in simple mode is option B, "One list": every favourite (not recents) in channel order as Live now rows, no preview pane, no See all (it would open What's on now, which simple mode hides). Picked over a trimmed Home with the pane: more channels on screen, one moving part. Options in the coordinator notes. Claude's call.
Simple mode shows only the Home and Full guide tabs; What's on now and 24/7 go too, because the plan says "only favourites and the guide". The filter is in `TopBar` (one place for every screen) and `showTab` refuses the rest. Hold OK drops "Hide this group" in simple mode: Settings, where a group comes back, is out of reach. "Remove from Favourites" stays, since the guide can add it back. Claude's calls.
The way out of simple mode is Back on the favourites list > "Leave Porch?" > "Simple mode off" > the code. It needs no Menu or number keys (a Chromecast remote has neither) and is easy for Ruben to find again; the code keeps Dad out. Settings can only turn it on. Claude's call.
The code is 4 digits on a D-pad keypad, typed twice when it is set. It is kept as typed in SharedPreferences: it guards against stray presses, not a determined person, and hashing four digits adds nothing. No lockout timer and no reset; a forgotten code means clearing app data (README). Claude's calls.
A wrong code says "That's not the code. Try again." and clears; a right one turns simple mode off and opens Settings, so it is plain that it is off. Claude's call.
The welcome card shows straight after turning simple mode on and at each start until it is dismissed (Start watching or Back), so Ruben can hand the remote over on it and it is never lost to a stray press before Dad sees it. Claude's call.
Back from the player in simple mode always goes to the favourites list, even when the guide opened the channel; Back from the guide still goes to Home. The cold-start zap list stays the last channel's playlist group (unchanged); making it the favourites is a follow-up if Dad finds it confusing. The greeting is unchanged elsewhere; simple Home's title is "Favourites", so Dad never sees "Hi Ruben". Claude's calls.
Second opinions (20 Nov): Codex found that the remote's Back from a player opened in the guide went back to the guide (the app's Back step, not the player's own button); fixed test-first. Antigravity's findings checked against the code: an empty favourites list leaving focus on a vanished pill was real and fixed (focus is placed again when the list empties or fills); route labels for non-tab screens now never pass simple mode's check (hardening). Not real: duplicate row keys or an empty `channels` list (one row per channel, never empty), a code lost to `apply()` (mode and code are written in one edit), a missing dispatcher (always present in the activity), the welcome dropping the resume point (by design, above).
Emulator (20 Nov): straight after the code pad's last key, the TV gave the welcome pill focus before it was listening, so it never drew its fill (OK still worked). The welcome clears focus and asks again after a frame. Claude's call.
Emulator (20 Nov): Leave on the "Leave Porch?" card goes to the TV home screen, and opening Porch again from the launcher starts on the last channel, as a cold start does. No change needed.

### 20 Nov 2026: Usability walk fixes
Simple mode cold start now zaps the favourites (one copy of each channel, by number, as the simple Home lists them), with the last channel first when it is not a favourite. This replaces the earlier choice to keep the last channel's playlist group. Hold OK in simple mode loses Remove from Favourites (Add stays). "What else is on" in simple mode folds only favourites. The reconnect try count shows the allowed number for that run (6, or fewer when another copy of the channel is waiting). "Open Settings" on Home goes to the Settings tab, not straight to the source's edit screen: smaller, and Settings lists the failed source. Claude's call.

### 20 Nov 2026: Settings opens on Channels on every tab visit
Bug: `SettingsViewModel.lastRow` is activity-scoped (no Navigation-Compose), so after editing a source, leaving by the tabs and coming back landed on the source row, not Channels (PR 127's promise). Fix: `SettingsFocus.startRow(lastRow, comingBack)` (domain, pure). `PorchApp.push` sets a `settingsBack` flag when the screen under the push is Settings; `showTab` clears it. Back from Edit, Channels, Setup and the other pushed screens still lands on the row you left; any tab visit starts on Channels. No new setting, no schema change. Claude's call.

### 20 Nov 2026: going public (Ruben's calls)
The public copy is a new repo `porch-tv` with one fresh commit. Old history holds a personal email and real-TV screenshots, and GitHub's PR records keep them even after a rewrite. Ruben's call.
This private repo stays as it is. Ruben's call.
Fable's six legal wording fixes (C11) are applied to the README, INSTALL and ROADMAP, with a Fair use section. Ruben's call.
The default Australian free-to-air backup guide stays, and is listed under "What Porch connects to" in the README. Ruben's call.
No APK download until there is a proper release key. Ruben's call.
TMDB posters come later. Ruben's call.

### 20 Nov 2026: SportChannelsSpeedTest time limit
The guard in `SportChannelsSpeedTest` went from 1,000 ms to 3,000 ms, and the test is now `a_big_guide_links_in_a_few_seconds`. It failed twice on a shared CI runner (19 and 20 Nov) and passed on re-run both times. It still fails if linking goes back to a slow pattern such as building a Regex per row. Approved by Ruben.

### 20 Nov 2026: Walk 2, setup, Settings, Search, 24/7, Pick channels
- Setup text fields focus with the `Focus` fill and `OnFocus` text, no ring (DESIGN.md's rule). Readable, so no `SurfaceRaised` needed. The Down link to Save is now also set on the typing box inside each field: the old link on the outer box was lost while typing, which is the likely cause of Down landing on Cancel.
- A default source name (the host) gets " 2", " 3" when taken (`SourceNames.unique`, applied in `addSource` for blank names only). A name the viewer typed is kept.
- 24/7 poster tiles now focus like Movies (fill card). DESIGN.md's 24/7 line updated.
- Settings opens at the top on a fresh visit when the Channels row still fits on screen; otherwise it scrolls as before.
- Search shows the source name in a row's muted line only when the same channel name appears twice in the results. Claude's call.

### 20 Nov 2026: ladders always show, and more groups count as 24/7
Ladders are no longer hidden. Rows go in position order, position at the left (new token `LadderPositionWidth`, 44 dp), stats at the right, and OK does nothing on a ladder row. Followed teams keep the "Following" label and do not move. Results stay hidden until OK exactly as before. Ruben's call, 20 Nov.
`LoopChannels.inLoopGroup` now accepts a 24/7 token (24/7, 24 / 7, 24-7, 24x7) anywhere in the group name, so "EN| 24/7 CARTOONS" and "24-7 KIDS" reach the 24/7 tab. "1247" and "24/70" do not match. `genreOf` drops leading provider labels ("EN|", "|UK|"). The only caller path is `LoopChannels.listed`, used by the 24/7 tab; other lists are unchanged. Claude's call.

## 20 Nov: CI runs less often

Actions ran out of minutes on 20 Nov (3,000 a month, shared by every private repo; Porch had 226 runs in November). The workflow no longer runs on `main` after a merge (the PR already ran the same build), on draft PRs, or on PRs that only change Markdown, `docs/`, the licence or `third_party/`. It can be started by hand. Builders already run the full gate locally before pushing. Ruben approved this and merging on a local green while minutes are out.

### 21 Nov 2026: zap speed spike (measure only)
- The warm-neighbour prototype only prepares and logs; it does not swap the ready player into the view. A swap changes who owns the one player on screen, which is too big for a spike. The saving is estimated, and the doc says so. Claude's call.
- The prototype is gated twice (debuggable build and a flag file in the app folder), so it cannot run in a release build. Claude's call.
- The fake provider frees a connection slot when the viewer closes (checked every 0.25 s), but only when `--max-connections` is set, so its other behaviour is unchanged. Claude's call.
- Recommendation in `docs/spikes/2026-11-zap-speed.md`: do not build it by default; ask the provider's limit first.

- 22 Nov: warm zapping is shelved (Claude's pick at Ruben's "pick", 22 Nov). Most provider logins allow 1 or 2 streams; at 1 the warm stream is always refused, and at 2 it made zaps about 2 s slower (4.5 s against 2.4 s). The debug-only WarmProbe prototype and its PlayerScreen hook were removed before merge, so no app code changed. The spike notes and the fake provider's `--start-delay` and `--max-connections` switches stay for any later try.

## 22 Nov: Ruben's "pick" answers

Ruben answered the open calls on 22 Nov. For each one below he said "pick", so each is Claude's pick at Ruben's "pick", 22 Nov.
- Warm zapping is shelved (PR 142, see the 21 Nov spike entry above).
- One roadmap: PLAN.md wins. ROADMAP.md no longer lists Recording or Profiles as next; both move to "Left out on purpose", with PLAN's reasons (too heavy for the Chromecast; one shared favourites list, simple mode covers a family TV). ROADMAP lists PLAN's post-launch order. Sync and team alerts stay as "ideas, not planned".
- The now-and-next mini guide on Down is dropped: Down is zap. Number keys are dropped too: the Chromecast with Google TV remote has no number keys.
- The public copy porch-tv is to be refreshed to main after this docs PR, by the CLAUDE.md rule, only if the scan is clean.
- Next goal: remember audio and subtitle choices per channel. Its contract is on `docs/BOARD.md` and waits for Ruben's OK before code. Risk MID: one new table keyed on the channel's source and identity, so refresh needs no copy step and existing tables are untouched.
- Cleanup: removed the worktrees whose branches were merged into main and clean, with their local and remote branches (ci-trim, goal1-player, goal2-sources, goal3-dad-tv, goal4-setup, show-in-two-categories, walk2-a, walk2-b, walk2-c). Kept: agent-a9141aec (locked, adb running in it), hide-group-fast, settings-fresh-focus, usability-walk, vodplayer-url-key and zap-speed-spike (a Claude process still open in each; hide-group-fast also has an untracked file), release-a83 (detached, no branch), walk2-shots (not merged).
- Ladders and 24/7 on the living room TV: parked until Ruben reports something. Claude can't see that TV, and it is not a test device.
