# Plan: Sport schedule page (draft for Ruben's OK, 16 Nov 2026)

Ruben asked (16 Nov) for an upcoming sports page with a filter per sport, in the TV's time zone, and later scores or ladders with no spoilers.

## What Porch already has

- **Sport chip on What's on now** (`domain/SportMatch.kt`): matches live now and in the next 3 hours, read from the guide.
- **Following** (`domain/FollowTerms.kt`, `HomeLists.following`): followed names on now or in the next 7 days, from the guide.
- **Reminders** for one programme, fired while Porch is open.
- **Pick your channels topics** (`domain/FavouriteTopics.kt`): AFL, NRL, EPL, UCL, A-League, F1, NFL, UFC, NBA, golf, tennis, wrestling. Channels only, no fixtures.

Gaps: the guide looks only a few days ahead, it has no idea what a fixture is, and this provider's Fox Sports 502 and 504 (NRL, AFL) have no guide match. So the guide alone can't give a fortnight of AFL or NFL.

## Ruben's answers (16 Nov)

- Sports: AFL, NRL, EPL, Champions League, A-League, NFL, NBA, UFC, F1, golf, tennis. Wrestling stays with the guide.
- Source: ESPN's public feed. Free, no key, unofficial. Accepted.
- Spoilers: results and ladders stay hidden until OK reveals them, however old.
- First cut: the schedule. Scores and ladders follow in a later PR.

## Data source checked (16 Nov)

`site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard` answered 200 for all 13 leagues: nfl, nba, ufc, f1, pga, liv, eur (DP World), atp, eng.1, uefa.champions, aus.1, afl, rugby-league/3 (NRL). Standings and tennis rankings also answer. No key. Times are UTC. Some leagues take a date range (`?dates=YYYYMMDD-YYYYMMDD`); NFL refused an 8-day range, so it goes by week or by day.

Not chosen: TheSportsDB (free key gives 1 upcoming game per league; US$9 a month for more), football-data.org (EPL and UCL free with a key, no A-League), Squiggle (AFL only, free, wants a contact in the User-Agent), Jolpica (F1 only). Squiggle and Jolpica stay as fallbacks if ESPN closes.

## It works when...

1. A new **Sport** tab lists the next 14 days of fixtures for the 11 sports, in the TV's time zone, grouped by day ("Sat 22 Nov · 7:30 pm Fulham v Man City").
2. A chip row filters by sport (All, AFL, NRL, Football, NFL, NBA, UFC, F1, Golf, Tennis). D-pad only. Golf, tennis and F1 show as one event with its dates ("Thu 20 to Sun 23 Nov").
3. Liverpool and Vikings games (anything Following has) are marked and come first on their day.
4. When the guide has the game on a channel, the row says which, and OK plays it if it's on or sets a reminder if it isn't. With no guide match, the row just shows the time.
5. The feed is fetched when the tab opens, at most once an hour, and the last good copy is kept on the TV. If ESPN fails, the page says "Schedule couldn't load" with Retry and shows the last copy and its age.
6. No score, ladder or result appears anywhere in this first cut.

## Risk

MID. A new outside source and a new tab. No Room schema change (the cache is a file, like the guide cache), no login, no change to playback. Codex review before the PR, per the rules.

## PRs

1. **Schedule page**: ESPN reader in `domain/` (pure Kotlin, tests use made-up teams and saved sample replies), file cache in `data/`, the tab and chips in `ui/sport/`. Covers lines 1, 2, 5, 6.
2. **Channel and follow link**: match fixtures to guide programmes (team names in the title, start within 30 minutes, sport channel), follow marks, OK plays or reminds. Covers lines 3 and 4.
3. **Later, scores and ladders**: last 7 days of results and each league's ladder or ranking, every one hidden until OK on that row. Separate OK from Ruben before it starts. Ruben OK'd it on 16 Nov: Coming up, Results and Ladders views; rows hidden until OK and forgotten when Porch closes; ladders A to Z; tennis top 20; no ladder for UFC or golf.

## Open calls (I'll decide and log unless Ruben objects)

- Tab name "Sport" and its place after "What's on now".
- 14 days ahead; NRL and AFL show nothing until 2027 fixtures start, with a "Season starts <date>" line if ESPN gives it, otherwise "No games in the next 14 days".
- Football is one chip (EPL, UCL, A-League) to keep the chip row short.
