import unittest

from measure import foreground_package, parse_meminfo, restore_foreground, summarize
from unittest.mock import Mock, patch


MEMINFO = """
Applications Memory Usage (in Kilobytes):
** MEMINFO in pid 1234 [example.launcher] **
 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:     2220                          16384
            Graphics:    32220                          32220
           TOTAL PSS:   112668            TOTAL RSS:   164396       TOTAL SWAP PSS:    22801
"""


class MemoryParsingTests(unittest.TestCase):
    def test_parses_summary_without_confusing_pss_with_rss(self):
        self.assertEqual(parse_meminfo(MEMINFO), {
            "pid": 1234, "pss_kib": 112668, "rss_kib": 164396,
            "swap_pss_kib": 22801, "graphics_kib": 32220,
        })

    def test_zero_graphics_is_valid(self):
        self.assertEqual(parse_meminfo(MEMINFO.replace("32220", "0"))["graphics_kib"], 0)

    def test_rejects_multiple_processes_instead_of_silently_undercounting(self):
        with self.assertRaises(ValueError):
            parse_meminfo(MEMINFO + MEMINFO.replace("1234", "5678"))

    def test_restoration_waits_for_original_activity_and_rejects_redirects(self):
        device = Mock()
        initial = "example.launcher/.HomeActivity"
        device.focus.side_effect = ["other.app/.Main", initial]
        with patch("measure.time.sleep"):
            restore_foreground(device, initial, attempts=2)
        device.start.assert_called_once_with(initial)
        device.focus.side_effect = None
        device.focus.return_value = "example.launcher/.DifferentActivity"
        with patch("measure.time.sleep"), self.assertRaises(ValueError):
            restore_foreground(device, initial, attempts=2)

    def test_missing_process_or_metric_is_an_error(self):
        for text in ("No process found for: example.launcher", MEMINFO.replace("Graphics:", "Other:"),
                     MEMINFO.replace("TOTAL RSS:", "RSS:")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_meminfo(text)

    def test_uses_focused_window_and_does_not_match_package_prefixes(self):
        self.assertEqual(foreground_package(
            "mCurrentFocus=Window{a33 u0 example.launcher/.HomeActivity}"), "example.launcher")
        self.assertEqual(foreground_package(
            "mCurrentFocus=Window{a33 u0 example.launcher.native/.HomeActivity}"), "example.launcher.native")
        with self.assertRaises(ValueError):
            foreground_package("mCurrentFocus=null")

    def test_summary_keeps_states_and_packages_separate(self):
        rows = [dict(package="a", state="settled", pss_kib=100, graphics_kib=10, rss_kib=200, swap_pss_kib=0),
                dict(package="a", state="settled", pss_kib=120, graphics_kib=20, rss_kib=240, swap_pss_kib=10),
                dict(package="a", state="background", pss_kib=80, graphics_kib=0, rss_kib=100, swap_pss_kib=20)]
        result = summarize(rows)
        self.assertEqual(result["a"]["settled"]["pss_kib"]["mean"], 110)
        self.assertEqual(result["a"]["background"]["graphics_kib"]["mean"], 0)
        self.assertEqual(result["a"]["settled"]["samples"], 2)


if __name__ == "__main__":
    unittest.main()
