# Porch — notes for the next coding agent

Read DESIGN.md before touching UI. Never invent a colour, size or radius. If a value is missing, add it to DESIGN.md first.

Porch is a personal IPTV player for Android TV. Ruben watches with a remote. He does not write the code. Keep the project small, documented, and remote-first.

## Rules that do not bend

- Ship no channels, no providers, and no stream sources. Do not add sample playlists to the app.
- Do not copy code from GPL projects (OwnTV, Keel TV, OpenTV, StreamVault). Reading them for ideas is fine. Prefer original code. IPTVnator is MIT; still prefer writing our own.
- Tests use fake names and `example.com` only. Never commit a real playlist URL, a real guide URL, a password, or a stream URL.
- Every screen works with a D-pad and nothing else. No touch-only controls, no mouse assumptions.
- Do the simplest thing that works. Do not add a setting, a dependency, or a feature that is not in the request. If a behaviour is unclear, write the assumption in the README.
- The app is MIT. See `LICENSE`. The Inter and Manrope fonts are SIL OFL, in `third_party/inter/OFL.txt` and `third_party/manrope/OFL.txt`.

## What v1 is

A single-module app, `tv.porch.app`. The person adds an M3U URL or an Xtream Codes login, plus an XMLTV URL. Porch stores channels and programmes in Room, matches them, and plays with Media3. Out of scope is listed in `ROADMAP.md`.

## Layout

```
app/src/main/java/tv/porch/app/
  PorchApplication.kt     AppGraph: database, repository, sync, playback bus. No Hilt.
  MainActivity.kt         One activity. PorchApp owns the back stack.
  parse/                  M3U, XMLTV, Xtream. Pure Kotlin. No Android APIs.
  domain/                 Matching, shelves, clock, names, retry, catch-up rules.
  data/                   Room, HTTP, repository, sync.
  setup/                  Phone-setup HTTP server and QR bitmap.
  player/PlaybackBus.kt   In-memory zap list. Tokens, not giant route arguments.
  ui/                     Compose for TV screens.
  ui/help/   One-page guide, shown once (HelpFlag in SharedPreferences, not Room).
app/src/test/             JUnit4. JVM only. No Robolectric.
app/schemas/              Room schema. Commit it when the schema changes.
tools/fakeiptv/  Dev-only fake provider (Python, stdlib). Never shipped.
```

Navigation is a list of routes in `ui/PorchApp.kt`, not Navigation-Compose. The player pushes its own `BackHandler` after the parent, so Back closes the overlay before it leaves the player.

## Conventions

- Kotlin, official style. Public types get a short comment when the name is not enough.
- Blocking Room writes are plain DAO methods called on `Dispatchers.IO`. Do not `runBlocking` on the IO dispatcher.
- UI state is a `ViewModel` plus `Flow`. The player is the exception: ExoPlayer is created in the composable and released with it, so a ViewModel cannot outlive the screen and keep playing.
- Following replaces Starting soon as Home's third list only when it has something.
- Colours and the greeting live in `ui/theme/Theme.kt`. `VIEWER_NAME` is `Ruben`.
- Cards are capped at 5 plus a "+N more" tile (`SHELF_CAP`). Home shows at most 6 category shelves (`HOME_CATEGORY_CAP`).
- Favourites come first on "what's on now". "Starting soon" is chronological.
- The zap list is the full list for that shelf or guide page, not just the 5 visible cards. Zap does not wrap.
- Do not log stream URLs or passwords. HTTP errors say the code, not the address.
- User-Agent for streams and playlists is `Porch/1.0 (Android TV)`.

## Data

- A channel's identity is `m3u:` plus the stream URL, or `xt:` plus the Xtream stream id. Refresh copies favourite, group override, and last-played when the identity matches.
- Channel names are cleaned at import and stored (`shownTitle`, `shownTag`, `shownCode`, `divider`). A null `divider` marks an old row, cleaned at read until the next refresh. A change to the `ChannelNames` rules shows only after a refresh.
- Hidden groups are rows in `hidden_groups`, matched on the effective group (override, otherwise the playlist group).
- Programmes are stored under a generation. The visible generation is `sources.guideGeneration`. A failed guide refresh keeps the previous generation and still saves channels. A guide with zero programmes counts as failed (`GuideRules`). The guide is saved whole to a cache file before parsing (`GuideDownload`, one retry); streaming it at parse speed held the connection for minutes and providers cut it off. Gzip is spotted from the first two bytes, not the file name. An empty playlist does not wipe channels.
- The guide offset is added to stored XMLTV times for display and for now/soon queries. Catch-up asks for the corrected start (with the offset), written in the panel's own time zone from the login reply, or UTC when the panel gives none. XMLTV times with no zone are UTC.
- A schema change bumps the Room version, adds an AutoMigration (or a Migration in `PorchDatabase.manualMigrations`), and commits the new `app/schemas` JSON. `SchemaGuardTest` fails when a version has no migration path, and the build fails when the JSON an AutoMigration starts from is missing. There is no destructive fallback on upgrade; a downgrade wipes.
- Movies and Series file each title in `film_listings` / `show_listings`, one row per category the provider lists it under (`VodRefresh.listedUnder`); chips read those, not `films.categoryId` or `shows.categoryId`. They prune per category: after a whole category download with items, that category's listings with an older `listedAt` are deleted, and a title goes only with its last listing. Refresh keeps `MAX(listedAt, new)`; do not change it to a plain overwrite. Resume points and show progress are never pruned.
- Sport schedule (`SportSchedule`, `SportRules`, `EspnScoreboard`): ESPN's public scoreboards, one request at a time, a saved copy per league in `filesDir/sport_schedule.json`, asked again only after an hour. The schedule parser reads names, dates and state only; a fixture has no score field, and none may be added (spoilers). Results and ladders (`SportScores`, `EspnResults`, `EspnLadders`, `SportScoreRules`) are separate types and files, and every score and winner stays hidden until OK on its own row, but ladders always show in position order (Ruben, 20 Nov; results rule from 16 Nov).
- Sport channel links are worked out in `SportViewModel` on its own background-priority thread (`linkWork`, `SportChannels.candidates`), once per schedule or guide change; the screen calls only the cheap `Candidates.at(now)` each minute. Never call `SportChannels.links` or build a `Regex` inside composition or once per row: on 16 Nov that froze the living room TV past Android's 5-second limit.
- ESPN refuses a date range for team leagues (HTTP 400), so they are asked day by day from their season calendar; UFC, F1, golf and tennis take a range. Day names are US Eastern.
- Editing a source (`SourceEdit`, `LibraryRepository.editSource`) updates only the form's columns of the same row, never a new row, so favourites, hidden groups and resume points stay. An Xtream login's own guide URL (it holds the password) is never put in the form, and is rebuilt from the new login. Channel identity rules are unchanged: a new M3U address with new stream URLs loses those favourites (said on the edit screen).
- A refresh failure is shown in plain words from `ProblemText` (guide or playlist part, kind of failure); the HTTP code is small print only. Guide failures are tagged `GuideRefreshException`. The last failure per source is kept in preferences (`ProblemStore`) because a failed guide still marks the source refreshed.
- Matching order is in `GuideMatcher`: case-insensitive tvg-id, then a cleaned name, then a close name that shares the first word. A tvg-id missing from the guide falls through to the name. Two close guide channels are left unmatched.

## Simple mode

Settings > Simple mode (Dad's TV). Rules are in `domain/SimpleMode.kt` (`SimpleMode`, `SimpleCode`, `StartScreen`); the flag, code and welcome-pending live in SharedPreferences (`SimpleModeStore`), not Room. `LocalSimpleMode` makes every `TopBar` show only Home and Full guide; `showTab` in `PorchApp` also refuses other tabs, so a new screen can't leak. The way out is Back on the favourites list > "Simple mode off" > code; keep it reachable with Up, Down, OK and Back only. Settings can only turn it on.

## Playback

- Live URL for Xtream: `{server}/live/{user}/{pass}/{id}.ts`. One fallback to `.m3u8` if the format is not recognised.
- Catch-up URL: `{server}/timeshift/{user}/{pass}/{minutes}/{yyyy-MM-dd:HH-mm}/{id}.ts`. The start is the offset-corrected one, written in the panel's own time zone (`server_info.timezone`, stored on the source at each refresh; UTC if none). Offered only when `tv_archive` is on, the stream id exists, and the programme start is still inside the archive window. A panel that sets archive on but gives no duration is treated as 3 days.
- Buffer is short on purpose (about 2s to start, 20s max) so channel changes stay quick.
- A drop is an error, a live stream ending cleanly, or 15 seconds stuck buffering (`domain/StreamHealth.kt`). A catch-up programme reaching its end is not a drop. Retry waits 1, 2, 4, 8, 16, then 30 seconds, six times. HTTP 401 and 403 stop the loop. The overlay opens whenever the player has something to say, and has a Retry button.
- Up/down zap, debounced by 250ms. Left seeks back 30s when the stream is seekable, otherwise the overlay says it can't rewind. Right moves into "what else is on". OK shows the overlay, then pauses. Menu or Info opens audio and subtitles.

## Phone setup

`SetupServer` binds `0.0.0.0` on ports 8765–8772 while that screen is open. There is no password on the page. It must stay that way only with the warning on the screen and in the README. Do not leave the server running after the screen closes.

## Building

JDK 17 or 21. Android SDK 36, build-tools 36.0.0. `local.properties` points `sdk.dir` at the SDK and is not committed.

```
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For the TV, install the release build (`./gradlew assembleRelease`, `app/build/outputs/apk/release/app-release.apk`): it is signed with the local debug key so it installs over a debug build, and Home is about 2.4x faster than debug on the Chromecast. After every `adb install`, run `adb shell cmd package compile -m speed -f tv.porch.app`: an adb install stays uncompiled until Android's idle job gets to it, and guide moves take about twice as long until then.

Public copy: `rubeydoobydoo/porch-tv` (public since 20 Nov) holds fresh snapshots of main, never this repo's history (old commits carry a personal email and real-TV screenshots). Update it only when Ruben asks: `git archive origin/main` into an empty folder, re-scan it, and commit on top of porch-tv as Ruben's GitHub noreply address. Never add porch-tv as a remote here or push this history to it.

CI: `.github/workflows/tests.yml` runs the same tests, the debug build and the fake provider tests on every ready PR that changes more than docs. It does not run on `main` after a merge, on drafts, or for docs-only PRs: Actions minutes are capped (3,000 a month across all private repos; ran out 20 Nov). Run the full gate locally before pushing; CI is the second check. A PR should be green before it merges; when Actions is out of minutes, a local green on the merged result is enough (Ruben, 20 Nov). Run it by hand with `gh workflow run tests.yml --ref <branch>`.

To develop without a provider, run `python3 tools/fakeiptv/server.py` (after `fetch-video` once). The README section "Testing without a provider" has the addresses. Its tests: `python3 -I -m unittest discover -s tools/fakeiptv`.

Dependency pins are in `gradle/libs.versions.toml`. Compose BOM `2026.03.01`, lifecycle `2.9.4`, and activity `1.10.1` are held back so the app builds with Android Gradle Plugin 8.13 and `compileSdk` 36. Newer Compose and lifecycle releases ask for `compileSdk` 37 and AGP 9.

## When you change behaviour

Update the assumption list in the README if the user would notice. Update this file if the next agent would get it wrong. Add a unit test for parser, matching, or clock changes. Do not add a bundled playlist to make the UI easier to screenshot.
