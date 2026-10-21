#!/usr/bin/env python3
"""
Re-captures the promo screenshots on a connected emulator/device so the device
state matches the soundtrack: 140 BPM and the same factory preset that each
video section plays.

Usage:
    tools/capture_screenshots.py            # all six shots, phone + tablet
    tools/capture_screenshots.py --only 3   # one section

Requires adb in PATH (or ANDROID_HOME pointing at the SDK).
"""

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "screenshots")
PKG = "com.danimanx.replicant303"
PHONE_SIZE, PHONE_DPI = "1080x2400", "420"
TABLET_SIZE, TABLET_DPI = "2560x1600", "320"

BANK_X = {letter: 119 + index * 121 for index, letter in enumerate("ABCDEFGH")}
BANK_Y = 508
SLOT_Y0 = 826
SLOT_DY = 73
SLOT_X0 = 176
SLOT_DX = 112

SECTIONS = [
    # (bank index, slot index, tab, skin, playing, file)
    (0, 23, "SYNTH", "green", True, "phone_synth_playing.png"),   # A/24 Reactor Replicant
    (1, 17, "SEQUENCE", "green", True, "phone_sequence.png"),     # B/18 Neon Kernel
    (3, 0, "VAULT", "green", False, "phone_vault.png"),           # D/01 Acid Rain
    (5, 16, "MIDI", "green", False, "phone_midi.png"),            # F/17 Shadow Rain
    (0, 13, "SYNTH", "red", False, "phone_synth_red.png"),        # A/14 Obsidian Oscillator
    (7, 20, "SYNTH", "green", False, "tablet_landscape.png"),     # H/21 Mirror Factory
]


def adb(*args, capture=False, timeout=30):
    cmd = ["adb", *args]
    result = subprocess.run(cmd, capture_output=capture, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr!r}")
    return result.stdout if capture else b""


def tap(x, y, settle=0.9):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))
    time.sleep(settle)


def restart():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(5)


def ui_nodes():
    for _ in range(4):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
            raw = adb("shell", "cat", "/sdcard/window.xml", capture=True)
            root = ET.fromstring(raw.decode("utf-8", errors="replace"))
            nodes = []
            for node in root.iter("node"):
                bounds = node.get("bounds") or ""
                match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
                if not match:
                    continue
                x1, y1, x2, y2 = (int(v) for v in match.groups())
                nodes.append({
                    "text": node.get("text") or "",
                    "desc": node.get("content-desc") or "",
                    "center": ((x1 + x2) / 2, (y1 + y2) / 2),
                })
            return nodes
        except Exception:
            time.sleep(1.2)
    return []


def find(nodes, label):
    for node in nodes:
        if node["text"] == label or node["desc"] == label:
            return node
    return None


def tap_label(label, settle=1.0):
    node = find(ui_nodes(), label)
    if node is None:
        return False
    tap(*node["center"], settle=settle)
    return True


def set_tempo():
    tap(958, 190, 1.4)
    tap_label("140", 0.9)
    tap_label("DONE", 1.2)


def set_skin(wanted):
    nodes = ui_nodes()
    label = "TD-3 GREEN" if wanted == "green" else "OBSIDIAN"
    other = "OBSIDIAN" if wanted == "green" else "TD-3 GREEN"
    if find(nodes, other):
        tap(798, 90, 1.4)
    elif not find(nodes, label):
        tap(798, 90, 1.4)


def load_preset(bank, slot):
    tap_label("VAULT", 1.2)
    tap(BANK_X["ABCDEFGH"[bank]], BANK_Y, 1.2)
    page, column = divmod(slot, 8)
    tap(SLOT_X0 + column * SLOT_DX, SLOT_Y0 + page * SLOT_DY, 1.6)


def ensure_playing():
    for _ in range(3):
        nodes = ui_nodes()
        if find(nodes, "Play"):
            tap_label("Play", 1.6)
        if find(nodes, "Stop"):
            time.sleep(2.5)
            return True
        time.sleep(1.0)
    return False


def capture(name):
    raw = adb("exec-out", "screencap", "-p", capture=True)
    with open(os.path.join(SHOTS, name), "wb") as handle:
        handle.write(raw)
    print(f"  captured {name}")


def run(only=None):
    adb("shell", "wm", "size", PHONE_SIZE)
    adb("shell", "wm", "density", PHONE_DPI)
    restart()
    set_tempo()

    for index, (bank, slot, tab, skin, playing, filename) in enumerate(SECTIONS):
        if only is not None and index not in only:
            continue
        print(f"[{index + 1}] {filename}")
        set_skin(skin)
        load_preset(bank, slot)
        set_tempo()

        if tab == "SYNTH" and filename.startswith("tablet"):
            # Tablet layout last: the window size changes every coordinate.
            adb("shell", "wm", "size", TABLET_SIZE)
            adb("shell", "wm", "density", TABLET_DPI)
            time.sleep(3)
            restart()
            time.sleep(2)
            tap_label("SYNTH", 1.5)
        else:
            tap_label(tab, 1.5)

        if playing:
            ensure_playing()
        else:
            time.sleep(1.0)
        capture(filename)

    adb("shell", "wm", "size", PHONE_SIZE)
    adb("shell", "wm", "density", PHONE_DPI)
    time.sleep(1)
    print("done")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, nargs="*", default=None,
                        help="1-based section indexes to capture")
    args = parser.parse_args()
    only = None if args.only is None else {i - 1 for i in args.only}
    try:
        run(only)
    except Exception as error:  # noqa: BLE001
        print(f"capture failed: {error}", file=sys.stderr)
        sys.exit(1)
