# Prompt for other AIs: make Porch an amazing IPTV app

Paste everything below the line into ChatGPT (deep research mode), Gemini or Grok. Bring the answer back as a file. Answers are leads: each one gets checked against the code before anything changes.

---

Use web search for this. Cite a real page URL for every claim about another app, and say when you could not find evidence. Do not invent features, reviews or quotes. Prefer official docs, user guides, app store listings, r/TiviMate-style forum threads and video walkthroughs from the last 2 years.

## The product

Porch is a personal IPTV player for Android TV, written in Kotlin with Compose for TV, Room and Media3. It ships no channels or providers: the owner adds their own M3U or Xtream Codes login plus an XMLTV guide. One person uses it, from a couch, with a small remote: D-pad, OK, Back only. There is no menu or play/pause key. It runs on a Google TV Streamer and a Chromecast with Google TV, so it is short on GPU power. Frames take about 21 ms at 1080p on the Chromecast, so heavy gradients, blur and big full-screen redraws are expensive. The goal is the only IPTV app on the main TV: very easy, very crisp, good enough to show off, no bloat. "Generic" or "vibe coded" looks count as failures.

The provider list is about 48,000 channels in hundreds of groups, many in languages the owner does not watch, and many are duplicates (HD, SD, backup).

## What exists today

- **Home:** a large hero for the focused channel, then shelves: What's on now, Starting soon (30 min), Favourites, Recent, then the 6 largest groups. Each shelf shows 5 tiles plus a "+N more" tile.
- **Full guide:** a time grid with one row per channel, chips for All, Favourites and groups, then a row of kind pills (Sport, News, Movies, Kids, Music, Docs).
- **What's on now:** chips for All, Favourites and each kind, then shelves for Live, Next hour and Tonight. "More" opens a full grid of tiles, such as "Live now, 909 channels". Each tile has the programme title, a letter badge, the channel name, a progress bar and "26 min left".
- **Search:** finds channels and programmes.
- **Settings:** My groups (a grid of tickable groups that decides what shows) and Clean up channels (hide a group, move a channel, filter by one language).
- **Player:** Up and Down change channel, Left rewinds, Right shows what else is on, a hold on OK opens a quick menu (favourite, hide group, pause, audio).
- **Data available:** channel name, number, provider group, a logo address (stored but not drawn; cards use a letter badge), archive days for catch-up, and for each programme its title, times, description and XMLTV category. A channel's kind is guessed today from words in its name and group.

## What the owner said after using it on the main TV

1. "Would love to see what's playing on sports channels specifically."
2. "Want the homepage to be super functional." Also: "No idea how the homepage works."
3. The Live now page: "I love the squares but surely a more effective way to represent this information." On screen, most tiles say "Paid Programming" and nearly all say "26 min left".
4. "Surely an easier way to sort channels, this is hard." (48,000 channels)
5. Deep in a long list there is no fast way back to the filters, and Back loses your place.
6. Selected and not-selected options look too alike.
7. Clean up: can't tell whether a group is hidden or showing.

## What I want from you

Study how the best IPTV and live TV interfaces solve these problems. Include at least TiviMate, Sparkle, OTT Navigator, IPTV Smarters and Televizo, plus at least two big-budget live TV interfaces such as YouTube TV, Apple TV or Sky Glass, and sports-first ones such as DAZN or ESPN. Then answer:

**A. Sport.** The best way to answer "what sport is on right now, and what's next?" on a remote. Cover how to find live sport from messy data: XMLTV category, title patterns such as "Team v Team" or league names, channel and group names, and how to remove replays, talk shows and duplicates of the same match across many channels. Give the matching rules and how they fail.

**B. Home.** Design the most useful Home for one person who wants to get to something good in under 10 seconds. What rows, in what order, and why? What should go? How does it explain itself without a tutorial? Remember Home has a limited GPU budget.

**C. Replacing the tile grid.** Compare 3 ways to show "everything on now" when there are hundreds of results: for example a dense list, grouping by programme, or grouping by kind. Say how many items fit on a 1080p screen read from 3 metres, what to do with filler such as "Paid Programming", and with the fact that most programmes end at the same time.

**D. Taming 48,000 channels.** The simplest first-run and ongoing flow, using only a D-pad, for getting down to the few hundred channels someone actually watches. Cover language or country, group picking, duplicates, new groups the provider adds later, and undo. Should "My groups" and "Clean up" be one screen?

**E. Remote navigation.** Conventions for Back, long press, jumping through long lists, returning from the player, and showing selected versus focused versus plain states. Point out where the top apps disagree and which one you would copy.

**F. Ranked list.** The 10 changes with the biggest effect from the couch, ranked. For each: what changes, the evidence with URL, rough size (small, medium or large), and the risk. Include 3 things Porch should NOT build, and 3 ideas nobody else does well.

## Rules for your answer

- Every design must work with D-pad, OK and Back only.
- No new paid services, and no accounts.
- Do not suggest copying code from GPL projects. Ideas are fine, and name the licence if you point at code.
- Do not name or link stream sources, playlists or providers. This is about the interface only.
- Be blunt. Say which of the existing screens you would delete.
- Keep it under 2,500 words, with plain words and a short list of sources at the end.
