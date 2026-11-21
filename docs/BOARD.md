# Porch board

Updated 22 Nov 2026. Main is the launch build. The newest item is at the top of each list.

## NEEDS ME (Ruben)

1. **OK the next goal: remember audio and subtitle choices per channel.** Risk MID. Needs a Room schema change (v8 to v9, one new table, no existing table changes). It works when:
   - You pick a different audio track on a channel, zap away and come back (or close and reopen Porch): that channel opens with the same audio. Other channels keep their own default.
   - You turn subtitles on in a language, or off, on a channel: that channel remembers it the same way.
   - After a playlist refresh the choice is still there. It is saved against the channel's identity (the stream URL or Xtream id), not its row number.
   - If the remembered language is no longer in the stream, the channel plays its default with no error or message.
   - Upgrading from v8 keeps logins, favourites, hidden groups and resume points (`SchemaGuardTest`, plus a v8 to v9 install over the old build on the emulator).
   - Not in this goal: Movies and Series, a "use this for every channel" setting.
2. Ladders and 24/7 on the living room TV: tell me what looks wrong. Parked until you do (Claude can't see that TV).
3. Office Chromecast: re-add your real provider login (it has the fake one). Walk finding 7 (the Search key leaves Porch) waits for that remote.
4. Dad's install: next year (Ruben, 22 Nov). Steps are in `docs/INSTALL.md` "Setting it up for Dad". Pick his 4-digit code then and keep it safe.

## DOING

- Nothing.

## NEXT

1. Refresh the public copy porch-tv to the main that includes this board, if the secrets and personal-details scan is clean.
2. On your OK: build the audio and subtitle goal (one PR, Codex review, emulator upgrade check).
3. Copy the whole setup to another TV (PLAN roadmap 4).
4. TMDB posters (needs your account and key).

## DONE

- 22 Nov: PLAN.md, ROADMAP.md and this board brought up to date (PR 143). PLAN wins on the roadmap.
- 22 Nov: PR 142 merged (c69c899). Zap speed spike notes and fake provider switches; WarmProbe removed, no app code changed. Warm zapping shelved.
- 22 Nov: issue 77 closed. All five items shipped in PR 81.
- 22 Nov: 9 old worktrees and their merged branches removed. Kept (in use, locked, dirty or unmerged): see `docs/DECISIONS.md` 22 Nov.
- 19 to 21 Nov: launch push merged (PRs 121, 124 to 141). Main a83bd64 installed on the living room TV 20 Nov.
