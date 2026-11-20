"""Tests for the fake provider. No internet. Run from the repo root:
python3 -I -m unittest discover -s tools/fakeiptv -v
"""
import contextlib
import gzip
import io
import json
import os
import re
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

import server

AUTH = "username=demo&password=demo-pass"


def get(base, path):
    """Return (status, headers, body). HTTP errors come back as values."""
    try:
        with urllib.request.urlopen(base + path, timeout=10) as r:
            return r.status, r.headers, r.read()
    except urllib.error.HTTPError as e:
        with e:
            return e.code, e.headers, e.read()


def parse_xmltv_time(text):
    m = re.fullmatch(r"(\d{14})(?: ([+-]\d{4}))?", text)
    naive = datetime.strptime(m.group(1), "%Y%m%d%H%M%S")
    zone = m.group(2) or "+0000"
    sign = 1 if zone[0] == "+" else -1
    offset = timedelta(hours=int(zone[1:3]), minutes=int(zone[3:5])) * sign
    return (naive - offset).replace(tzinfo=timezone.utc)


class FakeProviderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        names = []
        for i in range(3):
            name = f"seg{i}.ts"
            with open(os.path.join(cls.tmp.name, name), "wb") as f:
                f.write(bytes([0x47]) + bytes(187))
            names.append({"file": name, "duration": 0.1})
        with open(os.path.join(cls.tmp.name, "index.json"), "w") as f:
            json.dump({"segments": names}, f)
        cls.old_cache = os.environ.get("PORCH_FAKEIPTV_CACHE")
        os.environ["PORCH_FAKEIPTV_CACHE"] = cls.tmp.name
        cls.httpd = server.make_server(0)
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.tmp.cleanup()
        if cls.old_cache is None:
            os.environ.pop("PORCH_FAKEIPTV_CACHE", None)
        else:
            os.environ["PORCH_FAKEIPTV_CACHE"] = cls.old_cache

    def setUp(self):
        self.httpd.state.reset_switches()

    def test_m3u_and_api_describe_the_same_channels(self):
        _, _, m3u = get(self.base, "/playlist.m3u")
        m3u_ids = re.findall(r"/live/demo/demo-pass/(\d+)\.(?:ts|m3u8)", m3u.decode())
        m3u_names = [l.rsplit('",', 1)[1] for l in m3u.decode().splitlines() if l.startswith("#EXTINF")]
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_live_streams")
        streams = json.loads(raw)
        live = [s for s in streams if s["stream_type"] == "live"]
        movies = [s for s in streams if s["stream_type"] == "movie"]
        self.assertEqual([str(s["stream_id"]) for s in live], m3u_ids)
        self.assertEqual([s["name"] for s in live], m3u_names)
        self.assertIn("Weather, Wind and Tide", m3u_names)
        self.assertEqual(len(movies), 1)
        self.assertNotIn(str(movies[0]["stream_id"]), m3u_ids)
        self.assertIsInstance(live[0]["category_id"], str)

    def test_vod_streams_can_be_listed_by_category(self):
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_vod_streams&category_id=21")
        films = json.loads(raw)
        self.assertEqual([f["name"] for f in films], ["Harbour Lights (2019)", "Copper Summit"])
        _, _, every = get(self.base, f"/player_api.php?{AUTH}&action=get_vod_streams")
        self.assertEqual(len(json.loads(every)), 5)

    def test_series_lists_and_info_in_both_episode_shapes(self):
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_series_categories")
        self.assertEqual(len(json.loads(raw)), 4)
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_series&category_id=31")
        shows = json.loads(raw)
        self.assertEqual([s["name"] for s in shows], ["Harbour Detectives"])
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_series_info&series_id=8001")
        episodes = json.loads(raw)["episodes"]
        self.assertIsInstance(episodes, dict)
        self.assertEqual(sorted(episodes), ["1", "2"])
        self.assertEqual(len(episodes["1"]), 3)
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_series_info&series_id=8002")
        self.assertIsInstance(json.loads(raw)["episodes"], list)

    def test_a_title_in_two_categories_is_listed_under_both(self):
        # category_id still names only the first category, as some panels send it.
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_series&category_id=32")
        shows = json.loads(raw)
        self.assertEqual([s["name"] for s in shows], ["Harbour Detectives", "Flatmates"])
        self.assertEqual(shows[0]["category_id"], "31")
        _, _, raw = get(self.base, f"/player_api.php?{AUTH}&action=get_vod_streams&category_id=23")
        self.assertEqual([f["name"] for f in json.loads(raw)], ["Copper Summit", "Marble Island"])
        _, _, every = get(self.base, f"/player_api.php?{AUTH}&action=get_series")
        self.assertEqual(len(json.loads(every)), 4)

    def test_series_episode_plays_like_a_movie(self):
        # A wrong login proves the route reaches the movie handler without streaming the clip.
        status, _, _ = get(self.base, "/series/demo/wrong-pass/8101.mp4")
        self.assertEqual(status, 403)

    def test_gzip_guide_matches_plain_guide_and_has_formula_1_on_now(self):
        _, _, plain = get(self.base, "/guide.xml")
        _, _, packed = get(self.base, "/guide.xml.gz")
        self.assertEqual(gzip.decompress(packed), plain)
        root = ET.fromstring(plain)
        now = datetime.now(timezone.utc)
        on_now = [
            p.findtext("title")
            for p in root.iter("programme")
            if parse_xmltv_time(p.get("start")) <= now < parse_xmltv_time(p.get("stop"))
            and p.findtext("title", "").startswith("Formula 1")
        ]
        self.assertEqual(on_now, ["Formula 1: Practice 1"])

    def test_xtream_guide_address_checks_the_login(self):
        status, _, body = get(self.base, "/xmltv.php?" + AUTH)
        self.assertEqual(status, 200)
        self.assertEqual(body, get(self.base, "/guide.xml")[2])
        status, _, _ = get(self.base, "/xmltv.php?username=demo&password=wrong-pass")
        self.assertEqual(status, 403)

    def test_switches_change_what_the_client_sees(self):
        get(self.base, "/control/set?auth=401")
        status, _, _ = get(self.base, f"/player_api.php?{AUTH}")
        self.assertEqual(status, 401)
        self.httpd.state.reset_switches()
        get(self.base, "/control/set?guide=empty")
        _, _, xml = get(self.base, "/guide.xml")
        root = ET.fromstring(xml)
        self.assertGreater(len(root.findall("channel")), 0)
        self.assertEqual(len(root.findall("programme")), 0)

    def test_timeshift_prints_the_programme_at_that_time(self):
        when = datetime.now(timezone.utc) - timedelta(minutes=10)
        stamp = when.strftime("%Y-%m-%d:%H-%M")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            status, _, body = get(self.base, f"/timeshift/demo/demo-pass/60/{stamp}/6.ts")
        self.assertEqual(status, 200)
        self.assertTrue(body.startswith(b"\x47"))
        line = next(l for l in out.getvalue().splitlines() if l.startswith("CATCH-UP asked"))
        self.assertIn("channel 6", line)
        self.assertIn(f"start {stamp}", line)
        self.assertIn('"Formula 1: Practice 1"', line)
        self.assertNotIn("demo-pass", out.getvalue())

    def test_timeshift_stamp_is_read_in_the_panel_zone(self):
        from zoneinfo import ZoneInfo
        when = datetime.now(timezone.utc) - timedelta(minutes=10)
        stamp = when.astimezone(ZoneInfo("Australia/Sydney")).strftime("%Y-%m-%d:%H-%M")
        old, server.SERVER_TZ = server.SERVER_TZ, "Australia/Sydney"
        try:
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                status, _, _ = get(self.base, f"/timeshift/demo/demo-pass/60/{stamp}/6.ts")
        finally:
            server.SERVER_TZ = old
        self.assertEqual(status, 200)
        line = next(l for l in out.getvalue().splitlines() if l.startswith("CATCH-UP asked"))
        self.assertIn(f"read as Australia/Sydney = {when:%Y-%m-%dT%H:%MZ}", line)
        self.assertIn('guide programme at that Australia/Sydney time: "Formula 1: Practice 1"', line)


if __name__ == "__main__":
    unittest.main()
