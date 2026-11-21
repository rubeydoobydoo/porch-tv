#!/usr/bin/env python3
"""Fake IPTV provider for developing Porch. Dev only. Python 3 standard library only.

    python3 tools/fakeiptv/server.py [--port 8900] [--big] [--tz Europe/London]
    python3 tools/fakeiptv/server.py fetch-video

Every name, login and address here is made up. The only code that touches the
internet is fetch-video, and it asks first.
"""
import argparse
import base64
import gzip
import html
import json
import math
import os
import random
import re
import select
import socket
import struct
import sys
import threading
import time
import zlib
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urljoin, urlsplit
from xml.sax.saxutils import escape

CLIP_SOURCE = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
CLIP_CREDIT = "Big Buck Bunny (c) Blender Foundation, CC BY 3.0 (bigbuckbunny.org)"
MAX_SEGMENTS = 60
SERVER_TZ = "Europe/London"  # the panel zone it reports and reads catch-up stamps in; --tz changes it
USER, PASSWORD = "demo", "demo-pass"  # fake, labelled as fake in the README
BIG_CHANNELS = 20000
BIG_TTL, SMALL_TTL = 12 * 3600, 600
MISBEHAVE_AFTER = 20  # seconds into a stream
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")

SWITCHES = {
    "auth": ["ok", "401", "403"],
    "stream": ["ok", "drop20", "end20", "stall20", "dead1", "dead", "slow2"],  # dead1: channel 1 answers 503; dead: every stream does; slow2: channel 1 is dead and channel 2 waits 5 s before it starts
    "guide": ["ok", "empty", "html", "gzplain", "404"],
    "playlist": ["ok", "empty"],
    "hls_window": ["6", "180"],
}

# ---------------------------------------------------------------- channels


@dataclass
class Channel:
    id: int
    name: str
    tvg: str  # "" means the playlist has no tvg-id
    group: str
    ext: str = "ts"
    archive: int = 0
    days: int = 0  # 0 with archive 1 = panel gives no duration

    @property
    def gid(self):  # id used in the guide
        return self.tvg or re.sub(r"[^a-z0-9]+", ".", self.name.lower()).strip(".")


def _c(i, name, tvg, group, ext="ts", archive=0, days=0):
    return Channel(i, name, tvg, group, ext, archive, days)


BASE = [
    _c(1, "Harbour News 24 HD", "harbour.news", "UK | News"),
    _c(2, "Harbour News 24 SD", "harbour.news", "UK | News"),
    _c(3, "Northgate Headlines", "northgate.news", "UK | News"),
    _c(4, "Weather, Wind and Tide", "weather.tide", "UK | News", "m3u8"),
    _c(5, "Midland Report", "", "UK | News"),
    _c(6, "Fastlane Motorsport", "fastlane.motor", "UK | Sport", "ts", 1, 3),
    _c(7, "Fastlane Extra", "fastlane.extra", "UK | Sport", "ts", 1, 3),
    _c(8, "Terrace Football", "terrace.football", "UK | Sport", "m3u8", 1, 3),
    _c(9, "Cricket Ground Live", "cricket.ground", "UK | Sport", "ts", 1, 0),
    _c(10, "Racket Court", "racket.court", "UK | Sport", "ts", 1, 3),
    _c(11, "Little Lantern Kids", "lantern.kids", "EN: Kids"),
    _c(12, "Cartoon Cove", "cartoon.cove", "EN: Kids"),
    _c(13, "Story Hour Kids", "storyhour.kids", "EN: Kids", "m3u8"),
    _c(14, "Desert Cinema", "desert.cinema", "AR | Movies"),
    _c(15, "Oasis Films", "oasis.films", "AR | Movies", "m3u8"),
    _c(16, "Classic Reels", "classic.reels", "AR | Movies"),
    _c(17, "Night Owl Films", "nightowl.films", "AR | Movies"),
    _c(18, "Vinyl Radio TV", "vinyl.radio", "Music"),
    _c(19, "Club Beats", "club.beats", "Music"),
    _c(20, "Acoustic Corner", "acoustic.corner", "Music"),
]
MOVIE_ID = 900001
EXTGRP_GROUPS = {"Music"}  # these carry #EXTGRP instead of group-title
GROUPS = ["UK | News", "UK | Sport", "EN: Kids", "AR | Movies", "Music"]
BULK_GROUPS = [f"Bulk | {c}" for c in "ABCDEFGH"]


def iter_channels(big):
    yield from BASE
    if big:
        for i in range(len(BASE) + 1, BIG_CHANNELS + 1):
            yield _c(i, f"Bulk Channel {i}", f"bulk.{i}", BULK_GROUPS[i % 8], "m3u8" if i % 9 == 0 else "ts")


# Fake films for the Movies tab. Invented names only.
VOD_CATEGORIES = [("21", "EN | ACTION"), ("22", "UK | ACTION"), ("23", "EN | DRAMA"), ("24", "FR | COMEDIE")]


def vod_films():
    films = [
        {"num": 1, "name": "Harbour Lights (2019)", "stream_id": 9001, "stream_icon": "", "category_id": "21",
         "container_extension": "mp4", "added": "1700000300", "rating": "7.4", "year": ""},
        # Listed in two categories, the way some panels send it: category_id names only the first.
        {"num": 2, "name": "Copper Summit", "stream_id": 9002, "stream_icon": "", "category_id": "21", "category_ids": [21, 23],
         "container_extension": "", "added": "1700000200", "rating": 6.1, "year": "2016"},
        {"num": 3, "name": "Winter Garden", "stream_id": "9003", "stream_icon": "", "category_id": "22",
         "container_extension": "mkv", "added": "1700000100", "rating": 0, "year": "2014"},
        {"num": 4, "name": "Marble Island", "stream_id": 9004, "stream_icon": "", "category_id": "23",
         "container_extension": "mp4", "added": "1700000050", "rating": "8.0", "year": "2021"},
        {"num": 5, "name": "Le Petit Falcon", "stream_id": 9005, "stream_icon": "", "category_id": "24",
         "container_extension": "mp4", "added": "1700000000", "rating": "5.5", "year": "2012"},
    ]
    return films


def vod_info(vod_id):
    film = next((f for f in vod_films() if str(f["stream_id"]) == str(vod_id)), None)
    if not film:
        return {}
    return {"info": {"plot": "An example plot for " + film["name"] + ".", "duration": "01:52:10",
                     "duration_secs": 6730, "releasedate": "2019-05-01", "genre": "Action, Drama",
                     "rating": "7.4", "movie_image": ""},
            "movie_data": {"stream_id": film["stream_id"], "name": film["name"]}}


# Fake shows for the Series tab. Invented names only.
SERIES_CATEGORIES = [("31", "EN | CRIME DRAMA"), ("32", "EN | SITCOMS"), ("33", "EN | LIFESTYLE"), ("34", "EN | KIDS")]


def series_list():
    return [
        # Listed in two categories, the way some panels send it: category_id names only the first.
        {"num": 1, "name": "Harbour Detectives", "series_id": 8001, "cover": "", "category_id": "31", "category_ids": [31, 32],
         "rating": "7.8", "releaseDate": "2018-04-02", "plot": "Two officers and a lot of fog.",
         "genre": "Crime, Drama", "last_modified": "1700000400"},
        {"num": 2, "name": "Flatmates", "series_id": "8002", "cover": "", "category_id": "32",
         "rating": 6.9, "release_date": "2015-09-10", "plot": "Four people, one bathroom.",
         "genre": "Comedy", "last_modified": "1700000300"},
        {"num": 3, "name": "Garden Rescue Squad", "series_id": 8003, "cover": "", "category_id": "33",
         "rating": "0", "year": "2020", "plot": "", "genre": "Lifestyle", "last_modified": "1700000200"},
        {"num": 4, "name": "Pixel Pals", "series_id": 8004, "cover": "", "category_id": "34",
         "rating": "5.2", "year": "", "plot": "", "genre": "Kids", "last_modified": "1700000100"},
    ]


def in_category(item, wanted):
    """True when no category is asked for, or the item is listed under it (its category_id or category_ids)."""
    if not wanted:
        return True
    return wanted == item["category_id"] or wanted in [str(c) for c in item.get("category_ids", [])]


def _episode(eid, season, num):
    return {"id": str(eid), "episode_num": num, "title": f"Episode {season}x{num}",
            "container_extension": "mp4", "season": season,
            "info": {"duration": "00:00:40", "duration_secs": 40, "plot": f"Plot of {season}x{num}."}}


def series_info(series_id):
    show = next((s for s in series_list() if str(s["series_id"]) == str(series_id)), None)
    if not show:
        return {}
    info = {"name": show["name"], "plot": show["plot"], "genre": show["genre"], "cover": "",
            "releaseDate": show.get("releaseDate") or show.get("release_date") or "", "rating": str(show["rating"])}
    base = int(show["series_id"]) * 10 + 100
    if str(series_id) == "8001":  # episodes keyed by season number
        eps = {str(s): [_episode(base + s * 10 + n, s, n) for n in (1, 2, 3)] for s in (1, 2)}
    else:  # a list of lists, as some panels send it
        eps = [[_episode(base + s * 10 + n, s, n) for n in (1, 2, 3)] for s in (1, 2)]
    return {"info": info, "episodes": eps}


def category_id(group):
    return str((GROUPS + BULK_GROUPS).index(group) + 1)


def m3u_lines(host, big):
    yield "#EXTM3U\n"
    for ch in iter_channels(big):
        attrs = ['tvg-id="%s"' % ch.tvg] if ch.tvg else []
        attrs += [f'tvg-name="{ch.name}"', f'tvg-logo="http://{host}/logo/{ch.id}.png"']
        if ch.group not in EXTGRP_GROUPS:
            attrs.append(f'group-title="{ch.group}"')
        attrs.append(f'tvg-chno="{ch.id}"')
        if ch.archive:
            attrs.append('catchup="default"')
            if ch.days:
                attrs += [f'catchup-days="{ch.days}"', f'tvg-rec="{ch.days}"']
            attrs.append(f'catchup-source="http://{host}/catchup/{ch.id}.ts?utc={{utc}}&lutc={{lutc}}"')
        yield f"#EXTINF:-1 {' '.join(attrs)},{ch.name}\n"
        if ch.group in EXTGRP_GROUPS:
            yield f"#EXTGRP:{ch.group}\n"
        yield f"http://{host}/live/{USER}/{PASSWORD}/{ch.id}.{ch.ext}\n"


def json_array(items):
    first = True
    yield "["
    for item in items:
        yield ("" if first else ",") + json.dumps(item)
        first = False
    yield "]"


def api_streams(host, big):
    for n, ch in enumerate(iter_channels(big), 1):
        yield {
            "num": n, "name": ch.name, "stream_type": "live", "stream_id": ch.id,
            "stream_icon": f"http://{host}/logo/{ch.id}.png", "epg_channel_id": ch.tvg or None,
            "category_id": category_id(ch.group), "tv_archive": ch.archive,
            "tv_archive_duration": str(ch.days) if ch.days else None,
        }
    yield {"num": BIG_CHANNELS + 1, "name": "Fake Movie Night", "stream_type": "movie",
           "stream_id": MOVIE_ID, "stream_icon": "", "epg_channel_id": None,
           "category_id": category_id("AR | Movies"), "tv_archive": 0, "tv_archive_duration": None}


# ------------------------------------------------------------------- guide

ZONES = {"fastlane.extra": "+1100", "cartoon.cove": None}  # default +0000
DISPLAY = {"cartoon.cove": "Cartoon Cove TV", "harbour.news": "Harbour News 24"}
POOLS = {
    "UK | News": ("News", ["Morning Bulletin", "Harbour Report", "Business Round-up", "Weather Watch"]),
    "UK | Sport": ("Sport", ["Match Preview", "Highlights Show", "Live Action", "Sports Desk"]),
    "EN: Kids": ("Kids", ["Lantern Tales", "Cove Capers", "Story Time", "Puzzle Pals"]),
    "AR | Movies": ("Movies", ["Feature Film: The Lantern Maker", "Classic Matinee", "Late Feature"]),
    "Music": ("Music", ["Rock & Roll Years", "Chart Countdown", "Unplugged Hour"]),
}
BULK_POOL = ("General", ["Bulk Show One", "Bulk Show Two", "Bulk Show Three"])


def guide_channels(big):
    seen = {}
    for ch in iter_channels(big):
        if ch.gid not in seen:
            seen[ch.gid] = ch
    return list(seen.values())


def fixed_programmes(gid, now):
    if gid != "fastlane.motor":
        return []
    f1 = lambda t, a, b: (now + timedelta(minutes=a), now + timedelta(minutes=b), t,
                          "Made-up race weekend coverage.", ["Sport", "Motorsport"])
    return [f1("Formula 1: Qualifying", -240, -180),    # finished about 3 hours ago
            f1("Formula 1: Practice 1", -30, 75),       # on now
            f1("Formula 1: Race", 180, 300)]


def schedule(ch, now, short=False):
    """Programmes for one guide channel as (start, stop, title, desc, categories)."""
    rng = random.Random(ch.gid)
    category, titles = POOLS.get(ch.group, BULK_POOL)
    fixed = fixed_programmes(ch.gid, now)
    cursor = now - timedelta(minutes=rng.randint(30, 240)) if short else now - timedelta(days=3)
    end = now + timedelta(days=2)
    made = 0
    while made < 15 if short else cursor < end:
        nxt = next((f for f in fixed if f[0] >= cursor), None)
        if nxt and nxt[0] == cursor:
            yield nxt
            cursor = nxt[1]
        else:
            stop = cursor + timedelta(minutes=rng.randrange(30, 121, 15))
            if nxt and stop > nxt[0]:
                stop = nxt[0]
            title = rng.choice(titles)
            yield (cursor, stop, title, f"{title}. A made-up programme for testing.", [category])
            cursor = stop
        made += 1


def xmltv_time(dt, zone):
    if zone is None:
        return dt.strftime("%Y%m%d%H%M%S")
    sign = 1 if zone[0] == "+" else -1
    local = dt + sign * timedelta(hours=int(zone[1:3]), minutes=int(zone[3:5]))
    return f"{local:%Y%m%d%H%M%S} {zone}"


def guide_xml(now, big, empty):
    chans = guide_channels(big)
    yield '<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="porch-fakeiptv">\n'
    for ch in chans:
        name = DISPLAY.get(ch.gid) or re.sub(r" (HD|SD)$", "", ch.name)
        yield f'<channel id="{ch.gid}"><display-name>{escape(name)}</display-name></channel>\n'
    for ch in ([] if empty else chans):
        zone = ZONES.get(ch.gid, "+0000")
        for start, stop, title, desc, cats in schedule(ch, now, short=big and ch.id > len(BASE)):
            cat = "".join(f"<category>{escape(c)}</category>" for c in cats)
            yield (f'<programme start="{xmltv_time(start, zone)}" stop="{xmltv_time(stop, zone)}" '
                   f'channel="{ch.gid}"><title>{escape(title)}</title><desc>{escape(desc)}</desc>{cat}</programme>\n')
    yield "</tv>\n"


def programme_at(state, channel_id, when):
    ch = next((c for c in BASE if c.id == channel_id), None)
    if ch:
        for start, stop, title, _, _ in schedule(ch, state.anchor()):
            if start <= when < stop:
                return f'"{title}" {start:%H:%M}-{stop:%H:%M} UTC'
    return "none found"


# ------------------------------------------------------------------- state


class State:
    def __init__(self, big=False, cache_dir=None):
        self.big = big
        self.cache = cache_dir or os.environ.get("PORCH_FAKEIPTV_CACHE") or os.path.expanduser("~/.cache/porch-fakeiptv")
        self.lock = threading.RLock()
        self.sw = {}
        self.reset_switches()
        self._clip = None
        self._anchor = None
        self._anchor_ts = 0.0
        self._small = None
        # Spike switches, set at start-up, off by default: seconds before a live stream sends anything, and how many live streams may be open at once (0 = no limit).
        self.start_delay = 0.0
        self.max_connections = 0
        self.open_streams = 0

    def take_stream(self):
        """Count a live stream as open. False when the connection limit is already reached."""
        with self.lock:
            if self.max_connections and self.open_streams >= self.max_connections:
                return False
            self.open_streams += 1
            return True

    def drop_stream(self):
        with self.lock:
            self.open_streams -= 1

    def reset_switches(self):
        self.sw = {k: v[0] for k, v in SWITCHES.items()}

    def anchor(self):
        """The 'now' the guide is built around. Held steady so repeat fetches match."""
        with self.lock:
            ttl = BIG_TTL if self.big else SMALL_TTL
            if self._anchor is None or time.time() - self._anchor_ts > ttl:
                self._anchor = datetime.now(timezone.utc).replace(second=0, microsecond=0)
                self._anchor_ts = time.time()
                self._small = None
                if self.big:
                    self._drop(self.big_path())
            return self._anchor

    @staticmethod
    def _drop(path):
        try:
            os.remove(path)
        except OSError:
            pass

    def big_path(self):
        return os.path.join(self.cache, "guide-big.xml")

    def guide_source(self, empty):
        """Guide XML as bytes (small) or an iterator of bytes (big)."""
        now = self.anchor()
        with self.lock:
            if empty:
                chunks = (s.encode() for s in guide_xml(now, self.big, True))
                return chunks if self.big else b"".join(chunks)
            if not self.big:
                if self._small is None:
                    self._small = "".join(guide_xml(now, False, False)).encode()
                return self._small
            path = self.big_path()
            if not os.path.exists(path):
                os.makedirs(self.cache, exist_ok=True)
                log("Building the big guide file once (about 300,000 programmes). Please wait.")
                with open(path + ".tmp", "wb") as f:
                    for s in guide_xml(now, True, False):
                        f.write(s.encode())
                os.replace(path + ".tmp", path)
        return read_chunks(path)

    def clip(self):
        """List of (bytes, seconds), or None until fetch-video has run."""
        with self.lock:
            if self._clip is None:
                try:
                    with open(os.path.join(self.cache, "index.json")) as f:
                        index = json.load(f)["segments"]
                    clip = []
                    for s in index:
                        with open(os.path.join(self.cache, s["file"]), "rb") as f:
                            clip.append((f.read(), float(s["duration"])))
                    self._clip = clip or None
                except (OSError, ValueError, KeyError):
                    self._clip = None
            return self._clip


def read_chunks(path):
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            yield chunk


def gzip_chunks(chunks):
    z = zlib.compressobj(6, zlib.DEFLATED, 31)
    for c in chunks:
        if out := z.compress(c):
            yield out
    yield z.flush()


def log(msg):
    print(msg, flush=True)


def mask(text):
    text = re.sub(r"(/(?:live|timeshift)/[^/\s]+/)[^/\s]+", r"\1***", text)
    return re.sub(r"(password=)[^&\s\"]*", r"\1***", text)


# ----------------------------------------------------------------- handler

DISCONNECTS = (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, TimeoutError)


class Handler(BaseHTTPRequestHandler):
    server_version = "FakeIPTV/1"

    def log_message(self, fmt, *args):
        log(mask("%s - %s" % (self.address_string(), fmt % args)))

    @property
    def st(self):
        return self.server.state

    @property
    def host(self):
        return self.headers.get("Host") or "%s:%s" % self.server.server_address[:2]

    def send(self, status, ctype, body, headers=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        if isinstance(body, bytes):
            self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()  # HTTP/1.0: no length means the stream ends when we close
        if isinstance(body, bytes):
            self.wfile.write(body)
        else:
            for chunk in body:
                self.wfile.write(chunk)

    def text(self, status, message):
        self.send(status, "text/plain; charset=utf-8", message.encode() + b"\n")

    def blocked(self):
        mode = self.st.sw["auth"]
        if mode in ("401", "403"):
            self.text(int(mode), "Fake provider says: not allowed (auth switch).")
            return True
        return False

    def do_GET(self):
        try:
            self.route()
        except DISCONNECTS:
            pass

    def route(self):
        url = urlsplit(self.path)
        path = unquote(url.path)
        q = {k: v[-1] for k, v in parse_qs(url.query).items()}
        big = self.st.big
        if path in ("/", "/control"):
            return self.control()
        if path == "/control/set":
            return self.set_switch(q)
        if path.startswith("/logo/"):
            return self.send(200, "image/png", PNG)
        if path == "/playlist.m3u":
            if self.blocked():
                return
            if self.st.sw["playlist"] == "empty":
                return self.send(200, "audio/x-mpegurl", b"#EXTM3U\n")
            return self.send_gen("audio/x-mpegurl", m3u_lines(self.host, big))
        if path == "/player_api.php":
            return self.player_api(q)
        if path in ("/guide.xml", "/guide.xml.gz"):
            return self.guide(path.endswith(".gz"))
        if path == "/xmltv.php":  # the guide address an Xtream login carries; real panels check the login here
            if (q.get("username"), q.get("password")) != (USER, PASSWORD):
                return self.text(403, "Fake provider says: not allowed (wrong login).")
            return self.guide(False)
        m = re.fullmatch(r"/seg/(\d+)\.ts", path)
        if m:
            return self.segment(int(m.group(1)))
        m = re.fullmatch(r"/live/([^/]+)/([^/]+)/(\d+)\.(ts|m3u8)", path)
        if m:
            return self.live(m.group(1), m.group(2), int(m.group(3)), m.group(4))
        m = re.fullmatch(r"/movie/([^/]+)/([^/]+)/(\d+)\.(\w+)", path)
        if m:
            return self.movie(m.group(1), m.group(2), int(m.group(3)))
        m = re.fullmatch(r"/series/([^/]+)/([^/]+)/(\d+)\.(\w+)", path)
        if m:
            return self.movie(m.group(1), m.group(2), int(m.group(3)))
        m = re.fullmatch(r"/timeshift/([^/]+)/([^/]+)/(\d+)/([^/]+)/(\d+)\.ts", path)
        if m:
            return self.catchup(m.group(1), m.group(2), int(m.group(5)), m.group(4), None)
        m = re.fullmatch(r"/catchup/(\d+)\.ts", path)
        if m:
            return self.catchup(USER, PASSWORD, int(m.group(1)), q.get("utc", ""), q.get("lutc", ""))
        self.text(404, "Not found")

    def send_gen(self, ctype, lines, **kw):
        chunks = (s.encode() for s in lines)
        self.send(200, ctype, chunks if self.st.big else b"".join(chunks), **kw)

    # -- Xtream
    def player_api(self, q):
        if self.blocked():
            return
        if (q.get("username"), q.get("password")) != (USER, PASSWORD):
            return self.send(200, "application/json", b'{"user_info":{"auth":0}}')
        action = q.get("action")
        if action == "get_live_categories":
            cats = [{"category_id": category_id(g), "category_name": g, "parent_id": 0}
                    for g in GROUPS + (BULK_GROUPS if self.st.big else [])]
            return self.send(200, "application/json", json.dumps(cats).encode())
        if action == "get_live_streams":
            return self.send_gen("application/json", json_array(api_streams(self.host, self.st.big)))
        if action == "get_vod_categories":
            cats = [{"category_id": i, "category_name": n, "parent_id": 0} for i, n in VOD_CATEGORIES]
            return self.send(200, "application/json", json.dumps(cats).encode())
        if action == "get_vod_streams":
            films = [f for f in vod_films() if in_category(f, q.get("category_id"))]
            return self.send(200, "application/json", json.dumps(films).encode())
        if action == "get_vod_info":
            return self.send(200, "application/json", json.dumps(vod_info(q.get("vod_id", ""))).encode())
        if action == "get_series_categories":
            cats = [{"category_id": i, "category_name": n, "parent_id": 0} for i, n in SERIES_CATEGORIES]
            return self.send(200, "application/json", json.dumps(cats).encode())
        if action == "get_series":
            shows = [x for x in series_list() if in_category(x, q.get("category_id"))]
            return self.send(200, "application/json", json.dumps(shows).encode())
        if action == "get_series_info":
            return self.send(200, "application/json", json.dumps(series_info(q.get("series_id", ""))).encode())
        if action:
            return self.send(200, "application/json", b"[]")
        info = {"user_info": {"auth": 1, "username": USER, "status": "Active"},
                "server_info": {"timezone": SERVER_TZ, "time_now": server_now()}}
        self.send(200, "application/json", json.dumps(info).encode())

    # -- guide
    def guide(self, packed):
        mode = self.st.sw["guide"]
        if mode == "404":
            return self.text(404, "No guide here (guide switch).")
        if mode == "html":
            return self.send(200, "text/html", b"<html><body><h1>Service busy</h1><p>Try later.</p></body></html>")
        body = self.st.guide_source(mode == "empty")
        if packed or mode == "gzplain":
            body = gzip.compress(body, mtime=0) if isinstance(body, bytes) else gzip_chunks(body)
        ctype = "application/gzip" if packed else "application/octet-stream" if mode == "gzplain" else "application/xml"
        self.send(200, ctype, body)

    # -- video
    def clip_or_503(self):
        clip = self.st.clip()
        if not clip:
            self.text(503, "Run fetch-video first")
        return clip

    def live(self, user, password, cid, ext):
        if self.blocked():
            return
        if (user, password) != (USER, PASSWORD):
            return self.text(403, "Wrong login")
        mode = self.st.sw["stream"]
        if mode == "slow2" and cid == 2:
            time.sleep(5)
        if mode == "dead" or (mode in ("dead1", "slow2") and cid == 1):
            return self.text(503, "Fake provider says: this stream is down (stream switch).")
        if self.st.start_delay:
            time.sleep(self.st.start_delay)
        if not self.st.take_stream():
            return self.text(503, "Fake provider says: too many connections (max-connections).")
        try:
            clip = self.clip_or_503()
            if clip:
                self.live_ts(clip, cid) if ext == "ts" else self.live_m3u8(clip, cid)
        finally:
            self.st.drop_stream()

    def movie(self, user, password, vid):
        if self.blocked():
            return
        if (user, password) != (USER, PASSWORD):
            return self.text(403, "Wrong login")
        clip = self.clip_or_503()
        if clip:
            self.live_ts(clip, vid)

    def live_ts(self, clip, cid):
        began = time.monotonic()
        self.send_response(200)
        self.send_header("Content-Type", "video/mp2t")
        self.end_headers()
        i = cid % len(clip)  # each channel starts somewhere different
        while True:
            data, seconds = clip[i]
            self.wfile.write(data)
            until = time.monotonic() + seconds
            while time.monotonic() < until:
                mode = self.st.sw["stream"]
                if mode in ("drop20", "end20", "stall20") and time.monotonic() - began >= MISBEHAVE_AFTER:
                    return self.misbehave(mode)
                time.sleep(min(0.25, max(0.0, until - time.monotonic())))
                if self.st.max_connections and self.client_left():
                    return  # a real provider frees the slot as soon as the viewer leaves
            i = (i + 1) % len(clip)

    def client_left(self):
        """True when the viewer has closed the connection (used with --max-connections, so a slot frees at once)."""
        try:
            ready, _, _ = select.select([self.request], [], [], 0)
            return bool(ready) and self.request.recv(1, socket.MSG_PEEK) == b""
        except OSError:
            return True

    def misbehave(self, mode):
        log(f"STREAM {mode}: acting up now")
        self.close_connection = True
        if mode == "end20":
            return  # HTTP/1.0 close: the client sees a clean end
        if mode == "drop20":
            sock = self.request
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
            os.close(sock.detach())  # linger 0 makes this a reset, not a polite close
            return
        while True:  # stall20: send nothing, keep the socket open until the client leaves
            ready, _, _ = select.select([self.request], [], [], 1)
            if ready and not self.request.recv(1024):
                return

    def live_m3u8(self, clip, cid):
        window = int(self.st.sw["hls_window"])
        target = max(1, round(sum(s for _, s in clip) / len(clip)))
        newest = int(time.time() // target) + cid * 7
        first = newest - window + 1
        lines = ["#EXTM3U", "#EXT-X-VERSION:3", f"#EXT-X-TARGETDURATION:{math.ceil(max(s for _, s in clip))}",
                 f"#EXT-X-MEDIA-SEQUENCE:{first}", f"#EXT-X-DISCONTINUITY-SEQUENCE:{first // len(clip)}"]
        for n in range(first, newest + 1):
            if n % len(clip) == 0 and n != first:
                lines.append("#EXT-X-DISCONTINUITY")  # the clip loops here
            lines += [f"#EXTINF:{clip[n % len(clip)][1]:.3f},", f"/seg/{n}.ts"]
        self.send(200, "application/vnd.apple.mpegurl", ("\n".join(lines) + "\n").encode())

    def segment(self, n):
        clip = self.clip_or_503()
        if clip:
            self.send(200, "video/mp2t", clip[n % len(clip)][0])

    def catchup(self, user, password, cid, sent, lutc):
        if self.blocked():
            return
        if (user, password) != (USER, PASSWORD):
            return self.text(403, "Wrong login")
        clip = self.clip_or_503()
        if not clip:
            return
        log(catchup_line(self.st, cid, sent, lutc))
        self.send(200, "video/mp2t", b"".join(d for d, _ in clip))  # from the start, not paced

    # -- control page
    def control(self):
        rows = []
        for name, values in SWITCHES.items():
            links = " ".join(f"<b>[{v}]</b>" if self.st.sw[name] == v else f'<a href="/control/set?{name}={v}">{v}</a>'
                             for v in values)
            rows.append(f"<p><code>{name}</code>: {links}</p>")
        page = ("<html><body><h1>Fake IPTV switches</h1>" + "".join(rows) +
                "<p>The stream switch only affects <code>.ts</code> channels, not the <code>.m3u8</code> ones.</p>"
                f'<p><a href="/control/set?reset=1">reset all</a></p><p>Clip: {"ready" if self.st.clip() else "not fetched"}</p>'
                "</body></html>")
        self.send(200, "text/html; charset=utf-8", page.encode())

    def set_switch(self, q):
        if "reset" in q:
            self.st.reset_switches()
            log("SWITCH all reset")
        for name, value in q.items():
            if name in SWITCHES and value in SWITCHES[name]:
                self.st.sw[name] = value
                log(f"SWITCH {name} = {value}")
            elif name != "reset":
                return self.text(400, f"Unknown switch or value: {html.escape(name)}={html.escape(value)}")
        self.send(303, "text/plain", b"", {"Location": "/control"})


def server_now():
    try:
        from zoneinfo import ZoneInfo
        return datetime.now(ZoneInfo(SERVER_TZ)).strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return time.strftime("%Y-%m-%d %H:%M:%S")


def catchup_line(state, cid, sent, lutc):
    iso = lambda dt: dt.strftime("%Y-%m-%dT%H:%MZ")
    try:
        from zoneinfo import ZoneInfo
        zone = ZoneInfo(SERVER_TZ)
    except Exception:
        zone = None

    def as_zone(naive):  # a clock reading in the panel zone, as the UTC moment it names
        return naive.replace(tzinfo=zone).astimezone(timezone.utc) if zone else None

    try:
        if lutc is None:  # timeshift: yyyy-MM-dd:HH-mm
            naive = datetime.strptime(sent, "%Y-%m-%d:%H-%M")
            utc, shown, zoned = naive.replace(tzinfo=timezone.utc), sent, as_zone(naive)
        else:  # catchup: utc and lutc are epoch seconds
            utc = datetime.fromtimestamp(int(sent), timezone.utc)
            shown = f"utc={sent} lutc={lutc}"
            zoned = as_zone(datetime.fromtimestamp(int(lutc), timezone.utc).replace(tzinfo=None))
    except (ValueError, OverflowError):
        return f"CATCH-UP asked: channel {cid} start {sent} (could not read that time)"
    zone_text = iso(zoned) if zoned else "unknown"
    zone_prog = programme_at(state, cid, zoned) if zoned else "unknown"
    return (f"CATCH-UP asked: channel {cid} start {shown} (read as UTC = {iso(utc)}; "
            f"read as {SERVER_TZ} = {zone_text}); guide programme at that UTC time: {programme_at(state, cid, utc)}; "
            f"guide programme at that {SERVER_TZ} time: {zone_prog}")


# --------------------------------------------------------------------- cli


def make_server(port, big=False, start_delay=0.0, max_connections=0):
    httpd = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    httpd.daemon_threads = True
    httpd.state = State(big)
    httpd.state.start_delay = start_delay
    httpd.state.max_connections = max_connections
    return httpd


def lan_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("192.0.2.1", 9))  # a UDP connect only picks a route; nothing is sent
            return s.getsockname()[0]
    except OSError:
        return "your-mac-ip"


def serve(port, big, start_delay=0.0, max_connections=0):
    httpd = make_server(port, big, start_delay, max_connections)
    st = httpd.state
    log("Fake IPTV provider. Dev only. Every channel and login here is made up.")
    for label, host in (("Android emulator", "10.0.2.2"), ("Real box on Wi-Fi", lan_ip())):
        log(f"\n{label}:\n  M3U playlist: http://{host}:{port}/playlist.m3u\n  Xtream server: http://{host}:{port}"
            f"   user: {USER}   password: see README (it is fake)\n  Guide: http://{host}:{port}/guide.xml (or .gz)")
    log(f"\nSwitches: http://localhost:{port}/control")
    log(f"Channels: {BIG_CHANNELS if big else len(BASE)}" + ("  (big mode)" if big else ""))
    log("Video clip: ready" if st.clip() else "Video clip: not fetched. Stream URLs answer 503 until you run fetch-video.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        log("\nStopped.")


def parse_hls(text, base):
    """Return (variants, segments): variants as [(bandwidth, url)], segments as [(url, seconds)]."""
    variants, segments, bw, seconds = [], [], None, None
    for line in (l.strip() for l in text.splitlines()):
        if line.startswith("#EXT-X-STREAM-INF"):
            m = re.search(r"BANDWIDTH=(\d+)", line)
            bw = int(m.group(1)) if m else 0
        elif line.startswith("#EXTINF:"):
            seconds = float(line[8:].split(",")[0])
        elif line and not line.startswith("#"):
            if bw is not None:
                variants.append((bw, urljoin(base, line)))
                bw = None
            elif seconds is not None:
                segments.append((urljoin(base, line), seconds))
                seconds = None
    return variants, segments


def fetch_video(cache_dir):
    from urllib.request import urlopen
    print(f"This downloads a test clip from:\n  {CLIP_SOURCE}\n{CLIP_CREDIT}\nIt goes into {cache_dir}")
    if input("Download it? [y/N] ").strip().lower() != "y":
        print("Cancelled. Nothing was downloaded.")
        return 1
    def grab(url):
        with urlopen(url, timeout=30) as r:
            return r.read()
    variants, segments = parse_hls(grab(CLIP_SOURCE).decode(), CLIP_SOURCE)
    if variants:
        media_url = min(variants)[1]  # lowest bandwidth
        _, segments = parse_hls(grab(media_url).decode(), media_url)
    os.makedirs(cache_dir, exist_ok=True)
    index = []
    for i, (url, seconds) in enumerate(segments[:MAX_SEGMENTS]):
        name = f"seg{i}.ts"
        with open(os.path.join(cache_dir, name), "wb") as f:
            f.write(grab(url))
        index.append({"file": name, "duration": seconds})
        print(f"  {name} ({seconds:.1f}s)")
    with open(os.path.join(cache_dir, "index.json"), "w") as f:
        json.dump({"segments": index, "credit": CLIP_CREDIT}, f)
    print(f"Done: {len(index)} segments.")
    return 0


def main(argv=None):
    global SERVER_TZ
    ap = argparse.ArgumentParser(description="Fake IPTV provider for Porch. Dev only.")
    ap.add_argument("command", nargs="?", choices=["serve", "fetch-video"], default="serve")
    ap.add_argument("--port", type=int, default=8900)
    ap.add_argument("--big", action="store_true", help="20,000 channels and about 300,000 programmes")
    ap.add_argument("--start-delay", type=float, default=0.0, help="seconds a live stream waits before it sends anything (spike; default 0)")
    ap.add_argument("--max-connections", type=int, default=0, help="live streams open at once; more get HTTP 503 (spike; default 0 = no limit)")
    ap.add_argument("--tz", default=SERVER_TZ, help="panel time zone to report and read catch-up in, e.g. Australia/Sydney")
    args = ap.parse_args(argv)
    try:
        from zoneinfo import ZoneInfo
        ZoneInfo(args.tz)
    except Exception:
        sys.exit(f"Unknown time zone: {args.tz}")
    SERVER_TZ = args.tz
    if args.command == "fetch-video":
        return fetch_video(State().cache)
    serve(args.port, args.big, args.start_delay, args.max_connections)
    return 0


if __name__ == "__main__":
    sys.exit(main())
