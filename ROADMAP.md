# Roadmap

Porch 1.0 is a player. You bring your own playlist and guide. These are the next pieces, in the order they matter. None of them are in this version.

## Next

Movies, Series, logos, Sport and a simple mode are built. These are next, in this order.

1. **Remember audio and subtitle choices per channel.** Pick a language once and the channel opens with it next time.
2. **Copy the whole setup to another TV.** Sources, favourites and hidden groups in one go, so a second TV is set up once.
3. **Posters for Movies and Series** from TMDB (needs an account and key).
4. **Sleep timer**, then **Plex server** support.
5. **A shared core and a phone layout**, then Apple devices.

Ideas, not planned yet: sync across devices, alerts for a whole team (one-programme reminders shipped on 3 Nov and fire only while Porch is open).

## Left out of 1.0 on purpose

- **Multi-view.** Off the roadmap (Ruben, 2 Nov 2026). Many services allow one stream at a time, and the Chromecast is already short on GPU.
- **Long timeshift.** Pause and rewind only use the player's short buffer (about 20 seconds). There is no separate recorder for live TV.
- **M3U catch-up playback.** If a playlist line says `catchup-days`, Porch stores the number and does not play it. Catch-up playback is Xtream only.
- **Encrypted logins.** Usernames and passwords sit in the local database. Backups are off.
- **Recording.** Too heavy for a Chromecast-class box (2 GB of memory, 8 GB of storage).
- **Profiles and a parental PIN.** One shared favourites list. Simple mode, with a code to leave it, covers a family TV.
- **Warm channel changes.** Opening the channels above and below ahead of time needs extra streams, and most logins allow 1 or 2. In a test with a limit of 2 it made changes slower (notes in `docs/spikes/2026-11-zap-speed.md`).
- **A hold-down mini-guide.** Up and down change channel, so a hold-down guide would fight the zap. The overlay shows what is next, and Right opens what else is on.
