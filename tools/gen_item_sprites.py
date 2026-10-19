#!/usr/bin/env python3
"""Every inventory sprite of the mod: sixteen pixels a side, drawn the way Minecraft draws its own items.

    python3 tools/gen_item_sprites.py      # textures/item/<name>.png for every entry of ITEM_TEXTURES

Vanilla's rules, and nothing else: a silhouette that says what the thing is at hotbar size; a ramp of five tones a
material (outline, shadow, base, light, highlight) lit from the top left; the outline in the material's own darkest
tone, never black; no noise.  A drawing is a function over a Sprite, which keeps a material and a tone a pixel, so the
outline is worked out from the silhouette (Sprite.outline) rather than drawn round it by hand.

One machine family reads as a family: the six turbines are one drawing at six sizes, the three lattice towers one
body with three heads, the four PV mounts one panel on four kinds of support.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas                                                       # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures', 'item')
W = 16

# ------------------------------------------------------------------ the ramps: outline, shadow, base, light, highlight

IRON = ((54, 54, 60), (110, 110, 118), (160, 160, 166), (204, 204, 208), (242, 242, 244))
STEEL = ((32, 36, 44), (70, 78, 90), (106, 115, 128), (146, 154, 168), (190, 197, 208))
ALU = ((66, 70, 78), (132, 138, 148), (176, 182, 190), (210, 214, 220), (240, 242, 246))
WHITE = ((80, 82, 88), (164, 167, 172), (206, 209, 213), (232, 234, 236), (252, 252, 252))
PANEL = ((50, 54, 60), (126, 132, 138), (168, 174, 178), (202, 206, 208), (228, 231, 232))
DARK = ((12, 12, 16), (34, 34, 40), (56, 56, 64), (84, 84, 94), (122, 122, 134))
COPPER = ((92, 40, 22), (150, 66, 40), (196, 98, 60), (232, 134, 92), (252, 172, 134))
GOLD = ((110, 62, 10), (176, 112, 16), (226, 176, 30), (250, 226, 90), (255, 250, 184))
SOLAR = ((10, 18, 44), (22, 40, 92), (36, 66, 142), (66, 106, 192), (146, 188, 238))
PCB = ((12, 42, 20), (22, 76, 34), (36, 110, 50), (64, 148, 76), (120, 196, 116))
PORCELAIN = ((60, 28, 14), (108, 54, 28), (150, 80, 44), (190, 112, 66), (224, 152, 102))
GLASS = ((82, 120, 132), (150, 198, 212), (192, 228, 238), (224, 244, 250), (255, 255, 255))
CONCRETE = ((70, 70, 68), (118, 118, 114), (152, 152, 148), (180, 180, 176), (206, 206, 202))
WOOD = ((42, 26, 14), (74, 48, 28), (102, 68, 40), (132, 92, 58), (160, 118, 80))
SILICON = ((34, 38, 52), (68, 76, 98), (100, 110, 136), (140, 152, 178), (192, 202, 222))
RED = ((82, 10, 10), (146, 22, 22), (198, 40, 38), (234, 86, 74), (255, 150, 138))
LED = ((10, 54, 20), (18, 100, 36), (40, 172, 66), (104, 226, 114), (200, 255, 200))
YELLOW = ((102, 74, 0), (176, 136, 8), (228, 188, 28), (250, 222, 80), (255, 246, 170))
BLUE = ((14, 28, 68), (28, 56, 124), (46, 88, 176), (86, 136, 218), (164, 202, 250))
SCREEN = ((8, 34, 38), (14, 70, 72), (24, 116, 112), (70, 184, 164), (170, 240, 220))
KIOSK = ((20, 44, 28), (40, 82, 52), (60, 114, 74), (90, 148, 102), (140, 190, 146))
BEIGE = ((84, 72, 54), (156, 142, 114), (196, 182, 150), (220, 208, 178), (240, 232, 206))
ORANGE = ((106, 46, 0), (178, 82, 8), (228, 122, 24), (250, 166, 70), (255, 212, 148))
AMBER = ((104, 62, 8), (168, 108, 22), (214, 156, 48), (240, 196, 102), (255, 234, 170))
CAST = ((34, 44, 40), (64, 80, 74), (92, 110, 102), (124, 142, 134), (166, 182, 174))
RUST = ((60, 30, 16), (104, 54, 30), (140, 80, 46), (172, 110, 70), (204, 146, 104))


# ------------------------------------------------------------------ the sprite

class Sprite:
    """Sixteen by sixteen cells, each a (ramp, tone, keep) or empty.  ``keep`` cells are left alone by outline()."""

    def __init__(self):
        self.cells = [[None] * W for _ in range(W)]

    def get(self, x, y):
        return self.cells[y][x] if 0 <= x < W and 0 <= y < W else None

    def put(self, x, y, ramp, tone=2, keep=False):
        if 0 <= x < W and 0 <= y < W:
            self.cells[y][x] = (ramp, max(0, min(4, tone)), keep)

    def fill(self, x0, y0, x1, y1, ramp, tone=2, keep=False):
        """A rectangle, corners inclusive."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, ramp, tone, keep)

    def retone(self, x, y, tone):
        cell = self.get(x, y)
        if cell is not None:
            self.cells[y][x] = (cell[0], max(0, min(4, tone)), cell[2])

    def erase(self, x, y):
        if 0 <= x < W and 0 <= y < W:
            self.cells[y][x] = None

    def outline(self):
        """Every cell on the silhouette's edge takes its own material's darkest tone, as vanilla's items do."""
        edge = []
        for y in range(W):
            for x in range(W):
                cell = self.cells[y][x]
                if cell is None or cell[2]:
                    continue
                if any(self.get(x + dx, y + dy) is None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for x, y in edge:
            self.retone(x, y, 0)

    def canvas(self):
        c = Canvas(W, W)
        for y in range(W):
            for x in range(W):
                cell = self.cells[y][x]
                if cell is not None:
                    c.set(x, y, cell[0][cell[1]] + (255,))
        return c


# ------------------------------------------------------------------ shapes

def line(s, x0, y0, x1, y1, ramp, tone=2, keep=False):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        s.put(x0, y0, ramp, tone, keep)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def bevel(s, x0, y0, x1, y1, ramp, light=3, dark=1):
    """A filled rectangle lit from the top left: its outer ring is left for outline() or the caller."""
    s.fill(x0, y0, x1, y1, ramp, 2)
    for x in range(x0 + 1, x1):
        s.put(x, y0 + 1, ramp, light)
        s.put(x, y1 - 1, ramp, dark)
    for y in range(y0 + 1, y1):
        s.put(x0 + 1, y, ramp, light)
        s.put(x1 - 1, y, ramp, dark)
    s.put(x0 + 1, y0 + 1, ramp, min(4, light + 1))


def ring(s, x0, y0, x1, y1, ramp, tone=0):
    """A one-pixel frame, corners inclusive."""
    for x in range(x0, x1 + 1):
        s.put(x, y0, ramp, tone)
        s.put(x, y1, ramp, tone)
    for y in range(y0, y1 + 1):
        s.put(x0, y, ramp, tone)
        s.put(x1, y, ramp, tone)


def box3d(s, x0, y0, x1, y1, depth, front, top=None, side=None):
    """A box seen from the front, a little from above and from the right: the top and the right face go back
    ``depth`` pixels at forty-five degrees.  Returns nothing; outline() closes it."""
    top, side = top or front, side or front
    for k in range(1, depth + 1):
        for x in range(x0 + k, x1 + k + 1):
            s.put(x, y0 - k, top, 3)
        for y in range(y0 - k, y1 - k + 1):
            s.put(x1 + k, y, side, 1)
    for k in range(1, depth + 1):
        s.put(x0 + k, y0 - k, top, 4 if k == depth else 3)
    bevel(s, x0, y0, x1, y1, front)


def column_tone(x, x0, x1):
    """The tone of a column of an upright cylinder, lit from the left."""
    t = (x - x0) / max(1, x1 - x0)
    return 4 if 0.12 < t < 0.3 else 3 if t < 0.42 else 2 if t < 0.72 else 1


def row_tone(y, y0, y1):
    """The tone of a row of a lying cylinder, lit from above."""
    t = (y - y0) / max(1, y1 - y0)
    return 4 if 0.12 < t < 0.3 else 3 if t < 0.42 else 2 if t < 0.72 else 1


def can(s, x0, y0, x1, y1, ramp, top=None):
    """An upright cylinder seen a little from above: the body, and the top as a flat ellipse two rows high."""
    top = top or ramp
    for y in range(y0 + 1, y1 + 1):
        for x in range(x0, x1 + 1):
            s.put(x, y, ramp, column_tone(x, x0, x1))
    for x in range(x0 + 1, x1):
        s.put(x, y0, top, 3)
        s.put(x, y0 + 1, top, 4 if x < (x0 + x1) // 2 else 3)
    s.erase(x0, y1)
    s.erase(x1, y1)


def disc(s, cx, cy, r, ramp, hole=0.0, keep=False):
    """A disc (or ring) lit from the top left; ``cx`` and ``cy`` may be half pixels."""
    for y in range(W):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            if d > r + 0.35 or d < hole - 0.35:
                continue
            k = ((x - cx) + (y - cy)) / (1.414 * max(r, 1.0))
            if hole and d < (hole + r) / 2.0:
                k = -k
            tone = 4 if k < -0.62 else 3 if k < -0.2 else 2 if k < 0.36 else 1
            s.put(x, y, ramp, tone, keep)


def diagonal(s, x0, y0, length, width, ramp, tones=None, keep=False):
    """A bar going up and to the right from (x0, y0), ``width`` pixels across each row: the vanilla tool handle."""
    tones = tones or ([3] + [2] * (width - 2) + [1] if width > 2 else [3, 1][:width])
    for i in range(length):
        for w in range(width):
            s.put(x0 + i + w, y0 - i, ramp, tones[min(w, len(tones) - 1)], keep)


def coil(s, cx, cy, r, thick, ramp, twist=0, tail=None):
    """A coil of cable seen end on: a ring ``thick`` across, with a lay of light strands every ``twist`` pixels round it."""
    disc(s, cx, cy, r, ramp, hole=r - thick + 0.5)
    if twist:
        for y in range(W):
            for x in range(W):
                cell = s.get(x, y)
                if cell and cell[0] is ramp and (x + y) % twist == 0 and cell[1] >= 2:
                    s.retone(x, y, cell[1] + 1)
    if tail:
        tx, ty = tail
        line(s, int(cx + r * 0.6), int(cy + r * 0.6), tx, ty, ramp, 2)


# ------------------------------------------------------------------ raw stock and the older parts

def ingot(ramp, mark=None):
    """Vanilla's ingot: a bar lying across the slot, its top lit and its long face in light, its end in shadow."""
    s = Sprite()
    for k in range(4):
        for x in range(3 + k, 12 + k):
            s.put(x, 8 - k, ramp, 3 if k < 3 else 4)
    for y in range(8, 12):
        for x in range(1 + (11 - y) // 2 * 0 + 2 - (y - 8) // 2, 12 + 0):
            s.put(x, y, ramp, 2)
    for y in range(5, 12):
        for x in range(12, 15):
            if x - 12 <= y - 5 and y - x <= -1 + 12 - 12 + (y - 5 >= 0) * 0 + 11 - 12 + 1:
                s.put(x, y, ramp, 1)
    s.outline()
    if mark:
        mark(s)
    return s


def silicon_ingot():
    s = Sprite()
    # a sawn boule: a lying cylinder with its cut end towards the viewer
    for y in range(5, 12):
        for x in range(4, 14):
            s.put(x, y, SILICON, row_tone(y, 5, 11))
    disc(s, 4, 8.0, 3.4, SILICON)
    for y in range(5, 12):
        s.put(4, y, SILICON, 3)
    s.outline()
    for x in range(6, 13, 3):
        s.put(x, 6, SILICON, 4)
    return s


def silicon_wafer():
    s = Sprite()
    # a mono wafer: square, its corners cut, a grey mirror with the sky going across it
    for y in range(2, 14):
        for x in range(2, 14):
            if min(x - 2, 13 - x) + min(y - 2, 13 - y) < 1:
                continue
            s.put(x, y, SILICON, 2)
    s.outline()
    for k in range(3, 13):
        s.put(k, 15 - k - 2, SILICON, 4 if 5 < k < 10 else 3)
        if k > 3:
            s.put(k, 15 - k - 1, SILICON, 3)
    for k in range(3, 7):
        s.put(k + 6, 15 - k - 2 + 6 - 6, SILICON, 1) if False else None
    return s


def steel_plate():
    s = Sprite()
    # a plate lying flat, seen from above and the front: a parallelogram and its edge
    for k, y in enumerate(range(4, 11)):
        for x in range(4 - k // 2, 14 - k // 2):
            s.put(x, y, STEEL, 3)
    for y in range(11, 13):
        for x in range(1, 11):
            s.put(x, y, STEEL, 1)
    s.outline()
    for y in range(5, 10):
        s.put(12 - (y - 4) // 2, y, STEEL, 2)
    line(s, 5, 5, 9, 5, STEEL, 4)
    for x, y in ((5, 6), (11, 6), (4, 9), (10, 9)):
        s.put(x, y, STEEL, 1)
    return s


def steel_section():
    s = Sprite()
    # an I-beam end on: two flanges and a web
    s.fill(2, 2, 13, 4, STEEL, 3)
    s.fill(2, 11, 13, 13, STEEL, 2)
    s.fill(6, 4, 9, 11, STEEL, 2)
    s.outline()
    for x in range(3, 13):
        s.put(x, 3, STEEL, 4 if x < 8 else 3)
        s.put(x, 12, STEEL, 2 if x < 9 else 1)
    for y in range(5, 11):
        s.put(7, y, STEEL, 3)
        s.put(8, y, STEEL, 2)
    return s


def busbar():
    s = Sprite()
    diagonal(s, 1, 13, 11, 4, COPPER, tones=[3, 3, 2, 1])
    s.outline()
    for x, y in ((4, 10), (9, 5)):
        s.put(x, y, COPPER, 0)
        s.put(x + 1, y, COPPER, 1)
    for i in range(1, 10):
        s.put(2 + i, 12 - i, COPPER, 4 if i % 3 == 0 else 3)
    return s


def copper_coil():
    s = Sprite()
    # a bobbin: two steel cheeks and the winding between them
    s.fill(2, 2, 13, 3, IRON, 3)
    s.fill(2, 12, 13, 13, IRON, 2)
    for y in range(4, 12):
        for x in range(3, 13):
            s.put(x, y, COPPER, column_tone(x, 3, 12) - (1 if y % 2 else 0))
    s.outline()
    for x in range(3, 13):
        s.put(x, 2, IRON, 4 if x < 7 else 3)
    return s


def resin():
    s = Sprite()
    # a roll of encapsulant film: a lying cylinder, its end a spiral
    for y in range(4, 13):
        for x in range(5, 15):
            s.put(x, y, AMBER, row_tone(y, 4, 12))
    disc(s, 5, 8.0, 4.4, AMBER)
    s.outline()
    disc(s, 5, 8.0, 1.2, AMBER, keep=True)
    for x, y in ((5, 6), (4, 7), (7, 8), (5, 10), (3, 9)):
        s.put(x, y, AMBER, 1)
    for x in range(7, 14, 2):
        s.put(x, 5, AMBER, 4)
    return s


def tempered_glass():
    s = Sprite()
    s.fill(2, 2, 13, 13, GLASS, 2)
    s.outline()
    for y in range(3, 13):
        for x in range(3, 13):
            s.put(x, y, GLASS, 2)
    line(s, 4, 9, 9, 4, GLASS, 4)
    line(s, 4, 10, 10, 4, GLASS, 3)
    line(s, 9, 12, 12, 9, GLASS, 3)
    for x in range(3, 13):
        s.put(x, 3, GLASS, 3)
    # glass is seen through: its inside lets the slot show
    for y in range(4, 12):
        for x in range(4, 12):
            cell = s.get(x, y)
            if cell and cell[1] == 2:
                s.erase(x, y)
    return s


def metal_casing():
    s = Sprite()
    box3d(s, 2, 6, 10, 13, 3, IRON)
    s.outline()
    # the open front: a dark mouth in a lit frame
    s.fill(4, 8, 8, 11, DARK, 1)
    for x in range(4, 9):
        s.put(x, 8, DARK, 0)
    return s


def circuit_board():
    s = Sprite()
    bevel(s, 1, 3, 14, 12, PCB)
    s.outline()
    s.fill(6, 5, 10, 9, DARK, 2)
    ring(s, 6, 5, 10, 9, DARK, 1)
    for x in range(6, 11, 2):
        s.put(x, 4, IRON, 3)
        s.put(x, 10, IRON, 3)
    for y in range(5, 11, 2):
        line(s, 2, y + 1, 5, y + 1, GOLD, 2 if y % 4 else 3)
    line(s, 11, 6, 13, 6, GOLD, 3)
    line(s, 11, 8, 12, 8, GOLD, 2)
    s.put(12, 10, GOLD, 4)
    s.put(3, 4, IRON, 4)
    return s


def control_board():
    s = Sprite()
    bevel(s, 1, 3, 14, 12, BLUE)
    s.outline()
    # a microcontroller, two LEDs and a green terminal block along the edge
    s.fill(4, 5, 7, 8, DARK, 2)
    s.put(4, 5, DARK, 4)
    s.fill(9, 9, 13, 11, LED, 1)
    for x in range(9, 14, 2):
        s.put(x, 10, IRON, 3)
    s.put(10, 5, RED, 4)
    s.put(12, 5, LED, 4)
    line(s, 3, 10, 7, 10, GOLD, 3)
    line(s, 8, 6, 8, 8, GOLD, 2)
    return s


def cpu():
    s = Sprite()
    s.fill(3, 3, 12, 12, DARK, 2)
    s.outline()
    for i in range(4, 12, 2):
        for x, y in ((i, 2), (i, 13), (2, i), (13, i)):
            s.put(x, y, GOLD, 3, keep=True)
    bevel(s, 5, 5, 10, 10, IRON)
    ring(s, 5, 5, 10, 10, IRON, 1)
    s.put(4, 4, GOLD, 4)
    return s


def screen():
    s = Sprite()
    bevel(s, 1, 2, 14, 11, DARK)
    s.fill(6, 12, 9, 12, DARK, 2)
    s.fill(4, 13, 11, 13, DARK, 2)
    s.outline()
    s.fill(3, 4, 12, 9, SCREEN, 2)
    line(s, 3, 8, 6, 6, LED, 3)
    line(s, 6, 6, 8, 7, LED, 3)
    line(s, 8, 7, 12, 4, LED, 3)
    s.put(3, 4, SCREEN, 4)
    s.put(12, 10, LED, 4)
    return s


def insulator():
    s = Sprite()
    # a pin insulator: three porcelain sheds on a galvanised pin
    s.fill(7, 12, 8, 15, IRON, 2)
    for y, half in ((2, 2), (3, 3), (4, 5), (5, 6), (6, 3), (7, 4), (8, 6), (9, 3), (10, 4), (11, 5)):
        for x in range(8 - half, 8 + half):
            s.put(x, y, PORCELAIN, column_tone(x, 8 - half, 7 + half))
    s.outline()
    for y in (4, 7, 10):
        s.put(7 - (2 if y > 4 else 1), y, PORCELAIN, 4)
    s.put(7, 2, PORCELAIN, 3)
    return s


def motor_core():
    s = Sprite()
    # a stator: a laminated ring with copper in its slots
    disc(s, 7.5, 7.5, 7.0, IRON, hole=3.6)
    s.outline()
    for a in range(0, 360, 45):
        x = int(round(7.5 + 4.6 * math.cos(math.radians(a))))
        y = int(round(7.5 + 4.6 * math.sin(math.radians(a))))
        s.put(x, y, COPPER, 3 if a in (180, 225, 270) else 2)
    disc(s, 7.5, 7.5, 2.0, STEEL)
    return s


def magnetic_core():
    s = Sprite()
    # a transformer core: laminated iron round two windows, with a winding on the middle limb
    bevel(s, 1, 2, 14, 13, IRON)
    s.outline()
    for x0 in (4, 9):
        s.fill(x0, 5, x0 + 2, 10, None or IRON, 0)
        for y in range(5, 11):
            for x in range(x0, x0 + 3):
                s.erase(x, y)
    for y in range(3, 13):
        if y % 2:
            for x in (2, 13):
                s.put(x, y, IRON, 1)
    for y in range(4, 12):
        for x in (7, 8):
            s.put(x, y, COPPER, 3 if x == 7 else 1)
    return s


def power_box():
    s = Sprite()
    # the pad-mount kiosk: a green cabinet with louvres and a lockable door
    box3d(s, 2, 5, 11, 14, 3, KIOSK)
    s.outline()
    line(s, 6, 6, 6, 13, KIOSK, 0)
    for y in range(7, 13, 2):
        line(s, 3, y, 5, y, KIOSK, 1)
        line(s, 7, y, 10, y, KIOSK, 1)
    s.put(5, 10, IRON, 4)
    s.put(8, 3, YELLOW, 3)
    return s


def utility_pole():
    s = Sprite()
    # a wooden pole, its crossarm and three pin insulators
    s.fill(7, 3, 8, 15, WOOD, 2)
    s.fill(1, 4, 14, 5, WOOD, 3)
    s.outline()
    for x in range(7, 9):
        for y in range(6, 15):
            s.put(x, y, WOOD, 3 if x == 7 else 1)
    for x in range(2, 14):
        s.put(x, 5, WOOD, 1)
    line(s, 5, 6, 7, 8, WOOD, 0)
    line(s, 10, 6, 8, 8, WOOD, 0)
    for x in (2, 7, 12):
        s.fill(x, 2, x + 1, 3, PORCELAIN, 3, keep=True)
        s.put(x + 1, 3, PORCELAIN, 1, keep=True)
    return s


def cab():
    s = Sprite()
    # the substation cabin: a prefabricated concrete box, a flat roof, a steel door with its warning sign
    box3d(s, 1, 6, 11, 14, 3, BEIGE, top=CONCRETE)
    s.outline()
    s.fill(3, 8, 6, 14, STEEL, 2)
    ring(s, 3, 8, 6, 14, STEEL, 0)
    s.put(5, 11, IRON, 4)
    s.put(4, 9, YELLOW, 4)
    for y in range(9, 13, 2):
        line(s, 8, y, 10, y, BEIGE, 1)
    return s


# ------------------------------------------------------------------ components

def bearing():
    s = Sprite()
    disc(s, 7.5, 7.5, 7.0, IRON, hole=2.4)
    s.outline()
    disc(s, 7.5, 7.5, 2.6, IRON, hole=2.0, keep=True)
    for a in range(0, 360, 40):
        x = int(round(7.5 + 4.7 * math.cos(math.radians(a))))
        y = int(round(7.5 + 4.7 * math.sin(math.radians(a))))
        s.put(x, y, STEEL, 1)
        if (a // 40) % 2 == 0:
            s.put(x, y, STEEL, 0)
    return s


def gear(s, cx, cy, r, teeth, ramp, phase=0.0):
    disc(s, cx, cy, r - 1.0, ramp, hole=max(0.0, r * 0.32))
    for k in range(teeth):
        a = phase + 2.0 * math.pi * k / teeth
        for t in (r - 0.4, r + 0.3):
            x, y = int(round(cx + t * math.cos(a))), int(round(cy + t * math.sin(a)))
            if s.get(x, y) is None:
                s.put(x, y, ramp, 2)


def gear_set():
    s = Sprite()
    gear(s, 5.0, 5.0, 4.6, 8, IRON)
    gear(s, 10.5, 10.5, 4.2, 8, STEEL, phase=0.4)
    s.outline()
    disc(s, 5.0, 5.0, 1.0, DARK, keep=True)
    disc(s, 10.5, 10.5, 1.0, DARK, keep=True)
    return s


def bypass_diode():
    s = Sprite()
    line(s, 1, 14, 5, 10, IRON, 3, keep=True)
    line(s, 10, 5, 14, 1, IRON, 3, keep=True)
    diagonal(s, 3, 12, 8, 5, DARK, tones=[3, 2, 2, 2, 1])
    s.outline()
    for i in range(5):
        s.put(9 + i - 1, 7 - i + 0, IRON, 3)
    return s


def mc4_connector():
    s = Sprite()
    # a mated pair: a black body each side of the coupling nut, a cable out of each end
    diagonal(s, 0, 15, 4, 2, DARK, tones=[3, 1])
    diagonal(s, 12, 3, 4, 2, DARK, tones=[3, 1])
    diagonal(s, 3, 12, 5, 4, DARK, tones=[3, 2, 2, 1])
    diagonal(s, 8, 7, 5, 4, DARK, tones=[3, 2, 2, 1])
    s.outline()
    for i in range(4):
        s.put(7 + i, 8 - i, IRON, 3)
    s.put(5, 11, BLUE, 3)
    s.put(10, 6, RED, 3)
    return s


def junction_box():
    s = Sprite()
    box3d(s, 3, 5, 11, 11, 2, DARK)
    s.outline()
    # two leads out of the bottom, red and black, each in its gland
    s.fill(4, 12, 5, 15, DARK, 2)
    s.fill(9, 12, 10, 15, DARK, 2)
    s.put(4, 12, RED, 3)
    s.put(9, 12, IRON, 3)
    line(s, 5, 7, 9, 7, DARK, 3)
    s.put(5, 9, IRON, 3)
    return s


def gpv_fuse():
    s = Sprite()
    # a cylindrical fuse lying across the slot: a white ceramic barrel and two silver caps
    for y in range(5, 11):
        for x in range(1, 15):
            ramp = IRON if x < 4 or x > 11 else WHITE
            s.put(x, y, ramp, row_tone(y, 5, 10))
    s.outline()
    for x in range(5, 11):
        s.put(x, 8, YELLOW if x in (6, 7) else WHITE, 2)
    s.put(4, 6, IRON, 4)
    return s


def load_break_switch():
    s = Sprite()
    bevel(s, 2, 2, 13, 13, PANEL)
    s.outline()
    s.fill(4, 4, 11, 11, YELLOW, 2)
    ring(s, 4, 4, 11, 11, YELLOW, 1)
    disc(s, 7.5, 7.5, 2.4, RED)
    line(s, 5, 10, 10, 5, RED, 1)
    line(s, 5, 9, 9, 5, RED, 3)
    return s


def capacitor_bank():
    s = Sprite()
    for x0 in (1, 6, 11):
        can(s, x0, 3, x0 + 3, 12, BLUE, top=IRON)
    s.fill(0, 13, 15, 14, STEEL, 2)
    s.outline()
    for x0 in (1, 6, 11):
        s.put(x0 + 1, 2, IRON, 3)
        s.put(x0 + 2, 2, IRON, 2)
    return s


def power_module():
    s = Sprite()
    # an IGBT module: a black moulded case on a copper baseplate, three screw terminals on top
    s.fill(1, 11, 14, 12, COPPER, 2)
    box3d(s, 2, 6, 12, 10, 2, DARK)
    s.outline()
    for x in (4, 7, 10):
        s.fill(x, 3, x + 1, 4, IRON, 3)
        s.put(x + 1, 4, IRON, 1)
    for x in range(2, 14):
        s.put(x, 11, COPPER, 3)
    return s


def inverter_bridge():
    s = Sprite()
    # three power modules on a finned heatsink
    for x in range(1, 15):
        for y in range(9, 15):
            s.put(x, y, ALU, 3 if x % 2 else 1)
    s.fill(1, 8, 14, 9, ALU, 2)
    for x0 in (1, 6, 11):
        s.fill(x0, 4, x0 + 3, 7, DARK, 2)
    s.outline()
    for x0 in (1, 6, 11):
        s.put(x0 + 1, 3, IRON, 3, keep=True)
        s.put(x0 + 2, 3, IRON, 3, keep=True)
        s.put(x0 + 1, 5, DARK, 3)
    return s


def enclosure():
    s = Sprite()
    # a steel cabinet with its door open
    box3d(s, 3, 5, 11, 14, 2, PANEL)
    s.outline()
    s.fill(5, 7, 10, 13, DARK, 2)
    for y in range(3, 15):
        s.put(1, y, PANEL, 3)
        s.put(2, y, PANEL, 1)
    for y in (3, 14):
        s.put(1, y, PANEL, 0)
        s.put(2, y, PANEL, 0)
    s.put(1, 8, IRON, 4)
    return s


def dc_section():
    s = Sprite()
    # a DC input panel: a row of fuse holders over a load-break switch
    bevel(s, 1, 1, 14, 14, PANEL)
    s.outline()
    for x0 in (3, 7, 11):
        s.fill(x0, 3, x0 + 1, 7, WHITE, 3)
        s.put(x0 + 1, 7, WHITE, 1)
        s.put(x0, 3, IRON, 4)
    s.fill(5, 9, 10, 12, YELLOW, 2)
    line(s, 6, 12, 9, 9, RED, 2)
    s.put(7, 11, RED, 3)
    return s


def sensor_head():
    s = Sprite()
    # a cup anemometer: three cups on a spider round its spindle
    disc(s, 7.5, 9.0, 1.4, IRON)
    s.fill(7, 10, 8, 14, IRON, 2)
    for angle in (90.0, 210.0, 330.0):
        a = math.radians(angle)
        cx, cy = 7.5 + 5.0 * math.cos(a), 9.0 - 5.0 * math.sin(a)
        line(s, 7 + (angle > 180) * (1 if angle == 330.0 else 0), 9, int(round(cx)), int(round(cy)), IRON, 2)
        disc(s, cx, cy, 2.1, WHITE)
    s.outline()
    return s


def mounting_rack():
    s = Sprite()
    # a fixed rack from the side: the rafter the modules lie on, a short front post, a tall back one, the brace
    for k in range(14):
        y = 9 - k * 6 // 13
        s.put(1 + k, y, ALU, 3)
        s.put(1 + k, y + 1, ALU, 2)
    s.fill(3, 10, 4, 15, ALU, 2)
    s.fill(11, 5, 12, 15, ALU, 2)
    line(s, 5, 14, 10, 9, ALU, 2)
    line(s, 5, 15, 10, 10, ALU, 2)
    s.outline()
    for x in (2, 6, 10, 13):
        s.put(x, 8 - (x - 1) * 6 // 13, ALU, 4, keep=True)
    return s


def torque_tube():
    s = Sprite()
    # a long square tube across the slot, its open end towards the top right
    diagonal(s, 0, 15, 11, 5, STEEL, tones=[3, 3, 2, 2, 1])
    s.fill(11, 1, 14, 4, STEEL, 2)
    s.outline()
    s.fill(12, 2, 13, 3, DARK, 1)
    for i in range(1, 10):
        s.put(1 + i, 14 - i, STEEL, 4 if i % 4 == 1 else 3)
    return s


def slew_drive():
    s = Sprite()
    # a slewing ring and the worm motor that turns it
    disc(s, 7.0, 9.0, 6.4, IRON, hole=2.6)
    box3d(s, 10, 2, 14, 6, 1, CAST)
    s.outline()
    disc(s, 7.0, 9.0, 3.0, STEEL, hole=2.4, keep=True)
    for a in range(0, 360, 30):
        x = int(round(7.0 + 6.4 * math.cos(math.radians(a))))
        y = int(round(9.0 + 6.4 * math.sin(math.radians(a))))
        s.retone(x, y, 1)
    return s


def generator_set():
    s = Sprite()
    # a generator: a finned cylindrical frame lying on its feet, the shaft out of its end
    for y in range(3, 12):
        for x in range(2, 13):
            s.put(x, y, CAST, row_tone(y, 3, 11))
    s.fill(3, 12, 5, 13, CAST, 1)
    s.fill(10, 12, 12, 13, CAST, 1)
    s.fill(13, 6, 15, 8, IRON, 3)
    s.outline()
    for x in range(4, 12, 2):
        line(s, x, 4, x, 10, CAST, 1)
    s.put(15, 7, IRON, 0)
    s.fill(5, 1, 8, 2, CAST, 2)
    ring(s, 5, 1, 8, 2, CAST, 0)
    return s


def gearbox():
    s = Sprite()
    # a cast housing, a fast shaft out of one side and the slow shaft's flange out of the other
    box3d(s, 3, 5, 11, 13, 2, CAST)
    s.fill(0, 7, 2, 10, IRON, 3)
    s.fill(14, 9, 15, 10, IRON, 2)
    s.outline()
    for y in (7, 11):
        line(s, 4, y, 10, y, CAST, 1)
    s.put(6, 9, IRON, 4)
    return s


def blade(long):
    """A blade lying across the slot, the root flange bottom left; the long one is longer and has the red tip."""
    s = Sprite()
    length = 13 if long else 10
    for i in range(length):
        width = 4 if i < 4 else 3 if i < length - 3 else 2
        for j in range(width):
            tip = long and i >= length - 3
            s.put(2 + i + j, 13 - i, RED if tip else WHITE, 3 if j == 0 else 1 if j == width - 1 else 2)
    disc(s, 2.5, 13.0, 1.9, IRON)
    s.outline()
    for i in range(1, length - 1):
        s.retone(3 + i, 12 - i, 4 if i < length // 2 else 3)
    return s


def tower_section():
    s = Sprite()
    # one can of a tower seen from above: a short white tube, hollow, the bolted flange round its rim
    for y in range(6, 14):
        for x in range(2, 14):
            s.put(x, y, WHITE, column_tone(x, 2, 13))
    for y in range(3, 9):
        for x in range(1, 15):
            if ((x - 7.5) / 6.8) ** 2 + ((y - 5.5) / 2.9) ** 2 <= 1.0:
                s.put(x, y, IRON, 3 if y < 6 else 2)
    s.erase(2, 13)
    s.erase(13, 13)
    s.outline()
    for y in range(4, 8):
        for x in range(3, 13):
            if ((x - 7.5) / 4.8) ** 2 + ((y - 5.6) / 1.7) ** 2 <= 1.0:
                s.put(x, y, DARK, 1 if y > 5 else 2)
    for x in range(3, 13, 3):
        s.put(x, 8 if 4 < x < 11 else 7, STEEL, 1)
    return s


# ------------------------------------------------------------------ the PV family: one panel on four supports

def panel(s, rows, ramp=SOLAR):
    """A module as a list of (x0, x1) spans a row, top to bottom: blue glass, its cell grid in a lighter tone."""
    for y, (x0, x1) in rows:
        for x in range(x0, x1 + 1):
            s.put(x, y, ramp, 2)
    for y, (x0, x1) in rows:
        for x in range(x0, x1 + 1):
            if (x - x0) % 3 == 0 or y % 3 == 0:
                s.retone(x, y, 3)


def pv_laminate():
    s = Sprite()
    panel(s, [(y, (2, 13)) for y in range(1, 15)])
    s.outline()
    for y in range(2, 14):
        s.put(2, y, SOLAR, 4 if y < 5 else 3)
    return s


def solar_cell():
    s = Sprite()
    # a mono cell: blue, its corners clipped, two silver busbars and the fingers across them
    for y in range(2, 14):
        for x in range(2, 14):
            if (x in (2, 13)) and (y in (2, 13)):
                continue
            s.put(x, y, SOLAR, 2 if y % 2 else 3)
    s.outline()
    for x in (5, 10):
        for y in range(3, 13):
            s.put(x, y, IRON, 3 if y < 8 else 2)
    s.put(3, 3, SOLAR, 4)
    s.put(4, 3, SOLAR, 4)
    return s


def tilted_panel(s, x0, y0, width, height, lean):
    """A module seen from in front and above, its top edge ``lean`` pixels further right than its bottom."""
    rows = []
    for k in range(height):
        shift = lean * (height - 1 - k) // max(1, height - 1)
        rows.append((y0 + k, (x0 + shift, x0 + shift + width - 1)))
    panel(s, rows)


def pv_flat():
    s = Sprite()
    # a ballasted flat roof table: the module almost flat, on concrete blocks
    tilted_panel(s, 1, 6, 12, 5, 3)
    s.fill(2, 11, 4, 13, CONCRETE, 2)
    s.fill(11, 11, 13, 13, CONCRETE, 2)
    s.outline()
    return s


def pv_tilt():
    s = Sprite()
    # a fixed rack: the module leaning back, a long leg behind and a short one in front
    tilted_panel(s, 1, 2, 11, 9, 3)
    s.fill(3, 11, 4, 14, ALU, 2)
    s.fill(11, 9, 12, 14, ALU, 2)
    s.outline()
    return s


def pv_track():
    s = Sprite()
    # a single-axis tracker: a row of modules on its torque tube, one post under the middle
    tilted_panel(s, 0, 4, 16, 4, 0)
    s.fill(0, 8, 15, 8, STEEL, 2)
    s.fill(7, 9, 8, 15, STEEL, 2)
    s.outline()
    for x in range(1, 15):
        s.put(x, 8, STEEL, 1)
    return s


def pv_dual():
    s = Sprite()
    # a dual-axis tracker: a square of modules on a pedestal, turned to the sun
    tilted_panel(s, 2, 1, 12, 9, 2)
    s.fill(7, 10, 8, 13, STEEL, 2)
    s.fill(4, 14, 11, 15, CONCRETE, 2)
    s.outline()
    s.put(7, 11, STEEL, 3)
    return s


# ------------------------------------------------------------------ cabinets: the inverter, the combiner, the controller

def pv_inverter():
    s = Sprite()
    # a central inverter: a white cabinet with two doors, a display, the fan hoods on the roof
    box3d(s, 1, 4, 11, 15, 3, WHITE)
    s.fill(4, 0, 6, 1, PANEL, 2)
    s.fill(9, 0, 11, 1, PANEL, 2)
    s.outline()
    line(s, 6, 5, 6, 14, WHITE, 1)
    s.fill(2, 6, 5, 8, SCREEN, 2)
    s.put(2, 6, SCREEN, 4)
    for y in range(11, 14):
        line(s, 8, y, 10, y, WHITE, 1 if y % 2 else 3)
    s.put(4, 10, LED, 3)
    return s


def pv_combiner():
    s = Sprite()
    # a string combiner: a grey box with its row of cable glands underneath
    box3d(s, 2, 4, 11, 12, 2, PANEL)
    s.outline()
    for x in range(3, 11, 2):
        s.put(x, 13, DARK, 2, keep=True)
        s.put(x, 14, DARK, 1, keep=True)
    s.fill(4, 6, 9, 8, WHITE, 3)
    s.put(4, 10, YELLOW, 3)
    s.put(5, 10, DARK, 2)
    return s


def plant_controller():
    s = Sprite()
    # the plant control cabinet: a tall grey cabinet with a screen and a row of status lamps
    box3d(s, 2, 3, 11, 15, 3, PANEL)
    s.outline()
    s.fill(4, 5, 9, 8, SCREEN, 2)
    line(s, 4, 7, 6, 6, LED, 3)
    line(s, 7, 6, 9, 5, LED, 3)
    s.put(4, 10, LED, 4)
    s.put(6, 10, YELLOW, 4)
    s.put(8, 10, RED, 4)
    line(s, 4, 12, 9, 12, PANEL, 1)
    return s


def met_station():
    s = Sprite()
    # a met mast: a tall thin lattice mast, a boom each side with its instrument, a guy wire to the ground
    for y in range(2, 16):
        s.put(7, y, STEEL, 2)
        s.put(8, y, STEEL, 1)
    for y in range(3, 15, 2):
        s.put(7 if y % 4 == 1 else 8, y, IRON, 3)
    line(s, 2, 5, 13, 5, STEEL, 2)
    line(s, 3, 10, 12, 10, STEEL, 2)
    disc(s, 2.0, 4.0, 1.3, WHITE)
    disc(s, 13.0, 4.0, 1.3, WHITE)
    s.fill(11, 8, 13, 9, WHITE, 3)
    s.fill(6, 0, 9, 1, WHITE, 3)
    s.outline()
    line(s, 9, 3, 15, 15, IRON, 1, keep=True)
    line(s, 6, 3, 0, 15, IRON, 1, keep=True)
    return s


# ------------------------------------------------------------------ the turbines: one drawing at six sizes

def blades(s, hub, reach, root, tips, angles=(90.0, 210.0, 330.0)):
    """Three tapered blades out of ``hub``: lit along their leading edge, in shadow along the other, no outline -
    a white blade on a grey slot reads by its own two tones, and an outline would turn it into a dark stick."""
    for angle in angles:
        a = math.radians(angle)
        d = (math.cos(a), -math.sin(a))
        n = (-d[1], d[0])
        for y in range(W):
            for x in range(W):
                px, py = x + 0.5 - hub[0], y + 0.5 - hub[1]
                t, u = px * d[0] + py * d[1], px * n[0] + py * n[1]
                if t < 1.0 or t > reach + 0.3:
                    continue
                width = root + (0.9 - root) * t / reach
                if abs(u) <= width / 2.0:
                    ramp = RED if tips and t > reach - 1.8 else WHITE
                    s.put(x, y, ramp, 4 if u < 0 else 1, keep=True)


def turbine(reach, hub_y, foot, tips=False, root=2.6, small=False):
    """A turbine from the front: the tube tapering up from ``foot`` pixels to two, the hub, three blades ``reach``
    pixels long.  The small machine stands on a steel mast with its tail vane behind it."""
    s = Sprite()
    hx, hy = 8.0, hub_y
    for y in range(int(hy) + 1, W):
        if small:
            s.put(7, y, STEEL, 3)
            s.put(8, y, STEEL, 1)
            continue
        width = int(round(2 + (foot - 2) * (y - hy) / (15 - hy)))
        left = int(round(hx - width / 2.0))
        # a white tube two pixels wide would be all outline: it is lit and shaded like the blades instead
        for x in range(left, left + width):
            s.put(x, y, WHITE, 4 if x == left else 1 if x == left + width - 1 else 2, keep=True)
    if small:
        line(s, 6, int(hy) - 1, 4, int(hy) - 2, STEEL, 2)
        for k, y in enumerate(range(int(hy) - 5, int(hy))):
            s.fill(1 + max(0, 2 - k), y, 3, y, WHITE, 3 if k < 3 else 2)
    s.outline()
    blades(s, (hx, hy), reach, root, tips)
    disc(s, hx - 0.5, hy - 0.5, 1.3, WHITE)
    return s


# Six sizes of one machine: the rotor grows from the SW-10's four pixels to the C130's seven and a half, the tube
# thickens with it, and the two that stand over 150 m carry the red tips aviation rules ask for.
TURBINES = {
    'sw_10': dict(reach=4.2, hub_y=7.0, foot=2, small=True, root=2.2),
    'c52_085': dict(reach=5.0, hub_y=8.0, foot=2),
    'c80_20': dict(reach=6.0, hub_y=7.5, foot=3),
    'c90_30': dict(reach=6.5, hub_y=7.0, foot=3),
    'c112_30': dict(reach=7.0, hub_y=7.0, foot=4, tips=True),
    'wind_turbine': dict(reach=7.6, hub_y=7.0, foot=4, tips=True, root=3.0),
}


def turbine_tower():
    s = Sprite()
    # a section of tower as a placed block: a white tube with the door at its foot and a flange at its head
    for y in range(1, 16):
        half = 3 + (y > 8)
        for x in range(8 - half, 8 + half):
            s.put(x, y, WHITE, column_tone(x, 8 - half, 7 + half))
    for x in range(4, 12):
        s.put(x, 1, IRON, column_tone(x, 4, 11))
    s.outline()
    s.fill(7, 10, 8, 14, STEEL, 2)
    s.put(7, 10, STEEL, 3)
    return s


# ------------------------------------------------------------------ the grid

def transformer(substation):
    """An oil-filled transformer: the tank, its radiators, three bushings; the substation one taller, with a conservator."""
    s = Sprite()
    top = 7 if substation else 8
    box3d(s, 3, top, 12, 14, 2, CAST)
    for x in (1, 2, 13, 14):
        for y in range(top + 1, 14):
            s.put(x, y, CAST, 3 if x in (1, 13) else 1)
    if substation:
        s.fill(4, 1, 11, 3, CAST, 2)
        for x in (6, 9):
            s.put(x, 4, CAST, 1)
    s.outline()
    for x in (5, 8, 11):
        h = 5 if substation else 3
        for y in range(top - h, top):
            s.put(x, y, PORCELAIN, 3 if y % 2 else 1, keep=True)
        s.put(x, top - h - 1, IRON, 3, keep=True)
    return s


def disconnector():
    s = Sprite()
    # three post insulators on a steel base, the blade of the middle pole open
    s.fill(0, 12, 15, 13, STEEL, 2)
    for x in (2, 7, 12):
        for y in range(5, 12):
            s.put(x, y, PORCELAIN, 3 if y % 2 else 1)
            s.put(x + 1, y, PORCELAIN, 2 if y % 2 else 0)
    s.outline()
    line(s, 2, 4, 6, 2, IRON, 3, keep=True)
    line(s, 7, 4, 10, 1, IRON, 3, keep=True)
    line(s, 12, 4, 15, 2, IRON, 3, keep=True)
    return s


def breaker():
    s = Sprite()
    # a dead-tank breaker: three tall porcelain columns on a mechanism cabinet
    box3d(s, 1, 10, 12, 15, 2, PANEL)
    for x in (2, 6, 10):
        for y in range(1, 10):
            s.put(x, y, PORCELAIN, 3 if y % 2 else 1)
            s.put(x + 1, y, PORCELAIN, 2 if y % 2 else 0)
    s.outline()
    for x in (2, 6, 10):
        s.put(x, 0, IRON, 3, keep=True)
        s.put(x + 1, 0, IRON, 2, keep=True)
    s.put(3, 12, RED, 3)
    s.put(5, 12, LED, 3)
    return s


def lattice(arms, strings, foot):
    """A lattice pylon: two legs from a wide foot to the earth-wire peak, one cross of bracing a panel, and its arms -
    each a beam and the tie under it.  The strings hang under the arms ('I', suspension) or lie along them ('-',
    tension); ``foot`` is the half width at the ground."""
    s = Sprite()
    top = 2
    half = lambda y: 0.6 + (y - top) * (foot - 0.6) / (15 - top)            # noqa: E731
    # every member is a pixel or two wide, so none is outlined: a leg is a lit pixel and a dark one, an arm a lit row
    # over a dark one, which reads on the grey slot and on the black hotbar alike; the bracing is a single mid tone
    panels = [y for y, _ in arms] + [15]
    for y0, y1 in zip(panels, panels[1:]):
        line(s, int(round(7.5 - half(y0))), y0, int(round(7.5 + half(y1))), y1, STEEL, 2)
        line(s, int(round(7.5 + half(y0))), y0, int(round(7.5 - half(y1))), y1, STEEL, 2)
    for y, reach in arms:
        line(s, int(round(7.5 - reach)), y + 1, int(round(7.5 - half(y + 2))), y + 2, STEEL, 2)
        line(s, int(round(7.5 + reach)), y + 1, int(round(7.5 + half(y + 2))), y + 2, STEEL, 2)
        line(s, int(round(7.5 - reach)), y, int(round(7.5 + reach)), y, IRON, 3)
        line(s, int(round(7.5 - reach)) + 1, y + 1, int(round(7.5 + reach)) - 1, y + 1, STEEL, 0)
    for y in range(top, W):
        left, right = int(math.floor(7.5 - half(y) + 0.5)), int(math.floor(7.5 + half(y) + 0.5))
        s.put(left, y, IRON, 3)
        s.put(left + 1, y, STEEL, 0)
        s.put(right, y, STEEL, 0)
        s.put(right - 1, y, IRON, 2)
    s.fill(7, 0, 8, top, STEEL, 0)
    s.put(7, 0, IRON, 3)
    s.put(7, 1, IRON, 3)
    for y, reach in arms:
        for side in (-1, 1):
            x = int(round(7.5 + side * reach))
            if strings == 'I':
                s.put(x, y + 1, GLASS, 3, keep=True)
                s.put(x, y + 2, GLASS, 1, keep=True)
            else:
                s.put(x + side, y, GLASS, 3, keep=True)
                s.put(x + 2 * side, y, GLASS, 1, keep=True)
    return s


def lattice_suspension():
    return lattice([(4, 4.0), (8, 5.5)], 'I', 3.5)


def lattice_tension():
    return lattice([(4, 3.5), (8, 4.5)], '-', 5.5)


def lattice_terminal():
    return lattice([(3, 3.5), (6, 4.5), (9, 4.5)], '-', 6.5)


def conductor(ramp, thick, core=None, twist=0):
    """A reel of conductor end on: a coil, and the cut end lying out of it."""
    s = Sprite()
    coil(s, 7.0, 7.0, 6.3, thick, ramp, twist=twist)
    s.fill(11, 12, 14, 14, ramp, 2)
    s.outline()
    if core:
        s.put(13, 13, core, 2)
    return s


def abc_conductor():
    s = conductor(DARK, 3, core=IRON, twist=3)
    return s


def mv_conductor():
    return conductor(ALU, 2, core=STEEL, twist=2)


def hv_conductor():
    return conductor(ALU, 4, core=STEEL, twist=2)


def dc_string_cable():
    s = conductor(DARK, 2, twist=0)
    # an MC4 on the end of the lead
    s.fill(12, 11, 15, 13, DARK, 3)
    s.put(15, 12, IRON, 3)
    s.put(13, 11, RED, 3)
    return s


def dc_trunk_cable():
    s = conductor(DARK, 4, twist=0)
    # a crimped copper lug on the end
    s.fill(12, 12, 15, 14, COPPER, 3)
    s.put(14, 13, COPPER, 0)
    return s


def power_wrench():
    s = Sprite()
    # a spanner held like a tool: the red grip bottom left, the open jaw top right
    diagonal(s, 2, 13, 8, 3, IRON, tones=[3, 2, 1])
    diagonal(s, 1, 15, 4, 3, RED, tones=[3, 2, 1])
    disc(s, 11.5, 4.0, 3.4, IRON)
    for y in range(W):
        for x in range(W):
            u, v = (x - 11.5 + y - 4.0) / 1.414, (x - 11.5 - (y - 4.0)) / 1.414
            if v > 0.2 and abs(u) < 1.2:
                s.erase(x, y)
    s.outline()
    s.retone(10, 2, 4)
    return s


# ------------------------------------------------------------------ the table

ITEM_TEXTURES = {
    # raw stock and the older parts
    'silicon_ingot': silicon_ingot,
    'silicon_wafer': silicon_wafer,
    'steel_plate': steel_plate,
    'steel_section': steel_section,
    'busbar': busbar,
    'copper_coil': copper_coil,
    'resin': resin,
    'tempered_glass': tempered_glass,
    'metal_casing': metal_casing,
    'circuit_board': circuit_board,
    'control_board': control_board,
    'cpu': cpu,
    'screen': screen,
    'insulator': insulator,
    'motor_core': motor_core,
    'magnetic_core': magnetic_core,
    # components
    'bearing': bearing,
    'gear_set': gear_set,
    'bypass_diode': bypass_diode,
    'mc4_connector': mc4_connector,
    'junction_box': junction_box,
    'gpv_fuse': gpv_fuse,
    'load_break_switch': load_break_switch,
    'capacitor_bank': capacitor_bank,
    'power_module': power_module,
    'sensor_head': sensor_head,
    # assemblies
    'pv_laminate': pv_laminate,
    'solar_cell': solar_cell,
    'enclosure': enclosure,
    'dc_section': dc_section,
    'inverter_bridge': inverter_bridge,
    'mounting_rack': mounting_rack,
    'torque_tube': torque_tube,
    'slew_drive': slew_drive,
    'generator_set': generator_set,
    'gearbox': gearbox,
    'turbine_blade': lambda: blade(False),
    'long_blade': lambda: blade(True),
    'tower_section': tower_section,
    # machines
    'pv_flat': pv_flat,
    'pv_tilt': pv_tilt,
    'pv_track': pv_track,
    'pv_dual': pv_dual,
    'pv_inverter': pv_inverter,
    'pv_combiner': pv_combiner,
    'plant_controller': plant_controller,
    'met_station': met_station,
    'turbine_tower': turbine_tower,
    'power_box': power_box,
    'utility_pole': utility_pole,
    'cab': cab,
    'tx_machine': lambda: transformer(False),
    'tx_substation': lambda: transformer(True),
    'mv_disconnector': disconnector,
    'mv_breaker': breaker,
    'lattice_suspension': lattice_suspension,
    'lattice_tension': lattice_tension,
    'lattice_terminal': lattice_terminal,
    # what a span is strung with, and the tool
    'abc_conductor': abc_conductor,
    'mv_conductor': mv_conductor,
    'hv_conductor': hv_conductor,
    'dc_string_cable': dc_string_cable,
    'dc_trunk_cable': dc_trunk_cable,
    'power_wrench': power_wrench,
}
ITEM_TEXTURES.update({name: (lambda spec=spec: turbine(**spec)) for name, spec in TURBINES.items()})


def main():
    for name, draw in ITEM_TEXTURES.items():
        draw().canvas().write(os.path.join(OUT, name + '.png'))
    print('%d item sprites -> %s' % (len(ITEM_TEXTURES), OUT))


if __name__ == '__main__':
    main()
