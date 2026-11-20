# Porch couch rebuild: plan from three outside reviews (1 Nov 2026)

Prompt: `docs/prompts/iptv-app-outside-review.md`. Three AI answers came back in chat, and a fourth as a file (added at the end). They are leads. Below is what they agreed on, checked against Porch's code and against the real provider data on the emulator. Nothing here is built yet.

## Checked against our real data (49,299 channels)

| Reviews said | What the data says | So |
|---|---|---|
| Find sport through XMLTV categories first | The provider's guide has **no categories**. All 1,831 programmes with one are from the fake provider. | Category can't be the main signal. Use channel and group names, plus title patterns. |
| Picking "English" hides about 40,000 | US, UK and CA tags alone are 13,254 channels, in 168 groups. Language leaves far too many. | Language needs multi-select, and a group step must follow it. |
| Hide new provider groups by default | Already true once any group is picked (`GroupChoice.filter`). | Nothing to build, only a "new groups" note. |
| "Paid Programming" floods the lists | 7,823 of 292,210 programmes (about 3%), but concentrated in US channels. | Hide filler from discovery lists, keep it in the full guide. |
| Add a hold OK menu | Already merged (#34). | Nothing to build. |
| Hero is costly on the Chromecast | Frames are 21 ms, GPU-bound, and `heroWash` was already the lead. | Agrees. Confirm by measuring after the change. |

Also: only 10,607 of 49,299 channels match the guide, and there are 769 groups.

## What all three agreed

1. Home loses the hero and the six largest groups.
2. Live now becomes rows, not a grid of tiles. Show end times, not "26 min left", and no live progress bars in long lists.
3. Sport lists events, not channels: one match, then its channels.
4. My groups and Clean up become one Channels screen, with plain Showing and Hidden labels.
5. First-run order: language, then groups.
6. Focused, selected and plain must look different in more than colour.
7. Back keeps your place (done, #36). Don't build multi-view.

## Where they disagree, and my pick

- **Top of Home.** Recent first (one review) or sport first (two). Pick: sport first, then Favourites on now, Recent, Starting soon. Porch already reopens on your last channel, so Recent matters less at the top.
- **Live now shape.** Plain list (two) or grouped by programme (one). Pick: one row per programme, with identical titles at the same time folded into "+3 channels".
- **Logos.** One says add them, two say keep letter badges. Pick: later, measure first.
- **Left and Right to page through lists.** Rejected for the guide, where they move through time. Holding Up and Down can speed up instead.
- **Exit prompt on Back at Home.** One review only. Parked.

## Sport matching, first version

A programme counts as live sport when it is on now, is not on the excluded list, and passes any one of these:
- the title has "X v Y", "X vs Y" or "X @ Y", and the channel or group reads as Sport (`Kinds`);
- the title starts with a known league or event (UFC, NFL, EPL, F1 and so on) on a sport channel.

Exclude titles with replay, highlights, classic, preview, talk, news. Reject "v" titles off sport channels, which stops "Batman v Superman". Same title at the same start time folds into one card. Anything unsure stays out of the row but remains in the Sport list. Known gaps: team name spellings, other languages of one match, and replays with no marker.

## Build order (one PR each, a mock-up first for anything that changes a look)

1. **Selected look and hidden labels.** A tick on selected, "Hidden" on hidden groups. Small.
2. **Channels screen.** Merge the two screens; multi-select languages, then tick groups, with a live count.
3. **Live now rows.** Replace the tile grid and the More screen; hide filler.
4. **Sport.** Matching rules with tests from fake titles, then the Sport list.
5. **Home.** New rows, no hero; measure frames on the Chromecast before and after.

It works when, on the Office Chromecast with the remote only:
- A long-time viewer gets from 49,000 channels to a few hundred in under 2 minutes.
- Each of these takes under 10 seconds: start a favourite, find a live match, hide a group, return to where you were.
- No screen shows a wall of "Paid Programming".
- Home frames come in under 16 ms at the 95th percentile, or we say why not.

## Fourth review (research file, same evening)

It agrees with the above, and adds:
- **Fold duplicates is its top change.** HD, SD and backup copies become one channel, falling back to the next copy on error (OTT Navigator does this, per its FAQ). Not in the build order yet: it needs a look at real duplicate names first, and a way to undo a wrong fold. Ruben's call.
- **When the current show is filler, show the next real one** ("Next 9:30, The Project").
- **A title the same as the channel name counts as filler.**
- **Say what was hidden:** "34 filler hidden", "Hidden, 1,204 channels".
- **Its rule 1 is category too,** so the "no categories" check above applies. Its `previously-shown` replay check also needs checking against the real guide.
- **It suggests a Resume strip on top of Home.** Porch already reopens on the last channel, so my pick stays sport first.

## Ruben's picks (2 Nov 2026)

Yes to all six. Squares stay only if they hold a real picture; otherwise rows. The provider also has 199,589 movies and 46,413 series, which Porch doesn't show (roadmap item, not in this build).

## Out of the box (Ruben, 2 Nov 2026)

"I kinda want it out of the box to work." So step 2 changes: Porch picks a sensible start by itself, and the Channels screen is for changing it, not for setting it up.

Ruben's call: AU, NZ, UK and US all start on, and 24/7, replay and on-demand groups stay in. Rough count by group name, leaving out radio: 158 groups and 12,424 channels, 1,971 of them with guide data. The word list found no groups named adult or backup, so that isn't proof there are none; check by eye on the real names.

The guide does the narrowing: lists like All and Home only use channels with guide data, about 2,000. The rest stay reachable in their own group.

Step 2 works when:
- A new TV goes from login to a usable Home and guide with no picking step. Its main lists hold about 2,000 channels, and every kept group is still reachable.
- The start comes from the TV's country setting, and falls back to English-language groups when nothing matches.
- Porch only picks when the viewer has never picked. Existing picks are never overwritten, so the living room TV keeps its own.
- Channels shows what's kept ("Keeping 12,424 of 49,299"), with Showing and Hidden on each group, and one OK changes any of them.
- Moving a single channel is gone from the screen.

## A better guide (2 Nov 2026, not built)

Why so few channels match, on the real data: 39,950 of 49,283 channels come from the provider with no guide id, and its guide only covers 4,387 channels (about 5 days). Matched by country: AU 50 of 282, NZ 43 of 73, UK 683 of 3,142, US 1,195 of 8,927. Some of the rest can never match, such as 24/7 loops and event feeds.

Lead, unverified: free XMLTV files for AU, UK and US (epgshare01). Test before building: run the files through `GuideMatcher` on the Mac and count the extra channels matched. Porch holds one guide address per source today, so a second guide means merging them, and a bigger guide on the Chromecast has to be measured.
