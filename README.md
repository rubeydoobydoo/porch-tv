# Porch

Porch is a TV app for Android TV and Google TV that plays internet TV (IPTV) you already have. It opens on what is on now, not on a long list of channels, and you drive it with the remote.

**Porch comes with no channels, no providers and no streams, and it does not help you find any.** You add a playlist and a TV guide from a service you are allowed to use.

Porch is one person's project, built for his own lounge room and shared as it is. It is not in the Play Store.

## What you need

- An Android TV or Google TV device, and a remote.
- A playlist from a service you are allowed to use: either an M3U web address, or an Xtream-style login (server, username and password).
- An XMLTV guide address (the programme listings). A playlist link needs one. An Xtream login can leave it empty and use the guide the provider gives with the login.

**Words used here.** A *playlist* (M3U) is a web address that lists your channels. A *guide* (XMLTV) is a web address with the programme times. An *Xtream login* is a server address with a username and password, which some services give you in place of a playlist. A *provider* is whoever gave you those. An *APK* is an Android app file, and *sideloading* means putting it on the TV yourself, not through the Play Store.

## Install the app

Build the release APK (see "Build it yourself") and sideload `app/build/outputs/apk/release/app-release.apk`. It is signed with a local debug key. That is fine for a box you own. It is not a Play Store build.

Step by step, for a Chromecast with Google TV, is in [docs/INSTALL.md](docs/INSTALL.md).

The home screen says "Hi Ruben". That is the author's name, set in the code (`VIEWER_NAME` in `ui/theme/Theme.kt`). Change it there before you build if you want your own.

## Add a playlist

The first screen is **Add a source**. Porch stays there until one source is saved.

**Playlist URL.** Paste the playlist address and the guide address. Both should start with `http://` or `https://`.

**Xtream login.** Enter the server (a host, or a full `http://` address), the username and the password. The guide address is optional: leave it empty and Porch uses the guide that comes with the login.

The name is optional. If you leave it blank, Porch uses the host name.

With the remote: Up and Down move between boxes. Press OK on a box to type, then the keyboard's tick to move to the next box. The tick on the last box saves.

**Use your phone.** Choose **Start phone setup**. On the same Wi-Fi, the TV shows an address like `192.168.x.x:8765/k7m2xq` (the numbers on your TV will differ). There is no QR code. Type that address into your phone's browser and fill in the form. The last part is a one-time code that changes each time, so other devices on the Wi-Fi can't guess the page. The page has no password of its own, and it closes when you leave the screen. Only do this on a network you trust.

Choose **Save and import**. Porch fetches the playlist and the guide. Channels show up as they are stored. A bad guide address keeps the previous listings if you already had some, and still saves the channels. A guide with no programmes in it (an empty file, or an error page) counts as bad. Gzipped guides (`.xml.gz`) work. An empty playlist does not erase channels you already have.

To change an address or a password later, open the source in Settings and choose **Edit**. Saving fetches it again. Favourites, hidden groups, moved channels, last-played and where you stopped in films and shows all stay. A new playlist address can give channels new stream addresses; a favourite on a channel like that is lost. The type (playlist or Xtream) can't be changed, and phone setup is not offered for an edit. Edit is for a typo or a new password; to move to another provider, add it as a new source.

If a fetch fails, Settings and Home say what went wrong in plain words ("Guide address not found. Check it in Settings."), with the error code in small print on the source's card. Home still shows your favourites and recent channels when there is no guide. While a playlist imports, Home says what it is doing ("Found 20,000 channels. Fetching the guide…").

## Movies, Series and Sport

The tabs along the top also hold **24/7** (channels that play one show on a loop), **Movies** and **Series** (an Xtream login only; an M3U playlist has none) and **Sport**.

- **Movies** and **Series** list the provider's on-demand titles by category chip. OK opens a film or show page, and OK on a film or episode plays it. Left and Right skip 10 seconds. Porch remembers where you stopped, and a **Continue watching** row on the tab picks it up.
- **Sport** lists the next 14 days of fixtures, then **Results** and **Ladders**. Scores, winners and positions stay hidden until you press OK on that row. See assumptions 36 and 37.


## Watching

The home screen greets you, then shows three lists: **Live sport** (sport on now), **Your channels** (your favourites), and **Starting soon** (the next 30 minutes), which becomes **Following** when something you follow is coming up.

- **OK** on a show plays it.
- **Hold OK** on a channel opens the quick menu: add or remove a favourite, hide its group, and in the player pause and Audio & subtitles. In the player, OK shows the overlay with a focused Pause button, and OK again pauses or plays, so a hold is never the only way. Up or Down at the end of a short list says "End of this list".
- **Up and down** in the player change channel. The list does not wrap.
- **Left** rewinds about 30 seconds when the stream allows it.
- **Right** opens what else is on.
- **OK** shows the player overlay, and OK again pauses.
- **Menu** or **Info** opens audio and subtitles.
- **Back** closes the overlay, then leaves the player.

Settings has **How Porch works**, a one-page reminder of these keys. Porch shows it once by itself, the first time it has channels.

If a stream drops, Porch waits and tries again (1s, 2s, 4s, 8s, 16s, 30s, up to six times). A login the server refuses (HTTP 401 or 403) is not retried. Use **Retry** on the overlay when you want another try.

Xtream channels that advertise catch-up can play a programme that has already ended, from the guide. Press OK on that programme. Live pause and rewind only cover the player's short buffer, about 20 seconds. There is no separate timeshift recorder in this version.

## Guide time

Guides are often out by an hour or two. In **Settings**, go to **Guide offset** and press Left or Right. Each press moves every guide by 5 minutes, up to 12 hours either way.

If programmes keep starting after the guide says, press Right to list them later. If they start before the guide says, press Left. This changes the times Porch shows and what it counts as "on now". Catch-up asks for the corrected start, written in the provider's server time zone (`server_info.timezone` from the login reply). A server that gives no zone is read as UTC.

## Channels

**Settings → Channels.** From Home: Up, Left (the tabs wrap round, so Left from Home is Settings), OK, then OK on Channels, where Settings opens.

- A new source starts with the groups in your language and country (AU, NZ, UK, US, EN and your TV's country, plus 24/7 and untagged groups; radio is left out). Later refreshes never pick again.
- **Languages** are on the left. **OK** on one switches all of its groups; the groups of the language in focus are on the right, and **OK** on a group switches it.
- A group says **Showing** or **Hidden**. "Keeping 180 of 420" counts the channels in showing groups.

## Testing without a provider

No IPTV provider? Run a pretend one on your Mac. It is for development only and is not part of the app. It needs Python 3 and nothing else.

```bash
python3 tools/fakeiptv/server.py fetch-video   # once; asks before it downloads
python3 tools/fakeiptv/server.py               # serve on port 8900 (--port to change)
```

It prints the addresses to use. Type these into Porch:

| From | M3U playlist | Xtream server |
| --- | --- | --- |
| Android emulator | `http://10.0.2.2:8900/playlist.m3u` | `http://10.0.2.2:8900` |
| Real box on Wi-Fi | `http://<your Mac's IP>:8900/playlist.m3u` | `http://<your Mac's IP>:8900` |

- The fake Xtream login is `demo` and `demo-pass`. These are made up. They work only with this tool.
- The guide is at `/guide.xml` and `/guide.xml.gz` on the same address.
- Open `http://localhost:8900/control` to flip switches: bad logins, streams that drop or stall, broken guides, a longer rewind window.
- Add `--big` for 20,000 channels and about 300,000 programmes.
- Without the clip, streams answer 503 and everything else still works.
- Passwords are masked in the log.

The test video is Big Buck Bunny, (c) Blender Foundation, CC BY 3.0 (bigbuckbunny.org). It is downloaded into `~/.cache/porch-fakeiptv/`, never into this repo.

## Build it yourself

You need JDK 17 or 21, and Android SDK 36 with build-tools 36.0.0.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

`local.properties` is not part of the project. Tests use made-up channel names and `example.com` addresses.

## What Porch connects to

- Your own playlist, guide and streams, at the addresses you type in.
- A backup guide for Australian free-to-air listings (i.mjh.nz), unless you remove it in Settings, Backup guides.
- ESPN's public scoreboards, when you open the Sport tab.
- TVmaze, for show posters on the 24/7 tab.
- RatingPosterDB, only if you add your own key in Settings, Poster ratings.

Porch has no account of its own and no adverts, and it includes no analytics or crash-reporting library. Your login is stored on the TV and is sent only to the server you typed in. TVmaze is sent the names of the shows on your 24/7 tab so it can find posters. RatingPosterDB is sent your key and the show's IMDb number.

## Assumptions

These are the choices this version makes:

1. The app is named Porch. The home screen says "Hi Ruben" (`VIEWER_NAME` in `ui/theme/Theme.kt`).
2. No channels, playlists, logins or streams are bundled. The first run is an empty setup screen. One guide address is pre-filled under Settings, Backup guides: a free listings file for Australian free-to-air channels. It carries programme times only, and you can remove it.
3. Logins are stored in the local Room database, not encrypted. Android backup is off.
4. Cleartext HTTP is allowed, because many playlist servers are plain HTTP.
5. An Xtream live address is `{server}/live/{user}/{pass}/{id}.ts`. If the player does not recognise the file, it tries the same address with `.m3u8` once.
6. Catch-up uses `{server}/timeshift/{user}/{pass}/{minutes}/{yyyy-MM-dd:HH-mm}/{id}.ts`, with the start corrected by the guide offset and written in the provider's server time zone (`server_info.timezone` from the login reply). A server that gives no zone is read as UTC.
7. The guide offset is added to XMLTV times for display and for "on now" / "starting soon".
8. An XMLTV time with no timezone is read as UTC.
9. Follows are names or titles kept in the app's preferences (not the database). Home's third list is "Following" (on now and the next 7 days) only when something followed is on; otherwise it is "Starting soon". Close spellings match.
10. Phone setup has no password of its own, only a one-time code in the address. It binds ports 8765–8772 and stops when you leave the screen.
11. Search looks from 36 hours ago through 48 hours ahead. A later result tunes to that channel live.
12. Home shows at most six category shelves, the largest groups. The rest are under What's on now and the guide.
13. The channel list for Up and Down is the full list you opened, not only the five visible cards. It does not wrap.
14. Pause and rewind use ExoPlayer's buffer only (about 20 seconds). If the stream is not seekable, pause still works and rewind says so.
15. `catchup-days` on an M3U line is stored. Catch-up playback is Xtream only.
16. Channel logos are drawn on a light plate over the letter badge once they load. A missing, slow or failed logo stays a letter badge. Only http and https addresses are fetched.
17. A language filter hides tagged groups only. Untagged groups stay.
18. Favourites, moved groups, hidden groups, and last-played survive a refresh when the channel identity matches (the M3U URL, or the Xtream stream id).
19. Editing a source keeps the same source, so everything tied to it stays. An Xtream login's own guide address follows a new username or password. A refresh already running finishes first, then one more runs with the new details. The last failure of each source is remembered across restarts (plain words and the code, never the address) and cleared by the next good refresh.
20. There is no hold-down mini-guide. Up and down change channel. The overlay shows the next programme. Right opens what else is on.
21. The guide grid shows one source at a time. A source chip appears when you have two or more.
22. **Next hour** means programmes that start in the next 60 minutes. **Tonight** means programmes that start between 6pm and midnight local time, or from now if it is already after 6pm.
23. Voice search uses the system recogniser when the device has one.
24. Wiring is manual (`AppGraph`). There is no dependency-injection library.
25. The APK you sideload is the release build, signed with a local debug key so it installs over an older debug build. There is no Play Store build or release signing key yet.
26. If an Xtream provider says catch-up is on but gives no number of days, Porch allows 3 days.
27. If the guide fetch fails, channels are still saved and the previous guide stays.
28. An empty playlist does not wipe channels.
29. Starting soon is in time order. What's on now is favourites first.
30. Programme descriptions are stored. Shelves do not show them. The player does not show them.
31. Channel numbers come from `tvg-chno` when the playlist has one, otherwise from the line order. `#EXTGRP` is used only when `group-title` is missing.
32. Updating Porch never wipes its data. A database change ships with a migration, and the build's own test fails if it does not. Installing an older build over a newer one does start from empty.
33. "Pick your channels" (after setup, and in Settings) offers up to five channels each for AFL, NRL, Premier League, Champions League, A-League, F1, NFL, UFC, NBA, golf, tennis, wrestling, free-to-air, world news, kids, movies, drama and crime, comedy, lifestyle (food, renovation, travel, home), TV shows (E! and general entertainment), docs and music. One copy of each name, with a logo, no 24/7 loops. Channels with a regular schedule come before one-off event channels, then Australian copies, then ones with a guide, then the most widely carried. A channel needs a guide unless it is Australian; a topic with nothing guided offers the rest. Free-to-air means Australian channels only. Picking makes a favourite; nothing is hidden.
34. Movies and Series show only categories labelled for English-speaking countries or your own (unlabelled ones stay), read even when the provider starts the name with a bar ("|EN| COMEDY"). They re-pick each time the categories refresh (once a day); there is no category chooser. On Series, two or more kids, cartoon or animation categories share one "Kids & cartoons" chip.
35. Films and shows the provider no longer lists leave Movies, Series and Search when their category next refreshes (at most once a day, when you open its chip). A refresh that fails or comes back empty removes nothing. If you were part-way through one, its Continue tile hides, but your place is kept and comes back if the provider lists it again.
36. The Sport tab lists the next 14 days of fixtures from ESPN's public scoreboards (AFL, NRL, Premier League, Champions League, A-League, NFL, NBA, UFC, F1 qualifying and races, golf and tennis tournaments), in the TV's time zone. It needs no login, is fetched when the tab opens at most once an hour, and keeps the last good copy on the TV. ESPN is unofficial and may change or close. Home team comes first. Golf and tennis dates are ESPN's US Eastern dates, so a tournament in Asia can read a day off. It shows no scores or results, and a later knockout fixture can still name who won an earlier round. A team game ("A v B") shows the channel playing it when your guide has a programme on a Sport channel that starts within 30 minutes of the game and names both teams, one each side of its "v". UFC, F1, golf and tennis never link. A name written differently in the two lists ("Man City" and "Manchester City") may miss, and a miss shows no channel. The guide is read once each time the Sport tab opens. A game you follow (the Following list) says "Following" and comes first on its day. OK plays a game that is on or about to start, or sets (or takes off) a reminder for a later one; with no channel it does nothing. If a day of a team league fails to load, that day keeps the games from the last good copy.
37. The Sport tab also has Results and Ladders, beside Coming up. Results lists games, races, fights and tournaments that finished in the last 7 days. Ladders lists each league's table (AFL, NRL, Premier League, Champions League, A-League, NFL and NBA conferences, F1 drivers and constructors) and the ATP and WTA top 20. UFC and golf have no ladder. Every row says "Hidden" until OK on that row shows its score, winner or position, and a shown row stays shown only until Porch closes. Ladder rows go A to Z so the order gives nothing away, but a player in the tennis list is in the top 20. A UFC result is the main event's winner (the last fight ESPN lists); a tennis result is the men's and women's singles winners once ESPN marks the final finished; golf shows the leader once the tournament's last day is over. Results and Ladders are fetched only when you open that view, at most once an hour, kept on the TV, and a failure there never touches Coming up.
38. A film or show the provider lists in two categories shows in both chips. If the provider drops it from one, it stays in the other.
39. The top tabs wrap at the ends on a fresh press: Left on Home goes to Settings, Right on Settings goes to Home. Holding Left or Right stops at the end. Settings opens with Channels in focus; coming back from a Settings screen lands on the row you left.
40. Simple mode (Settings > Simple mode) is for someone who only wants their channels. Home becomes one list of your favourites, the only tabs are Home and Full guide, and hold OK can't hide a group or remove a favourite. "What else is on" (Right in the player) lists favourites only. Porch opens on the last channel played, and Back from the player always goes to the favourites list. Back there asks "Leave Porch?". Turning it on asks for a 4-digit code twice, typed on an on-screen keypad. To turn it off: Back on the favourites list, then "Simple mode off", then the code. The code is kept as typed in the app's preferences; it stops stray presses, not a determined person. There is no reset: a forgotten code means clearing Porch's data, which also wipes the login and favourites. The welcome card (the three keys) shows straight after turning it on, and again at each start until "Start watching" or Back is pressed, so hand the remote over on it. Up and Down after a cold start change channel through the favourites, in the order of the list.
41. While the live player waits to reconnect after a drop, the Pause button becomes Retry and the top line says which try it is on ("Reconnecting… try 3 of 6"). OK tries again at once. A film whose stream gives no length can't seek: Left and Right say "This film can't skip." instead of restarting it. On Home, when a source failed, the button under the failure line is "Open Settings".

## Not in this version

See [ROADMAP.md](ROADMAP.md). Recording, profiles and sync are next. Multi-view is off the roadmap. They are not hidden switches in this build.

## Fair use

Porch is a player. It has no channels, playlists or streams, and this project does not supply or recommend any. Only use it with sources you have the right to watch. The author is not responsible for what you play through it.

Android TV, Google TV and Chromecast are trademarks of Google LLC. Porch is not connected with Google, ESPN, TVmaze, RatingPosterDB or any TV provider.

## Licence

Porch is [MIT](LICENSE). The Inter and Manrope fonts are used under the SIL Open Font License, in `third_party/manrope/OFL.txt` and `third_party/inter/OFL.txt`.

`CLAUDE.md`, `PLAN.md`, `DESIGN.md` and everything in `docs/` except `INSTALL.md` are the builder's working notes. They are kept for the record and are not needed to use Porch.
