#!/usr/bin/env python3
"""Inventory sprites for the components and the grid machines: the sixteen-pixel drawings that
gen_pv_textures.py does not write.

    python3 tools/gen_component_sprites.py

Sixteen pixels, one dark outline, three tones lit from the top left, and a silhouette that says what the
thing is at hotbar size - the same rules as every other sprite in the mod.  Each drawing is a function so
that a palette can be changed in one place.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas                                                       # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures', 'item')

INK = (26, 26, 32, 255)
STEEL = ((52, 56, 64, 255), (126, 132, 142, 255), (190, 196, 206, 255), (236, 240, 244, 255))
COPPER = ((96, 46, 22, 255), (176, 94, 44, 255), (226, 146, 76, 255))
GOLD = ((150, 110, 24, 255), (222, 176, 52, 255), (252, 226, 120, 255))
WOOD = ((62, 40, 22, 255), (110, 74, 42, 255), (156, 110, 64, 255))
PORCELAIN = ((88, 52, 32, 255), (148, 92, 58, 255), (204, 140, 96, 255))


def line(c, x0, y0, x1, y1, colour):
    """A one-pixel line, which is all a sixteen pixel sprite ever needs."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        c.set(x0, y0, colour)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def box(c, x0, y0, x1, y1, tones):
    """An outlined, bevelled block: tones is (dark, mid, light); x1 and y1 are inclusive."""
    dark, mid, light = tones[:3]
    c.rect(x0, y0, x1 + 1, y1 + 1, INK)
    c.rect(x0 + 1, y0 + 1, x1, y1, mid)
    for x in range(x0 + 1, x1):
        c.set(x, y0 + 1, light)
        c.set(x, y1 - 1, dark)
    for y in range(y0 + 1, y1):
        c.set(x0 + 1, y, light)
        c.set(x1 - 1, y, dark)


def cpu():
    """A package with its pins out on every side and a die you can see through the lid."""
    c = Canvas(16, 16)
    pins = (4, 6, 8, 10, 12)
    for p in pins:
        for edge in (2, 14):
            c.set(p, edge, GOLD[1])
            c.set(edge, p, GOLD[1])
        c.set(p, 3, GOLD[2])
        c.set(3, p, GOLD[2])
    box(c, 3, 3, 13, 13, ((30, 32, 38, 255), (54, 58, 68, 255), (98, 104, 118, 255)))
    c.rect(5, 5, 12, 12, INK)
    c.rect(6, 6, 11, 11, (92, 104, 124, 255))
    c.rect(6, 6, 10, 7, (132, 148, 174, 255))
    c.rect(6, 6, 7, 10, (132, 148, 174, 255))
    c.rect(9, 9, 11, 11, (62, 72, 90, 255))
    c.set(7, 7, (214, 224, 240, 255))
    c.set(4, 4, (232, 232, 238, 255))
    return c


def circuit_board():
    """A green board with traces, one chip, a resistor and the gold edge fingers."""
    c = Canvas(16, 16)
    c.rect(1, 2, 15, 14, INK)
    c.rect(2, 3, 14, 13, (34, 118, 62, 255))
    for x in range(2, 14):
        c.set(x, 3, (76, 176, 100, 255))
    for y in range(3, 13):
        c.set(2, y, (76, 176, 100, 255))
        c.set(13, y, (20, 78, 40, 255))
    for x in range(2, 14):
        c.set(x, 12, (20, 78, 40, 255))
    trace = (110, 206, 128, 255)
    line(c, 3, 11, 6, 11, trace)
    line(c, 6, 11, 6, 9, trace)
    line(c, 9, 5, 12, 5, trace)
    line(c, 12, 5, 12, 8, trace)
    line(c, 9, 9, 9, 11, trace)
    c.rect(4, 5, 9, 9, INK)
    c.rect(5, 6, 8, 8, (54, 58, 68, 255))
    c.set(5, 6, (110, 116, 130, 255))
    for x in (4, 5, 6, 7, 8):
        c.set(x, 4, (190, 196, 206, 255))
        c.set(x, 9, (190, 196, 206, 255))
    c.rect(10, 8, 12, 10, (220, 150, 70, 255))
    c.set(11, 8, (252, 214, 140, 255))
    c.set(11, 9, (70, 40, 20, 255))
    c.rect(10, 11, 12, 12, (46, 98, 168, 255))
    c.set(10, 11, (110, 160, 226, 255))
    for x in (3, 5, 7, 9, 11):
        c.set(x, 13, GOLD[1])
    return c


def insulator():
    """A pin insulator: a steel pin on top, three glazed skirts getting wider, and the threaded base."""
    c = Canvas(16, 16)
    c.rect(7, 1, 9, 4, INK)
    c.rect(8, 1, 9, 4, STEEL[1])
    c.set(8, 1, STEEL[3])
    c.set(8, 2, STEEL[2])
    sheds = ((4, 5, 3), (6, 7, 4), (9, 11, 6))
    for y0, y1, half in sheds:
        c.rect(8 - half - 1, y0, 8 + half + 1, y1 + 1, INK)
        c.rect(8 - half, y0 + 1, 8 + half, y1, PORCELAIN[1])
        for x in range(8 - half, 8 + half):
            c.set(x, y0 + 1, PORCELAIN[2])
        for x in range(8 - half, 8 + half + 1):
            c.set(x, y1, PORCELAIN[0])
        c.set(8 - half, y0 + 1, PORCELAIN[2])
    c.rect(6, 8, 11, 9, INK)
    c.rect(7, 8, 10, 9, PORCELAIN[1])
    c.set(7, 8, PORCELAIN[2])
    c.rect(5, 12, 12, 15, INK)
    c.rect(6, 12, 11, 14, STEEL[1])
    for x in range(6, 11):
        c.set(x, 12, STEEL[2])
        c.set(x, 13, STEEL[0] if x % 2 else STEEL[1])
    c.set(6, 12, STEEL[3])
    return c


def metal_casing():
    """A sheet-steel case: folded edge, four rivets and a row of louvres."""
    c = Canvas(16, 16)
    box(c, 1, 2, 14, 13, (STEEL[0], STEEL[1], STEEL[2]))
    c.rect(3, 4, 13, 12, (STEEL[0]))
    c.rect(4, 5, 12, 11, STEEL[1])
    for x in range(4, 12):
        c.set(x, 5, STEEL[0])
    for y in range(5, 11):
        c.set(4, y, STEEL[0])
    for x in range(5, 12):
        c.set(x, 10, STEEL[2])
    for y in range(5, 10):
        c.set(11, y, STEEL[2])
    for y in (6, 8):
        for x in range(6, 10):
            c.set(x, y, INK)
            c.set(x, y + 1, STEEL[2])
    for x, y in ((2, 3), (13, 3), (2, 12), (13, 12)):
        c.set(x, y, STEEL[3])
    return c


def motor_core():
    """A rotor: laminated iron under copper windings, with the shaft and its key coming out of one end."""
    c = Canvas(16, 16)
    c.rect(11, 6, 16, 10, INK)
    c.rect(11, 7, 16, 9, STEEL[1])
    for x in range(11, 16):
        c.set(x, 7, STEEL[3])
        c.set(x, 9, STEEL[0])
    c.set(13, 7, INK)
    c.rect(2, 3, 12, 13, INK)
    c.rect(3, 4, 11, 12, STEEL[1])
    for y in range(4, 12):
        c.set(3, y, STEEL[2])
        c.set(11, y, STEEL[0])
    for x in range(3, 12):
        c.set(x, 4, STEEL[2])
        c.set(x, 12, STEEL[0])
    for x0 in (4, 8):
        c.rect(x0, 3, x0 + 3, 14, INK)
        c.rect(x0 + 1, 4, x0 + 3, 13, COPPER[1])
        for y in range(4, 13):
            c.set(x0 + 1, y, COPPER[2] if y % 2 else COPPER[1])
            c.set(x0 + 3, y, COPPER[0])
    c.rect(2, 7, 4, 9, INK)
    c.rect(2, 8, 3, 8, STEEL[1])
    return c


def screen():
    """A flat display: black bezel, a lit panel with a trace across it, and the glint."""
    c = Canvas(16, 16)
    box(c, 1, 2, 14, 11, ((20, 22, 28, 255), (46, 50, 58, 255), (92, 98, 110, 255)))
    c.rect(3, 4, 13, 10, INK)
    for y in range(4, 10):
        shade = (30 + y * 7, 80 + y * 9, 138 + y * 6, 255)
        c.rect(3, y, 13, y + 1, shade)
    line(c, 4, 8, 6, 8, (120, 232, 150, 255))
    line(c, 6, 8, 7, 6, (120, 232, 150, 255))
    line(c, 7, 6, 9, 9, (120, 232, 150, 255))
    line(c, 9, 9, 12, 7, (120, 232, 150, 255))
    for i in range(3):
        c.set(4 + i, 5, (176, 214, 244, 255))
    c.set(4, 4, (226, 240, 252, 255))
    c.set(5, 4, (176, 214, 244, 255))
    c.set(4, 5, (176, 214, 244, 255))
    c.set(12, 11, (96, 232, 120, 255))
    c.rect(6, 12, 10, 14, INK)
    c.rect(7, 12, 9, 13, STEEL[1])
    c.rect(4, 14, 12, 15, INK)
    c.rect(5, 14, 11, 14, STEEL[2])
    return c


def power_box():
    """A pad-mounted distribution cabinet: two doors on the seam, the handle, and the bolt warning."""
    c = Canvas(16, 16)
    c.rect(1, 14, 15, 16, INK)
    c.rect(2, 14, 14, 15, (128, 124, 116, 255))
    box(c, 2, 2, 13, 14, ((70, 82, 76, 255), (112, 128, 118, 255), (162, 178, 166, 255)))
    for y in range(3, 14):
        c.set(7, y, INK)
        c.set(8, y, (88, 100, 92, 255))
    c.rect(5, 7, 6, 10, (226, 194, 60, 255))
    c.set(5, 7, (252, 236, 150, 255))
    c.set(5, 8, (252, 236, 150, 255))
    for p in ((10, 4), (11, 5), (10, 6), (11, 6), (10, 7), (11, 7), (10, 8)):
        c.set(p[0], p[1], (232, 196, 52, 255))
    c.set(11, 9, (232, 196, 52, 255))
    c.set(9, 9, INK)
    c.set(9, 10, INK)
    for x, y in ((3, 3), (12, 3)):
        c.set(x, y, STEEL[3])
    line(c, 9, 12, 12, 12, INK)
    return c


def utility_pole():
    """A wooden pole with the crossarm and its two pin insulators, which is the whole identification."""
    c = Canvas(16, 16)
    c.rect(6, 2, 10, 16, INK)
    c.rect(7, 2, 9, 15, WOOD[1])
    for y in range(2, 15):
        c.set(7, y, WOOD[2])
        c.set(9, y, WOOD[0])
        if y % 4 == 1:
            c.set(8, y, WOOD[0])
    c.rect(1, 4, 15, 7, INK)
    c.rect(2, 5, 14, 6, WOOD[1])
    for x in range(2, 14):
        c.set(x, 5, WOOD[2])
    line(c, 5, 8, 7, 6, INK)
    line(c, 11, 8, 9, 6, INK)
    for x in (2, 13):
        c.rect(x, 1, x + 1, 4, INK)
        c.rect(x, 2, x, 3, PORCELAIN[2])
        c.rect(x + 1, 2, x + 1, 3, PORCELAIN[1])
    for x in (4, 11):
        c.rect(x, 2, x + 1, 4, INK)
        c.rect(x, 3, x, 3, PORCELAIN[2])
        c.rect(x + 1, 3, x + 1, 3, PORCELAIN[1])
    c.rect(5, 15, 11, 16, INK)
    c.rect(6, 15, 10, 15, (110, 104, 96, 255))
    return c


def cab():
    """An electric cabin: a small block-built substation with a flat roof, a door, a vent and two insulators."""
    c = Canvas(16, 16)
    for x in (4, 11):
        c.rect(x, 0, x + 2, 3, INK)
        c.rect(x, 1, x, 2, PORCELAIN[2])
        c.rect(x + 1, 1, x + 1, 2, PORCELAIN[1])
    c.rect(1, 3, 15, 16, INK)
    c.rect(2, 4, 14, 5, (82, 86, 94, 255))
    for x in range(2, 14):
        c.set(x, 4, (126, 132, 142, 255))
    c.rect(2, 6, 14, 15, (182, 178, 168, 255))
    for y in range(6, 15):
        c.set(2, y, (214, 210, 200, 255))
        c.set(13, y, (138, 134, 124, 255))
    c.rect(4, 8, 9, 15, INK)
    c.rect(5, 9, 8, 15, (60, 112, 90, 255))
    for y in range(9, 15):
        c.set(5, y, (96, 156, 128, 255))
    c.set(8, 12, (232, 208, 90, 255))
    c.rect(10, 7, 13, 11, INK)
    c.rect(11, 8, 12, 10, (56, 60, 68, 255))
    for y in (8, 10):
        c.set(11, y, (120, 126, 136, 255))
        c.set(12, y, (120, 126, 136, 255))
    c.rect(10, 13, 13, 14, (226, 194, 60, 255))
    c.set(11, 13, INK)
    c.set(12, 14, INK)
    return c


# Read by check_generated_assets like every other *_TEXTURES table, so a second writer of one of these fails it.
ITEM_TEXTURES = {
    'cpu': cpu,
    'circuit_board': circuit_board,
    'insulator': insulator,
    'metal_casing': metal_casing,
    'motor_core': motor_core,
    'screen': screen,
    'power_box': power_box,
    'utility_pole': utility_pole,
    'cab': cab,
}


def main():
    for name, draw in ITEM_TEXTURES.items():
        draw().write(os.path.join(OUT, name + '.png'))
        print('item/%s.png' % name)


if __name__ == '__main__':
    main()
