#!/usr/bin/env python3
"""
Replicant 303 Android — 1080p promotional video generator.

Uses real emulator screenshots of the Compose UI, a soundtrack rendered by the
native 303 engine (tools/host/render_demo.cpp), and a procedural hard-techno
bed. Outputs:

    assets/replicant303_android_promo_1080p.mp4
    assets/replicant303_android_poster.png
"""

import math
import os
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
DURATION = 48.0
SR = 48000
BPM = 132.0

GREEN = (183, 255, 75)
MINT = (47, 230, 160)
RED = (255, 90, 94)
AMBER = (255, 180, 92)
WHITE = (232, 255, 240)
MUTED = (140, 170, 150)
DIM = (80, 104, 90)

F_TITLE = "/usr/share/fonts/TTF/RobotoCondensed-Bold.ttf"
F_BODY = "/usr/share/fonts/TTF/RobotoCondensed-Regular.ttf"
F_MONO = "/usr/share/fonts/TTF/JetBrainsMonoNerdFontMono-Bold.ttf"

_fonts = {}


def font(path, size):
    key = (path, size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(path, size)
    return _fonts[key]


# ---------------------------------------------------------------------------
# Audio
# ---------------------------------------------------------------------------

def load_wav(path, n):
    with wave.open(path, "rb") as w:
        ch = w.getnchannels()
        sw = w.getsampwidth()
        raw = np.frombuffer(w.readframes(w.getnframes()), dtype=np.uint8)
    if sw == 2:
        v = raw.view(np.int16).astype(np.float32) / 32768.0
    elif sw == 3:
        b = raw.reshape(-1, 3)
        x = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8)
             | (b[:, 2].astype(np.int32) << 16))
        x = np.where(x >= 1 << 23, x - (1 << 24), x).astype(np.float32) / 8388608.0
        v = x
    else:
        v = raw.astype(np.float32) / 127.0 - 1.0
    v = v.reshape(-1, ch)
    if ch == 1:
        v = np.repeat(v, 2, axis=1)
    if len(v) < n:
        v = np.pad(v, ((0, n - len(v)), (0, 0)))
    return v[:n]


def env_ramp(n, points):
    times = np.array([p[0] for p in points], dtype=np.float64)
    gains = np.array([p[1] for p in points], dtype=np.float64)
    t = np.arange(n) / SR
    return np.interp(t, times, gains).astype(np.float32)


def synth_drums(n, rng):
    t = np.arange(n) / SR
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

    # Four-on-the-floor kick.
    kick_len = int(0.34 * SR)
    kt = np.arange(kick_len) / SR
    freq = 46.0 + 95.0 * np.exp(-kt / 0.030)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    kick = np.sin(phase) * np.exp(-kt / 0.20)
    click = rng.standard_normal(kick_len).astype(np.float32) * np.exp(-kt / 0.003) * 0.22
    kick = (kick + click).astype(np.float32)
    for k in range(int(DURATION / beat) + 1):
        add(kick, k * beat, 0.95)

    # Closed hats on the off-beat 8ths.
    hat_len = int(0.055 * SR)
    ht = np.arange(hat_len) / SR
    sos = signal.butter(2, 7000.0, "hp", fs=SR, output="sos")
    for k in range(int(DURATION / (beat / 2)) + 1):
        noise = rng.standard_normal(hat_len).astype(np.float32)
        noise = signal.sosfilt(sos, noise).astype(np.float32)
        hat = noise * np.exp(-ht / 0.018)
        add(hat, k * beat / 2 + beat / 2, 0.20, -0.25)

    # Clap on two and four.
    clap_len = int(0.16 * SR)
    ct = np.arange(clap_len) / SR
    sos_band = signal.butter(2, [900.0, 3800.0], "bp", fs=SR, output="sos")
    for bar in range(int(DURATION / (beat * 4)) + 1):
        for offset in (beat, beat * 3):
            noise = rng.standard_normal(clap_len).astype(np.float32)
            noise = signal.sosfilt(sos_band, noise).astype(np.float32)
            env = np.exp(-ct / 0.045) * (1.0 + 0.6 * np.exp(-ct / 0.008))
            add((noise * env).astype(np.float32), bar * beat * 4 + offset, 0.30, 0.15)

    return out


def build_audio():
    n = int(DURATION * SR)
    rng = np.random.default_rng(303)
    t = np.arange(n) / SR

    stem = os.path.join(ASSETS, "replicant303_android_demo.wav")
    if not os.path.exists(stem):
        raise SystemExit(f"missing {stem}; run tools/host render_demo first")

    acid = load_wav(stem, n)
    drums = synth_drums(n, rng)

    # Ping-pong dotted-8th delay on the acid line.
    delay = int((60.0 / BPM) * 0.75 * SR)
    delayed = np.zeros_like(acid)
    delayed[delay:, 0] = acid[:-delay, 1] * 0.30
    delayed[delay * 2:, 1] = acid[:-delay * 2, 0] * 0.20
    acid = acid * 0.82 + delayed

    # Dark detuned pad.
    pad = np.zeros((n, 2), dtype=np.float32)
    for ch, detune in ((0, 0.996), (1, 1.004)):
        p = np.zeros(n, dtype=np.float32)
        for f in (55.0, 82.407, 110.0, 130.813):
            p += (2.0 * ((f * detune * t) % 1.0) - 1.0).astype(np.float32) * 0.16
        sos = signal.butter(2, 340.0, "lp", fs=SR, output="sos")
        pad[:, ch] = signal.sosfilt(sos, p).astype(np.float32)

    beat = 60.0 / BPM
    sidechain = np.ones(n, dtype=np.float32)
    for k in range(int(DURATION / beat) + 1):
        i0 = int(k * beat * SR)
        length = min(int(beat * 0.7 * SR), n - i0)
        if length <= 0:
            continue
        shape = 0.55 + 0.45 * (1.0 - np.exp(-np.arange(length) / (SR * 0.11)))
        sidechain[i0:i0 + length] = np.minimum(sidechain[i0:i0 + length], shape)

    pad_gain = env_ramp(n, [(0, 0.0), (5, 0.55), (12, 0.38), (26, 0.40),
                            (36, 0.46), (44, 0.30), (48, 0.0)])

    # Noise riser into the final act.
    riser = np.zeros((n, 2), dtype=np.float32)
    mask = (t > 41.0) & (t < 45.0)
    if np.any(mask):
        idx = np.where(mask)[0]
        noise = rng.standard_normal(len(idx)).astype(np.float32)
        sos = signal.butter(2, 1600.0, "hp", fs=SR, output="sos")
        noise = signal.sosfilt(sos, noise).astype(np.float32)
        env = np.linspace(0.0, 0.20, len(idx)) ** 1.5
        riser[idx, 0] = noise * env
        riser[idx, 1] = noise * env

    drum_gain = env_ramp(n, [(0, 0.0), (2.0, 0.35), (5.0, 0.92), (43.0, 0.92),
                             (46.0, 0.55), (48.0, 0.0)])
    acid_gain = env_ramp(n, [(0, 0.0), (1.0, 0.55), (4.0, 0.90), (44.0, 0.90),
                             (48.0, 0.0)])

    mix = (acid * acid_gain[:, None] * 1.00
           + drums * drum_gain[:, None] * 0.62
           + pad * (pad_gain * sidechain)[:, None] * 0.42
           + riser)

    # Gentle bus glue and a hard safety limiter.
    mix = np.tanh(mix * 1.15).astype(np.float32)
    peak = np.abs(mix).max()
    if peak > 0:
        mix *= 0.88 / peak
    fade = int(1.2 * SR)
    mix[:fade] *= np.linspace(0.0, 1.0, fade)[:, None]
    mix[-int(1.6 * SR):] *= np.linspace(1.0, 0.0, int(1.6 * SR))[:, None]

    out = "/tmp/replicant303_promo_mix.wav"
    pcm = (np.clip(mix, -1.0, 1.0) * 32767.0).astype(np.int16)
    with wave.open(out, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"[audio] wrote {out} ({DURATION:.0f}s, peak {np.abs(mix).max():.2f})")
    return out, mix


# ---------------------------------------------------------------------------
# Artwork
# ---------------------------------------------------------------------------

def background():
    top = np.array([7, 20, 12], dtype=np.float32)
    bottom = np.array([2, 5, 3], dtype=np.float32)
    t = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    arr = (top[None, None, :] * (1 - t) + bottom[None, None, :] * t)
    arr = np.repeat(arr, W, axis=1).astype(np.uint8)
    img = Image.fromarray(arr, "RGB").convert("RGBA")

    grid = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for x in range(0, W, 64):
        gd.line((x, 0, x, H), fill=(30, 54, 38, 34), width=1)
    for y in range(0, H, 64):
        gd.line((0, y, W, y), fill=(30, 54, 38, 18), width=1)
    img = Image.alpha_composite(img, grid)

    for color, cx, cy, radius, alpha in (
        (GREEN, 330, 20, 640, 26),
        (MINT, 1660, 1010, 680, 20),
        (RED, 1180, 120, 420, 12),
    ):
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
                   fill=(color[0], color[1], color[2], alpha))
        glow = glow.filter(ImageFilter.GaussianBlur(150))
        img = Image.alpha_composite(img, glow)

    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse((-W * 0.28, -H * 0.35, W * 1.28, H * 1.35), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(170))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 118))
    dark.putalpha(Image.eval(vig, lambda p: 118 - int(p * 118 / 255)))
    img = Image.alpha_composite(img, dark)

    rng = np.random.default_rng(303)
    noise = rng.normal(0, 6, (H, W, 1)).repeat(3, axis=2)
    noise_img = Image.fromarray(np.clip(noise + 128, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    noise_img.putalpha(9)
    img = Image.alpha_composite(img, noise_img)
    return img


def device_frame(path, height=None, width=None, radius_ratio=0.045, camera=True):
    src = Image.open(path).convert("RGB")
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
                        fill=(8, 16, 10, 255), outline=(55, 92, 64, 255), width=2)

    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, w - 1, h - 1), radius=max(4, outer_r - bezel), fill=255
    )
    frame.paste(screen, (bezel, bezel), mask)

    d.rounded_rectangle((bezel - 1, bezel - 1, bezel + w, bezel + h),
                        radius=max(4, outer_r - bezel), outline=(0, 0, 0, 150), width=1)

    if camera and h > w:
        cx, cy = fw // 2, bezel // 2 + 1
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(20, 32, 24, 255))
        d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=(6, 12, 8, 255))
    return frame


def shadow_for(frame):
    sw, sh = frame.size
    shadow = Image.new("RGBA", (sw + 160, sh + 160), (0, 0, 0, 0))
    mask = frame.getchannel("A")
    black = Image.new("RGBA", frame.size, (0, 0, 0, 220))
    shadow.paste(black, (80, 92), mask)
    return shadow.filter(ImageFilter.GaussianBlur(36))


def glow_for(w, h, color, alpha=80):
    g = Image.new("RGBA", (w + 300, h + 300), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.rounded_rectangle((150, 150, 150 + w, 150 + h), radius=60,
                        fill=(color[0], color[1], color[2], alpha))
    return g.filter(ImageFilter.GaussianBlur(90))


def paste_device(layer, frame, shadow, glow, x, y):
    if glow is not None:
        layer.alpha_composite(glow, (x - 150, y - 150))
    layer.alpha_composite(shadow, (x - 80, y - 80))
    layer.alpha_composite(frame, (x, y))


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


def text_glow(layer, xy, text, fnt, fill, anchor="la", blur=16, alpha=80):
    g = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.text(xy, text, font=fnt, fill=(fill[0], fill[1], fill[2], alpha), anchor=anchor)
    g = g.filter(ImageFilter.GaussianBlur(blur))
    layer.alpha_composite(g)
    ImageDraw.Draw(layer).text(xy, text, font=fnt, fill=fill, anchor=anchor)


def text_block(layer, x, y, eyebrow, title, bullets, accent=GREEN, title_size=88):
    d = ImageDraw.Draw(layer)
    f_eye = font(F_MONO, 19)
    f_title = font(F_TITLE, title_size)
    f_bullet = font(F_MONO, 19)

    d.ellipse((x, y + 6, x + 10, y + 16), fill=accent)
    spaced(d, (x + 22, y), eyebrow, f_eye, accent, spacing=2)

    yy = y + 40
    for line in title.split("\n"):
        text_glow(layer, (x, yy), line, f_title, WHITE, blur=20, alpha=55)
        yy += int(title_size * 0.92)

    yy += 16
    for b in bullets:
        d.rectangle((x, yy + 9, x + 16, yy + 11), fill=accent)
        d.text((x + 28, yy), b, font=f_bullet, fill=MUTED, anchor="la")
        yy += 35
    return yy


def scene_title(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)

    d.rounded_rectangle((110, 176, 246, 318), radius=18,
                        fill=(183, 255, 75, 18), outline=(183, 255, 75, 120), width=2)
    spaced(d, (132, 208), "303", font(F_TITLE, 66), GREEN, spacing=2)
    text_glow(s, (108, 340), "REPLICANT", font(F_TITLE, 142), WHITE, blur=32, alpha=70)
    text_glow(s, (108, 472), "303", font(F_TITLE, 142), GREEN, blur=32, alpha=60)
    spaced(d, (116, 616), "ANDROID ACID ENGINE", font(F_MONO, 26), MINT, spacing=4)
    d.line((116, 674, 760, 674), fill=(183, 255, 75, 80), width=1)
    spaced(d, (116, 706), "TB-303 VIRTUAL ANALOG // NATIVE C++20", font(F_MONO, 17), MUTED, spacing=2)
    spaced(d, (116, 742), "KOTLIN + COMPOSE  ·  OBOE / AAUDIO  ·  USB MIDI", font(F_MONO, 15), DIM, spacing=1)

    paste_device(s, frames["phone_playing"], shadows["phone_playing"], glows["green"], 1290, 118)
    paste_device(s, frames["phone_red"], shadows["phone_red"], glows["red"], 900, 260)
    return s


def scene_latency(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    text_block(s, 108, 150, "01 // LOW LATENCY", "FAST ENOUGH\nTO FEEL ANALOG.",
               ["OBOE 1.9.3  ·  AAUDIO EXCLUSIVE FIRST",
                "ONE-BURST BUFFER WHEN THE HAL ALLOWS IT",
                "48 KHZ FLOAT32  ·  NO LOCKS IN THE CALLBACK",
                "LIVE APIS / LATENCY / XRUN TELEMETRY"], accent=GREEN, title_size=86)
    paste_device(s, frames["phone_playing"], shadows["phone_playing"], glows["green"], 1290, 112)
    return s


def scene_sequencer(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    spaced(d, (92, 72), "02 // PATTERN CORE", font(F_MONO, 19), MINT, spacing=3)
    text_glow(s, (92, 108), "8 × 16 ACID STEPS.", font(F_TITLE, 78), WHITE, blur=20, alpha=55)
    spaced(d, (94, 198), "GATE · ACCENT · SLIDE · SWING · 1-16 LENGTH", font(F_MONO, 17), MUTED, spacing=1)
    paste_device(s, frames["tablet_land"], shadows["tablet_land"], glows["green"], 70, 286)
    paste_device(s, frames["phone_sequence"], shadows["phone_sequence"], glows["mint"], 1430, 118)
    return s


def scene_vault(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    text_block(s, 108, 140, "03 // PRESET VAULT", "512 PATCHES.\nZERO FRICTION.",
               ["8 BANKS × 64 COMPLETE PRESETS",
                "DETERMINISTIC PROCEDURAL GENERATOR",
                ".MIDI IMPORT / EXPORT WITH PATCH METADATA",
                "FACTORY OR USER  ·  STORED WITH THE SESSION"], accent=AMBER, title_size=84)
    paste_device(s, frames["phone_vault"], shadows["phone_vault"], glows["amber"], 1290, 112)
    return s


def scene_midi(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    text_block(s, 108, 142, "04 // USB MIDI", "IN. OUT. IN SYNC.",
               ["CLASS-COMPLIANT USB MIDI HOSTS",
                "MIDI 1.0 BYTE STREAM + MIDI 2.0 UMP",
                "24 PPQN CLOCK  ·  START / STOP  ·  CC MAP",
                "CHANNEL FILTER  ·  LIVE MONITOR"], accent=RED, title_size=88)
    paste_device(s, frames["phone_midi"], shadows["phone_midi"], glows["red"], 1290, 112)
    return s


def scene_skins(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    spaced(d, (W // 2, 60), "05 // TWO PANELS", font(F_MONO, 19), MINT, spacing=4, anchor="ma")
    text_glow(s, (W // 2, 92), "ONE ENGINE.", font(F_TITLE, 82), WHITE, anchor="ma", blur=20, alpha=55)
    paste_device(s, frames["phone_playing"], shadows["phone_playing"], glows["green"], 300, 180)
    paste_device(s, frames["phone_red"], shadows["phone_red"], glows["red"], 1130, 180)
    d.text((W // 2, 1008), "TD-3 GREEN  ·  OBSIDIAN RED", font=font(F_MONO, 18),
           fill=MUTED, anchor="ma")
    return s


def scene_end(frames, shadows, glows):
    s = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    d.rounded_rectangle((W // 2 - 68, 208, W // 2 + 68, 344), radius=20,
                        fill=(183, 255, 75, 18), outline=(183, 255, 75, 110), width=2)
    spaced(d, (W // 2, 246), "303", font(F_TITLE, 62), GREEN, spacing=2, anchor="ma")
    text_glow(s, (W // 2, 386), "REPLICANT 303", font(F_TITLE, 116), WHITE, anchor="ma", blur=28, alpha=65)
    spaced(d, (W // 2, 526), "ANDROID ACID ENGINE", font(F_MONO, 24), GREEN, spacing=4, anchor="ma")
    spaced(d, (W // 2, 606), "FREE & OPEN SOURCE  ·  MIT LICENSED", font(F_MONO, 18), MUTED, spacing=2, anchor="ma")
    spaced(d, (W // 2, 664), "github.com/DanimanX/Replicant303Android", font(F_MONO, 22),
           (196, 230, 210), spacing=1, anchor="ma")
    d.line((W // 2 - 380, 740, W // 2 + 380, 740), fill=(183, 255, 75, 80), width=1)
    spaced(d, (W // 2, 772), "BUILT BY DANIMANX  ·  v1.0.0", font(F_MONO, 14), DIM, spacing=2, anchor="ma")
    return s


SCENE_STARTS = [0.0, 5.5, 13.0, 20.5, 28.0, 35.5, 43.5]
SCENE_ENDS = [5.5, 13.0, 20.5, 28.0, 35.5, 43.5, 48.0]


def scene_alpha(t, idx):
    start, end = SCENE_STARTS[idx], SCENE_ENDS[idx]
    if t < start or t > end:
        return 0.0
    local = t - start
    total = end - start
    fade_in = 0.65 if idx > 0 else 1.1
    fade_out = 0.5 if idx < len(SCENE_STARTS) - 1 else 1.2
    return min(1.0, local / fade_in, (total - local) / fade_out)


def waveform_strip(draw, envelope, t, accent):
    if not envelope:
        return
    n = len(envelope)
    idx = int((t / DURATION) * (n - 1))
    span = 110
    vals = []
    for i in range(span):
        j = idx - span // 2 + i
        vals.append(envelope[j] if 0 <= j < n else 0.0)
    base = H - 26
    bw = W / span
    for i, v in enumerate(vals):
        hgt = int(2 + v * 18)
        draw.rectangle((i * bw + 1, base - hgt, i * bw + bw - 2, base + 2),
                       fill=(accent[0], accent[1], accent[2], 46))
    # Playhead.
    x = (t / DURATION) * W
    draw.line((x, H - 52, x, H - 4), fill=(accent[0], accent[1], accent[2], 90), width=2)


def envelope_from_audio(mix):
    mono = np.abs(mix).mean(axis=1)
    win = int(SR * 0.02)
    usable = len(mono) // win
    env = mono[:usable * win].reshape(usable, win).max(axis=1)
    env = env / (env.max() + 1e-9)
    return env.tolist()


def build_scenes():
    bg = background()

    paths = {
        "phone_playing": "phone_synth_playing.png",
        "phone_red": "phone_synth_red.png",
        "phone_sequence": "phone_sequence.png",
        "phone_vault": "phone_vault_generated.png",
        "phone_midi": "phone_midi.png",
    }
    raw = {}
    for key, name in paths.items():
        raw[key] = device_frame(os.path.join(SHOTS, name), height=812)

    raw["tablet_land"] = device_frame(
        os.path.join(SHOTS, "tablet_landscape.png"), width=1180,
        radius_ratio=0.035, camera=False
    )

    shadows = {k: shadow_for(v) for k, v in raw.items()}
    glows = {
        "green": glow_for(560, 900, GREEN, 60),
        "mint": glow_for(560, 900, MINT, 55),
        "red": glow_for(560, 900, RED, 55),
        "amber": glow_for(560, 900, AMBER, 50),
    }

    scenes = [
        scene_title(raw, shadows, glows),
        scene_latency(raw, shadows, glows),
        scene_sequencer(raw, shadows, glows),
        scene_vault(raw, shadows, glows),
        scene_midi(raw, shadows, glows),
        scene_skins(raw, shadows, glows),
        scene_end(raw, shadows, glows),
    ]
    return scenes, bg


def render(video_path, poster_path, audio_path, mix):
    scenes, bg = build_scenes()
    env = envelope_from_audio(mix)
    cmd = [
        "ffmpeg", "-y",
        "-f", "rawvideo", "-vcodec", "rawvideo", "-pix_fmt", "rgb24",
        "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-i", audio_path,
        "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.2",
        "-af", "alimiter=limit=0.90", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", video_path,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE)
    total = int(DURATION * FPS)
    accents = [GREEN, GREEN, MINT, AMBER, RED, GREEN, GREEN]
    for frame_idx in range(total):
        t = frame_idx / FPS
        img = bg.copy()
        idx = max(i for i, st in enumerate(SCENE_STARTS) if t >= st)
        alpha = scene_alpha(t, idx)

        if idx < len(scenes) - 1 and t >= SCENE_STARTS[idx + 1] - 0.28:
            nxt = idx + 1
            a2 = min(1.0, (t - (SCENE_STARTS[nxt] - 0.28)) / 0.56)
            a1 = 1.0 - a2
            layer = scenes[idx].copy()
            layer.putalpha(layer.getchannel("A").point(lambda p, a=a1: int(p * a)))
            img.alpha_composite(layer, (int(3 * math.sin(t * 0.6)), int(2 * math.cos(t * 0.4))))
            layer2 = scenes[nxt].copy()
            layer2.putalpha(layer2.getchannel("A").point(lambda p, a=a2: int(p * a)))
            img.alpha_composite(layer2, (int(-2 * math.sin(t * 0.5)), int(2 * math.cos(t * 0.45))))
        else:
            layer = scenes[idx].copy()
            layer.putalpha(layer.getchannel("A").point(lambda p, a=alpha: int(p * a)))
            img.alpha_composite(layer, (int(3 * math.sin(t * 0.55)), int(2 * math.cos(t * 0.43))))

        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay, "RGBA")
        waveform_strip(od, env, t, accents[idx])

        # Timecode / BPM readout.
        od.text((W - 150, 26), f"{int(t // 60):02d}:{t % 60:05.2f}", font=font(F_MONO, 14),
                fill=(140, 170, 150, 120), anchor="la")
        od.text((30, 26), "REPLICANT 303 // ANDROID", font=font(F_MONO, 12),
                fill=(140, 170, 150, 90), anchor="la")
        od.line((0, 0, W, 0), fill=(183, 255, 75, 24), width=2)

        # Slow, soft scanning highlight.
        sx = int((t * 140) % (W + 400)) - 200
        od.line((sx, 0, sx + 140, H), fill=(183, 255, 75, 7), width=50)
        img.alpha_composite(overlay)

        proc.stdin.write(img.convert("RGB").tobytes())

        if abs(t - 8.6) < 1.0 / FPS:
            img.convert("RGB").save(poster_path)

    proc.stdin.close()
    proc.wait()
    if proc.returncode != 0:
        raise SystemExit("ffmpeg failed: " + proc.stderr.read().decode(errors="ignore")[-2000:])


def main():
    os.makedirs(ASSETS, exist_ok=True)
    audio_path, mix = build_audio()
    video_path = os.path.join(ASSETS, "replicant303_android_promo_1080p.mp4")
    poster_path = os.path.join(ASSETS, "replicant303_android_poster.png")
    render(video_path, poster_path, audio_path, mix)
    print(f"[video] wrote {video_path}")
    print(f"[poster] wrote {poster_path}")


if __name__ == "__main__":
    main()
