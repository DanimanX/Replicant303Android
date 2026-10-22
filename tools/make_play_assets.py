#!/usr/bin/env python3
"""Generate Google Play store assets from the in-repo artwork for Replicant303.

Produces, under store/:
  icon-512.png                  512x512, 32-bit PNG
  feature-graphic-1024x500.png  1024x500, 24-bit PNG (no alpha)
  screenshots/phone_*.png       2:1 max aspect ratio, 24-bit PNG (no alpha)
  screenshots/tablet_*.png      24-bit PNG (no alpha)

Play store asset rules enforced here:
  * icon: exactly 512x512, <= 1 MB
  * feature graphic: exactly 1024x500, no alpha, <= 15 MB
  * screenshots: min side 320 px, max side 3840 px, aspect ratio <= 2:1, no alpha
  * JPEG or 24-bit PNG, no transparency

Usage: python3 tools/make_play_assets.py
Requires: Pillow, rsvg-convert.
"""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store"
SHOTS_OUT = STORE / "screenshots"
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"

# Palette: Replicant 303 acid cyberpunk
BG = (8, 26, 16)         # #081A10 dark forest obsidian
BG_DARK = (5, 14, 9)
GREEN_DARK = (44, 102, 66) # #2C6642
GREEN_MID = (155, 201, 170) # #9BC9AA
ACID_GREEN = (183, 255, 75) # #B7FF4B toxic neon 303
INK = (245, 255, 240)
MUTED = (160, 195, 175)

ICON_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="#081A10"/>
  <!-- Aluminum knobs -->
  <circle cx="30" cy="34" r="11" fill="#2C6642"/>
  <circle cx="30" cy="34" r="8" fill="#9BC9AA"/>
  <rect x="29" y="25" width="2" height="9" fill="#B7FF4B"/>

  <circle cx="54" cy="34" r="11" fill="#2C6642"/>
  <circle cx="54" cy="34" r="8" fill="#9BC9AA"/>
  <rect x="54" y="33" width="9" height="2" fill="#B7FF4B"/>

  <circle cx="78" cy="34" r="11" fill="#2C6642"/>
  <circle cx="78" cy="34" r="8" fill="#9BC9AA"/>
  <rect x="77" y="25" width="2" height="9" fill="#B7FF4B"/>

  <!-- Sawtooth resonance wave -->
  <path d="M20,72 L34,56 L34,72 L48,56 L48,72 L62,56 L62,72 L76,56 L76,72 L90,56 L90,74 L20,74 Z" fill="#B7FF4B"/>

  <!-- Step LEDs -->
  <rect x="24" y="82" width="8" height="6" rx="1" fill="#B7FF4B"/>
  <rect x="36" y="82" width="8" height="6" rx="1" fill="#2C6642"/>
  <rect x="48" y="82" width="8" height="6" rx="1" fill="#B7FF4B"/>
  <rect x="60" y="82" width="8" height="6" rx="1" fill="#2C6642"/>
  <rect x="72" y="82" width="8" height="6" rx="1" fill="#B7FF4B"/>
</svg>
"""


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_DIR / name), size)


def build_icon() -> Path:
    """Render the 512x512 store icon."""
    out = STORE / "icon-512.png"
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as fh:
        fh.write(ICON_SVG)
        svg = fh.name
    try:
        subprocess.run(
            ["rsvg-convert", "-w", "512", "-h", "512", "-o", str(out), svg],
            check=True,
        )
    finally:
        os.unlink(svg)
    return out


def _glow(size: tuple[int, int], center: tuple[int, int], radius: int, color, alpha: int) -> Image.Image:
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse(
        [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius],
        fill=color + (alpha,),
    )
    return layer.filter(ImageFilter.GaussianBlur(radius // 2))


def _phone(shot: Path, height: int, crop_top: int = 0, radius: int = 18) -> Image.Image:
    img = Image.open(shot).convert("RGB")
    if crop_top:
        img = img.crop((0, crop_top, img.width, img.height))
    width = round(img.width * height / img.height)
    img = img.resize((width, height), Image.LANCZOS)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, height - 1], radius=radius, fill=255)
    frame = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    frame.paste(img, (0, 0), mask)
    border = ImageDraw.Draw(frame)
    border.rounded_rectangle([0, 0, width - 1, height - 1], radius=radius, outline=(44, 102, 66, 255), width=2)
    return frame


def build_feature_graphic() -> Path:
    """1024x500 hero graphic: branding and chips on left, phone UI on right."""
    w, h = 1024, 500
    canvas = Image.new("RGB", (w, h), BG_DARK)
    for glow in (
        _glow((w, h), (140, 50), 380, ACID_GREEN, 35),
        _glow((w, h), (960, 460), 380, GREEN_DARK, 70),
    ):
        canvas.paste(glow.convert("RGB"), (0, 0), glow.split()[3])

    draw = ImageDraw.Draw(canvas)
    for y in range(h):  # subtle vertical gradient
        shade = int(14 * (y / h))
        draw.line([(0, y), (w, y)], fill=(BG[0] + shade, BG[1] + shade, BG[2] + shade))

    # Right-hand device screens
    scale = 470 / 2400
    synth = _phone(ROOT / "screenshots" / "phone_synth_playing.png", 470)
    seq = _phone(ROOT / "screenshots" / "phone_sequence.png", round((2400 - 800) * scale), crop_top=800)
    synth_x, seq_x, top = 636, 636 + synth.width - 44, 58
    seq_y = top + (470 - seq.height)

    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    for x, y, panel in ((synth_x, top, synth), (seq_x, seq_y, seq)):
        sd.rounded_rectangle(
            [x + 12, y + 16, x + 12 + panel.width, y + 16 + panel.height], radius=22, fill=(0, 0, 0, 160)
        )
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    canvas.paste(shadow.convert("RGB"), (0, 0), shadow.split()[3])
    canvas.paste(seq, (seq_x, seq_y), seq)
    canvas.paste(synth, (synth_x, top), synth)

    # Left wordmark block
    text_right = synth_x - 36
    icon = Image.open(STORE / "icon-512.png").convert("RGB").resize((88, 88), Image.LANCZOS)
    icon_mask = Image.new("L", (88, 88), 0)
    ImageDraw.Draw(icon_mask).rounded_rectangle([0, 0, 87, 87], radius=19, fill=255)
    canvas.paste(icon, (56, 54), icon_mask)

    draw.rectangle([56, 174, 116, 178], fill=ACID_GREEN)

    title = "REPLICANT303"
    size = 62
    while size > 30 and draw.textlength(title, font=font("roboto_condensed_bold.ttf", size)) > text_right - 56:
        size -= 2
    draw.text((56, 194), title, font=font("roboto_condensed_bold.ttf", size), fill=INK)

    draw.text((58, 272), "BASS LINE SYNTHESIZER // ANDROID",
              font=font("roboto_condensed_bold.ttf", 24), fill=MUTED)

    y = 326
    for chip in ("48 kHz · AAUDIO LOW LATENCY", "ANALOG CIRCUIT DSP", "USB MIDI 1.0 + 2.0"):
        cf = font("roboto_condensed_bold.ttf", 20)
        tw = draw.textlength(chip, font=cf)
        draw.rounded_rectangle([56, y - 6, 56 + tw + 26, y + 26], radius=16, outline=(44, 102, 66), width=1)
        draw.text((69, y), chip, font=cf, fill=ACID_GREEN)
        y += 44

    out = STORE / "feature-graphic-1024x500.png"
    canvas.save(out, optimize=True)
    return out


def convert_screenshots() -> list[Path]:
    """Flatten alpha and bring every screenshot inside Play's 2:1 limit."""
    written = []
    for src in sorted((ROOT / "screenshots").glob("*.png")):
        img = Image.open(src).convert("RGB")
        w, h = img.size
        if max(w, h) / min(w, h) > 2.0:
            if h >= w:  # portrait: trim the long side uniformly
                img = img.resize((w, 2 * w), Image.LANCZOS)
            else:
                img = img.resize((2 * h, h), Image.LANCZOS)
        out = SHOTS_OUT / f"{src.stem}.png"
        img.save(out, optimize=True)
        written.append(out)
    return written


def verify(paths: list[Path]) -> list[str]:
    problems = []
    for path in paths:
        img = Image.open(path)
        size_mb = path.stat().st_size / (1024 * 1024)
        w, h = img.size
        if img.mode not in ("RGB", "P"):
            problems.append(f"{path.name}: mode {img.mode} has alpha")
        if path.name == "icon-512.png":
            if (w, h) != (512, 512):
                problems.append(f"{path.name}: {w}x{h} != 512x512")
            if size_mb > 1:
                problems.append(f"{path.name}: {size_mb:.2f} MB > 1 MB")
        elif path.name.startswith("feature-graphic"):
            if (w, h) != (1024, 500):
                problems.append(f"{path.name}: {w}x{h} != 1024x500")
        else:
            if min(w, h) < 320 or max(w, h) > 3840:
                problems.append(f"{path.name}: {w}x{h} outside 320..3840")
            if max(w, h) / min(w, h) > 2.0:
                problems.append(f"{path.name}: {w}x{h} aspect {max(w, h) / min(w, h):.2f} > 2:1")
    return problems


def main() -> int:
    STORE.mkdir(exist_ok=True)
    SHOTS_OUT.mkdir(exist_ok=True)
    outputs = [build_icon(), build_feature_graphic(), *convert_screenshots()]
    problems = verify(outputs)
    for path in outputs:
        w, h = Image.open(path).size
        print(f"{path.relative_to(ROOT)}  {w}x{h}  {path.stat().st_size / 1024:.0f} KB")
    if problems:
        print("\nPLAY SPEC VIOLATIONS:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("\nAll assets satisfy the Play specs enforced by this script.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
