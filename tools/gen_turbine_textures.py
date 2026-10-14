#!/usr/bin/env python3
"""The wind turbine's textures: painted tower steel, the blade's gelcoat, spinner, plates, louvres.

    python3 tools/gen_turbine_textures.py            # every turbine texture
    python3 tools/gen_turbine_textures.py turbine_blade

The models name them (gen_turbine_models.MATERIALS); the insulator's two and the plinth's concrete are the
mod's own and are written by gen_block_textures, so nothing here claims them.

Resolution follows how close a player gets to the face - CLAUDE.md section 2.  The tower and blade are 1024: a
player looks up the tower from a metre and the blade is the biggest surface in the mod.  Everything is built
as material, then weathering; the paint is RAL 7035 light grey, which is what a turbine is painted.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import (Canvas, Field, bevel, galvanised, grime, groove, hash01, louvre, mix, polygon,   # noqa: E402
                    powder, rubber, shade, streak, stretched, warning_triangle)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures', 'block')

PAINT = (198, 202, 205, 255)         # RAL 7035 light grey, the colour of the whole machine
GELCOAT = (214, 217, 218, 255)       # the blade's: whiter, a glass-fibre moulding rather than sheet steel
STEEL = (118, 122, 126, 255)         # bare structural steel, a flange or a bedplate
RED = (192, 52, 40, 255)             # RAL 3020 traffic red: the blade tips, the beacon, the danger sign
INK = (44, 47, 52, 255)


def seam_h(c, y, x0=0, x1=None, base=PAINT, width=3.0):
    """A horizontal weld or panel seam: a bead lit from above-left with its shadow under it."""
    x1 = c.w if x1 is None else x1
    c.aa_rect(x0, y - width, x1, y, shade(base, 18), alpha=0.55)
    c.aa_rect(x0, y, x1, y + width * 0.6, shade(base, -46), alpha=0.50)
    c.aa_rect(x0, y + width * 0.6, x1, y + width * 2.2, (0, 0, 0, 255), alpha=0.10)


def seam_v(c, x, y0, y1, base=PAINT, width=3.0):
    c.aa_rect(x - width, y0, x, y1, shade(base, 14), alpha=0.5)
    c.aa_rect(x, y0, x + width * 0.6, y1, shade(base, -40), alpha=0.45)


def stain(c, salt, colour, amount, cell, threshold=0.56, soft=0.16, where=None):
    """A soft-edged patchy stain over whatever is drawn: rust, oil, dirty water, never a rectangle.

    ``where`` is an optional mask - a function of (x, y) giving 0..1 - for a stain that belongs to a feature.
    """
    field = Field(c.w, cell=cell, octaves=4, salt=salt)

    def pixel(x, y, base):
        if not base[3]:
            return base
        a = min(1.0, max(0.0, (field.at(x, y) - threshold) / soft)) * amount
        if where is not None:
            a *= where(x, y)
        return mix(base, colour, a) if a > 0.0 else base

    c.over(pixel)


def spanwise(c, salt, amount, cell_across=14.0):
    """Long vertical grain over whatever is drawn: rolling marks, rain tracks, the mould's ripple."""
    field = stretched(c.w, along=c.w * 1.4, across=cell_across, octaves=3, salt=salt)
    c.over(lambda x, y, base: shade(base, field.signed(y, x) * amount) if base[3] else base)


# ------------------------------------------------------------------ the tower

def turbine_tower():
    """A rolled and welded steel tower can, painted RAL 7035: a tile is four blocks of height.

    Wraps in both directions: it goes round the shaft once and up it three and a quarter times
    (gen_turbine_models.TOWER_TILE), with a weld every two blocks - which is how a real tower is made, a can to
    a weld.  Four blocks and not two so the rust on a weld does not repeat on every one.
    """
    size = 1024
    c = Canvas(size, size)
    powder(c, (203, 206, 208, 255), salt=601, peel=4)
    spanwise(c, 603, 5.0, cell_across=size / 60.0)

    base = (203, 206, 208, 255)
    # two circumferential welds a tile, and a longitudinal one in each can, alternating sides
    welds = (size * 0.14, size * 0.64)
    for y in welds:
        seam_h(c, y, base=base, width=4.2)
    seam_v(c, size * 0.25, welds[0], welds[1], base=base, width=4.0)
    seam_v(c, size * 0.75, welds[1], size, base=base, width=4.0)
    seam_v(c, size * 0.75, 0, welds[0], base=base, width=4.0)

    # rust blooms along a weld, and general weathering over the whole can
    def along_weld(x, y):
        near = min(abs(y - w) for w in welds)
        return max(0.0, 1.0 - near / (size * 0.04))

    stain(c, 607, (150, 92, 52, 255), 0.55, size / 9.0, where=along_weld)
    stain(c, 609, (120, 116, 106, 255), 0.22, size / 3.0, threshold=0.5, soft=0.3)
    # rust weeps out of a weld and down the paint
    for k in range(12):
        x = size * (hash01(k, 1, 611) * 0.96 + 0.02)
        y = welds[k % 2] + 4
        streak(c, x, y, y + size * (0.05 + hash01(k, 2, 613) * 0.09), size * 0.006,
               colour=(122, 80, 46, 255), alpha=0.26, salt=615 + k)
    # and the long dark runs of dirty rain from the top of a can
    for k in range(14):
        x = size * hash01(k, 3, 617)
        y = size * (0.20 if k % 2 else 0.70)
        streak(c, x, y, y + size * 0.28, size * 0.010, colour=(112, 108, 100, 255), alpha=0.10, salt=621 + k)
    grime(c, salt=631, amount=0.16, colour=(84, 82, 76, 255))
    return c


# ------------------------------------------------------------------ steel

def turbine_flange():
    """Bare structural steel, painted dark: a flange, a bedplate, a door frame, a hinge."""
    size = 512
    c = Canvas(size, size)
    galvanised(c, STEEL, salt=641, spangle=False)
    spanwise(c, 643, 5.0, cell_across=size / 70.0)
    stain(c, 645, (112, 66, 36, 255), 0.46, size / 4.0, threshold=0.58, soft=0.14)
    stain(c, 647, (38, 36, 34, 255), 0.40, size / 3.0, threshold=0.52, soft=0.20)
    for k in range(10):
        x = size * hash01(k, 3, 653)
        streak(c, x, size * hash01(k, 4, 655) * 0.5, size, size * 0.012, colour=(108, 70, 44, 255),
               alpha=0.18, salt=657 + k)
    grime(c, salt=661, amount=0.26, colour=(48, 46, 44, 255))
    return c


def turbine_nacelle():
    """The nacelle's skin: RAL 7035 over panels, bolted along their joints, dirtied below every seam.

    A tile is eight tenths of a block.  Nothing on it is round except the rivets, which are small enough
    that the face they land on being a thin strip does not matter: the joints carry them in a line.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, PAINT, salt=671, peel=5)
    spanwise(c, 673, 4.0, cell_across=size / 40.0)

    rows, cols = (size * 0.12, size * 0.62), (size * 0.30, size * 0.80)
    for y in rows:
        seam_h(c, y, width=2.4)
    for x in cols:
        seam_v(c, x, 0, size, width=2.4)
    # the stiffener rivets: a row down each side of every joint
    for y in rows:
        for k in range(48):
            x = size * (k + 0.5) / 48
            c.aa_rect(x - 1, y - 9, x + 1.2, y - 7, shade(PAINT, -34), alpha=0.7)
            c.aa_rect(x - 1, y + 9, x + 1.2, y + 11, shade(PAINT, -34), alpha=0.7)
    for x in cols:
        for k in range(48):
            y = size * (k + 0.5) / 48
            c.aa_rect(x - 10, y - 1, x - 8, y + 1.2, shade(PAINT, -34), alpha=0.7)
            c.aa_rect(x + 10, y - 1, x + 12, y + 1.2, shade(PAINT, -34), alpha=0.7)

    for k in range(16):
        x = size * hash01(k, 5, 677)
        y = rows[k % 2] + 6
        streak(c, x, y, y + size * 0.20, size * 0.012, colour=(108, 104, 96, 255), alpha=0.14, salt=679 + k)
    # oil weeping from the seams and a patch of touched-up paint, which is a slightly different grey
    stain(c, 695, (58, 54, 48, 255), 0.16, size / 5.0, threshold=0.62, soft=0.18)
    stain(c, 697, (136, 132, 124, 255), 0.18, size / 2.5, threshold=0.50, soft=0.30)
    for k in range(3):
        x, y = size * hash01(k, 9, 689), size * hash01(k, 10, 691)
        c.aa_rect(x, y, x + size * 0.14, y + size * 0.09, (206, 209, 210, 255), alpha=0.35)
    grime(c, salt=693, amount=0.20, colour=(78, 76, 70, 255))
    return c


def turbine_louvre():
    """Louvre blades over a dark mesh: the cooler and the intake grilles, a pattern and not a picture."""
    size = 512
    c = Canvas(size, size)
    powder(c, shade(PAINT, -30), salt=701, peel=4)
    louvre(c, 0, 0, size, size, 24, shade(PAINT, -22))
    # the frame has to be at the edge of the face, not the tile: so the pattern carries no border of its own
    spanwise(c, 703, 3.0, cell_across=size / 50.0)
    for k in range(8):
        x = size * hash01(k, 1, 705)
        streak(c, x, 0, size * 0.6, size * 0.012, colour=(92, 88, 80, 255), alpha=0.16, salt=707 + k)
    grime(c, salt=709, amount=0.22, colour=(46, 44, 40, 255))
    return c


# ------------------------------------------------------------------ the rotor

def turbine_blade():
    """Gelcoat over glass fibre, the tip in red, the leading edge under an erosion shield.

    u goes round a section: 0 and 1 are the trailing edge, 0.5 the leading edge, so the shield is a column
    down the middle.  v is along the blade, the tip at the top.  A hundred and sixty rows to a block along it
    against a thousand across, so the speckle is drawn eight times wider than tall to come out round.
    """
    size = 1024
    c = Canvas(size, size)
    powder(c, GELCOAT, salt=721, peel=3)
    spanwise(c, 723, 4.0, cell_across=size / 90.0)
    # the pressure side is the one the weather hits: a little yellower
    c.over(lambda x, y, base: mix(base, (205, 200, 184, 255), 0.18) if x > size * 0.5 else base)

    # the leading-edge shield: a strip of polyurethane tape, greyer, with a hard edge each side
    left, right = size * 0.462, size * 0.538
    c.aa_rect(left, 0, right, size, (176, 180, 184, 255), alpha=0.62)
    c.aa_rect(left - 2, 0, left, size, shade(GELCOAT, -38), alpha=0.6)
    c.aa_rect(right, 0, right + 2, size, shade(GELCOAT, -38), alpha=0.6)
    # rain erosion and insects: more of both towards the tip, which is where the speed is
    for k in range(1300):
        y = size * hash01(k, 1, 727) ** 1.9
        x = size * 0.5 + (hash01(k, 2, 729) - 0.5) * size * (0.07 + 0.12 * hash01(k, 3, 731))
        dark = 60 + hash01(k, 4, 733) * 50
        c.aa_disc(x, y, 1.0 + hash01(k, 5, 735) * 2.6, (dark, dark - 8, dark - 14, 255), alpha=0.55, squash=0.16)

    # dirt collects at the trailing edge, where the glass is thinnest and the air slowest
    edge = Field(size, cell=size / 6.0, octaves=3, salt=737)

    def dirty(x, y, base):
        d = min(x, size - 1 - x) / size
        near = max(0.0, 0.14 - d) / 0.14
        return mix(base, (112, 108, 98, 255), near * (0.14 + edge.at(x, y) * 0.28))

    c.over(dirty)

    # the red tip, and the grey primer band where the root is bolted: an aviation marking and a flange
    tip = size * 0.075
    c.aa_rect(0, 0, size, tip, RED)
    c.aa_rect(0, tip, size, tip + size * 0.015, mix(GELCOAT, RED, 0.5))
    c.aa_rect(0, size * 0.985, size, size, (150, 154, 158, 255))
    spanwise(c, 739, 3.0, cell_across=size / 70.0)
    grime(c, salt=741, amount=0.12, colour=(80, 76, 70, 255))
    return c


def turbine_hub():
    """The spinner: three glass-fibre shells, so three meridian seams; a ring where the cone meets the barrel."""
    size = 512
    c = Canvas(size, size)
    powder(c, (224, 226, 228, 255), salt=751, peel=3)
    spanwise(c, 753, 3.0, cell_across=size / 50.0)
    for k in range(3):
        x = size * k / 3.0
        seam_v(c, x if x else 2, 0, size, base=(224, 226, 228, 255), width=2.4)
    seam_h(c, size * 0.50, base=(224, 226, 228, 255), width=2.2)
    # splattered insects on the nose, which meets the air first
    for k in range(70):
        y = size * hash01(k, 1, 757) ** 2.4 * 0.5
        x = size * hash01(k, 2, 759)
        dark = 70 + hash01(k, 3, 761) * 40
        c.aa_disc(x, y, 0.8 + hash01(k, 4, 763) * 1.2, (dark + 40, dark + 32, dark + 24, 255), alpha=0.35)
    for k in range(10):
        x = size * hash01(k, 5, 765)
        streak(c, x, size * 0.5, size * 0.5 + size * 0.4, size * 0.01, colour=(118, 112, 104, 255), alpha=0.12,
               salt=767 + k)
    grime(c, salt=771, amount=0.10, colour=(90, 86, 80, 255))
    return c


def turbine_seal():
    """The rubber boot round a blade root: black, ribbed round its length."""
    size = 128
    c = Canvas(size, size)
    rubber(c, (46, 48, 52, 255), salt=781, sheen=10)
    for k in range(4):
        y = size * (k + 0.5) / 4
        c.aa_rect(0, y - 2, size, y, shade((46, 48, 52, 255), 14), alpha=0.8)
        c.aa_rect(0, y, size, y + 2, (0, 0, 0, 255), alpha=0.35)
    grime(c, salt=783, amount=0.14, colour=(80, 76, 70, 255))
    return c


# ------------------------------------------------------------------ the details

def turbine_door():
    """The tower door's leaf: a pressed panel with a louvre low down, for the air a tower breathes.

    A leaf is 0.13 by 0.30, so the tile is drawn at that shape: a border 4 pixels wide is 4 high and 1.7
    across, or it comes out thicker on the sides than top and bottom.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, PAINT, salt=791, peel=4)
    spanwise(c, 793, 3.0, cell_across=size / 60.0)
    ratio = 0.130 / 0.300
    pad_y = size * 0.05
    pad_x = pad_y * ratio
    bevel(c, pad_x, pad_y, size - pad_x, size - pad_y, size * 0.035, lift=22, drop=26)
    groove(c, pad_x * 2.6, size * 0.60, size - pad_x * 2.6, 0, size * 0.006, dark=56, light=26)
    louvre(c, size * 0.18, size * 0.66, size * 0.82, size * 0.90, 8, shade(PAINT, -20))
    # a kick plate along the foot
    c.aa_rect(pad_x * 1.2, size * 0.93, size - pad_x * 1.2, size * 0.975, shade(PAINT, -44), alpha=0.9)
    for k in range(5):
        x = size * (0.12 + 0.76 * hash01(k, 1, 795))
        streak(c, x, size * 0.2, size * 0.62, size * 0.02, colour=(108, 104, 96, 255), alpha=0.14, salt=797 + k)
    grime(c, salt=801, amount=0.20, colour=(70, 68, 62, 255))
    return c


def turbine_plate():
    """Three decals on one tile, each in a quarter of it: the rating plate, the danger sign, the maker's mark.

    The quarters are square so that a square plate shows its decal undistorted: the sign's triangle is round
    enough for check_model_textures to hold the face to it.
    """
    size = 512
    c = Canvas(size, size, (0, 0, 0, 255))
    half = size // 2

    # top left: the rating plate, etched steel with rows of text too small to read
    plate = Canvas(half, half)
    galvanised(plate, (176, 180, 184, 255), salt=811, spangle=False)
    bevel(plate, 2, 2, half - 2, half - 2, 5, lift=34, drop=36)
    for k, width in enumerate((0.78, 0.56, 0.70, 0.46, 0.64, 0.52, 0.72, 0.40)):
        y = half * (0.13 + 0.095 * k)
        plate.aa_rect(half * 0.10, y, half * (0.10 + 0.80 * width), y + half * 0.032, INK)
    plate.aa_rect(half * 0.10, half * 0.04, half * 0.90, half * 0.10, INK)
    c.each(lambda x, y: plate.get(x, y) if x < half and y < half else None)

    # top right: a danger sign, yellow on black
    sign = Canvas(half, half, (236, 236, 232, 255))
    sign.aa_rect(0, 0, half, half, (230, 230, 226, 255))
    warning_triangle(sign, half * 0.5, half * 0.44, half * 0.62)
    sign.aa_rect(half * 0.12, half * 0.80, half * 0.88, half * 0.90, INK)
    sign.aa_rect(0, 0, half, 3, INK)
    sign.aa_rect(0, half - 3, half, half, INK)
    sign.aa_rect(0, 0, 3, half, INK)
    sign.aa_rect(half - 3, 0, half, half, INK)
    c.each(lambda x, y: sign.get(x - half, y) if x >= half and y < half else None)

    # bottom left: the maker's mark, an isometric cube in three tones on white
    logo = Canvas(half, half, (240, 241, 242, 255))
    cx, cy, r = half * 0.5, half * 0.5, half * 0.34
    top = [(cx, cy - r), (cx + r * 0.866, cy - r * 0.5), (cx, cy), (cx - r * 0.866, cy - r * 0.5)]
    left = [(cx - r * 0.866, cy - r * 0.5), (cx, cy), (cx, cy + r), (cx - r * 0.866, cy + r * 0.5)]
    right = [(cx + r * 0.866, cy - r * 0.5), (cx + r * 0.866, cy + r * 0.5), (cx, cy + r), (cx, cy)]
    polygon(logo, top, (92, 150, 200, 255))
    polygon(logo, left, (44, 92, 146, 255))
    polygon(logo, right, (26, 58, 104, 255))
    c.each(lambda x, y: logo.get(x, y - half) if x < half and y >= half else None)

    # bottom right: a plain steel plate, for a warning that is only ever drawn at this size
    plain = Canvas(half, half)
    galvanised(plain, (172, 176, 180, 255), salt=821, spangle=False)
    bevel(plain, 2, 2, half - 2, half - 2, 5, lift=30, drop=32)
    c.each(lambda x, y: plain.get(x - half, y - half) if x >= half and y >= half else None)
    return c


def turbine_sensor():
    """The anemometer's and vane's moulding: dark grey plastic with a matt skin."""
    size = 128
    c = Canvas(size, size)
    powder(c, (62, 64, 68, 255), salt=831, peel=6)
    spanwise(c, 833, 5.0, cell_across=size / 20.0)
    for k in range(3):
        y = size * (0.2 + 0.3 * k)
        c.aa_rect(0, y, size, y + 2, shade((62, 64, 68, 255), 22), alpha=0.6)
    return c


def turbine_beacon():
    """An aviation light's lens: red, deeper at the base, with the ribs a Fresnel dome is moulded with."""
    size = 128
    c = Canvas(size, size)
    for y in range(size):
        t = y / (size - 1.0)
        c.aa_rect(0, y, size, y + 1, mix((236, 78, 60, 255), (140, 20, 20, 255), t))
    for k in range(10):
        x = size * (k + 0.5) / 10
        c.aa_rect(x - 1, 0, x + 1, size, (255, 160, 140, 255), alpha=0.22)
    return c


TEXTURES = {
    'turbine_tower': turbine_tower,
    'turbine_flange': turbine_flange,
    'turbine_nacelle': turbine_nacelle,
    'turbine_louvre': turbine_louvre,
    'turbine_blade': turbine_blade,
    'turbine_hub': turbine_hub,
    'turbine_seal': turbine_seal,
    'turbine_door': turbine_door,
    'turbine_plate': turbine_plate,
    'turbine_sensor': turbine_sensor,
    'turbine_beacon': turbine_beacon,
}


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith('-')]
    for name, builder in TEXTURES.items():
        if wanted and name not in wanted:
            continue
        path = os.path.join(OUT, name + '.png')
        canvas = builder()
        canvas.write(path)
        print('block/%-26s %4d x %4d  %5d KB' % (name + '.png', canvas.w, canvas.h,
                                                 os.path.getsize(path) // 1024))


if __name__ == '__main__':
    main()
