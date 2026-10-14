#!/usr/bin/env python3
"""The three lattice transmission towers: a 2 x 400 kV Donaumast in its three duties.

    python3 tools/gen_tower_models.py            # the three models, and the check that the Java is current
    python3 tools/gen_tower_models.py --java     # and the cell tables LatticeTowerBlock declares

Drawn in metres off a real tower and scaled once, SCALE blocks to the metre: a Donaumast stands about 54 m to its
earth-wire peak and this one is HEIGHT blocks.  Two crossarm levels - the lower carries two phases a side, the upper
one - quad bundles in porcelain cap-and-pin strings, one earth wire on the peak.  What differs between the duties is
what a real line asks of them: a suspension tower hangs I-strings and only carries weight; a tension tower dead-ends
both ways on double strings, with a jumper loop under the arm, and is wider and cross-braced; a terminal tower takes
the whole pull from one side and is the widest of the three.  docs/model-audit.md gives every figure's source.

Every member runs from node to node: the body's bracing lies on gussets inside the legs' flanges, an arm's lacing on
its chords' flanges, a string hangs from a plate bolted under a transverse at a station.  check_connectivity.py
proves it.  The members are drawn heavier than true, so a lattice still reads as steel from the ground.

PHASES is in wire-index order and cannot be reordered without moving every wire in every world.  Each phase's
conductor clamps are a group of their own, ``clamp_N``: its box is where LatticeTowerBlockEntity lands the bundle.
"""

import functools
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, arc, bolt, box, coarse, cylinder, emit, frame_box,   # noqa: E402
                      revolve, tube)
from gen_conductor_models import CONDUCTORS                                      # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'models')

MATERIALS = {
    'steel': 'tower_steel.png',
    'plate': 'tower_plate.png',
    'porcelain': 'porcelain_brown.png',
    'concrete': 'pole_concrete.png',
    # bare stranded aluminium and its fittings: the tiles the ground-laid conductors use
    'conductor': 'line_alu.png',
    'fitting': 'line_fitting.png',
    'sign': 'pole_plate.png',
}

# ---------------------------------------------------------------- the real tower, in metres

HEIGHT = 11.5                  # blocks, to the earth wire
TOP_M = 54.0                   # metres, the same point on a real Donaumast
SCALE = HEIGHT / TOP_M         # blocks to the metre: one block is 4.7 m of tower

WAIST_M = 34.0                 # the lower crossarm's bottom chord: ground clearance + sag + string
LOWER_TOP_M = 37.2             # where its top chords meet the body
UPPER_M = 44.5                 # the upper crossarm's bottom chord
UPPER_TOP_M = 47.2             # its top chords, and the foot of the earth-wire peak
PEAK_M = 53.4                  # the peak's top frame; the earth wire rides 0.6 m above it
PIER_M = 0.45                  # how far a footing's concrete stands out of the ground

# Where the body is braced: a node just over the concrete, big panels at the foot closing up to the waist, then the
# head and the peak.
LEVELS_M = (0.0, 0.8, 6.5, 12.5, 18.0, 23.0, 27.5, 31.0, WAIST_M, LOWER_TOP_M, 40.8, UPPER_M, UPPER_TOP_M, 50.3,
            PEAK_M)
# Plan bracing - a horizontal cross that keeps the section square - where an arm's bottom chords load the body.
PLAN_M = (WAIST_M, UPPER_M)

# The three phase positions of a circuit, metres from the axis: two on the lower arm, one on the upper.
OUTER_M, INNER_M, UPPER_PHASE_M = 16.0, 8.0, 11.0
# Each arm: bottom chord height, top chord root height, stations along it (metres out, the last one the tip), its
# half depth along the line at the tip, and its own depth there.  A phase hangs at a station.
ARMS = {
    'lower': (WAIST_M, LOWER_TOP_M, (4.4, INNER_M, 12.0, OUTER_M), 0.35, 0.9),
    'upper': (UPPER_M, UPPER_TOP_M, (6.0, UPPER_PHASE_M), 0.35, 0.8),
}

# Where the six phases hang, in wire-index order: the lower arm from the outside in, then the upper.
PHASES = ((-OUTER_M, 'lower'), (-INNER_M, 'lower'), (INNER_M, 'lower'), (OUTER_M, 'lower'),
          (-UPPER_PHASE_M, 'upper'), (UPPER_PHASE_M, 'upper'))

# The body as half widths in metres - at the foot, at the waist, at the peak's foot and at its top - and the members
# drawn on it in blocks: the leg angle's width and thickness, the arm chords', the bracing bars'.  A wider foot is a
# stiffer tower, which is what a tension and a terminal tower are.
DUTIES = {
    'suspension': dict(foot=4.8, waist=1.6, head=1.2, peak=0.30, leg=(0.070, 0.013), chord=(0.046, 0.010),
                       brace=0.024, cross=False),
    'tension': dict(foot=6.2, waist=2.0, head=1.5, peak=0.35, leg=(0.082, 0.015), chord=(0.052, 0.011),
                    brace=0.028, cross=True),
    'terminal': dict(foot=7.2, waist=2.3, head=1.7, peak=0.35, leg=(0.090, 0.016), chord=(0.056, 0.012),
                     brace=0.030, cross=True),
}
GUSSET = 0.007                 # a gusset plate's thickness, blocks

# Cap-and-pin porcelain units to IEC 60305: a U160BS (146 mm pitch, 280 mm across) in a suspension string, a U210B
# (170 mm, 300 mm) in a tension one.  The count is the creepage IEC TS 60815-2 asks of 420 kV on a medium-pollution
# site, 420 kV x 20 mm/kV over the 380 mm a unit gives: 22.  The diameter is drawn half again as wide as true and
# the pitch is not, so a string is as long as a real one and still reads as discs from the ground.
SUSPENSION_UNITS, SUSPENSION_PITCH_M, SUSPENSION_DISC_M = 22, 0.146, 0.280
TENSION_UNITS, TENSION_PITCH_M, TENSION_DISC_M = 23, 0.170, 0.300
DISC_WIDEN = 1.5
ROD = 0.0085                   # the caps and pins, drawn as the one steel rod they look like from the ground
# The two strings of a double tension string are 450 mm apart, widened with the discs.
DOUBLE_GAP = 0.45 * SCALE * DISC_WIDEN

# The quad bundle: ConductorCatalog's own sub-conductor radius and spacing, which is what WireRenderer draws a span
# with, so a span lands in the clamps rather than beside them.
_, SUB_RADIUS, SUBS, BUNDLE, _ = next(c for c in CONDUCTORS if c[0] == 'hv_conductor')
HALF_BUNDLE = BUNDLE / 2.0
CLAMP_RADIUS = SUB_RADIUS + 0.007
SUSPENSION_CLAMP = 0.035       # half the length of a suspension clamp along the line
DEAD_END = 0.13                # a compression dead-end clamp's length
JUMPER_DROP = 0.32             # how far a tension tower's jumper loop sags below its terminal blocks


def m(value):
    return value * SCALE


# ---------------------------------------------------------------- vector arithmetic

def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def unit(a):
    length = math.sqrt(dot(a, a)) or 1.0
    return (a[0] / length, a[1] / length, a[2] / length)


def square(direction, toward):
    """The part of ``toward`` square to ``direction``, as a unit vector."""
    return unit(sub(toward, mul(direction, dot(toward, direction))))


# ---------------------------------------------------------------- members

def angle_member(mesh, faces, p0, p1, out_a, out_b, size):
    """A rolled angle from p0 to p1 with its heel out towards out_a + out_b: the L a lattice is made of.

    p0 and p1 are the member's working line; the flanges stand outside it, so the face a brace is bolted to is a
    half width out.  Returns the frame (along, a, b) the bracing is laid against.
    """
    width, thick = size
    d = unit(sub(p1, p0))
    a = square(d, out_a)
    b = square(d, sub(out_b, mul(a, dot(out_b, a))))
    length = math.dist(p0, p1)
    half = width / 2.0
    frame_box(mesh, faces, p0, (d, a, b), (0.0, half - thick, -half), (length, half, half), uv_scale=1.6)
    frame_box(mesh, faces, p0, (d, a, b), (0.0, -half, half - thick), (length, half - thick, half), uv_scale=1.6)
    return d, a, b


def bar(mesh, faces, p0, p1, size):
    """A bracing member between two points: square, ``size`` across."""
    d = unit(sub(p1, p0))
    a = square(d, (0.0, 1.0, 0.0) if abs(d[1]) < 0.9 else (1.0, 0.0, 0.0))
    b = cross(d, a)
    half = size / 2.0
    frame_box(mesh, faces, p0, (d, a, b), (0.0, -half, -half), (math.dist(p0, p1), half, half), uv_scale=1.6)


# ---------------------------------------------------------------- the body

class Tower:
    """One tower's figures, by duty, and the points everything else is hung off."""

    def __init__(self, duty):
        self.duty = duty
        self.spec = DUTIES[duty]
        self.leg_width, self.leg_thick = self.spec['leg']
        self.brace = self.spec['brace']
        self.levels = [m(y) for y in LEVELS_M]

    def half(self, y):
        """The body's half width at a height, in blocks: battered to the waist, then the head, then the peak."""
        knots = ((0.0, self.spec['foot']), (WAIST_M, self.spec['waist']), (UPPER_TOP_M, self.spec['head']),
                 (PEAK_M, self.spec['peak']))
        y_m = y / SCALE
        for (y0, h0), (y1, h1) in zip(knots, knots[1:]):
            if y_m <= y1 + 1e-9:
                return m(h0 + (h1 - h0) * max(0.0, (y_m - y0) / (y1 - y0)))
        return m(knots[-1][1])

    def node(self, sx, sz, y):
        h = self.half(y)
        return (sx * h, y, sz * h)

    def leg_frame(self, sx, sz, y):
        """The leg's (along, a, b) in the panel above ``y`` (below it at the top): a outward in x, b in z."""
        index = max(i for i, level in enumerate(self.levels[:-1]) if level <= y + 1e-9)
        d = unit(sub(self.node(sx, sz, self.levels[index + 1]), self.node(sx, sz, self.levels[index])))
        a = square(d, (sx, 0.0, 0.0))
        b = square(d, sub((0.0, 0.0, sz), mul(a, sz * a[2])))
        return d, a, b

    def inset(self):
        """How far out of the working line a bar on a gusset lies: inside the flange, the gusset between them."""
        return self.leg_width / 2.0 - self.leg_thick - GUSSET - self.brace / 2.0


# The four faces as (leg, leg, the axis the face is square to): the line runs along z and the arms along x.
BODY_FACES = (((-1, -1), (1, -1), 'z'), ((1, -1), (1, 1), 'x'), ((1, 1), (-1, 1), 'z'), ((-1, 1), (-1, -1), 'x'))


def legs(mesh, tower):
    """The four legs, node to node up the whole height, their foot cast into the concrete."""
    steel = mesh.faces('body', 'steel')
    for sx in (-1, 1):
        for sz in (-1, 1):
            for y0, y1 in zip(tower.levels, tower.levels[1:]):
                angle_member(mesh, steel, tower.node(sx, sz, y0), tower.node(sx, sz, y1), (sx, 0.0, 0.0),
                             (0.0, 0.0, sz), (tower.leg_width, tower.leg_thick))


def face_point(tower, corner, y, axis):
    """A point on a leg's working line moved out to where a bar lies on the gusset in that face."""
    sx, sz = corner
    _, a, b = tower.leg_frame(sx, sz, y)
    return add(tower.node(sx, sz, y), mul(a if axis == 'x' else b, tower.inset()))


def gusset(mesh, tower, corner, y, axis, toward, reach):
    """A gusset on the inside of a leg's flange in one face, reaching ``reach`` along the face towards ``toward``."""
    sx, sz = corner
    d, a, b = tower.leg_frame(sx, sz, y)
    out = a if axis == 'x' else b
    inward = unit(sub(square(d, sub(toward, tower.node(sx, sz, y))), mul(out, 0.0)))
    inward = unit(sub(inward, mul(out, dot(inward, out))))
    face = tower.leg_width / 2.0 - tower.leg_thick
    frame_box(mesh, mesh.faces('body', 'plate'), tower.node(sx, sz, y), (d, inward, out),
              (-0.05, -tower.leg_width / 2.0 + tower.leg_thick, face - GUSSET), (0.05, reach, face), uv_scale=2.0)


def bracing(mesh, tower):
    """Every face of the body braced panel by panel, each bar ending on a gusset at a leg.

    A suspension tower is single-braced - one diagonal a panel, alternating, so a face is a run of triangles - and a
    tension or terminal tower carries a cross in every panel because it is pulled both ways; so does every tower's
    peak.  A horizontal closes each panel, and at the two arm levels a plan cross keeps the section square.
    """
    steel = mesh.faces('body', 'steel')
    levels = tower.levels
    top = len(levels) - 1
    for c0, c1, axis in BODY_FACES:
        for index in range(1, top + 1):
            y = levels[index]
            if 1 < index < top:
                bar(mesh, steel, face_point(tower, c0, y, axis), face_point(tower, c1, y, axis), tower.brace)
            for corner, other in ((c0, c1), (c1, c0)):
                gusset(mesh, tower, corner, y, axis, tower.node(other[0], other[1], y), 0.10 if index < top else 0.04)

        for index in range(1, top):
            y0, y1 = levels[index], levels[index + 1]
            a0, a1 = face_point(tower, c0, y0, axis), face_point(tower, c0, y1, axis)
            b0, b1 = face_point(tower, c1, y0, axis), face_point(tower, c1, y1, axis)
            if tower.spec['cross'] or y0 >= m(UPPER_TOP_M) - 1e-6:
                bar(mesh, steel, a0, b1, tower.brace)
                bar(mesh, steel, b0, a1, tower.brace)
            elif index % 2:
                bar(mesh, steel, a0, b1, tower.brace)
            else:
                bar(mesh, steel, b0, a1, tower.brace)

    # the plan crosses, on a gusset laid flat in the corner of each leg's angle
    plate = mesh.faces('body', 'plate')
    for y_m in PLAN_M:
        y = m(y_m)
        face = tower.leg_width / 2.0 - tower.leg_thick
        for sx in (-1, 1):
            for sz in (-1, 1):
                frame_box(mesh, plate, tower.node(sx, sz, y), ((-sx, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, -sz)),
                          (-face, -GUSSET, -face), (0.11, 0.0, 0.11), uv_scale=2.0)
        for (sx, sz), (tx, tz) in (((-1, -1), (1, 1)), ((1, -1), (-1, 1))):
            p, q = tower.node(sx, sz, y), tower.node(tx, tz, y)
            lift = (0.0, -GUSSET - tower.brace / 2.0, 0.0)
            bar(mesh, steel, add(add(p, lift), (-sx * 0.04, 0.0, -sz * 0.04)),
                add(add(q, lift), (-tx * 0.04, 0.0, -tz * 0.04)), tower.brace)


def footings(mesh, tower):
    """A concrete pier under each leg with the stub angle cast into it: what a tower actually stands on."""
    concrete = mesh.faces('footing', 'concrete')
    half = m(0.65)
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, _, z = tower.node(sx, sz, 0.0)
            box(mesh, concrete, (x - half, 0.0, z - half), (x + half, m(PIER_M), z + half), uv_scale=0.5)


def peak(mesh, tower):
    """The earth-wire peak's cap and the bracket the earth wire's clamp rides on, 0.6 m over it."""
    top = m(PEAK_M)
    half = tower.half(top) + tower.leg_width / 2.0
    plate = mesh.faces('peak', 'plate')
    box(mesh, plate, (-half, top, -half), (half, top + 0.012, half), uv_scale=0.6)
    box(mesh, plate, (-0.010, top + 0.012, -0.045), (0.010, HEIGHT - 0.012, 0.045), uv_scale=0.4)
    steel = mesh.faces('peak', 'steel')
    if tower.duty == 'suspension':
        # an earth-wire suspension clamp, along the line, in the bracket's slot
        cylinder(mesh, steel, (0.0, HEIGHT - 0.012, 0.0), 'z', 0.014, 0.05, sides=FITTING, uv_scale=0.3, caps=steel)
    else:
        # a strain clamp each way the earth wire is dead-ended - one way only on a terminal tower
        for sign in ((-1.0, 1.0) if tower.duty == 'tension' else (-1.0,)):
            cylinder(mesh, steel, (0.0, HEIGHT - 0.012, sign * 0.085), 'z', 0.013, 0.045, sides=FITTING,
                     uv_scale=0.3, caps=steel)
    for z in (-0.025, 0.025):
        bolt(mesh, steel, (0.010, HEIGHT - 0.045, z), 'x', 0.005, 0.008, uv_scale=0.1)


def sign_plate(mesh, tower):
    """The number plate and danger sign, bolted to a leg's flange three metres up and facing out of the tower."""
    y = m(3.2)
    sx, sz = -1, -1
    d, a, b = tower.leg_frame(sx, sz, y)
    origin = tower.node(sx, sz, y)
    inward = unit(sub(square(d, (-sx, 0.0, 0.0)), mul(b, dot(square(d, (-sx, 0.0, 0.0)), b))))
    face = tower.leg_width / 2.0
    width, height, thick = 0.21, 0.26, 0.006
    lo = (-height / 2.0, -tower.leg_width / 2.0, face + 0.001)
    hi = (height / 2.0, -tower.leg_width / 2.0 + width, face + 0.001 + thick)
    frame_box(mesh, mesh.faces('plate', 'plate'), origin, (d, inward, b), lo, (hi[0], hi[1], hi[2] - 0.0005),
              uv_scale=2.0)

    def at(u, v):
        return add(origin, add(add(mul(d, u), mul(inward, v)), mul(b, hi[2])))
    # the face a player reads: the whole tile, on a face as wide over tall as SQUASH['pole_plate'] says it is drawn
    mesh.quad(mesh.faces('plate', 'sign'), (at(lo[0], lo[1]), at(lo[0], hi[1]), at(hi[0], hi[1]), at(hi[0], lo[1])),
              b, ((1.0, 0.0), (0.0, 0.0), (0.0, 1.0), (1.0, 1.0)))


# ---------------------------------------------------------------- the crossarms

class Arm:
    """One crossarm on one side: its chords as functions of the distance out, and its stations."""

    def __init__(self, tower, which, side):
        self.tower, self.side = tower, side
        bottom_m, top_m, stations_m, tip_half_m, tip_depth_m = ARMS[which]
        self.bottom, self.top_root = m(bottom_m), m(top_m)
        self.tip_half, self.tip_depth = m(tip_half_m), m(tip_depth_m)
        self.root_bottom = tower.half(self.bottom)
        self.root_top = tower.half(self.top_root)
        self.stations = [m(s) for s in stations_m]
        self.tip = self.stations[-1]
        self.chord = tower.spec['chord']
        self.brace = tower.brace * 0.85
        self.frames = {}

    def bottom_point(self, x, sz):
        t = (x - self.root_bottom) / (self.tip - self.root_bottom)
        return (self.side * x, self.bottom, sz * (self.root_bottom + (self.tip_half - self.root_bottom) * t))

    def top_point(self, x, sz):
        t = (max(x, self.root_top) - self.root_top) / (self.tip - self.root_top)
        return (self.side * max(x, self.root_top), self.top_root + (self.bottom + self.tip_depth - self.top_root) * t,
                sz * (self.root_top + (self.tip_half - self.root_top) * t))

    def half_depth(self, x):
        return abs(self.bottom_point(x, 1)[2])

    def lacing_y(self):
        """The height of a bar laid on the bottom chords' flat flanges."""
        return self.bottom - (self.chord[0] / 2.0 - self.chord[1] - self.brace / 2.0)

    def on(self, kind, sz, x, face):
        """A chord's point at x, moved out onto its flange in ``face``: 'side' (the arm's flank) or 'flat'."""
        _, a, b = self.frames[(kind, sz)]
        base = self.bottom_point(x, sz) if kind == 'bottom' else self.top_point(x, sz)
        return add(base, mul(a if face == 'side' else b, self.chord[0] / 2.0 - self.chord[1] - self.brace / 2.0))


def crossarm(mesh, tower, which, side):
    """One crossarm: four chords from the body's leg nodes converging on the tip, laced on all four faces.

    A trapezoid in elevation - the bottom chords level, the top chords falling to the tip - and a taper in plan, which
    is how a Donaumast's arm is built.  The bottom and top planes carry a transverse at every station and a diagonal
    a panel, each flank a post and a diagonal; a phase hangs at a station, so its hanger is on a node.
    """
    arm = Arm(tower, which, side)
    steel = mesh.faces('arm', 'steel')
    for sz in (-1, 1):
        arm.frames[('bottom', sz)] = angle_member(mesh, steel, arm.bottom_point(arm.root_bottom, sz),
                                                  arm.bottom_point(arm.tip, sz), (0.0, 0.0, sz), (0.0, -1.0, 0.0),
                                                  arm.chord)
        arm.frames[('top', sz)] = angle_member(mesh, steel, arm.top_point(arm.root_top, sz),
                                               arm.top_point(arm.tip, sz), (0.0, 0.0, sz), (0.0, 1.0, 0.0), arm.chord)

    stations = [arm.root_bottom] + arm.stations
    for i, x in enumerate(stations):
        if i:
            bar(mesh, steel, arm.on('bottom', -1, x, 'flat'), arm.on('bottom', 1, x, 'flat'), arm.brace)
            bar(mesh, steel, arm.on('top', -1, x, 'flat'), arm.on('top', 1, x, 'flat'), arm.brace)
            for sz in (-1, 1):
                bar(mesh, steel, arm.on('bottom', sz, x, 'side'), arm.on('top', sz, x, 'side'), arm.brace)
        if i + 1 < len(stations):
            x1 = stations[i + 1]
            flip = 1 if i % 2 else -1
            bar(mesh, steel, arm.on('bottom', flip, x, 'flat'), arm.on('bottom', -flip, x1, 'flat'), arm.brace)
            if i:
                bar(mesh, steel, arm.on('top', -flip, x, 'flat'), arm.on('top', flip, x1, 'flat'), arm.brace)
            for sz in (-1, 1):
                bar(mesh, steel, arm.on('bottom', sz, x, 'side'), arm.on('top', sz, x1, 'side'), arm.brace)
    return arm


# ---------------------------------------------------------------- the strings

def disc(mesh, faces, centre, axis, diameter, pitch):
    """One cap-and-pin unit about ``axis`` (pointing at its cap): a shallow shed over its cap and the underside.

    Two bands: at a fifth of a block a unit's ribs are a pixel, and a tension tower carries 552 of them.
    """
    radius = diameter / 2.0
    revolve(mesh, faces, centre, axis, [(ROD, 0.30 * pitch), (radius, -0.16 * pitch), (ROD, -0.30 * pitch)],
            sides=FITTING, uv_scale=1.0, uv_along=1.0)


def ring(mesh, faces, centre, half_a, half_b, axes):
    """A grading ring: a racetrack of tube in the plane of two axes, round the line end of a string."""
    r = min(half_a, half_b) * 0.9
    points = []
    for corner, (sa, sb) in enumerate(((1, 1), (-1, 1), (-1, -1), (1, -1))):
        middle = add(add(centre, mul(axes[0], sa * (half_a - r))), mul(axes[1], sb * (half_b - r)))
        for step in range(5):
            angle = math.radians(90.0 * corner + 22.5 * step)
            p = add(add(middle, mul(axes[0], r * math.cos(angle))), mul(axes[1], r * math.sin(angle)))
            if not points or math.dist(points[-1], p) > 1e-6:
                points.append(p)
    points.append(points[0])
    tube(mesh, faces, points, 0.0055, sides=8, uv_scale=1.0)


def hanger(mesh, arm, x, index):
    """The plate a suspension string hangs from, bolted under the arm's bottom transverse at its station."""
    y = arm.lacing_y() - arm.brace / 2.0
    plate = mesh.faces('string_%d' % index, 'plate')
    cx = arm.side * x
    box(mesh, plate, (cx - 0.035, y - 0.060, -0.006), (cx + 0.035, y + 0.003, 0.006), uv_scale=0.6)
    for dx in (-0.018, 0.018):
        bolt(mesh, plate, (cx + dx, y - 0.025, -0.006), 'z', 0.0045, 0.012, uv_scale=0.1)
    return y - 0.060


def suspension_string(mesh, index, x, top):
    """An I-string: link, twenty-two units, a grading ring, the yoke, and the square of four clamps the bundle lies in."""
    porcelain = mesh.faces('insulator_%d' % index, 'porcelain')
    steel = mesh.faces('string_%d' % index, 'steel')
    pitch = m(SUSPENSION_PITCH_M)
    radius = m(SUSPENSION_DISC_M) * DISC_WIDEN / 2.0
    link = 0.045
    first = top - link
    length = SUSPENSION_UNITS * pitch
    cylinder(mesh, steel, (x, top - link / 2.0 + 0.004, 0.0), 'y', 0.006, link / 2.0 + 0.004, sides=8,
             uv_scale=0.2, caps=steel)
    cylinder(mesh, steel, (x, first - length / 2.0 - 0.012, 0.0), 'y', ROD, length / 2.0 + 0.012, sides=FITTING,
             uv_scale=0.3, caps=steel)
    for k in range(SUSPENSION_UNITS):
        disc(mesh, porcelain, (x, first - (k + 0.5) * pitch, 0.0), (0.0, 1.0, 0.0), radius * 2.0, pitch)

    # the socket clevis's yoke plate, across the line, and the grading ring standing on it round the last units
    yoke_top = first - length - 0.022
    yoke = yoke_top - 0.040
    box(mesh, steel, (x - 0.062, yoke, -0.006), (x + 0.062, yoke_top, 0.006), uv_scale=0.4)
    ring_y = first - length + 0.03
    ring_r = radius + 0.028
    ring(mesh, steel, (x, ring_y, 0.0), ring_r, ring_r, ((1.0, 0.0, 0.0), (0.0, 0.0, 1.0)))
    for sx in (-1, 1):
        tube(mesh, steel, [(x + sx * 0.055, yoke_top - 0.004, 0.0), (x + sx * ring_r, ring_y, 0.0)], 0.0045,
             sides=8, uv_scale=1.0, caps=steel)

    # the four suspension clamps on a square frame, one sub-conductor in each corner
    fitting = mesh.faces('clamp_%d' % index, 'fitting')
    centre_y = yoke - HALF_BUNDLE - CLAMP_RADIUS + 0.004
    for dx in (-HALF_BUNDLE, HALF_BUNDLE):
        for dy in (-HALF_BUNDLE, HALF_BUNDLE):
            cylinder(mesh, fitting, (x + dx, centre_y + dy, 0.0), 'z', CLAMP_RADIUS, SUSPENSION_CLAMP,
                     sides=FITTING, uv_scale=0.5, caps=fitting)
    for k in (-1, 1):
        box(mesh, fitting, (x - HALF_BUNDLE, centre_y + k * HALF_BUNDLE - 0.006, -0.005),
            (x + HALF_BUNDLE, centre_y + k * HALF_BUNDLE + 0.006, 0.005), uv_scale=0.4)
        box(mesh, fitting, (x + k * HALF_BUNDLE - 0.006, centre_y - HALF_BUNDLE, -0.005),
            (x + k * HALF_BUNDLE + 0.006, centre_y + HALF_BUNDLE, 0.005), uv_scale=0.4)
    return centre_y


def tension_string(mesh, arm, index, x, sign):
    """A double tension string dead-ending one side of a phase, along the line on ``sign``'s side of the arm.

    Strain plate under the chord node, a yoke, two strings of twenty-three units, a grading ring, the yoke at the
    line end and four compression dead-end clamps.  Returns (string axis height, where the clamps begin and end).
    """
    porcelain = mesh.faces('insulator_%d' % index, 'porcelain')
    steel = mesh.faces('string_%d' % index, 'steel')
    plate = mesh.faces('string_%d' % index, 'plate')
    pitch = m(TENSION_PITCH_M)
    radius = m(TENSION_DISC_M) * DISC_WIDEN / 2.0
    chord = arm.chord[0]
    cx = arm.side * x
    z0 = sign * arm.half_depth(x)
    axis_y = arm.bottom - chord / 2.0 - 0.050

    def along(distance):
        return z0 + sign * distance

    # the strain plate hanging under the bottom chord's node, and its two bolts
    lo_z, hi_z = sorted((along(-0.025), along(0.105)))
    box(mesh, plate, (cx - 0.006, axis_y - 0.030, lo_z), (cx + 0.006, arm.bottom - chord / 2.0 + 0.004, hi_z),
        uv_scale=0.6)
    for distance in (0.0, 0.045):
        bolt(mesh, plate, (cx - 0.006, axis_y + 0.022, along(distance)), 'x', 0.0045, 0.012, uv_scale=0.1)
    # the yoke that splits the pull into two strings
    half = DOUBLE_GAP / 2.0 + 0.022
    lo_z, hi_z = sorted((along(0.045), along(0.105)))
    box(mesh, plate, (cx - half, axis_y - 0.005, lo_z), (cx + half, axis_y + 0.005, hi_z), uv_scale=0.5)

    length = TENSION_UNITS * pitch
    start = 0.090
    end = start + 0.020 + length + 0.020
    for dx in (-DOUBLE_GAP / 2.0, DOUBLE_GAP / 2.0):
        rod_lo, rod_hi = sorted((along(start), along(end + 0.006)))
        cylinder(mesh, steel, (cx + dx, axis_y, (rod_lo + rod_hi) / 2.0), 'z', ROD, (rod_hi - rod_lo) / 2.0,
                 sides=FITTING, uv_scale=0.3, caps=steel)
        for k in range(TENSION_UNITS):
            disc(mesh, porcelain, (cx + dx, axis_y, along(start + 0.020 + (k + 0.5) * pitch)), (0.0, 0.0, -sign),
                 radius * 2.0, pitch)

    # the yoke at the line end: a bar between the strings that the four clamps' rows are bolted above and below
    lo_z, hi_z = sorted((along(end), along(end + 0.012)))
    box(mesh, plate, (cx - half, axis_y - 0.0105, lo_z), (cx + half, axis_y + 0.0105, hi_z), uv_scale=0.5)
    ring_z = along(end - 0.035)
    ring(mesh, steel, (cx, axis_y, ring_z), DOUBLE_GAP / 2.0 + radius + 0.022, radius + 0.022,
         ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0)))
    for sx in (-1, 1):
        tube(mesh, steel, [(cx + sx * (half - 0.004), axis_y, along(end + 0.004)),
                           (cx + sx * (DOUBLE_GAP / 2.0 + radius + 0.022), axis_y, ring_z)], 0.0045, sides=8,
             uv_scale=1.0, caps=steel)

    fitting = mesh.faces('clamp_%d' % index, 'fitting')
    clamp_lo, clamp_hi = sorted((along(end + 0.008), along(end + 0.008 + DEAD_END)))
    for dx in (-HALF_BUNDLE, HALF_BUNDLE):
        for dy in (-HALF_BUNDLE, HALF_BUNDLE):
            cylinder(mesh, fitting, (cx + dx, axis_y + dy, (clamp_lo + clamp_hi) / 2.0), 'z', CLAMP_RADIUS,
                     DEAD_END / 2.0, sides=FITTING, uv_scale=0.5, caps=fitting)
    return axis_y, along(end + 0.008 + DEAD_END * 0.45)


def jumper(mesh, index, x, axis_y, clamp_z):
    """The jumper that carries a phase across a tension tower: a quad loop from one side's clamps to the other's.

    Each side's four dead-end clamps are bolted to a jumper terminal under them, and the four sub-conductors leave it
    as a bundle, sag under the arm and come up into the other side's terminal, held apart by two spacers.
    """
    conductor = mesh.faces('jumper_%d' % index, 'conductor')
    fitting = mesh.faces('jumper_%d' % index, 'fitting')
    reach = abs(clamp_z)
    top = axis_y - HALF_BUNDLE - CLAMP_RADIUS + 0.012
    centre_y = top - 0.075
    for sign in (-1.0, 1.0):
        z = sign * reach
        box(mesh, fitting, (x - 0.072, centre_y - HALF_BUNDLE - SUB_RADIUS - 0.012, z - 0.032),
            (x + 0.072, top, z + 0.032), uv_scale=0.5)

    steps = 10
    for dx in (-HALF_BUNDLE, HALF_BUNDLE):
        for dy in (-HALF_BUNDLE, HALF_BUNDLE):
            points = []
            for k in range(steps + 1):
                z = -reach + 2.0 * reach * k / steps
                points.append((x + dx, centre_y + dy - JUMPER_DROP * (1.0 - (z / reach) ** 2), z))
            tube(mesh, conductor, points, SUB_RADIUS, sides=FITTING, uv_scale=1.0, uv_along=4.0, caps=conductor)

    # two spacers, each a square frame that clamps the four sub-conductors
    for z in (-0.5 * reach, 0.5 * reach):
        y = centre_y - JUMPER_DROP * (1.0 - (z / reach) ** 2)
        for k in (-1, 1):
            box(mesh, fitting, (x - HALF_BUNDLE, y + k * HALF_BUNDLE - 0.005, z - 0.006),
                (x + HALF_BUNDLE, y + k * HALF_BUNDLE + 0.005, z + 0.006), uv_scale=0.4)
            box(mesh, fitting, (x + k * HALF_BUNDLE - 0.005, y - HALF_BUNDLE, z - 0.006),
                (x + k * HALF_BUNDLE + 0.005, y + HALF_BUNDLE, z + 0.006), uv_scale=0.4)


# ---------------------------------------------------------------- the whole tower

def tower(duty):
    """One tower, by duty: what changes between the three and what does not."""
    mesh = Mesh()
    shape = Tower(duty)
    footings(mesh, shape)
    legs(mesh, shape)
    bracing(mesh, shape)
    arms = {(which, side): crossarm(mesh, shape, which, side) for which in ARMS for side in (-1, 1)}
    peak(mesh, shape)
    sign_plate(mesh, shape)

    for index, (x_m, which) in enumerate(PHASES, start=1):
        x = abs(m(x_m))
        arm = arms[(which, 1 if x_m > 0 else -1)]
        if duty == 'suspension':
            suspension_string(mesh, index, arm.side * x, hanger(mesh, arm, x, index))
        elif duty == 'tension':
            for sign in (-1.0, 1.0):
                axis_y, clamp_z = tension_string(mesh, arm, index, x, sign)
            jumper(mesh, index, arm.side * x, axis_y, clamp_z)
        else:
            # the whole pull comes from -z: the line leaves the tower that way and nowhere else
            tension_string(mesh, arm, index, x, -1.0)
    return mesh


def anchors(duty):
    """Where each phase's bundle lands, as LatticeTowerBlockEntity works it out from the clamp group's box."""
    mesh = tower(duty)
    out = []
    for index in range(1, len(PHASES) + 1):
        points = [mesh.v[i[0] - 1] for name, _, faces in mesh.objects if name == 'clamp_%d' % index
                  for face in faces for i in face]
        lo = [min(p[k] for p in points) for k in range(3)]
        hi = [max(p[k] for p in points) for k in range(3)]
        centre = [(lo[k] + hi[k]) / 2.0 for k in range(3)]
        out.append((centre[0], centre[1], lo[2] if duty == 'terminal' else centre[2]))
    return out


# ---------------------------------------------------------------- collision
#
# Authored rather than cut from the model, and this is the one place in the mod where that is right.  Cut from every
# group, a lattice's diagonals sweep almost every cell of its bounding volume: a tower you cannot walk into and
# hundreds of shell blocks a placement.  What a player collides with is the structure - the legs, the floor of each
# crossarm, the footings - and the strings, which are what a span is hung on.  Between the members is air.
COLLISION_LEG = 0.10          # half how wide a leg collides, a little wider than the angle so nobody slips past it
COLLISION_ARM = 0.07          # how far above and below its bottom chords an arm's floor collides


def collision_boxes(duty):
    """Every box the tower collides as, in model space and in blocks."""
    shape = Tower(duty)
    boxes = []
    top = m(PEAK_M)
    for sx in (-1, 1):
        for sz in (-1, 1):
            for level in range(int(math.ceil(top))):
                y0, y1 = float(level), min(top, level + 1.0)
                if y1 - y0 < 0.05:
                    continue
                half = shape.half((y0 + y1) * 0.5)
                boxes.append(((sx * half - COLLISION_LEG, y0, sz * half - COLLISION_LEG),
                              (sx * half + COLLISION_LEG, y1, sz * half + COLLISION_LEG)))
            x, _, z = shape.node(sx, sz, 0.0)
            pier = m(0.65)
            boxes.append(((x - pier, 0.0, z - pier), (x + pier, m(PIER_M), z + pier)))

    for which in ARMS:
        for side in (-1, 1):
            arm = Arm(shape, which, side)
            stations = [arm.root_bottom] + arm.stations
            for x0, x1 in zip(stations, stations[1:]):
                depth = arm.half_depth(x0) + arm.chord[0] / 2.0
                boxes.append(((min(side * x0, side * x1), arm.bottom - COLLISION_ARM, -depth),
                              (max(side * x0, side * x1), arm.bottom + COLLISION_ARM, depth)))

    boxes.append(((-shape.half(top) - 0.05, top - 0.6, -shape.half(top) - 0.05),
                  (shape.half(top) + 0.05, HEIGHT, shape.half(top) + 0.05)))
    boxes += string_boxes(duty)
    return [(tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3))) for a, b in boxes]


@functools.lru_cache(maxsize=None)
def string_boxes(duty):
    """A box round each phase's string, cut to it.

    A fitting is what a player aims at - a span is hung on one by pointing at it - so every insulator in this mod
    carries collision of its own.  The jumper does not: a conductor is a wire, and no wire in this mod collides.
    """
    mesh = tower(duty)
    boxes = []
    for index in range(1, len(PHASES) + 1):
        points = [mesh.v[i[0] - 1] for name, _, faces in mesh.objects
                  if name in ('insulator_%d' % index, 'clamp_%d' % index) for face in faces for i in face]
        if duty == 'tension':
            for sign in (-1, 1):
                side = [p for p in points if p[2] * sign > 0]
                boxes.append((tuple(min(p[k] for p in side) for k in range(3)),
                              tuple(max(p[k] for p in side) for k in range(3))))
        else:
            boxes.append((tuple(min(p[k] for p in points) for k in range(3)),
                          tuple(max(p[k] for p in points) for k in range(3))))
    return boxes


def collision_cells(duty):
    """The boxes clipped into block cells, keyed by cell, in the sixteenths Block.box wants."""
    cells = {}
    for lo, hi in collision_boxes(duty):
        for cx in range(int(math.floor(lo[0] + 0.5)), int(math.ceil(hi[0] + 0.5))):
            for cy in range(int(math.floor(lo[1])), int(math.ceil(hi[1]))):
                for cz in range(int(math.floor(lo[2] + 0.5)), int(math.ceil(hi[2] + 0.5))):
                    low = (max(lo[0], cx - 0.5), max(lo[1], cy), max(lo[2], cz - 0.5))
                    high = (min(hi[0], cx + 0.5), min(hi[1], cy + 1.0), min(hi[2], cz + 0.5))
                    if any(high[i] - low[i] < 0.03 for i in range(3)):
                        continue

                    box_px = (round((low[0] - cx + 0.5) * 16.0, 2), round((low[1] - cy) * 16.0, 2),
                              round((low[2] - cz + 0.5) * 16.0, 2),
                              round((high[0] - cx + 0.5) * 16.0, 2), round((high[1] - cy) * 16.0, 2),
                              round((high[2] - cz + 0.5) * 16.0, 2))
                    cells.setdefault((cx, cy, cz), set()).add(box_px)

    # coarse() per cell: a lattice cell held one box per member, and a tower a player climbs does not need its
    # collision carved per angle iron - modellib.COLLISION_SLACK decides how coarse, for the whole mod.
    out = {}
    for cell, boxes in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        pairs = coarse([((b[0], b[1], b[2]), (b[3], b[4], b[5])) for b in boxes])
        out[cell] = sorted(tuple(round(v, 2) for v in lo + hi) for lo, hi in pairs)
    return out


@functools.lru_cache(maxsize=None)
def tables():
    """The three duties' tables, as the declarations LatticeTowerBlock holds.

    One table a duty: the duties are three towers, not one body with three heads - a terminal tower stands on a
    foot half as wide again as a suspension tower's, so no cell of the one is a cell of the other.
    """
    return [('%s_CELLS' % duty.upper(), collision_cells(duty), None) for _, duty in MODELS]


def declaration(name, cells, over):
    """One table as the line LatticeTowerBlock declares it on."""
    rows = []
    for (x, y, z), boxes in cells.items():
        drawn = ['Block.box(%s)' % ', '.join('%.2f' % v for v in b) for b in boxes]
        shape = drawn[0] if len(drawn) == 1 else 'Shapes.or(%s)' % (',\n\t\t\t\t\t'.join(drawn))
        rows.append('\t\t\tnew Cell(%d, %d, %d, %s)' % (x, y, z, shape))

    body = ',\n'.join(rows)
    if over is None:
        return '\tprivate static final List<Cell> %s = List.of(\n%s);' % (name, body)

    return '\tprivate static final List<Cell> %s = over(%s, List.of(\n%s));' % (name, over, body)


def check_java():
    """Proves LatticeTowerBlock still declares the cells this authors, for all three duties.

    check_hitboxes cannot compare these, because they are authored rather than cut from the model, so nothing else
    would notice them going stale.
    """
    path = os.path.join('src', 'main', 'java', 'io', 'github', 'simonerecchia', 'renewables', 'block',
                        'LatticeTowerBlock.java')
    source = ' '.join(open(path).read().split())
    return ['%s is not what LatticeTowerBlock declares' % name
            for name, cells, over in tables() if ' '.join(declaration(name, cells, over).split()) not in source]


MODELS = [
    ('lattice_suspension', 'suspension'),
    ('lattice_tension', 'tension'),
    ('lattice_terminal', 'terminal'),
]


def main():
    duties = dict(MODELS)
    builders = [(name, functools.partial(tower, duty)) for name, duty in MODELS]
    for name, _ in emit(OUT, builders, MATERIALS, 'gen_tower_models.py'):
        cells = collision_cells(duties[name])
        print('    %3d collision cells, %3d boxes' % (len(cells), sum(len(b) for b in cells.values())))
        if '--anchors' in sys.argv:
            for index, point in enumerate(anchors(duties[name]), start=1):
                print('    anchor %d: %.3f, %.3f, %.3f' % ((index,) + tuple(point)))

    problems = check_java()
    for line in problems:
        print('    TABLE %s' % line)
    if not problems:
        print('LatticeTowerBlock declares the cells this authors, for all three duties')

    if '--java' in sys.argv:
        for name, cells, over in tables():
            print('\n\t/** ---- %d cell(s), printed by tools/gen_tower_models.py --java ---- */' % len(cells))
            print(declaration(name, cells, over))


if __name__ == '__main__':
    main()
