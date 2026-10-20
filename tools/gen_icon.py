#!/usr/bin/env python3
"""The mod's logo, 512 px: a turbine against a low sun, a far one on the hill, a rack of panels in front.

    python3 tools/gen_icon.py

One file, src/main/resources/assets/renewables/icon.png: mods.toml's logoFile and the Modrinth project's icon
(tools/modrinth.py), which crops it to a rounded square and caps it at 256 KiB. It has to read at 32 px in a
list, so it is a few large shapes, each in two tones, and the blades carry the red tips the model has.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas, polygon, mix                                          # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'icon.png')
SIZE = 512
CORNER = 84
SUN = (382.0, 142.0, 54.0)

SKY_TOP = (28, 86, 176)
SKY_MID = (86, 158, 228)
HORIZON = (255, 212, 148)
SUN_CORE = (255, 232, 140)
SUN_RIM = (255, 214, 104)
# glare is a lightening: gold mixed into blue would be grey
GLOW = (255, 248, 226)

WHITE = (247, 249, 252)
SHADE = (196, 205, 220)
RED = (214, 62, 50)


def sky(c):
    sx, sy, _ = SUN

    def pixel(x, y):
        t = y / SIZE
        base = mix(SKY_TOP, SKY_MID, t / 0.5) if t < 0.5 else mix(SKY_MID, HORIZON, min(1.0, (t - 0.5) / 0.22))
        d = math.hypot(x + 0.5 - sx, y + 0.5 - sy)
        glow = 0.75 * math.exp(-(d / 120.0) ** 2)
        return mix(base, GLOW, glow)[:3] + (255,)

    c.each(pixel)
    c.aa_disc(sx, sy, SUN[2], SUN_RIM)
    c.aa_disc(sx, sy, SUN[2] - 5, SUN_CORE)


def hill(c, base, amplitude, period, phase, top, bottom):
    """A hill from its crest down, lighter at the crest: the light is low and behind the viewer's right shoulder."""
    crest = [base + amplitude * math.sin(x / period + phase) for x in range(SIZE + 1)]
    points = [(x, crest[x]) for x in range(0, SIZE + 1, 4)] + [(SIZE, SIZE), (0, SIZE)]
    polygon(c, points, top)

    def pixel(x, y, colour):
        if y < crest[x] + 1.0:
            return None
        return mix(top, bottom, min(1.0, (y - crest[x]) / 140.0))[:3] + (colour[3],)

    c.over(pixel)


def blade(c, hx, hy, angle, length, root):
    """One blade, split along its pitch axis into a lit and a shaded half, the outer eighth painted red."""
    ux, uy = math.sin(angle), -math.cos(angle)
    px, py = -uy, ux

    def at(t, side):
        chord = root * (0.55 + 1.6 * t) if t < 0.22 else root * (0.9 - 0.75 * (t - 0.22) / 0.78)
        return (hx + ux * length * t + px * chord * side, hy + uy * length * t + py * chord * side)

    steps = [0.06, 0.22, 0.5, 0.875, 1.0]
    lead = [at(t, 1.0) for t in steps]
    trail = [at(t, -0.55) for t in steps]
    axis = [at(t, 0.0) for t in steps]
    polygon(c, lead + axis[::-1], WHITE)
    polygon(c, axis + trail[::-1], SHADE)
    # the red band, over both halves
    band = [at(0.875, 1.0), at(1.0, 1.0), at(1.0, -0.55), at(0.875, -0.55)]
    polygon(c, band, RED)


def turbine(c, x, foot, hub_y, length, tower, turn, near=True):
    top = tower * 0.5
    polygon(c, [(x - tower, foot), (x + tower, foot), (x + top, hub_y), (x - top, hub_y)], WHITE if near else SHADE)
    polygon(c, [(x + tower * 0.15, foot), (x + tower, foot), (x + top, hub_y), (x + top * 0.15, hub_y)],
            SHADE if near else mix(SHADE, SKY_MID, 0.35))
    # the nacelle, seen a little from the side, behind the hub
    n = tower * 1.05
    polygon(c, [(x - n * 0.9, hub_y - n * 0.75), (x + n * 2.6, hub_y - n * 0.95), (x + n * 2.7, hub_y + n * 0.55),
                (x - n * 0.8, hub_y + n * 0.7)], WHITE if near else SHADE)
    polygon(c, [(x - n * 0.8, hub_y + n * 0.1), (x + n * 2.65, hub_y - n * 0.1), (x + n * 2.7, hub_y + n * 0.55),
                (x - n * 0.8, hub_y + n * 0.7)], SHADE if near else mix(SHADE, SKY_MID, 0.35))
    for k in range(3):
        blade(c, x, hub_y, turn + k * 2.0 * math.pi / 3.0, length, tower * 0.9)
    c.aa_disc(x, hub_y, tower * 0.95, SHADE)
    c.aa_disc(x - tower * 0.2, hub_y - tower * 0.2, tower * 0.7, WHITE)


def panels(c):
    """A tilted table of three modules, the near end largest, on two legs."""
    fl, fr, br, bl = (236, 470), (486, 432), (468, 368), (258, 394)
    for (lx, ly) in ((272, 462), (452, 436)):
        c.aa_rect(lx - 4, ly - 8, lx + 4, ly + 34, (122, 128, 138))
        c.aa_rect(lx, ly - 8, lx + 4, ly + 34, (92, 98, 108))
    polygon(c, [(246, 496), (486, 462), (486, 472), (246, 508)], (40, 96, 52), alpha=0.45)
    polygon(c, [(fl[0] - 6, fl[1] + 6), (fr[0] + 6, fr[1] + 4), (br[0] + 4, br[1] - 5), (bl[0] - 5, bl[1] - 5)],
            (214, 220, 228))

    def lerp(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    polygon(c, [fl, fr, br, bl], (24, 46, 96))
    # cells: 3 modules along the table, each six cells long, four across
    for i in range(18):
        for j in range(4):
            u0, u1 = i / 18.0, (i + 1) / 18.0
            v0, v1 = j / 4.0, (j + 1) / 4.0
            a, b = lerp(fl, fr, u0), lerp(fl, fr, u1)
            d, e = lerp(bl, br, u0), lerp(bl, br, u1)
            corners = [lerp(a, d, v0), lerp(b, e, v0), lerp(b, e, v1), lerp(a, d, v1)]
            inset = [(p[0] * 0.92 + q[0] * 0.08, p[1] * 0.92 + q[1] * 0.08)
                     for p, q in zip(corners, corners[2:] + corners[:2])]
            polygon(c, inset, mix((38, 74, 150), (62, 108, 196), (j / 3.0) * 0.6 + (i % 6) / 30.0))
    for k in (1, 2):
        a, b = lerp(fl, fr, k / 3.0), lerp(bl, br, k / 3.0)
        c.aa_line(a[0], a[1], b[0], b[1], (214, 220, 228), width=3.0)
    # the sky in the glass
    polygon(c, [lerp(bl, br, 0.08), lerp(bl, br, 0.30), lerp(fl, fr, 0.20), lerp(fl, fr, 0.02)], (210, 232, 255),
            alpha=0.28)
    polygon(c, [lerp(bl, br, 0.38), lerp(bl, br, 0.46), lerp(fl, fr, 0.36), lerp(fl, fr, 0.28)], (210, 232, 255),
            alpha=0.18)


def round_corners(c):
    """Transparent outside a rounded square, with a thin dark rim inside it so it holds on a light page."""
    half = SIZE / 2.0
    inner = half - CORNER

    def pixel(x, y, colour):
        qx = abs(x + 0.5 - half) - inner
        qy = abs(y + 0.5 - half) - inner
        d = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - CORNER
        cover = min(1.0, max(0.0, 0.5 - d))
        rim = 0.35 * min(1.0, max(0.0, d + 6.5)) * cover
        out = mix(colour, (14, 34, 62, 255), rim)
        return out[:3] + (int(round(colour[3] * cover)),)

    c.over(pixel)


def main():
    c = Canvas(SIZE, SIZE)
    sky(c)
    hill(c, 352, 14, 70.0, 0.8, (112, 176, 96), (82, 146, 78))
    turbine(c, 432, 352, 262, 52, 5.0, 0.9, near=False)
    hill(c, 410, 18, 95.0, 2.6, (82, 154, 76), (44, 104, 56))
    turbine(c, 186, SIZE + 10, 196, 168, 15.0, -0.32)
    panels(c)
    round_corners(c)
    c.write(OUT)
    print('icon.png')


if __name__ == '__main__':
    main()
