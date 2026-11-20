# Porch: Studio designs, direction A (Spotlight)

Picked by Ruben on 29 Oct 2026. Build from these frames. They're the spec, and the HTML is not included.

- **Frames (1920x1080):** home, home-loading, player-overlay, player-panel, player-message, player-audio, guide, guide-empty, whats-on-now, search, settings, setup, how-porch-works.
- **Tokens:** `tokens.md` has the colours, type sizes, radius, focus scale, ring and motion; `tokens.json` has the same values in machine-readable form.
- **Remote:** `remote-notes.md` covers what each button does on each screen, which the still frames can't show.

## Rules
- Red means live only: the LIVE label, progress bars and the guide's now-line. Don't use it for errors or buttons.
- Show one focused item at a time.
  - Tiles, rows, cells and fields get a white ring and lift slightly (about 150 ms; no lift when animations are off).
  - Buttons and keys turn white with dark text.
- Use 12-hour times (8:30 pm).
- Use Inter only.
