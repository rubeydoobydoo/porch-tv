# Plan: frozen streams come back (Review Top 5 #2)

Status: built and proved 26 Oct 2026, PR #6. The stall rule ended up live-only and only while the player wants to play (review findings); see docs/DECISIONS.md.

## The bug

Once a stream is playing, `PlayerScreen.kt` waits in `awaitError`, which only wakes on a player
error. Two common drops never raise one:

- The server closes the connection cleanly. ExoPlayer goes to `STATE_ENDED`, the picture freezes,
  no banner, no retry. (Fake provider: `stream=end20`.)
- The server keeps the socket open and sends nothing, or trickles bytes. ExoPlayer sits in
  `STATE_BUFFERING` with a spinner. (Fake provider: `stream=stall20`.)

A hard reset (`stream=drop20`) does raise an error and already retries. The fix must keep that.

## Deliverable

One PR, branch `player-frozen-stream`, about 100 changed lines:

1. `domain/StreamHealth.kt` (new, pure Kotlin): decides what a player event means.
   Input: playback state, whether an error arrived, whether this is live or catch-up, how long
   the player has been buffering. Output: `null` (keep watching) or a drop reason
   (`ERROR`, `ENDED`, `STALLED`). Stall limit 15 seconds, a named constant.
2. `PlayerScreen.kt`: `awaitError` becomes `awaitDrop(player, isLive)`. It resumes on an error,
   on `STATE_ENDED` when live, or when buffering has lasted past the stall limit. The existing
   retry loop treats all three as a drop: "Reconnecting…", backoff, six tries, Retry button.
3. One JVM test, `StreamHealthTest`, as a table: error -> ERROR; ended + live -> ENDED;
   ended + catch-up -> null; buffering 14 s -> null; buffering 16 s -> STALLED.
4. `CLAUDE.md` line 62 and `docs/DECISIONS.md` updated (stall limit, catch-up end left alone).

## Limits

- Touch only the two files above, the new test, CLAUDE.md and DECISIONS.md.
- No new dependencies. JUnit4, JVM only (repo rule). The ExoPlayer side is proved on the emulator.
- Keep the attempt counter resetting after each successful start, so a stream that dies every
  25 seconds keeps coming back instead of giving up.

## Non-goals

- Catch-up reaching the end of the programme keeps today's behaviour (picture holds, no retry).
- Home button keeping the stream playing (`ON_STOP`), real rewind, catch-up clock. Separate work.
- Stalls before the first frame. Connect (12 s) and read (20 s) timeouts already cover those.

## It works when

1. With `stream=end20`, the picture freezes at 20 s, "Reconnecting…" shows within 2 s, and
   video is moving again within 10 s. (Emulator screenshot pair.)
2. With `stream=stall20`, the spinner is replaced by "Reconnecting…" within 15 to 17 s and
   video is moving again within 10 s after that. (Screenshot pair.)
3. With `stream=drop20`, the stream still reconnects exactly as it does today. (Screenshot.)
4. A catch-up stream that reaches its end does not show "Reconnecting…" and does not restart.
   (Fake provider catch-up, screenshot.)
5. `StreamHealthTest` passes, and fails when the `STATE_ENDED` branch is commented out
   (break, show red, restore).
6. `./gradlew testDebugUnitTest assembleDebug` is green locally and CI is green on the PR.
   Test count goes from 20 to 21.

## Steps

1. Branch. Write `StreamHealth` and its test first (test red, then green).
2. Swap `awaitError` for `awaitDrop` in the player loop.
3. Fake provider up, emulator `porch-tv`, run the four switch checks, save shots to
   `review-shots/frozen-stream/`.
4. Docs, PR, CI, Opus review of the diff, merge if green.
