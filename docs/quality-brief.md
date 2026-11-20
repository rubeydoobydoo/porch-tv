# Porch quality brief (Ruben, 9 Nov 2026, from a ChatGPT review)

The standard: someone unfamiliar with the app can pick up the remote, find a channel and recover from a mistake without instructions.

Build order: playback reliability, remote navigation, guide and favourites, visual consistency, then animation and finishing touches.

## Before calling a change finished, show

- Screenshots of the main screens and their focused states.
- A remote-only walk: open app, favourite a channel, guide, another channel, Back.
- Results on the Office Chromecast, not only the emulator.
- Launch and channel-start times, with the device, connection and stream used.
- Checks for missing guide data, broken artwork, unavailable streams, network loss and wake from sleep.
- Known gaps, including anything tested only on the emulator.

## Other points to hold to

- Long presses are shortcuts, never the only way to do something.
- Clear loading feedback straight after a selection. Retries have a limit; no endless spinner.
- Audio and subtitle choices are easy to find and remembered.
- Setup errors say what went wrong in plain words and what to do next.
- Search results are still there after coming back from playback.
- Refreshes keep the viewer's position. Artwork never blocks navigation.

## Where DESIGN.md and past decisions win

- Focus is the solid light fill, not an outline (DESIGN.md).
- Back on Home asks twice before leaving (decided earlier).
- D-pad, OK and Back only, so no number keys.
