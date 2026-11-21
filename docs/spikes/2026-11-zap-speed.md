# Spike: can warming neighbour channels cut the wait for a channel change?

Date: 21 Nov 2026. Status: measured. Recommendation below. Roadmap item 1 in `PLAN.md`.

## Why

On the Office Chromecast a channel change takes about 4 s. About 3.2 s of that is the provider taking its time to send the first picture. Idea: while a channel plays, start the next one (and maybe the previous one) quietly, so Up or Down has a picture ready.

## Questions

1. What is the key-press-to-first-picture time on the emulator against the fake provider, with a 0 s and a 2 s provider start delay?
2. If one neighbour is prepared muted in the background, how soon is it ready, and what would a hit save?
3. What does a one-connection provider limit do? (Many IPTV providers allow 1 or 2 streams per login. A warm neighbour is a second stream.)
4. What are the costs: connections, Chromecast memory and decoder use, data use?

## Method

- Fake provider (`tools/fakeiptv/server.py`) gets two off-by-default switches: `--start-delay SECONDS` (wait before a live stream sends its first byte) and `--max-connections N` (live streams open at once; an extra one is refused with HTTP 503).
- Timing comes from the existing `tune ready N ms` log line in `PlayerScreen` (no URL, time only). The warm prototype adds a debug-only `warm ready N ms` line, also with no URL.
- Emulator: `emulator-5554` only, fake provider on port 8900 (`10.0.2.2`). Each setting is run several times and the median is taken.
- The warm prototype is debug-only and off unless a flag file is present. It is a measuring tool, not a shipping feature.

## Findings

Setup: debug build (slower than release), Android TV emulator, fake provider, 14-channel "What's on now" zap list, one Down or Up press every 7 s, 6 presses per setting. "Tune ready" is the app's existing log line: key press (after the 250 ms debounce) to the player reporting ready. It is not a rendered-frame time, so it reads a little early.

| Setting | Tune ready (median, ms) | Range |
|---|---|---|
| Provider delay 0 s, no warm | 516 | 460 to 540 |
| Provider delay 2 s, no warm | 2,458 | 2,432 to 2,587 |
| 2 s delay, warm neighbour prepared, no connection limit | 2,470 (warm channel was ready on 7 of 7 zaps) | 2,458 to 2,519 |
| 2 s delay, warm on, limit of 2 connections | 4,500 | 4,471 to 4,604 |
| 2 s delay, warm off, limit of 2 connections | 2,435 (no refusals) | 2,423 to 2,491 |

Warm player's own time to ready: about 2,080 ms with a 2 s provider delay (2,070 to 2,088; one run 2,543). So the warm player pays the provider wait in the background, as hoped.

The "warm on" rows do not use the warm player for the picture: the prototype only prepares the neighbour and logs whether it was ready when the viewer arrived. Swapping the ready player into the view was not built (it touches the lifecycle of the one player the screen owns). So the real saving is an estimate: a hit should cost about the 0 s figure plus a swap (roughly 0.5 s here), instead of 0.5 s plus the provider wait. On the Chromecast that is the difference between about 4 s and about 1 s, if the neighbour is ready.

Connection limits (fake provider, `--max-connections`):

- Limit of 1: the warm player is refused (HTTP 503) every time, so it never helps. Porch's own plain zapping already hit 503 and "Reconnecting" on some presses on this fake (9 refusals in 6 presses, 3 of 6 tunes logged ready within the run). The fake frees a slot within about 0.25 s of the viewer closing, so a real one-connection provider may be worse or better. Warm on raised the refusals to 16 because the warm player's own loader retries.
- Limit of 2: warm made it worse, not better. Tune ready went from about 2.4 s to about 4.5 s, with 12 refusals against 0. The old stream, the warm stream and the new stream overlap for a moment on a zap. The new stream is refused, and Porch's retry waits 1 s first.
- No limit: warm works and costs nothing visible on the emulator.

Timing window: the warm player starts only after the current channel is playing, then needs the provider wait again. With a 3.2 s provider wait that is roughly 4 s of playing plus 3.2 s of warming, about 7 s after a press. A viewer who zaps faster than that (the usual case when flicking through channels) never lands on a warm channel. The prototype's neighbour guess is the last zap direction; the first zap after opening has no warm channel.

## Risks

- Connections: the big one. Providers commonly allow 1 or 2 streams per login. Warming is a second stream. At 1 it never works and adds refused requests. At 2 it can make zapping slower unless the warm stream is closed before the new main stream opens, and then the warm picture is gone.
- Provider reaction: extra refused or short-lived connections can look like abuse to a provider. Unknown without asking one.
- Chromecast memory and decoder: a second ExoPlayer holds a buffer (20 s cap here) and may claim a second hardware decoder. Not measured; the emulator cannot say. Cheap Chromecasts often have one hardware video decoder.
- Data use: a warmed neighbour downloads video nobody watches, about the stream's bitrate for as long as it is held (an HD stream is roughly 2 to 4 MB per second). Two neighbours would double it.
- Not tested: a real provider, the Chromecast, HLS channels, the swap itself, catch-up.

## Recommendation

Do not build warm neighbours as a default. The best case saves about 3 s but only for slow, patient zapping, it needs a second connection that many logins do not allow, and with a limit of 2 it made zapping about 2 s slower in this test.

If Ruben wants to go further, in order of cost:

1. Ask what the provider's connection limit is. If it is 1, stop: warm cannot work.
2. Cheaper idea first: after a settled zap (viewer stays 2 s), start only the next neighbour, and only when a setting is on and the limit is 3 or more. Still needs the swap.
3. A different cheap fix: show the new channel's name and programme at once and a clear "Starting..." line (already there), so the wait feels shorter.
4. Before building the swap, measure the same four rows on the Chromecast against the real provider, once, with a test Ruben runs.

## What is in this PR

- `tools/fakeiptv/server.py`: `--start-delay` and `--max-connections` (off by default) and three unit tests.
- `app/src/main/java/tv/porch/app/player/WarmProbe.kt` and a few lines in `PlayerScreen.kt`: the prototype. Debug builds only, and only when `files/warm_neighbour.flag` exists in the app's folder. Logs time and an HTTP code, never an address. Safe to delete if the answer is no.
