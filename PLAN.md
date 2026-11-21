# Porch plan: one core, two Android layouts, Apple later

Written 29 Oct 2026 from Ruben's brief. The Android TV app is built and in daily use; the core extraction, the phone layout and Apple below are not started. The Studio TV design (`design/studio/`, PR 12) is part of this plan, not a separate one; its own PR list is in `docs/plan-studio-ui.md`.

## Status (22 Nov 2026)

- **Launch push: done.** All five goals and both usability walks are merged (PRs 121, 124 to 141). Main a83bd64 is on the living room TV (20 Nov). Launch means "sideload for Dad plus clean repo"; the clean repo is done and Dad's install waits until next year (Ruben, 22 Nov). The APK is kept for it.
- **Zap speed spike: done, shelved.** PR 142 kept the findings (`docs/spikes/2026-11-zap-speed.md`) and the fake provider switches, with no app code. Warm zapping is not built (see roadmap item 1).
- **Next goal:** remember audio and subtitle choices per channel (roadmap item 3). It waits for Ruben's OK on its "It works when" contract in `docs/BOARD.md`.
- **Live board:** `docs/BOARD.md` (NEEDS ME, DOING, NEXT, DONE).

## Launch push (19 to 21 Nov 2026): done

All five goals are merged. Goal 1: PR 124. Goal 2: PR 126 (and 127, hide a group fast). Goal 3: PR 130. Goal 4: PRs 121, 128 and 129. Goal 5: PRs 125, 131, 133, 135 and 136 (PR 27 closed). Walk fixes after the push: PRs 132, 134, 137 to 141.

**Brief (Ruben, 19 Nov):** "set yourself AMBITIOUS GOALS ... think about USABILITY, DESIGN, EVERYTHING NEXT LEVEL. Dont waste the tokens on endless tests. lets get this to a super usable and awesome state for launch, with a clear roadmap working forward." **Launch (Ruben, 19 Nov):** "launch means sideload for dad plus clean repo". No store listing, no money.

**Inputs:** Fable usability walk of main 78f02fb on the emulator, big fake source (19 Nov, partial: Search, 24/7, drop and Retry not walked). Scores: setup 5, Pick your channels 6, Movies/Series 6, Settings 6, Home 7, What's on now 7, player 7, Sport 7, guide 8, help 8. Web and X research (19 Nov): for a family or an older parent, "premium" means instant zaps, a fast grid guide, "set up once, then it opens to TV" (resume the last channel, short favourites, hidden groups), Back and OK that never need a long press, and a simple or locked mode (Sparkle's TV mode, Channels' Kids Only Mode). A blank guide while channels play is the common complaint.

**Order:** goals 1 to 3 first (two builders at a time, emulator serialised), then 4 and 5. Each goal is one PR. Logic gets a failing test first; UI follows the design-loop skill. Codex on MID and HIGH.

### Goal 1: the player never needs a long press (MID)
It works when:
- OK on a picture with no overlay shows the overlay with a focused Pause button; OK on it pauses, OK again plays. Hold OK still opens the quick menu.
- After a channel starts, its name and programme stay on screen about 3 s, and the overlay never shows the old channel's programme under the new name.
- Up or Down on the last item of a short list says "End of this list" instead of doing nothing.

### Goal 2: a broken guide or a typo never strands you (MID, touches saved sources)
It works when:
- A source row in Settings has Edit. Changing its guide address, server or password keeps favourites, hidden groups and resume points, and refreshes.
- Home shows favourites and recent channels when there is no guide, with one plain line ("Guide address not found. Check it in Settings.") instead of "No guide yet" or "HTTP 404".
- While a playlist imports, Home says what it is doing ("Fetching 20,000 channels...") instead of looking empty.

### Goal 3: Dad's TV (MID, design-heavy)
It works when:
- Settings has "Simple mode": Home shows only favourites and the guide; Movies, Series, Sport, Search and Settings rows are hidden; leaving simple mode needs a 4-digit code set when it is turned on.
- In simple mode Porch opens straight into the last channel played, and Back from the player goes to the favourites list, never out of the app without a "Leave Porch?" check.
- A first-run welcome card (one screen, after Ruben sets it up) tells Dad the three keys he needs: Up/Down to change channel, OK for what's on, Back for the list.

### Goal 4: on-demand polish (LOW to MID)
It works when:
- Movies and Series cards focus with the solid light fill from DESIGN.md, not a white outline.
- A film or episode whose stream reports no length still saves a resume point from the playing position, and the overlay hides "0:00" instead of showing it.
- Setup's "Pick your channels" title is not cut off, and focus stays in the phone panel after Start phone setup.
- PR 121 (a title in two categories shows in both chips, Room v8) merges after its v7 to v8 upgrade check on the emulator passes.

### Goal 5: a clean public repo and a Dad install guide (LOW)
It works when:
- README matches the code (no QR code, optional Xtream guide, hidden groups, Sport, Movies, Series), replacing the stale PR 27, which is then closed.
- A check-only secrets and real-URL audit of the whole git history passes, or its findings are on Ruben's list (rewriting history is his call).
- `docs/INSTALL.md` says how to sideload the release APK on a Chromecast with Google TV in plain steps, and how Ruben sets it up for Dad.
- Untracked local files (the TiViMate guide PDF, `REVIEW-claude.md`, screenshot folders) stay out of git.

**Not in this push:** a release signing key (a new key means the living room TV must uninstall, which wipes its login and favourites; keep the local debug key until a planned move), TMDB posters (needs an account and key: Ruben), store listing.

### Roadmap after launch
1. ~~Warm the channels above and below.~~ Shelved 22 Nov after the spike (`docs/spikes/2026-11-zap-speed.md`): most logins allow 1 or 2 streams, and at 2 it made zaps about 2 s slower.
2. ~~A now-and-next mini guide over video on Down, and number keys.~~ Dropped 22 Nov: Down is zap, and the Chromecast remote has no number keys.
3. Remember audio and subtitle choices per channel (next; contract waits for Ruben's OK).
4. Export the whole setup to another TV in one go (Dad's second TV).
5. TMDB posters for Movies and Series (Ruben's account and key).
6. Sleep timer, then Plex server support (check current docs first).
7. Core extraction and the phone layout (below), then Apple.

## Earlier status (10 Nov 2026)

**The bar (Ruben, 3 Nov):** a guest could use it easily and would say "wow, app quality". Judged from the couch. Quality checklist: `docs/quality-brief.md`.

**Decided** (detail in `docs/DECISIONS.md`): DESIGN.md wins; Linen look as a Settings choice, Dark default (5 Nov); Guide 2, small card, 6 channels, 3 hours (9 Nov); guide updates itself on the TV, not a server (9 Nov); i.mjh.nz `au/all` free-to-air guide as a default backup (9 Nov); Following replaces Starting soon as Home's third list when it has something (10 Nov); Movies tab layout A, film sheet 1, background title list for film search (10 Nov); Series: season chips plus episode list, one Continue tile per show, drama/crime then comedy then lifestyle first, Series tab plus Search "Shows" (Ruben, 10 Nov).

**On main (53cb596) and on the living room TV, installed asleep:** Movies tab (110), self-updating guide (PR 88), compile after install (90), Linen (92), fallback guide (94), bigger guide (96), typo search (99), favourites at setup (100), What's on now loading line (102), "Starting…" while a channel starts (104), Following (107). Ruben hasn't used it on the TV yet.

**Pick your channels (PR 112, main a2f8e23):** installed on the living room TV 10 Nov 18:01, asleep, compiled. Splitting its TV shows group into Drama and crime, Comedy and Lifestyle is on branch `split-tv-shows`.

**Measured on the Office Chromecast (compiled):** launch 0.5 to 1.1 s; channel change about 4 s, of which about 3.2 s is waiting for the stream's first picture (provider side); guide frames 34 to 38 ms median, 48 to 65 ms 90th.

**Sport (16 Nov):** PR 116 (schedule) and PR 117 (channel links, Following, OK plays or reminds) are merged. **Sport PR 3 (results and ladders)** merged as PR 119 (main 2d6f207) and installed on the living room TV 16 Nov 16:44 (Ruben asked). Views Coming up, Results (last 7 days), Ladders (A to Z), every row "Hidden" until OK, forgotten when Porch closes; tennis top 20; no ladder for UFC or golf. 337 tests green; spoiler guard broken on purpose and caught; Sonnet, Opus and Codex reviews all fixed; emulator walk done (screenshots in docs/shots-2026-11-16-sport-pr3/, untracked in the main checkout). Not seen on a real TV.

**Next steps**
0. Sport PR 3 is merged and on the living room TV (16 Nov 16:44, Ruben asked). Ruben checks Sport, Series and Movies there. First Results open takes about a minute; later opens are instant.
1. Ruben opens Porch once on the living room TV (switches on the guide job and the fallback), and says what annoyed him on the couch.
2. On-demand: Movies (PR 110), Series (PR 114) and pruning of dropped films and shows (PR 115) are merged. Both tabs show only English-labelled categories (the provider's leading "|" hid the label). A title listed in two categories now shows in both: PR 121 (Room v8, films too), CI green, reviews done (Opus, Codex, Antigravity); the emulator upgrade walk is half done, so not merged yet (handover `~/.claude/handovers/2026-11-16-porch-two-categories.md`). Follow-ups: Fable LOW: VodPlayer's URL effect keys on the player only. Then TMDB posters with Ruben's OK.
3. Tidier channel list: hide a group in a few presses, not 19.
4. Ruben and Rads share one favourites list, no profiles (decided 10 Nov). Picks from what you watch need a watch history first.
5. Dad's version: Ruben sets it up first, then a short welcome screen. His own provider login.

**Tested on the emulator 16 Nov (fake provider, release build):** network loss mid-stream (a short loss recovers after the network returns, a loss past the retry window ends on "The stream dropped. Press Retry" and Retry works), and sleep then wake on a playing channel (it stops asleep and restarts on wake). Details in the playback-robustness PR. Not tested on the Chromecast itself.

**Not yet tested:** the guide job on a sleeping TV, the favourites picker on a real new install, network loss during a film or episode (`VodPlayer` has no retry loop: a failure shows "won't play right now", follow-up).

**Later level ups:** Plex server support (check current docs first); sleep timer; live rewind spike.

**Picked from other AIs' ideas (10 Nov, checked against the code):**
- [x] Decoder fallback: try the software decoder when the Chromecast's hardware one fails a stream (done 16 Nov in the live player and `VodPlayer`: `DefaultRenderersFactory.setEnableDecoderFallback(true)`). Not exercised on a real failing stream: the emulator can't be made to fail a hardware decoder on demand.
- Warm the channels above and below: spiked 21 Nov, shelved 22 Nov (see roadmap item 1).
- Remember audio and subtitle choices per channel.
- A locked simple mode for Dad: favourites and guide only, Settings behind a code (fits step 5).
- Export the whole setup to another TV in one go, so Dad's is set up once (fits step 5).
- Skipped: multi-view and recording (too heavy for a 2 GB, 8 GB Chromecast), outside list editors, theme options. Buffer tuning won't help: the buffer is already short and most of the wait is the provider.

**Parked:** backdrop behind Home; Projectivy tweaks unless Ruben asks; live video inside the guide (too heavy for a Chromecast); the core and phone plan below waits until the TV app is done.

## Where the code stands today

One Android module, `app`, 6,249 lines of Kotlin, 26 unit tests (JVM, no Robolectric). The split is already clean enough to move:

| Package | Lines | Platform ties |
|---|---|---|
| `parse/` (M3U, XMLTV, own XML cursor, Xtream URLs and JSON) | 889 | `java.io.Reader`, `java.time`, `java.util.zip`, `java.net.URLEncoder` |
| `domain/` (matching, shelves, clock, names, catch-up, retry, stream health) | 544 | `java.time` in `ClockText` only |
| `data/` (Room entities, DAOs, repository, sync, HTTP) | 862 | Room (Android), `HttpURLConnection`, `Context` in the database builder and `HelpFlag` |
| `player/PlaybackBus` | 30 | none that matter |
| `setup/` (phone-setup server, QR) | 316 | sockets: stays in the app (QR code removed 30 Oct 2026) |
| `ui/` (Compose for TV) | 3,544 | stays in the app |

No Android import sits in `parse/` or `domain/`. The work is swapping `java.*` for multiplatform equivalents and turning blocking DAO calls into suspend ones.

## Target layout

```
porch/
  core/                      Kotlin Multiplatform. Targets now: android, jvm (tests). iOS targets added in the Apple phase.
    src/commonMain/kotlin/tv/porch/core/
      parse/     M3uParser, XmltvParser, XmlCursor, XmltvTimes, Parsed, Xtream (urls, zone, mapper), PercentEncoder
      guide/     GuideMatcher, GuideClock, GuideRules, Catchup, ChannelNames, Shelves, Library, RetryPolicy, StreamHealth
      data/      Entities, Daos (suspend + Flow), PorchDatabase (Room KMP), Mappers, LibraryRepository, SyncController, HttpFetcher (interface)
      api/       Watch<T>: the one Flow wrapper Swift will see. Nothing else exposes Flow publicly.
    src/androidMain/         database builder (keeps porch.db), UrlConnectionFetcher. Under 100 lines, by rule.
    src/jvmTest/             the 26 tests, moved, plus the fixture tests and the API-shape test
    schemas/                 Room schema JSON (moved from app/schemas)
  app/                       Android. Compose. Two layouts, one set of view models.
    ui/model/                HomeViewModel, GuideViewModel, NowViewModel, SearchViewModel, PlayerViewModel (moved out of screen files)
    ui/theme/                tokens from design/studio/tokens.md
    ui/shared/               badge, tile, chips, hero, progress, LIVE chip: used by both layouts
    ui/tv/                   today's screens, restyled to the Studio frames
    ui/phone/                channel list, favourites, guide, player (touch)
    player/                  PlaybackBus, ExoPlayer wiring
    setup/                   phone-setup server, QR
  THIRD_PARTY_NOTICES.md     added by the first PR that copies code or adds a library
```

The compiler enforces "no Android in core": `commonMain` cannot see the Android SDK. A `verify` grep also fails the build on `import java.` or `import android.` under `core/src/commonMain`.

## Calls made here (say if you want any changed)

**Storage: Room, as Room KMP. Not SQLDelight.** Porch already runs Room 2.8.5 with exported schema JSON, an AutoMigration and `SchemaGuardTest`. Room has shipped for Android, iOS and JVM since 2.7 with the same annotations, so the entities, migrations and the schema files carry over, and the Chromecast keeps its `porch.db` with no data conversion. SQLDelight would mean rewriting all 35 DAO methods as `.sq` files, a new migration chain and a one-off conversion of the existing database on Ruben's TV. The cost of Room KMP is that common-code DAO methods must be `suspend` or return `Flow`; 28 blocking methods become `suspend`. Callers already run them on `Dispatchers.IO`, so the change is mechanical, and the one rule that matters (the server time zone is written in the same transaction as the channels) gets a JVM test, because Room KMP's bundled SQLite driver lets the repository run on the JVM for the first time.

**HTTP stays an interface.** `HttpFetcher` already is one. It moves to core returning an Okio `Source`; Android keeps `HttpURLConnection`; the Apple app will plug in `URLSession`. No Ktor in phase 1: zero network behaviour change and one fewer dependency.

**Three multiplatform libraries, all Apache-2.0:** Okio (byte sources, gzip, UTF-8; replaces `java.io` and `java.util.zip`), kotlinx-datetime (replaces `java.time` for the XMLTV offsets and the catch-up stamp), kotlinx-serialization (already in). The on-screen clock text (`ClockText`) stays in the app for now; it is wording, not logic.

**iOS targets are not declared yet.** They need Xcode on the build Mac and slow every build. The Apple phase adds `iosArm64` and `iosSimulatorArm64` and the XCFramework task. Swift-friendliness is kept honest now by rule and by test (below), not by compiling for iOS.

**Swift-friendly API rules for core (public declarations only):** data classes with `String`, `Int`, `Long`, `Boolean`, `Double`, `List`; `suspend` functions (Swift sees `async`); `Flow` only inside `Watch<T>`; no `Result`, `Sequence`, value classes, generic sealed hierarchies, nullable primitives (`Long?`) or default arguments relied on by callers. A JVM test walks core's public API with Kotlin reflection and fails on any of those. SKIE (Apache-2.0) can come in the Apple phase if the wrappers get tiresome; not now.

**compose-cupertino is a spike, not a commitment.** Its last push was October 2025 and it targets Compose Multiplatform, so it may not sit on Compose BOM 2026.03. Phase 1c tries it in one PR; if it fights the BOM, phone controls are Material 3 shaped by Porch's own tokens, which still reads Apple-inspired. Haze (Apache-2.0, active) is in for the frosted glass over video on both layouts.

**The TV app comes first, before core and phone (Ruben, 29 Oct 2026).** Order: the Studio TV pass, then the core extraction, then phone. The TV pass touches `ui/` only and the extraction touches `parse/`, `domain/` and `data/`, so nothing is built twice. The phone screens reuse the badge, tile, chip and hero parts the TV pass creates, and the view models get pulled out of the screen files in that pass.

## Licences, checked on GitHub today (29 Oct 2026)

| Repo | Licence | Use |
|---|---|---|
| 4gray/iptvnator | MIT | logic reference, port by hand with notice |
| lizongying/my-tv-0 | MIT | reference |
| vantaflow/ultra-tv | MIT | reference |
| mikehouse/Apple-TV-Player | MIT | reference for the Apple phase |
| alexzhirkevich/compose-cupertino | Apache-2.0 | phone controls, spike |
| chrisbanes/haze | Apache-2.0 | frosted glass |
| iptv-org/iptv | Unlicense (public domain) | parser fixtures, sanitised |
| Okio, kotlinx-datetime, kotlinx-serialization | Apache-2.0 | core libraries |
| OwnTV, M3UAndroid, Lume, AerioTV, ynotv, ViniPlay, IronTV, KSPlayer | GPL, AGPL or none | ideas only, never pasted |

Honest expectation: phase 1 copies little or nothing. The parsers, matcher and Xtream client already exist and have tests. `THIRD_PARTY_NOTICES.md` starts with Inter (OFL) and the libraries, and grows only when something is actually copied.

## Two things only you can decide

1. ~~Fixture URLs.~~ Decided 29 Oct 2026: sanitised to `example.com`. **Fixture URLs.** `CLAUDE.md` says never commit a real playlist or stream URL. iptv-org playlists are full of them. My recommendation: copy the playlists for their real-world quirks (odd attributes, commas in names, non-ASCII, `#EXTVLCOPT`, `catchup` tags, blank lines, BOMs) but rewrite every host to `example.com` and keep a note of the source file. A test asserts no fixture holds a host other than `example.com`.
2. ~~Order of the middle.~~ Decided 29 Oct 2026: TV pass first, then core, then phone.

## PR order

The TV pass (Studio frames, `docs/plan-studio-ui.md`, 8 PRs) runs first. The numbering below is kept for the core and phone phases that follow it.

Each PR is one branch, merged before the next starts, with the unit tests and the TV app on the emulator green before and after. Risk is by the lean-dispatch scale; HIGH gets Codex plus an Opus review before the PR.

### Phase 1a: core, no behaviour change

| # | PR | Risk | Proof |
|---|----|------|-------|
| 1 | Create `:core` (android + jvm targets, Okio, kotlinx-datetime, serialization, coroutines-core). Move `Parsed` and `M3uParser` with their test and fixtures. `M3uParser` reads an Okio source instead of a `Reader`. App depends on core. | LOW | tests pass from `core/`; TV app runs on the fake provider |
| 2 | Phase 1b: sanitised iptv-org fixtures, 3 to 5 parser tests (quirks above), the no-real-host test. | LOW | new tests fail on a broken parser (shown), pass on the real one |
| 3 | Move `XmlCursor`, `XmltvTimes`, `XmltvParser`. `java.time` to kotlinx-datetime; `Reader` to Okio; gzip via `GzipSource`. | MID | big-guide import on the emulator (fake provider `--big`) timed before and after, within 10% |
| 4 | Move `Xtream` (URLs, zone, mapper) and add `PercentEncoder` to replace `URLEncoder`. | MID | catch-up stamp tests move unchanged; one encoder test |
| 5 | Move the `domain/` rules to `core/guide/`. `ClockText` stays. | LOW | the 14 domain tests pass from core |
| 6 | Room to core: entities, DAOs as suspend, database with `@ConstructedBy`, schemas folder, `androidMain` builder keeping `porch.db`; repository, mappers, sync and the fetcher interface follow. `SchemaGuardTest` runs on the JVM with the bundled driver, plus one transaction test for the zone-with-channels rule, plus the API-shape test. | HIGH | installing over the previous build on the emulator keeps sources, favourites and hidden groups; the Chromecast upgrade keeps Ruben's source |

### Phase 1 TV restyle (the Studio frames)

PRs 7 to 14 follow `docs/plan-studio-ui.md` (tokens and shared parts, Home with channels-first import, player, guide, what's on now, search, settings and setup, how Porch works). PR 7 also moves the four view models into `ui/model/` and adds a `PlayerViewModel` so the phone player can share it.

### Phase 1c: phone layout

| # | PR | Risk | Proof |
|---|----|------|-------|
| 15 | Manifest gains a phone launcher entry; layout picked by leanback feature; phone shell with bottom tabs (Channels, Guide, Favourites, Settings); Haze in; compose-cupertino spike decided in this PR. | MID | phone AVD screenshots; TV AVD unchanged |
| 16 | Channel list with search and favourites (same `HomeViewModel` and `SearchViewModel`). | LOW | screenshots; favourite toggled on phone shows on TV build from the same database |
| 17 | Guide on phone (same `GuideViewModel`): vertical channel list, horizontal time, tap plays. | MID | screenshots |
| 18 | Player on phone: tap for overlay, swipe for channel, same stream-health and retry code, landscape full-screen. | MID | screenshots; the drop-and-retry fake modes recover on phone |

Rough size: 18 PRs. At about 10 hours a week that is roughly 3 sessions for 1a, 3 to 4 for the Studio pass, 2 to 3 for phone.

## It works when (phase 1)

1. `core/src/commonMain` has no `java.*` or `android.*` import, checked by the compiler and by the verify grep.
2. The TV app behaves as before: the fake-provider run (setup, Home, guide, player, catch-up, drop and retry) matches before and after, and all 26 existing tests pass unchanged from their new home.
3. A build installed over the old one keeps sources, favourites and hidden groups, on the emulator and on the Chromecast.
4. The big-guide import is no slower than today, within 10%.
5. A JVM test fails when a public core declaration leaks `Flow`, `Result`, `Sequence`, a value class or a nullable primitive.
6. Fixture tests cover the iptv-org quirks, with no host but `example.com` anywhere in `core/src/jvmTest/resources`.
7. On a phone AVD: channel list, favourites, guide and player work by touch, driven by the same view model classes the TV uses.
8. Every TV screen matches its Studio frame at 1080p, and the remote works as `remote-notes.md` says.

## Risks

- **Room KMP versions.** Room 2.8.5 with Kotlin 2.2.21 and KSP 2 should be fine; if a bump is needed it happens in PR 6 alone, never with other changes.
- **The 28 DAO conversions.** Mechanical, but a missed `withContext` or a transaction split would be a silent data bug. The JVM repository test and the install-over-old-build check are the gates.
- **XML speed.** The cursor reads char by char through a `Reader` today; through Okio it must not get slower. PR 3 measures. If it does, the cursor reads a `ByteArray` window instead.
- **Time zones.** The catch-up stamp in the panel's zone is tested; kotlinx-datetime uses the platform's zone rules on Android, so behaviour holds. The tests are the proof.
- **compose-cupertino may not build** against the current Compose BOM. Covered by the spike rule above.
- **Fixtures with real hosts** would break Porch's own rule. Covered by the sanitising test.
- **Swift friendliness is untested on Swift** until the Apple phase. The reflection test catches the type leaks; the first XCFramework build will still find surprises.
- **Budget.** 18 PRs is a quarter's worth of spare hours. Each PR stands alone, so stopping after any one leaves the app whole.

## Phase 2, plan only: multiview

Up to four tiles, each its own ExoPlayer, audio only on the selected tile (the others at volume 0, not muted at the track level, so switching is instant). A `MultiviewViewModel` holds up to four `PlaybackRequest`s and the selected index. One `TilePlayer` composable reuses the player's stream-health, retry and format-fallback code, so a drop in one tile retries alone. Layouts: one, two side by side, three (one large, two small), four in a grid. On TV, Left and Right move between tiles, OK goes full-screen on the selected tile, Back returns to the grid; on phone, tap selects and double-tap goes full-screen. Entry: "Add to multiview" in the What else is on panel (hold OK) and on a phone channel row.

Two risks decide whether it is worth it. Most Xtream subscriptions allow one connection, so the second tile may be refused by the provider; Porch must say so in plain words rather than retry. And a Chromecast with Google TV has limited hardware decoders, so four 1080p streams may stutter; the plan starts at two tiles on TV and measures, with four as the ceiling. Own code throughout; the GPL multiview apps are not read for this.

## Rules carried from the brief

- Every bug fix ships with a test that fails before the fix and passes after, in the same PR.
- No secrets, keys or real credentials in code, tests, fixtures or commits.
- Ask before any destructive git step.
- Copy only from MIT, Apache-2.0 or BSD repos, with notices in `THIRD_PARTY_NOTICES.md`.
