#!/usr/bin/env python3
"""The mod's logo: a wind turbine and a row of solar panels on a hill, under a low sun.

    python3 tools/gen_icon.py
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas, polygon, mix                                          # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'icon.png')
SIZE = 128


def sky(c):
    top, bottom = (58, 120, 196, 255), (200, 228, 244, 255)
    for y in range(SIZE):
        t = y / (SIZE * 0.78)
        row = mix(top, bottom, min(1.0, t))
        for x in range(SIZE):
            c.set(x, y, row)


def sun(c):
    cx, cy = 98, 36
    for r, alpha in ((30, 0.10), (24, 0.16), (18, 0.30)):
        c.aa_disc(cx, cy, r, (255, 236, 150, 255), alpha=alpha)
    c.aa_disc(cx, cy, 12, (255, 244, 190, 255))


def hills(c):
    for base, colour, amp, phase in ((92, (86, 150, 84, 255), 7, 0.0), (104, (58, 116, 66, 255), 6, 1.7)):
        for x in range(SIZE):
            y0 = int(base + amp * math.sin(x / 19.0 + phase))
            for y in range(y0, SIZE):
                shade = 1.0 - 0.18 * (y - y0) / 40.0
                c.set(x, y, tuple(int(v * shade) for v in colour[:3]) + (255,))


def turbine(c, cx, cy, tower_len, blade, turn):
    foot_y = cy + tower_len
    polygon(c, [(cx - 2.2, foot_y), (cx + 2.2, foot_y), (cx + 1.0, cy), (cx - 1.0, cy)], (238, 240, 244, 255))
    polygon(c, [(cx + 0.2, foot_y), (cx + 2.2, foot_y), (cx + 1.0, cy), (cx + 0.2, cy)], (178, 184, 194, 255))
    for k in range(3):
        a = turn + k * 2.0 * math.pi / 3.0
        ux, uy = math.sin(a), -math.cos(a)
        px, py = -uy, ux
        tip = (cx + ux * blade, cy + uy * blade)
        root = (cx + ux * 4, cy + uy * 4)
        mid = (cx + ux * blade * 0.35, cy + uy * blade * 0.35)
        polygon(c, [(root[0] + px * 1.6, root[1] + py * 1.6), (mid[0] + px * 2.8, mid[1] + py * 2.8),
                    tip, (mid[0] - px * 1.2, mid[1] - py * 1.2), (root[0] - px * 1.0, root[1] - py * 1.0)],
                (246, 248, 252, 255))
    c.aa_disc(cx, cy, 3.6, (214, 218, 226, 255))
    c.aa_disc(cx - 0.8, cy - 0.8, 1.6, (252, 252, 255, 255))


def panels(c):
    for i in range(4):
        x0 = 26 + i * 17
        y0 = 100 + i * 2
        polygon(c, [(x0, y0), (x0 + 15, y0 - 3), (x0 + 15, y0 + 7), (x0, y0 + 10)], (24, 52, 104, 255))
        polygon(c, [(x0 + 1, y0 + 1), (x0 + 14, y0 - 1.6), (x0 + 14, y0 + 2), (x0 + 1, y0 + 4.5)], (52, 100, 178, 255))
        for k in (5, 10):
            c.aa_line(x0 + k, y0 - 3 + k * 0.2, x0 + k, y0 + 9 + k * 0.1, (20, 40, 86, 255), width=0.7, alpha=0.8)
        c.aa_line(x0 + 1, y0 + 4.6, x0 + 14, y0 + 2, (20, 40, 86, 255), width=0.7, alpha=0.8)
        c.aa_line(x0 + 2, y0 + 1.2, x0 + 8, y0 - 0.4, (170, 210, 250, 255), width=1.2, alpha=0.55)
        c.rect(x0 + 6, y0 + 9, x0 + 9, y0 + 14, (90, 94, 102, 255))


def main():
    c = Canvas(SIZE, SIZE)
    sky(c)
    sun(c)
    hills(c)
    turbine(c, 40, 40, 58, 30, 0.35)
    turbine(c, 84, 62, 36, 18, 1.1)
    panels(c)
    c.write(OUT)
    print('icon.png')


if __name__ == '__main__':
    main()
