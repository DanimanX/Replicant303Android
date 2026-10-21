#!/usr/bin/env python3
"""
Replicant 303 Android — cyberpunk promotional video generator.

The edit is cut against the real factory presets rendered by the native engine
(tools/host/render_track), so every preset name on screen is a patch that ships
in the app. The soundtrack layers a hard-techno bed under the engine output.

Outputs:
    assets/replicant303_android_promo_1080p.mp4
    assets/replicant303_android_poster.png
"""

import json
import math
import os
import random
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import signal

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
SHOTS = os.path.join(ROOT, "screenshots")

W, H = 1920, 1080
FPS = 30
SR = 48000

# Cyberpunk palette: night-city magenta, signal cyan, acid yellow, hot orange.
VOID = (6, 3, 12)
INK = (10, 6, 20)
MAGENTA = (255, 30, 111)
CYAN = (25, 240, 255)
ACID = (234, 255, 58)
ORANGE = (255, 122, 26)
WHITE = (245, 246, 255)
MUTED = (150, 140, 175)
DIM = (92, 84, 112)
GREEN = (183, 255, 75)

F_TITLE = "/usr/share/fonts/TTF/RobotoCondensed-Bold.ttf"
F_BODY = "/usr/share/fonts/TTF/RobotoCondensed-Regular.ttf"
F_MONO = "/usr/share/fonts/TTF/JetBrainsMonoNerdFontMono-Bold.ttf"
F_JP = "/usr/share/fonts/droid/DroidSansJapanese.ttf"

_fonts = {}


def font(path, size):
    key = (path, size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(path, size)
    return _fonts[key]


def load_manifest():
    with open(os.path.join(ASSETS, "replicant303_promo_track.json"), encoding="utf-8") as handle:
        data = json.load(handle)
    data["intro_end"] = float(data.get("introEnd", data["sections"][0]["start"]))
    data["duration"] = float(data["duration"])
    return data


MANIFEST = load_manifest()
BPM = float(MANIFEST.get("bpm", 140.0))
DURATION = MANIFEST["duration"]
INTRO_END = MANIFEST["intro_end"]
OUTRO_START = float(MANIFEST["sections"][-1]["end"])
SECTIONS = MANIFEST["sections"]

# The six scenes are tied to the six rendered sections.
SECTION_COPY = [
    {
        "eyebrow": "01 // THE ENGINE",
        "bullets": ["NATIVE C++20 ACID VOICE", "4-POLE DIODE LADDER", "5 DISTORTION MODELS"],
        "shot": "phone_synth_playing.png",
        "accent": GREEN,
        "kana": "アシッド・エンジン",
    },
    {
        "eyebrow": "02 // PATTERN CORE",
        "bullets": ["8 PATTERNS x 16 STEPS", "GATE / ACCENT / SLIDE", "SWING + 1-16 LENGTH"],
        "shot": "phone_sequence.png",
        "accent": CYAN,
        "kana": "シーケンサー",
    },
    {
        "eyebrow": "03 // PRESET VAULT",
        "bullets": ["512 COMPLETE PATCHES", "8 BANKS x 64 SLOTS", "GEN / STORE / MIDI EXPORT"],
        "shot": "phone_vault.png",
        "accent": ACID,
        "kana": "プリセット",
    },
    {
        "eyebrow": "04 // USB MIDI",
        "bullets": ["MIDI IN + MIDI OUT", "MIDI 2.0 UMP TRANSLATION", "24 PPQN CLOCK + TRANSPORT"],
        "shot": "phone_midi.png",
        "accent": ORANGE,
        "kana": "ミディ",
    },
    {
        "eyebrow": "05 // TWO PANELS",
        "bullets": ["TD-3 GREEN / OBSIDIAN RED", "13-KEY TACTILE KEYBOARD", "SESSION PERSISTENCE"],
        "shot": "phone_synth_red.png",
        "accent": MAGENTA,
        "kana": "スキン",
    },
    {
        "eyebrow": "06 // ANY SCREEN",
        "bullets": ["PHONE + TABLET LAYOUTS", "ADAPTIVE COMPOSE PANELS", "LIVE SYSTEM TELEMETRY"],
        "shot": "tablet_landscape.png",
        "accent": GREEN,
        "kana": "タブレット",
    },
]


# ---------------------------------------------------------------------------
# Audio
# ---------------------------------------------------------------------------

def load_wav(path, n):
    with wave.open(path, "rb") as handle:
        channels = handle.getnchannels()
        width = handle.getsampwidth()
        raw = np.frombuffer(handle.readframes(handle.getnframes()), dtype=np.uint8)
    if width == 2:
        data = raw.view(np.int16).astype(np.float32) / 32768.0
    elif width == 3:
        b = raw.reshape(-1, 3)
        x = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8)
             | (b[:, 2].astype(np.int32) << 16))
        x = np.where(x >= 1 << 23, x - (1 << 24), x).astype(np.float32) / 8388608.0
        data = x
    else:
        data = raw.astype(np.float32) / 127.0 - 1.0
    data = data.reshape(-1, channels)
    if channels == 1:
        data = np.repeat(data, 2, axis=1)
    if len(data) < n:
        data = np.pad(data, ((0, n - len(data)), (0, 0)))
    return data[:n]


def env_ramp(n, points):
    times = np.array([p[0] for p in points], dtype=np.float64)
    gains = np.array([p[1] for p in points], dtype=np.float64)
    return np.interp(np.arange(n) / SR, times, gains).astype(np.float32)


def section_gains(mix, n):
    gains = np.ones(n, dtype=np.float32)
    for section in SECTIONS:
        a = int(section["start"] * SR)
        b = min(int(section["end"] * SR), n)
        block = mix[a:b]
        rms = float(np.sqrt(np.mean(block ** 2) + 1e-12))
        target = 0.085
        gain = float(np.clip(target / max(rms, 1e-6), 0.5, 3.2))
        gains[a:b] = gain
    return gains


def synth_drums(n, rng):
    beat = 60.0 / BPM
    out = np.zeros((n, 2), dtype=np.float32)

    def add(sig, start, gain=1.0, pan=0.0):
        i0 = int(start * SR)
        if i0 >= n:
            return
        length = min(len(sig), n - i0)
        left = gain * (1.0 - max(0.0, pan))
        right = gain * (1.0 + min(0.0, pan))
        out[i0:i0 + length, 0] += sig[:length] * left
        out[i0:i0 + length, 1] += sig[:length] * right

    # Distorted four-on-the-floor kick.
    kick_len = int(0.42 * SR)
    kt = np.arange(kick_len) / SR
    freq = 44.0 + 150.0 * np.exp(-kt / 0.024)
    kick = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-kt / 0.17)
    click = rng.standard_normal(kick_len).astype(np.float32) * np.exp(-kt / 0.0025) * 0.35
    kick = np.tanh((kick + click) * 1.9).astype(np.float32)
    for k in range(int(DURATION / beat) + 1):
        add(kick, k * beat, 0.90)

    # Industrial snare / clap on two and four.
    snare_len = int(0.30 * SR)
    st = np.arange(snare_len) / SR
    band = signal.butter(2, [1100.0, 5200.0], "bp", fs=SR, output="sos")
    body = np.sin(2 * np.pi * 190.0 * st) * np.exp(-st / 0.035)
    for bar in range(int(DURATION / (beat * 4)) + 1):
        for offset in (beat, beat * 3):
            noise = rng.standard_normal(snare_len).astype(np.float32)
            noise = signal.sosfilt(band, noise).astype(np.float32)
            env = np.exp(-st / 0.055) + 0.35 * np.exp(-st / 0.006)
            add(((noise * env * 0.8 + body * 0.5) * 0.55).astype(np.float32),
                bar * beat * 4 + offset, 0.42, 0.1)

    # 16th hats with accents.
    hat_len = int(0.05 * SR)
    ht = np.arange(hat_len) / SR
    hp = signal.butter(2, 7800.0, "hp", fs=SR, output="sos")
    for k in range(int(DURATION / (beat / 4)) + 1):
        noise = rng.standard_normal(hat_len).astype(np.float32)
        noise = signal.sosfilt(hp, noise).astype(np.float32)
        accent = 0.30 if k % 4 == 2 else 0.15
        add(noise * np.exp(-ht / 0.014), k * beat / 4, accent, -0.3 if k % 2 else 0.3)

    # Metallic percussion hits on the off-beats of every second bar.
    metal_len = int(0.5 * SR)
    mt = np.arange(metal_len) / SR
    for bar in range(int(DURATION / (beat * 4)) + 1):
        for ratio in (1.0, 1.41, 2.03):
            tone = np.sin(2 * np.pi * 620.0 * ratio * mt) * np.exp(-mt / (0.16 / ratio))
            add((tone * 0.16).astype(np.float32), bar * beat * 4 + beat * 2.0, 0.30, 0.4)

    # Sub pressure under the kick, ducked by the sidechain later.
    sub_len = int(0.9 * SR)
    sbt = np.arange(sub_len) / SR
    sub = np.sin(2 * np.pi * 36.7 * sbt) * np.exp(-sbt / 0.55)
    for k in range(int(DURATION / beat) + 1):
        add((sub * 0.5).astype(np.float32), k * beat, 0.55)

    return out


def add_risers(mix, rng):
    n = len(mix)
    out = mix.copy()
    for section in SECTIONS:
        start = section["start"]
        end = section["end"]
        # Riser in the last bar before each drop.
        r0 = end - (60.0 / BPM) * 4.0
        r1 = end
        a, b = int(r0 * SR), min(int(r1 * SR), n)
        if b <= a:
            continue
        length = b - a
        noise = rng.standard_normal(length).astype(np.float32)
        sos = signal.butter(2, 900.0, "hp", fs=SR, output="sos")
        noise = signal.sosfilt(sos, noise).astype(np.float32)
        env = np.linspace(0.0, 1.0, length, dtype=np.float32) ** 2.4
        sweep = np.sin(2 * np.pi * np.cumsum(np.linspace(180.0, 900.0, length)) / SR)
        out[a:b, 0] += (noise * 0.10 + sweep * 0.05) * env
        out[a:b, 1] += (noise * 0.10 + sweep * 0.05) * env

        # Downlifter / impact on the drop.
        i0 = int(start * SR)
        length = min(int(1.4 * SR), n - i0)
        if length > 0:
            t = np.arange(length) / SR
            boom = np.sin(2 * np.pi * (70.0 * np.exp(-t / 0.35) + 28.0) * t) * np.exp(-t / 0.5)
            out[i0:i0 + length, 0] += (boom * 0.30).astype(np.float32)
            out[i0:i0 + length, 1] += (boom * 0.30).astype(np.float32)
    return out


def glitch_stutter(mix, rng):
    out = mix.copy()
    n = len(out)
    slices = [SECTIONS[2]["start"], SECTIONS[5]["start"]]
    step = int((60.0 / BPM / 4.0) * SR)
    for at in slices:
        i0 = int(at * SR)
        length = min(step, n - i0)
        if length <= 0:
            continue
        chunk = out[i0:i0 + length].copy()
        edge = max(8, int(0.003 * SR))
        chunk[:edge] *= np.linspace(0.0, 1.0, edge, dtype=np.float32)[:, None]
        chunk[-edge:] *= np.linspace(1.0, 0.0, edge, dtype=np.float32)[:, None]
        for repeat in range(1, 4):
            j = i0 + step * repeat
            if j + length > n:
                break
            out[j:j + length] = chunk[::-1] if repeat % 2 else chunk
    return out


def build_audio():
    n = int(DURATION * SR)
    rng = np.random.default_rng(303)
    stem = os.path.join(ASSETS, "replicant303_promo_track.wav")
    if not os.path.exists(stem):
        raise SystemExit("missing assets/replicant303_promo_track.wav; run render_track first")

    acid = load_wav(stem, n)
    acid *= section_gains(acid, n)[:, None]

    # Ping-pong dotted-8th delay.
    delay = int((60.0 / BPM) * 0.75 * SR)
    echo = np.zeros_like(acid)
    echo[delay:, 0] = acid[:-delay, 1] * 0.26
    echo[delay * 2:, 1] = acid[:-delay * 2, 0] * 0.18
    acid = acid + echo

    drums = synth_drums(n, rng)

    # Sidechain on everything that is not the drums.
    beat = 60.0 / BPM
    duck = np.ones(n, dtype=np.float32)
    for k in range(int(DURATION / beat) + 1):
        i0 = int(k * beat * SR)
        length = min(int(beat * 0.62 * SR), n - i0)
        if length <= 0:
            continue
        shape = 0.38 + 0.62 * (1.0 - np.exp(-np.arange(length) / (SR * 0.075)))
        duck[i0:i0 + length] = np.minimum(duck[i0:i0 + length], shape)

    t = np.arange(n) / SR
    pad = np.zeros((n, 2), dtype=np.float32)
    for ch, detune in ((0, 0.9945), (1, 1.0055)):
        p = np.zeros(n, dtype=np.float32)
        for f in (36.71, 55.0, 73.42, 110.0):
            p += (2.0 * ((f * detune * t) % 1.0) - 1.0).astype(np.float32) * 0.12
        sos = signal.butter(2, 320.0, "lp", fs=SR, output="sos")
        pad[:, ch] = signal.sosfilt(sos, p).astype(np.float32)

    pad_gain = env_ramp(n, [(0, 0.0), (3.4, 0.55), (12, 0.42), (30, 0.48),
                            (46, 0.55), (53, 0.5), (DURATION, 0.0)])

    mix = (acid * duck[:, None] * 1.0
           + drums * 0.72
           + pad * (pad_gain * duck)[:, None] * 0.40)

    mix = add_risers(mix, rng)
    mix = glitch_stutter(mix, rng)
    mix = np.tanh(mix * 1.25).astype(np.float32)

    peak = float(np.abs(mix).max())
    if peak > 0:
        mix *= 0.90 / peak

    fade_in = int(0.35 * SR)
    fade_out = int(1.4 * SR)
    mix[:fade_in] *= np.linspace(0.0, 1.0, fade_in)[:, None]
    mix[-fade_out:] *= np.linspace(1.0, 0.0, fade_out)[:, None]

    path = "/tmp/replicant303_cyberpunk_mix.wav"
    pcm = (np.clip(mix, -1.0, 1.0) * 32767.0).astype(np.int16)
    with wave.open(path, "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(pcm.tobytes())
    print(f"[audio] {path} ({DURATION:.2f}s, peak {np.abs(mix).max():.2f})")
    return path, mix


# ---------------------------------------------------------------------------
# Artwork
# ---------------------------------------------------------------------------

def skyline_layer():
    """Procedural night-city skyline with lit windows and neon signs."""
    rng = random.Random(303)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    horizon = int(H * 0.615)

    # Far buildings: low contrast, high density.
    x = -40
    while x < W + 60:
        bw = rng.randint(38, 96)
        bh = rng.randint(70, 240)
        top = horizon - bh
        shade = rng.randint(10, 18)
        draw.rectangle((x, top, x + bw, horizon), fill=(shade, shade - 4, shade + 12, 220))
        x += bw + rng.randint(2, 10)

    # Near buildings.
    x = -60
    while x < W + 80:
        bw = rng.randint(70, 170)
        bh = rng.randint(140, 420)
        top = horizon - bh
        shade = rng.randint(7, 13)
        draw.rectangle((x, top, x + bw, horizon), fill=(shade + 2, shade - 2, shade + 10, 245))
        draw.line((x, top, x + bw, top), fill=(30, 20, 40, 255), width=2)

        # Windows.
        for wy in range(top + 12, horizon - 8, 16):
            for wx in range(x + 8, x + bw - 8, 14):
                if rng.random() < 0.22:
                    color = rng.choice([MAGENTA, CYAN, ACID, ORANGE])
                    alpha = rng.randint(70, 190)
                    draw.rectangle((wx, wy, wx + 5, wy + 7),
                                   fill=(color[0], color[1], color[2], alpha))
        x += bw + rng.randint(8, 26)

    # Neon signage bars.
    for _ in range(14):
        bx = rng.randint(30, W - 60)
        by = rng.randint(horizon - 380, horizon - 90)
        bw = rng.randint(4, 9)
        bh = rng.randint(40, 150)
        color = rng.choice([MAGENTA, CYAN, ACID])
        draw.rectangle((bx, by, bx + bw, by + bh),
                       fill=(color[0], color[1], color[2], rng.randint(90, 170)))

    glow = layer.filter(ImageFilter.GaussianBlur(18))
    layer = Image.alpha_composite(glow, layer)
    return layer


def background():
    top = np.array([9, 4, 18], dtype=np.float32)
    bottom = np.array([3, 1, 7], dtype=np.float32)
    t = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    arr = top[None, None, :] * (1 - t) + bottom[None, None, :] * t
    arr = np.repeat(arr, W, axis=1).astype(np.uint8)
    img = Image.fromarray(arr, "RGB").convert("RGBA")

    # Neon glows.
    for color, cx, cy, radius, alpha in (
        (MAGENTA, 250, 120, 720, 34),
        (CYAN, 1740, 880, 760, 30),
        (ACID, 1120, 60, 460, 14),
    ):
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
                   fill=(color[0], color[1], color[2], alpha))
        glow = glow.filter(ImageFilter.GaussianBlur(170))
        img = Image.alpha_composite(img, glow)

    img = Image.alpha_composite(img, skyline_layer())

    # Fog band over the horizon.
    fog = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fog)
    fd.rectangle((0, int(H * 0.56), W, int(H * 0.70)), fill=(70, 20, 90, 40))
    fog = fog.filter(ImageFilter.GaussianBlur(60))
    img = Image.alpha_composite(img, fog)

    # Vignette.
    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse((-W * 0.25, -H * 0.32, W * 1.25, H * 1.32), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(170))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 150))
    dark.putalpha(Image.eval(vig, lambda p: 150 - int(p * 150 / 255)))
    img = Image.alpha_composite(img, dark)

    return img


def scanline_overlay():
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for y in range(0, H, 3):
        draw.line((0, y, W, y), fill=(0, 0, 0, 34), width=1)
    for y in range(0, H, 96):
        draw.line((0, y, W, y), fill=(120, 200, 255, 6), width=1)
    return layer


def device_frame(path, height=None, width=None, radius_ratio=0.05, camera=True):
    src = Image.open(os.path.join(SHOTS, path)).convert("RGB")
    if width is not None:
        w = width
        h = int(src.height * w / src.width)
    else:
        h = height
        w = int(src.width * h / src.height)
    bezel = max(8, int(min(w, h) * 0.018))
    fw, fh = w + bezel * 2, h + bezel * 2
    outer_r = int(min(fw, fh) * radius_ratio)

    screen = src.resize((w, h), Image.LANCZOS)
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, fw - 1, fh - 1), radius=outer_r,
                        fill=(10, 6, 18, 255), outline=(90, 60, 110, 255), width=2)

    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, w - 1, h - 1), radius=max(4, outer_r - bezel), fill=255
    )
    frame.paste(screen, (bezel, bezel), mask)
    d.rounded_rectangle((bezel - 1, bezel - 1, bezel + w, bezel + h),
                        radius=max(4, outer_r - bezel), outline=(0, 0, 0, 170), width=1)

    if camera and h > w:
        cx, cy = fw // 2, bezel // 2 + 1
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(20, 14, 28, 255))
    return frame


def neon_glow(frame, color, spread=48, alpha=110):
    mask = frame.getchannel("A").point(lambda p: p)
    halo = Image.new("RGBA", (frame.width + spread * 4, frame.height + spread * 4),
                     (0, 0, 0, 0))
    tint = Image.new("RGBA", frame.size, (color[0], color[1], color[2], alpha))
    tint.putalpha(mask.point(lambda p: int(p * alpha / 255)))
    halo.alpha_composite(tint, (spread * 2, spread * 2))
    return halo.filter(ImageFilter.GaussianBlur(spread))


def shadow_for(frame):
    sw, sh = frame.size
    shadow = Image.new("RGBA", (sw + 160, sh + 160), (0, 0, 0, 0))
    mask = frame.getchannel("A")
    black = Image.new("RGBA", frame.size, (0, 0, 0, 210))
    shadow.paste(black, (80, 90), mask)
    return shadow.filter(ImageFilter.GaussianBlur(32))


def spaced(draw, xy, text, fnt, fill, spacing=3, anchor="la"):
    widths = [draw.textlength(c, font=fnt) for c in text]
    total = sum(widths) + spacing * max(0, len(text) - 1)
    x, y = xy
    if anchor[0] == "m":
        x -= total / 2
    elif anchor[0] == "r":
        x -= total
    va = anchor[1] if len(anchor) > 1 else "a"
    for c, w in zip(text, widths):
        draw.text((x, y), c, font=fnt, fill=fill, anchor="l" + va)
        x += w + spacing
    return total


def chroma_text(layer, xy, text, fnt, fill=WHITE, offset=5, anchor="la",
                accent=MAGENTA, accent2=CYAN, blur=14):
    """Chromatic-aberration headline: magenta left, cyan right, white core."""
    draw = ImageDraw.Draw(layer)
    draw.text((xy[0] - offset, xy[1]), text, font=fnt, fill=(*accent, 210), anchor=anchor)
    draw.text((xy[0] + offset, xy[1]), text, font=fnt, fill=(*accent2, 200), anchor=anchor)
    glow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(xy, text, font=fnt, fill=(*fill, 120), anchor=anchor)
    layer.alpha_composite(glow.filter(ImageFilter.GaussianBlur(blur)))
    draw.text(xy, text, font=fnt, fill=fill, anchor=anchor)


def wrap_name(name, max_chars=13):
    words = name.split()
    if len(words) <= 1:
        return [name]
    if len(name) <= max_chars:
        return [name]
    # Two-word preset names are the norm; balance the lines.
    best = None
    for cut in range(1, len(words)):
        first = " ".join(words[:cut])
        second = " ".join(words[cut:])
        score = abs(len(first) - len(second))
        if best is None or score < best[0]:
            best = (score, [first, second])
    return best[1]


def hud_frame(layer, accent):
    draw = ImageDraw.Draw(layer)
    a = (*accent, 190)
    m = 34
    length = 64
    for (x, y, dx, dy) in ((m, m, 1, 1), (W - m, m, -1, 1),
                           (m, H - m, 1, -1), (W - m, H - m, -1, -1)):
        draw.line((x, y, x + dx * length, y), fill=a, width=2)
        draw.line((x, y, x, y + dy * length), fill=a, width=2)


def vertical_kana(layer, text, x, y, color, size=20, gap=6):
    draw = ImageDraw.Draw(layer)
    fnt = font(F_JP, size)
    for index, char in enumerate(text):
        draw.text((x, y + index * (size + gap)), char, font=fnt,
                  fill=(*color, 150), anchor="la")


def build_device_layers():
    devices = {}
    for index, copy in enumerate(SECTION_COPY):
        name = copy["shot"]
        if name.startswith("tablet"):
            frame = device_frame(name, width=930, radius_ratio=0.035, camera=False)
        else:
            frame = device_frame(name, height=796)
        tilt = -1.6 if not name.startswith("tablet") else -1.0
        frame = frame.rotate(tilt, resample=Image.BICUBIC, expand=True)
        devices[index] = {
            "frame": frame,
            "glow": neon_glow(frame, copy["accent"]),
            "shadow": shadow_for(frame),
        }
    return devices


def panel(layer, x, y, w, h, accent, alpha=196, cut=26):
    draw = ImageDraw.Draw(layer, "RGBA")
    points = [(x, y), (x + w - cut, y), (x + w, y + cut),
              (x + w, y + h), (x, y + h)]
    draw.polygon(points, fill=(6, 3, 12, alpha))
    draw.line(points + [points[0]], fill=(*accent, 120), width=2)
    # Inner hairline.
    inner = [(x + 6, y + 6), (x + w - cut - 4, y + 6), (x + w - 6, y + cut + 4),
             (x + w - 6, y + h - 6), (x + 6, y + h - 6)]
    draw.line(inner + [inner[0]], fill=(*accent, 34), width=1)
    # Corner tick.
    draw.line((x, y, x + 46, y), fill=(*accent, 255), width=3)
    draw.line((x, y, x, y + 46), fill=(*accent, 255), width=3)


def hud_readout(layer, x, y, accent, index):
    draw = ImageDraw.Draw(layer)
    lines = [
        ("ENGINE", "AAUDIO / 48 KHZ"),
        ("BUFFER", "LOW LATENCY"),
        ("SYNC", "24 PPQN"),
        ("PATCH", f"{'ABCDEFGH'[SECTIONS[index]['bank']]}/{SECTIONS[index]['slot'] + 1:02d}"),
    ]
    for i, (key, value) in enumerate(lines):
        draw.text((x, y + i * 22), key, font=font(F_MONO, 12), fill=(*DIM, 255), anchor="la")
        draw.text((x + 74, y + i * 22), value, font=font(F_MONO, 12),
                  fill=(*accent, 190), anchor="la")


PANEL_H = {}


def build_scene(index, devices, base_hud=True):
    copy = SECTION_COPY[index]
    section = SECTIONS[index]
    accent = copy["accent"]

    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(scene)

    name_lines = wrap_name(section["name"])
    size = 104 if max(len(line) for line in name_lines) <= 12 else 88
    panel_h = 250 + int(size * 0.9) * len(name_lines) + 250
    PANEL_H[index] = panel_h
    panel(scene, 72, 132, 820, panel_h, accent)

    # Text block.
    x = 108
    y = 168
    draw.rectangle((x, y + 6, x + 10, y + 18), fill=(*accent, 255))
    spaced(draw, (x + 22, y), copy["eyebrow"], font(F_MONO, 21), accent, spacing=2)

    yy = y + 48
    for line in name_lines:
        chroma_text(scene, (x, yy), line, font(F_TITLE, size), WHITE, offset=5,
                    accent=MAGENTA, accent2=CYAN)
        yy += int(size * 0.90)

    yy += 12
    bank_letter = "ABCDEFGH"[section["bank"]]
    slot = section["slot"] + 1
    chip_w = 74
    draw.rounded_rectangle((x, yy, x + chip_w, yy + 30), radius=4,
                           fill=(*accent, 28), outline=(*accent, 170), width=1)
    draw.text((x + chip_w / 2, yy + 15), f"{bank_letter}/{slot:02d}",
              font=font(F_MONO, 16), fill=(*accent, 255), anchor="mm")
    draw.text((x + chip_w + 16, yy + 15), "FACTORY PATCH", font=font(F_MONO, 16),
              fill=(210, 200, 232), anchor="lm")
    draw.text((x + chip_w + 190, yy + 15), f"{int(BPM)} BPM", font=font(F_MONO, 16),
              fill=(*MAGENTA, 235), anchor="lm")

    yy += 54
    draw.line((x, yy, x + 700, yy), fill=(*accent, 90), width=1)
    yy += 20
    for bullet in copy["bullets"]:
        draw.rectangle((x, yy + 9, x + 16, yy + 11), fill=accent)
        draw.text((x + 28, yy), bullet, font=font(F_MONO, 19),
                  fill=(198, 190, 218), anchor="la")
        yy += 38

    hud_readout(scene, x, yy + 14, accent, index)

    # Vertical katakana accent.
    vertical_kana(scene, copy["kana"], 42, 190, accent, size=19, gap=7)

    # Device.
    entry = devices[index]
    frame = entry["frame"]
    if copy["shot"].startswith("tablet"):
        px, py = W - frame.width - 54, 176
    else:
        px, py = W - frame.width - 150, 142
    scene.alpha_composite(entry["glow"], (px - 192, py - 192))
    scene.alpha_composite(entry["shadow"], (px - 80, py - 80))
    scene.alpha_composite(frame, (px, py))

    if base_hud:
        hud_frame(scene, accent)

    return scene


def build_intro(scene_index=0):
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(scene)

    # Chip.
    draw.rounded_rectangle((118, 236, 286, 398), radius=22,
                           fill=(*ACID, 16), outline=(*ACID, 190), width=2)
    spaced(draw, (144, 276), "303", font(F_TITLE, 72), ACID, spacing=2)

    backing = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(backing).rounded_rectangle(
        (72, 180, 1080, 930), radius=18, fill=(6, 3, 12, 150))
    scene.alpha_composite(backing.filter(ImageFilter.GaussianBlur(28)))

    chroma_text(scene, (112, 408), "REPLICANT", font(F_TITLE, 168), WHITE,
                offset=7, accent=MAGENTA, accent2=CYAN)
    chroma_text(scene, (112, 556), "303", font(F_TITLE, 168), ACID,
                offset=7, accent=MAGENTA, accent2=CYAN)

    spaced(draw, (120, 736), "ACID PROTOCOL", font(F_MONO, 30), CYAN, spacing=5)
    draw.line((120, 792, 980, 792), fill=(*MAGENTA, 120), width=1)
    spaced(draw, (120, 818), "TB-303 ENGINE // NATIVE ANDROID", font(F_MONO, 19),
           (200, 190, 220), spacing=2)
    spaced(draw, (120, 856), "LOW LATENCY  ·  USB MIDI  ·  512 PRESETS", font(F_MONO, 16),
           DIM, spacing=2)

    vertical_kana(scene, "ナイト・プロトコル", 44, 236, MAGENTA, size=19, gap=7)
    hud_frame(scene, CYAN)
    return scene


def build_outro():
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(scene)

    panel(scene, 420, 96, 1080, 800, ACID, alpha=238)

    draw.rounded_rectangle((W // 2 - 88, 136, W // 2 + 88, 296), radius=22,
                           fill=(*ACID, 14), outline=(*ACID, 190), width=2)
    spaced(draw, (W // 2, 178), "303", font(F_TITLE, 74), ACID, spacing=2, anchor="ma")

    chroma_text(scene, (W // 2, 320), "REPLICANT 303", font(F_TITLE, 104), WHITE,
                offset=6, anchor="ma", accent=MAGENTA, accent2=CYAN)
    spaced(draw, (W // 2, 440), "ANDROID ACID ENGINE", font(F_MONO, 24), CYAN,
           spacing=5, anchor="ma")

    # Tracklist of the patches used in the cut.
    y = 520
    spaced(draw, (W // 2, y), "PATCHES IN THIS CUT", font(F_MONO, 14), MUTED,
           spacing=3, anchor="ma")
    y += 32
    for i, section in enumerate(SECTIONS):
        letter = "ABCDEFGH"[section["bank"]]
        left = f"{letter}/{section['slot'] + 1:02d}"
        draw.text((W // 2 - 400, y), left, font=font(F_MONO, 18),
                  fill=(150, 240, 255), anchor="la")
        draw.text((W // 2 - 310, y), section["name"], font=font(F_MONO, 18),
                  fill=(232, 228, 246), anchor="la")
        draw.text((W // 2 + 400, y), f"{section['end'] - section['start']:.1f}s",
                  font=font(F_MONO, 13), fill=(*DIM, 255), anchor="ra")
        y += 28

    y += 20
    draw.line((W // 2 - 400, y, W // 2 + 400, y), fill=(*ACID, 80), width=1)
    y += 26
    spaced(draw, (W // 2, y), "FREE & OPEN SOURCE  ·  MIT LICENSED", font(F_MONO, 17),
           (196, 186, 216), spacing=2, anchor="ma")
    spaced(draw, (W // 2, y + 42), "github.com/DanimanX/Replicant303Android",
           font(F_MONO, 21), CYAN, spacing=1, anchor="ma")

    vertical_kana(scene, "リプリカント", 44, 236, ACID, size=19, gap=7)
    hud_frame(scene, MAGENTA)
    return scene


def build_spectrum(mix, bands=40):
    """Per-video-frame log-spaced band energies for the analyzer strip."""
    frames = int(DURATION * FPS)
    window = 2048
    freqs = np.fft.rfftfreq(window, 1 / SR)
    edges = np.geomspace(40.0, 14000.0, bands + 1)
    indices = [np.where((freqs >= edges[i]) & (freqs < edges[i + 1]))[0] for i in range(bands)]
    out = np.zeros((frames, bands), dtype=np.float32)
    win = np.hanning(window)
    for f in range(frames):
        center = int(f / FPS * SR)
        a = max(0, center - window // 2)
        chunk = mix[a:a + window]
        if len(chunk) < window:
            chunk = np.pad(chunk, (0, window - len(chunk)))
        spec = np.abs(np.fft.rfft(chunk.mean(axis=1) * win))
        for b, idx in enumerate(indices):
            out[f, b] = spec[idx].mean() if len(idx) else 0.0
    out = np.log1p(out * 40.0)
    out /= max(1e-6, out.max())
    # Smooth in time so the bars feel like a meter, not a strobe.
    for f in range(1, frames):
        out[f] = np.maximum(out[f] * 0.55, out[f - 1] * 0.72)
    return out


def draw_spectrum(frame, values, accent):
    draw = ImageDraw.Draw(frame, "RGBA")
    base = H - 58
    count = len(values)
    width = W - 220
    bw = width / count
    # Dark strip so the analyzer stays readable over the city grid.
    draw.rectangle((72, base - 74, W - 72, base + 26), fill=(6, 3, 12, 150))
    draw.line((72, base + 26, W - 72, base + 26), fill=(*accent, 60), width=1)
    for i, value in enumerate(values):
        height = int(5 + value * 56)
        x = 110 + i * bw
        color = accent if i % 5 else WHITE
        alpha = int(60 + 130 * value)
        bar_w = bw * 0.60
        draw.rectangle((x, base - height, x + bar_w, base), fill=(*color, alpha))
        # Faint mirrored reflection on the strip.
        draw.rectangle((x, base + 4, x + bar_w, base + 4 + height * 0.22),
                       fill=(*color, int(alpha * 0.25)))


def draw_scope(frame, mix, t, x, y, width, height, accent):
    center = int(t * SR)
    window = 1024
    start = max(0, center - window // 2)
    chunk = mix[start:start + window]
    if len(chunk) < window:
        return
    mono = chunk.mean(axis=1)
    peak = float(np.abs(mono).max()) or 1.0
    mono = mono / peak
    draw = ImageDraw.Draw(frame, "RGBA")
    draw.line((x, y + height / 2, x + width, y + height / 2), fill=(*accent, 40), width=1)
    points = []
    for i in range(window):
        px = x + width * i / (window - 1)
        py = y + height / 2 - float(mono[i]) * height * 0.42
        points.append((px, py))
    draw.line(points, fill=(*accent, 70), width=5)
    draw.line(points, fill=(*accent, 235), width=2)
    draw.line((x, y - 22, x + width, y - 22), fill=(*accent, 45), width=1)
    draw.text((x, y - 18), "SCOPE // LIVE", font=font(F_MONO, 11), fill=(*DIM, 255), anchor="la")
    draw.text((x + width, y - 18), "48 KHZ / FLOAT32", font=font(F_MONO, 11),
              fill=(*accent, 150), anchor="ra")


def draw_grid(frame, t, accent):
    draw = ImageDraw.Draw(frame, "RGBA")
    horizon = int(H * 0.615)
    vp = (W // 2, horizon)
    # Converging verticals: faint, they only exist to give the floor depth.
    for i in range(-10, 11):
        x = W // 2 + i * 190
        draw.line((vp[0], vp[1], x, H), fill=(*accent, 9), width=1)
    # Perspective horizontals scrolling toward the viewer.
    speed = 0.55
    for i in range(1, 15):
        phase = (i / 14.0 + (t * speed) % 1.0) % 1.0
        y = horizon + int((H - horizon) * (phase ** 2.4))
        alpha = int(4 + 13 * phase)
        draw.line((0, y, W, y), fill=(*accent, alpha), width=1)


def glitch(frame, strength, rng):
    if strength <= 0.02:
        return frame
    arr = np.array(frame.convert("RGB"))
    h, w, _ = arr.shape
    out = arr.copy()

    # Horizontal slice displacement (the main glitch gesture).
    for _ in range(int(5 + 9 * strength)):
        y0 = rng.randint(0, max(1, h - 10))
        height = rng.randint(3, max(4, int(30 * strength)))
        shift = rng.randint(-int(70 * strength), int(70 * strength))
        out[y0:y0 + height] = np.roll(out[y0:y0 + height], shift, axis=1)

    # Chromatic split on a few bands only, so the frame keeps its colour.
    for _ in range(int(2 + 3 * strength)):
        y0 = rng.randint(0, max(1, h - 60))
        height = rng.randint(8, 46)
        split = int(3 + 14 * strength)
        out[y0:y0 + height, split:, 0] = arr[y0:y0 + height, :-split, 0]
        out[y0:y0 + height, :-split, 2] = arr[y0:y0 + height, split:, 2]

    # Sparse colour blocks and dropout lines.
    for _ in range(int(6 * strength)):
        bx = rng.randint(0, max(1, w - 90))
        by = rng.randint(0, max(1, h - 20))
        bw = rng.randint(18, int(160 * strength) + 20)
        bh = rng.randint(2, 9)
        tone = rng.choice([MAGENTA, CYAN, ACID])
        out[by:by + bh, bx:bx + bw] = tone
    return Image.fromarray(out, "RGB").convert("RGBA")


def section_index_at(t):
    for i, section in enumerate(SECTIONS):
        if section["start"] <= t < section["end"]:
            return i
    return None


def transition_strength(t):
    boundaries = [s["start"] for s in SECTIONS[1:]] + [OUTRO_START]
    strength = 0.0
    for b in boundaries:
        delta = abs(t - b)
        if delta < 0.20:
            strength = max(strength, 1.0 - delta / 0.20)
    return strength


def render(video_path, poster_path, audio_path, mix, spectrum):
    base = background()
    scan = scanline_overlay()
    devices = build_device_layers()

    scenes = [build_intro()]
    for i in range(len(SECTIONS)):
        scenes.append(build_scene(i, devices))
    scenes.append(build_outro())

    intro_index = 0
    outro_index = len(scenes) - 1

    def active_scene(t):
        if t < INTRO_END:
            return intro_index, None
        if t >= OUTRO_START:
            return outro_index, None
        index = section_index_at(t)
        if index is None:
            return outro_index, None
        return index + 1, index

    cmd = [
        "ffmpeg", "-y",
        "-f", "rawvideo", "-vcodec", "rawvideo", "-pix_fmt", "rgb24",
        "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-i", audio_path,
        "-c:v", "libx264", "-preset", "medium", "-crf", "17",
        "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.2",
        "-af", "alimiter=limit=0.94", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", video_path,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE)
    rng = random.Random(142)
    total = int(DURATION * FPS)

    for frame_index in range(total):
        t = frame_index / FPS
        scene_index, section_index = active_scene(t)
        accent = GREEN if section_index is None else SECTION_COPY[section_index]["accent"]

        img = base.copy()

        # Animated city floor.
        draw_grid(img, t, accent if section_index is not None else CYAN)

        # Scene layer with a short fade at the boundaries.
        layer = scenes[scene_index].copy()
        alpha = 1.0
        start = 0.0
        end = DURATION
        if scene_index == intro_index:
            end = INTRO_END
        elif scene_index == outro_index:
            start = OUTRO_START
        else:
            start = SECTIONS[scene_index - 1]["start"]
            end = SECTIONS[scene_index - 1]["end"]
        fade = 0.22
        alpha = min(1.0, (t - start) / fade + 0.15, (end - t) / fade + 0.15)
        alpha = max(0.0, min(1.0, alpha))
        if alpha < 1.0:
            layer.putalpha(layer.getchannel("A").point(lambda p, a=alpha: int(p * a)))

        # Subtle float on the device scenes, plus a decaying kick on each drop.
        dx = int(4 * math.sin(t * 0.7))
        dy = int(3 * math.sin(t * 0.53 + 1.1))
        for section in SECTIONS:
            since = t - section["start"]
            if 0.0 <= since < 0.22:
                decay = 1.0 - since / 0.22
                dx += int(7 * decay * math.sin(since * 90))
                dy += int(5 * decay * math.cos(since * 110))
                break
        img.alpha_composite(layer, (dx, dy))

        # Drop flash.
        for section in SECTIONS:
            since = t - section["start"]
            if 0.0 <= since < 0.12:
                alpha = int(55 * (1.0 - since / 0.12))
                flash = Image.new("RGBA", (W, H), (255, 245, 255, alpha))
                img.alpha_composite(flash)
                break

        # Live scope inside the text panel.
        if section_index is not None:
            height = PANEL_H.get(section_index)
            if height:
                draw_scope(img, mix, t, 108, 132 + height - 92, 700, 52, accent)

        # HUD chrome.
        draw = ImageDraw.Draw(img, "RGBA")
        draw_spectrum(img, spectrum[min(frame_index, len(spectrum) - 1)], accent)
        draw.text((W - 208, 44), f"{int(t // 60):02d}:{t % 60:05.2f}",
                  font=font(F_MONO, 15), fill=(150, 140, 175, 170), anchor="la")
        draw.text((110, 44), "REPLICANT 303 // NIGHT PROTOCOL",
                  font=font(F_MONO, 13), fill=(150, 140, 175, 120), anchor="la")
        draw.text((W - 208, H - 104), f"{int(BPM)} BPM",
                  font=font(F_MONO, 14), fill=(*accent, 200), anchor="la")
        draw.text((110, H - 104), f"SIGNAL {int(60 + 40 * math.sin(t * 3.1)):02d}",
                  font=font(F_MONO, 13), fill=(150, 140, 175, 140), anchor="la")

        # Scanlines + a slow sweep band.
        img.alpha_composite(scan)
        sweep_y = int((t * 210) % (H + 300)) - 150
        sweep = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sweep)
        sd.rectangle((0, sweep_y, W, sweep_y + 90), fill=(120, 240, 255, 10))
        img.alpha_composite(sweep.filter(ImageFilter.GaussianBlur(24)))

        # Glitch cuts around the section boundaries, plus the intro slam.
        strength = transition_strength(t)
        if t < 0.55:
            strength = max(strength, 0.85 * (1.0 - t / 0.55))
        if 1.10 < t < 1.28:
            strength = max(strength, 0.8)
        if strength > 0.02:
            img = glitch(img, strength, rng)

        proc.stdin.write(img.convert("RGB").tobytes())

        if abs(t - 14.2) < 1.0 / FPS:
            img.convert("RGB").save(poster_path)

    proc.stdin.close()
    proc.wait()
    if proc.returncode != 0:
        raise SystemExit("ffmpeg failed: " + proc.stderr.read().decode(errors="ignore")[-2000:])


def main():
    os.makedirs(ASSETS, exist_ok=True)
    audio_path, mix = build_audio()
    spectrum = build_spectrum(mix)
    video_path = os.path.join(ASSETS, "replicant303_android_promo_1080p.mp4")
    poster_path = os.path.join(ASSETS, "replicant303_android_poster.png")
    render(video_path, poster_path, audio_path, mix, spectrum)
    print(f"[video] {video_path}")
    print(f"[poster] {poster_path}")


if __name__ == "__main__":
    main()
