# Plan: build the Spotlight UI from the Studio frames

Written 29 Oct 2026. Spec: `design/studio/` (PR 12). Frames are the truth; `tokens.md` gives the numbers; `remote-notes.md` gives the buttons.

## Order (one branch and one PR each, merged before the next starts)

| # | PR | What it covers | Frame |
|---|----|----------------|-------|
| 1 | Tokens and shared parts | `Theme.kt` gets every token from tokens.md (colours, radii, type sizes, focus scale and ring, motion). New shared parts: frosted TopBar with the five tabs, Tile with the channel wash, focus ring rule, white-fill button rule, LIVE chip, hero block, 'more' tile. Badge palette checked for 3:1 with white. Honour the system 'remove animations' setting. No screen changes yet. | tokens |
| 1b | Readable names | Provider decoration stripped from channel and group names in the domain; divider rows hidden; cards never say "Nothing listed". | (Ruben, 29 Oct) |
| 1c | Fast import | Channels committed first so Home fills in seconds; guide streamed in small batches on a low-priority thread; an unchanged guide is not re-parsed; progress exposed for Home. Measured on the Chromecast: jank and CPU before and after (baseline 29 Oct: 44.7% janky frames, p95 650 ms, Porch 58% CPU during import). | home-loading |
| 2 | Home | Hero that follows focus, 'What's on now', 'Starting soon', 'Recent'. The loading state ('Your channels', 'Guide filling in: n of m') reads the progress from PR 1c. | home, home-loading |
| 3 | Player | Overlay (LIVE chip, title, times, next, hint line, Audio & subtitles button), 'What else is on' side panel with the 'Watching…' pill, message card with Retry, What else is on, Back to Home, and the audio sheet with named languages. | player-* |
| 4 | Guide | OK'd by Ruben 30 Oct as three PRs: 4a My groups (new chosen_groups table, Room v3) and the "Show channels without a guide" switch; 4b kinds and order; 4c the guide screen to the frame. Ruben's picks (29 Oct): **My groups** is the main way in (pick the provider groups you care about once; the guide and What's on now show only those, the rest behind an "All" switch); **categories by kind** (Sport, News, Movies, Kids, Music, Docs from name keywords, across provider groups) as a filter row; order inside a group is favourites, then on-air, then provider number; channels with no guide data are hidden behind a Settings switch "Show channels without a guide". Type-to-jump parked. |
| 4e | Stored display names | Store the cleaned title, tag, code and kind on each channel row at import (Room migration) so a cold launch skips the 1.6 s cleaning pass; target Home tiles under 3 s from cold launch on the emulator (measured 5 to 6 s after the speed PR, 30 Oct). |
| 4b | Interests boost | A short list of teams, leagues or shows in Settings; matching programmes float to the top of Home, What's on now and the guide. Plain word matching on the device; the Mac-side tagging tool stays parked. |
| 4 (frame) | Guide | Hero for the focused cell, filter chips, time header with the red now marker and line, past cells in surface-past, Jump to now, empty list state. | guide, guide-empty |
| 5 | What's on now | Group chips, three shelves with 'n more' tiles, hint line. | whats-on-now |
| 6 | Search | Key grid with white focused key, Space, Delete, Clear, results in On now, Later, Channels. | search |
| 7 | Settings and Setup | Two-column Settings with the Guide offset card; source row opens Refresh and Remove; Setup with the phone card. | settings, setup |
| 8 | How Porch works and final pass | The guide page, then a whole-branch look at every screen against its frame on the emulator and the Chromecast. | how-porch-works |

PR 3 and PR 4 will be over 300 lines; the player file is 816 today and the guide 455. Each is one screen, so they stay as one PR each.

## Calls I am making (logged in docs/DECISIONS.md as each PR lands)

- Tabs replace the root, not push. Choosing a tab from the top bar swaps the current screen for that one, so Back from any tab goes to Home, and Back from Home leaves the app. Today only Home has links and everything else is Back-only.
- No 'Edit' on a source. The frame's remote notes list Refresh, Edit, Remove; the 26 Oct decision says remove and re-add, and the Settings frame itself shows no edit form. Refresh and Remove only.
- 'Animations off' is the system setting. Porch has no animations switch and the rule is not to add settings. Lifts and slides are skipped when Android's animator scale is 0 (the accessibility 'remove animations' switch).
- Named audio tracks come from the language code. 'en' reads 'English', 'es' 'Spanish', a track flagged as description reads 'English, audio description'. Unknown codes fall back to 'Track 2'.
- Counts on Settings rows ('312 duplicates', 'Last 6:10 pm') show only where the number already exists. Cleanup has a duplicate count; sources have a last-refresh time. Nothing new is computed for a label.

## Not in scope

- Channel logos, artwork, the 'Kids' group (groups come from the playlist).
- New features the frames hint at but no note asks for.
- Any change to parsing, matching, storage or catch-up rules, except the channels-first import in PR 2.

## Tests

JVM only, no Robolectric, so screens are checked by screenshot, not by test. Each PR adds 1 to 3 tests for the logic it pulls into `domain/`: badge contrast rule, hero text for the loading state, import progress text, language names for tracks, tab-switch stack rule, time-left text in the panel. Existing tests untouched. Mutation check on each new rule.

## It works when

1. Every token in tokens.md has a named value in `Theme.kt` and no screen file holds a raw colour, size or radius.
2. Side by side at 1080p, each screen's emulator screenshot matches its frame in layout, type sizes, spacing and the one focused item, with fake-provider data.
3. Every screen still works with Up, Down, Left, Right, OK, Back and Mic exactly as remote-notes.md says, checked on the emulator by pressing the keys.
4. Home opens with channels within a few seconds of first setup on the Chromecast, shows 'Guide filling in: n of m' while the guide loads, and OK plays during that time.
5. The one focused item is obvious from three metres on the Chromecast: white ring and lift on tiles, white fill on buttons.
6. Red appears only on the LIVE chip, progress fills and the guide now-line.
7. `./gradlew testDebugUnitTest assembleDebug` is green on every PR, with the new tests listed in each report.

## How it runs

- Lean implementer (Sonnet) per PR with the frame, tokens and this plan as the packet; lean reviewer (Opus) per PR; Codex pass before each PR (MID risk: UI plus one import change). Final whole-branch review by Opus after PR 8.
- Before and after shots at 1080p per PR in `review-shots/studio/`, fake provider only. Chromecast check at PR 2 (Home) and PR 8.
