"""Capture same-device launcher memory without changing Home or clearing app data."""

import argparse
from collections import defaultdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import time


METRICS = ("pss_kib", "rss_kib", "swap_pss_kib", "graphics_kib")
COMPONENT = re.compile(r"[A-Za-z][A-Za-z0-9_.]+/\.?[A-Za-z][A-Za-z0-9_.]+")


def parse_cpu_stat(text: str) -> dict:
    start, end = text.find("("), text.rfind(")")
    if start < 1 or end <= start:
        raise ValueError("Missing process CPU counters")
    fields = text[end + 1:].split()
    if len(fields) < 20:
        raise ValueError("Truncated process CPU counters")
    try:
        return {"pid": int(text[:start].strip()), "ticks": int(fields[11]) + int(fields[12]),
                "start_ticks": int(fields[19])}
    except ValueError as error:
        raise ValueError("Invalid process CPU counters") from error


def cpu_usage(before: dict, after: dict, elapsed: float, frequency: int) -> dict:
    if (before["pid"], before["start_ticks"]) != (after["pid"], after["start_ticks"]):
        raise ValueError("Process restarted during CPU capture")
    ticks = after["ticks"] - before["ticks"]
    if ticks < 0 or elapsed <= 0 or frequency <= 0:
        raise ValueError("Invalid CPU interval")
    seconds = ticks / frequency
    return {"cpu_seconds": seconds, "elapsed_seconds": elapsed, "one_core_percent": 100 * seconds / elapsed}


def parse_meminfo(text: str) -> dict:
    if len(re.findall(r"\*\* MEMINFO in pid \d+ \[", text)) != 1:
        raise ValueError("Expected exactly one process; multiple processes require separate aggregation")
    patterns = {
        "pid": r"\*\* MEMINFO in pid (\d+) \[",
        "pss_kib": r"TOTAL PSS:\s*(\d+)",
        "rss_kib": r"TOTAL RSS:\s*(\d+)",
        "swap_pss_kib": r"TOTAL SWAP PSS:\s*(\d+)",
        "graphics_kib": r"^\s*Graphics:\s*(\d+)",
    }
    result = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, text, re.MULTILINE)
        if match is None:
            raise ValueError(f"meminfo missing {key}; absent process/unsupported output is not zero")
        result[key] = int(match[1])
    return result


def foreground_component(text: str) -> str:
    match = re.search(r"mCurrentFocus=Window\{[^\n}]*\s(" + COMPONENT.pattern + r")\}", text)
    if match is None:
        raise ValueError("No focused application window; dismiss dialogs/screensaver before measuring")
    return match[1]


def foreground_package(text: str) -> str:
    return foreground_component(text).split("/", 1)[0]


def summarize(samples: list[dict]) -> dict:
    groups = defaultdict(lambda: defaultdict(list))
    for sample in samples:
        groups[sample["package"]][sample["state"]].append(sample)
    return {package: {state: {
        "samples": len(rows),
        **{metric: {"mean": statistics.mean(row[metric] for row in rows),
                    "min": min(row[metric] for row in rows),
                    "max": max(row[metric] for row in rows)} for metric in METRICS},
    } for state, rows in states.items()} for package, states in groups.items()}


class Device:
    def __init__(self, serial: str):
        self.prefix = ["adb", "-s", serial]

    def run(self, *args: str, binary: bool = False) -> str | bytes:
        result = subprocess.run(self.prefix + list(args), check=True, capture_output=True,
                                text=not binary, timeout=30)
        return result.stdout

    def focus(self) -> str:
        return foreground_component(self.run("shell", "dumpsys", "window"))

    def require_foreground(self, package: str):
        actual = self.focus().split("/", 1)[0]
        if actual != package:
            raise ValueError(f"Expected foreground {package}, found {actual}; sample rejected")

    def start(self, component: str):
        if not COMPONENT.fullmatch(component):
            raise ValueError(f"Invalid activity component: {component}")
        output = self.run("shell", "am", "start", "-n", component)
        if "Error" in output or "Exception" in output:
            raise ValueError(output)

    def cpu_snapshot(self, package: str) -> tuple[dict, float, str]:
        pids = self.run("shell", "pidof", package).split()
        if len(pids) != 1 or not pids[0].isdigit():
            raise ValueError("CPU capture requires exactly one live process")
        text = self.run("shell", "cat", f"/proc/{pids[0]}/stat", "/proc/uptime")
        lines = text.strip().splitlines()
        if len(lines) != 2:
            raise ValueError("Missing CPU uptime/counters")
        return parse_cpu_stat(lines[0]), float(lines[1].split()[0]), text


def positive(value: str) -> int:
    number = int(value)
    if not 1 <= number <= 60:
        raise argparse.ArgumentTypeError("Use a value between 1 and 60")
    return number


def restore_foreground(device: Device, component: str, attempts: int = 20):
    device.start(component)
    for _ in range(attempts):
        try:
            if device.focus() == component:
                return
        except ValueError:
            pass  # The window can briefly be null while the activity is being resumed.
        time.sleep(0.5)
    raise ValueError(f"Original foreground activity was not restored: {component}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--target", action="append", required=True, help="package/.Activity; repeat in measurement order")
    parser.add_argument("--background", default="com.android.tv.settings/.MainSettings")
    parser.add_argument("--samples", type=positive, default=5)
    parser.add_argument("--interval", type=positive, default=3)
    parser.add_argument("--settle", type=positive, default=12)
    parser.add_argument("--state", action="append", choices=("settled", "navigated", "background", "returned"),
                        help="Capture only these states; repeat as needed. Default: all four states")
    parser.add_argument("--cpu-seconds", type=positive, default=0, help="Optional bounded process CPU interval")
    parser.add_argument("--cpu-workload", choices=("idle", "navigation"), default="idle")
    parser.add_argument("--scene", type=Path, help="Exact local scene used for native, when supplied")
    parser.add_argument("--scope", required=True, help="Describe content/settings parity and known mismatches")
    args = parser.parse_args()
    if any(not COMPONENT.fullmatch(item) for item in args.target + [args.background]):
        parser.error("Targets must be explicit package/activity components")
    if args.background.split("/")[0] in {item.split("/")[0] for item in args.target}:
        parser.error("Background activity must belong to a separate package")
    if args.cpu_workload == "navigation" and (args.state is None or "background" in args.state):
        parser.error("Navigation CPU capture requires explicit foreground states")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device(args.serial)
    initial = device.focus()
    home_before = device.run("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                             "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    result = {
        "started_at": datetime.now(timezone.utc).isoformat(), "serial": args.serial,
        "scope": args.scope, "targets": args.target, "initial_foreground": initial,
        "home_before": home_before, "samples": [], "complete": False,
        "parameters": {key: getattr(args, key) for key in ("samples", "interval", "settle", "background", "state")},
        "display": {"size": device.run("shell", "wm", "size"), "density": device.run("shell", "wm", "density")},
        "device": {key: device.run("shell", "getprop", key).strip() for key in
                   ("ro.product.model", "ro.build.version.release", "ro.build.fingerprint")},
    }
    if args.scene:
        data = args.scene.read_bytes()
        result["scene_sha256"] = hashlib.sha256(data).hexdigest()
        (args.output / "scene.json").write_bytes(data)
    error = None
    try:
        frequency = int(device.run("shell", "getconf", "CLK_TCK").strip()) if args.cpu_seconds else None
        result["cpu_parameters"] = {"seconds": args.cpu_seconds, "workload": args.cpu_workload, "ticks_per_second": frequency}
        for component in args.target:
            package = component.split("/", 1)[0]
            directory = args.output / package
            directory.mkdir()
            package_dump = device.run("shell", "dumpsys", "package", package)
            (directory / "package.txt").write_text(package_dump)
            paths = device.run("shell", "pm", "path", package)
            if not paths.startswith("package:"):
                raise ValueError(f"Package is not installed: {package}")
            apk = paths.splitlines()[0].removeprefix("package:")
            if re.fullmatch(r"/[A-Za-z0-9_./~+=-]+", apk) is None:
                raise ValueError("Unexpected APK path")
            (directory / "apk-sha256.txt").write_text(device.run("shell", "sha256sum", apk))
            device.start(component)
            time.sleep(args.settle)
            device.require_foreground(package)
            states = tuple(state for state in ("settled", "navigated", "background", "returned")
                           if args.state is None or state in args.state)
            for state in states:
                expected = package
                if state == "navigated":
                    for key in ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_UP"):
                        device.run("shell", "input", "keyevent", key)
                        time.sleep(0.25)
                    time.sleep(args.settle)
                elif state == "background":
                    device.start(args.background)
                    expected = args.background.split("/", 1)[0]
                    time.sleep(args.settle)
                elif state == "returned":
                    if args.state and "background" not in args.state:
                        device.start(args.background)
                        time.sleep(args.settle)
                    device.start(component)
                    time.sleep(args.settle)
                print(f"{package}: {state}", flush=True)
                for index in range(args.samples):
                    device.require_foreground(expected)
                    cpu = None
                    if args.cpu_seconds:
                        before, uptime_before, raw_before = device.cpu_snapshot(package)
                        if args.cpu_workload == "navigation":
                            for key in ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT") * 3:
                                device.run("shell", "input", "keyevent", key)
                                time.sleep(args.cpu_seconds / 6)
                        else:
                            time.sleep(args.cpu_seconds)
                        after, uptime_after, raw_after = device.cpu_snapshot(package)
                        device.require_foreground(expected)
                        cpu = cpu_usage(before, after, uptime_after - uptime_before, frequency)
                        cpu["actions"] = 6 if args.cpu_workload == "navigation" else 0
                        (directory / f"{state}-{index + 1}-cpu.txt").write_text(raw_before + "\n" + raw_after)
                    text = device.run("shell", "dumpsys", "meminfo", "-a", package)
                    (directory / f"{state}-{index + 1}.txt").write_text(text)
                    metrics = parse_meminfo(text)
                    device.require_foreground(expected)
                    result["samples"].append({"package": package, "state": state, "index": index + 1,
                        "captured_at": datetime.now(timezone.utc).isoformat(), "cpu": cpu, **metrics})
                    if index + 1 < args.samples:
                        time.sleep(args.interval)
                (directory / f"{state}-gfxinfo.txt").write_text(device.run("shell", "dumpsys", "gfxinfo", package, "framestats"))
                (directory / f"{state}.png").write_bytes(device.run("exec-out", "screencap", "-p", binary=True))
            (directory / "cpuinfo.txt").write_text(device.run("shell", "dumpsys", "cpuinfo"))
        result["complete"] = True
    except BaseException as exc:
        error = exc
        result["error"] = f"{type(exc).__name__}: {exc}"
    finally:
        try:
            restore_foreground(device, initial)
            result["restored_foreground"] = device.focus()
            result["home_after"] = device.run("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                                             "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
            if result["home_after"] != home_before:
                raise ValueError("Default Home changed during measurement")
        except BaseException as exc:
            result["restoration_error"] = str(exc)
            error = error or exc
            result["complete"] = False
        result["summary"] = summarize(result["samples"])
        (args.output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result["summary"], indent=2))
    if error:
        raise error


if __name__ == "__main__":
    main()
