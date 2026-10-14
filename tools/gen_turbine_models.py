#!/usr/bin/env python3
"""The wind turbine: tower, nacelle, hub and three twisted blades, and the one insulator at the foot.

    python3 tools/gen_turbine_models.py             # writes models/wind_turbine/wind_turbine.{obj,mtl}
    python3 tools/gen_turbine_models.py --contract  # prints every figure docs/turbine-model.md quotes
    python3 tools/gen_turbine_models.py --java      # the hitbox tables: the tower per block, the nacelle per model

One mesh serves all six turbines of the catalogue: the renderer scales it (docs/turbine-model.md, section 4).
It is the C130-4.0 at 1:1 - a block is 10 m - so the rotor is 130 m across, the hub sits 13 blocks up and the
tower is 13 blocks tall.  Authored facing SOUTH (the rotor is at +z), clockwise rotation seen from the front.

The figures everything else follows from are the constants below.  check_turbine.py reads the OBJ back and
proves the contract (group names, pivot, radius, closed surfaces) and check_hitboxes.py proves the tower and
nacelle shapes against it, so a figure that moves here fails the build until the tables are refreshed.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, HEX, ROUND, Mesh, box, clad_box, cylinder, frame_box, hemisphere,   # noqa: E402
                      lathe, pin_insulator, square_uv, tube, write_mtl)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'models')
NAME = 'wind_turbine'

# material -> texture.  The insulator's two are the mod's own, as wind.md section 15 asks.
MATERIALS = {
    'tower': 'turbine_tower.png',
    'flange': 'turbine_flange.png',
    'concrete': 'pole_concrete.png',
    'door': 'turbine_door.png',
    'plate': 'turbine_plate.png',
    'nacelle': 'turbine_nacelle.png',
    'vent': 'turbine_louvre.png',
    'sensor': 'turbine_sensor.png',
    'beacon': 'turbine_beacon.png',
    'hub': 'turbine_hub.png',
    'blade': 'turbine_blade.png',
    'seal': 'turbine_seal.png',
    'insulator_porcelain': 'porcelain_brown.png',
    'insulator_steel': 'pv_steel.png',
}

# ------------------------------------------------------------------ the figures
# Blocks, authored frame: origin on the tower axis at ground level, +y up, +z the front.

# The tower: one cone, 80-sided, from the foot to the nacelle.  pole* is stretched to the real height by the
# renderer, so everything on it that must not stretch lives in pole_base instead.
H_POLE = 13.0
R_BASE = 0.34              # shaft radius at y = 0 (6.8 m across)
R_TOP = 0.19               # and at y = H_POLE (3.8 m)
TOWER_TILE = 4.0           # blocks of authored height one copy of turbine_tower.png covers
TOP_FLANGE = 0.022         # the yaw-bearing flange standing proud of the shaft's top
TOP_FLANGE_H = 0.030
TOP_FLANGE_DROP = 0.012     # and how far below the top face it stops

# The foot: concrete plinth, bolted base flange, door.  pole_base is scaled uniformly, never stretched.
PLINTH_R, PLINTH_H = 0.42, 0.045
FLANGE_R, FLANGE_H = 0.40, 0.030
BOLTS = 48
BOLT_R = 0.372
DOOR_W, DOOR_H, DOOR_SILL = 0.130, 0.300, 0.095
DOOR_FRAME = 0.014

# The nacelle sits on the shaft's top face; everything in it is authored from y = NACELLE_Y.
NACELLE_Y = H_POLE
ROOF_Y = NACELLE_Y + 0.60
AXIS_Y = NACELLE_Y + 0.30             # the rotor axis: half way up the hull, as the contract asks
HULL = (                              # z, half width, floor, roof, upper corner radius, lower corner radius
    (-0.88, 0.200, 0.10, 0.54, 0.070, 0.050),
    (-0.80, 0.250, 0.05, 0.60, 0.090, 0.070),
    (-0.40, 0.270, 0.00, 0.60, 0.110, 0.080),
    (0.40, 0.270, 0.00, 0.60, 0.110, 0.080),
    (0.52, 0.250, 0.02, 0.58, 0.110, 0.080),
    (0.62, 0.200, 0.07, 0.52, 0.100, 0.070),
)
HULL_FRONT = 0.62
NOSE_R = 0.135                         # the main-bearing housing the hub is bolted to
SHAFT_R = 0.10                         # the main shaft's flange, from inside the housing to inside the hub
SHAFT_FROM, SHAFT_TO = HULL_FRONT + 0.030, 0.71

# The hub: a spinner, 80-sided, a solid of revolution about the rotor axis.
SPIN_Z0, SPIN_Z1, SPIN_TIP = 0.68, 1.06, 1.40
SPIN_R = 0.27
HUB_Z = (SPIN_Z0 + SPIN_TIP) / 2.0     # the pivot's z: the centre of the spinner's box

# The blades: a rotor of exactly 13 blocks across, three of them, one straight up in the authored pose.
ROTOR_R = 6.5
ROTOR_D = 2.0 * ROTOR_R
BLADE_Z = 0.90                         # where the roots leave the spinner, on its cylindrical part
BLADE_ANGLES = (90.0, 210.0, 330.0)    # counted anticlockwise from +x, seen from the front
CONE = math.radians(2.5)               # precone: the blades lean forward, away from the tower
ROOT_R0 = 0.17                         # the blade's first station, inside the spinner
SEAL_R = 0.19                          # the pitch-bearing seal ring's radius
SEAL_FROM, SEAL_TO = 0.262, 0.302      # and where along the blade it stands, straddling the spinner skin

# span station (blocks from the hub axis), chord, airfoil thickness ratio, twist in degrees
BLADE_TABLE = (
    (0.17, 0.30, 0.45, 20.0), (0.40, 0.30, 0.45, 19.0), (0.90, 0.365, 0.40, 17.5),
    (1.30, 0.410, 0.33, 15.5), (1.80, 0.415, 0.27, 12.5), (2.50, 0.370, 0.225, 9.0),
    (3.20, 0.320, 0.20, 6.5), (4.00, 0.260, 0.18, 4.5), (4.80, 0.205, 0.17, 3.0),
    (5.50, 0.155, 0.155, 2.0), (6.00, 0.118, 0.145, 1.2), (6.35, 0.082, 0.135, 0.5),
    (6.50, 0.060, 0.130, 0.2))
BLADE_STATIONS = (0.17, 0.28, 0.40, 0.55, 0.70, 0.85, 1.00, 1.15, 1.30, 1.50, 1.70, 1.95, 2.20, 2.50, 2.80,
                  3.15, 3.50, 3.90, 4.30, 4.70, 5.10, 5.45, 5.75, 6.00, 6.20, 6.35, 6.45, 6.50)
AIRFOIL_HALF = 32                      # points on each surface of a section
CAMBER, CAMBER_AT = 0.035, 0.40
PREBEND = 0.20                         # how far the tip is bent forward, blocks

# The insulator: a pin insulator on a galvanised post, standing on its own plate beside the tower foot.
INSULATOR_AT = (0.375, 0.375)
INSULATOR_BASE_Y = 0.25                # the porcelain's bottom centre
INSULATOR_D = 0.18
POST_R = 0.026
PLATE_R, PLATE_H = 0.10, 0.012
DUCT_W, DUCT_H = 0.05, 0.016           # the cable trough from the tower's foot to the post

# The catalogue, for the scale table and the clearance proof: id, rotor diameter m, rotor exaggeration,
# tower segments min and max.  wind.md section 2.
CATALOGUE = (
    ('sw_10', 7.0, 3.3, 2, 4),
    ('c52_085', 52.0, 1.0, 4, 7),
    ('c80_20', 80.0, 1.0, 6, 10),
    ('c90_30', 90.0, 1.0, 8, 11),
    ('c112_30', 112.0, 1.0, 8, 12),
    ('c130_40', 130.0, 1.0, 9, 13),
)


def scales(diameter_m, exaggeration, small=False):
    """(rotor scale, nacelle scale): the contract's s_r and s_n, with the authored rotor of ROTOR_D blocks."""
    rotor = diameter_m / 10.0 * exaggeration / ROTOR_D
    return rotor, (max(rotor, 0.25) if small else rotor)


def shaft_radius(y):
    """The tower's radius at an authored height."""
    return R_BASE + (R_TOP - R_BASE) * y / H_POLE


# ------------------------------------------------------------------ small helpers

def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def unit(a):
    length = math.sqrt(dot(a, a)) or 1.0
    return (a[0] / length, a[1] / length, a[2] / length)


def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def mean(points):
    return tuple(sum(p[i] for p in points) / len(points) for i in range(3))


def pchip(xs, ys):
    """A monotone cubic through the table, so a chord never overshoots between two stations."""
    n = len(xs)
    h = [xs[i + 1] - xs[i] for i in range(n - 1)]
    d = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
    m = [d[0]] + [0.0] * (n - 2) + [d[-1]]
    for i in range(1, n - 1):
        if d[i - 1] * d[i] > 0:
            w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])

    def at(x):
        x = max(xs[0], min(xs[-1], x))
        i = max(0, min(n - 2, next((k for k in range(n - 1) if xs[k] <= x <= xs[k + 1]), n - 2)))
        t = (x - xs[i]) / h[i]
        t2, t3 = t * t, t * t * t
        return ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h[i] * m[i + 1])

    return at


def tile_uv(lo, hi, tile):
    """A uv rect per face off the part's own size: ``tile`` is how many blocks one copy of the texture spans."""
    return square_uv(lo, hi, 1.0 / tile)


# Where each decal sits on turbine_plate.png, as OBJ uv (v is up): the tile's four quarters.
DECAL_RATING = (0.0, 0.5, 0.5, 1.0)
DECAL_DANGER = (0.5, 0.5, 1.0, 1.0)
DECAL_LOGO = (0.0, 0.0, 0.5, 0.5)


def slab(mesh, name, material, lo, hi, tile=0.5, sides=None, windows=None):
    """A box mapped off world size.  ``sides`` re-clads named faces in another material, and ``windows`` maps
    a face onto a part of its texture instead of a stretch of it."""
    clad_box(mesh, name, lo, hi, dict({'*': material}, **(sides or {})),
             uv=dict(tile_uv(lo, hi, tile), **(windows or {})))


def lathe_z(mesh, faces, centre, profile, sides=ROUND, uv_scale=1.0, uv_along=1.0):
    """A surface of revolution about an axis parallel to z.  ``profile`` is (radius, z) from the back forwards.

    modellib.lathe turns about y; the rotor turns about z, so the same figure is drawn here with the axes
    exchanged.  The profile is walked with the solid on its left, as lathe's is, so the normals come out.
    """
    cx, cy, cz = centre
    lengths, travelled = [0.0], 0.0
    for i in range(1, len(profile)):
        travelled += math.dist(profile[i], profile[i - 1])
        lengths.append(travelled)

    def point(index, i):
        radius, z = profile[index]
        a = 2.0 * math.pi * i / sides
        return (cx + math.cos(a) * radius, cy + math.sin(a) * radius, cz + z)

    for index in range(len(profile) - 1):
        (r0, z0), (r1, z1) = profile[index], profile[index + 1]
        if r0 <= 1e-9 and r1 <= 1e-9:
            continue

        v0 = lengths[index] / max(travelled, 1e-9) * uv_along
        v1 = lengths[index + 1] / max(travelled, 1e-9) * uv_along
        dr, dz = r1 - r0, z1 - z0
        span = math.hypot(dr, dz) or 1.0
        for i in range(sides):
            j = (i + 1) % sides
            mid = 2.0 * math.pi * (i + 0.5) / sides
            normal = (math.cos(mid) * dz / span, math.sin(mid) * dz / span, -dr / span)
            u0, u1 = i / sides * uv_scale, (i + 1) / sides * uv_scale
            mesh.quad(faces, (point(index, i), point(index, j), point(index + 1, j), point(index + 1, i)),
                      normal, ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))


def cap(mesh, faces, ring, normal, tile=1.0, axes=(0, 1)):
    """A convex outline closed with a fan of triangles about its centre, so no triangulation can fold it.

    ``axes`` are the two coordinates the texture is laid over: (0, 1) for a face that looks along z."""
    centre = mean(ring)
    for i in range(len(ring)):
        a, b = ring[i], ring[(i + 1) % len(ring)]
        mesh.quad(faces, (centre, a, b), normal,
                  [(p[axes[0]] / tile + 0.5, p[axes[1]] / tile + 0.5) for p in (centre, a, b)])


def dome(mesh, faces, centre, radius, sides=FITTING, rings=5):
    """A closed hemisphere: modellib's is open at the equator, which is a hole in whatever it sits on."""
    hemisphere(mesh, faces, centre, radius, sides=sides, rings=rings, up=1, uv_scale=1.0)
    rim = [(centre[0] + radius * math.cos(2.0 * math.pi * i / sides), centre[1],
            centre[2] + radius * math.sin(2.0 * math.pi * i / sides)) for i in range(sides)]
    cap(mesh, faces, rim, (0.0, -1.0, 0.0), tile=radius * 2.0, axes=(0, 2))


def skin(mesh, faces_for, rings, tile=0.8):
    """A closed run of rings lofted into quads, each wound outwards.  ``faces_for`` picks a face list per quad.

    u is the arc length round the ring and v the distance between rings, both over ``tile``, so a texture
    keeps its scale whatever the section does.  The rings must have the same number of points.
    """
    columns = len(rings[0])
    perimeters = []
    for ring in rings:
        along, total = [0.0], 0.0
        for i in range(columns):
            total += math.dist(ring[i], ring[(i + 1) % columns])
            along.append(total)
        perimeters.append(along)

    v = 0.0
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        step = sum(math.dist(a[i], b[i]) for i in range(columns)) / columns
        centre = mean(a + b)
        for i in range(columns):
            j = (i + 1) % columns
            corners = (a[i], a[j], b[j], b[i])
            normal = unit(cross(sub(corners[1], corners[0]), sub(corners[3], corners[0])))
            if dot(normal, sub(mean(corners), centre)) < 0.0:
                normal = mul(normal, -1.0)
            uvs = ((perimeters[k][i] / tile, v / tile), (perimeters[k][i + 1] / tile, v / tile),
                   (perimeters[k + 1][i + 1] / tile, (v + step) / tile),
                   (perimeters[k + 1][i] / tile, (v + step) / tile))
            mesh.quad(faces_for(normal), corners, normal, uvs)
        v += step


# ------------------------------------------------------------------ the tower

def tower(mesh):
    """The shaft: one cone, and the flange at its head that the nacelle's yaw bearing sits on."""
    faces = mesh.faces('pole', 'tower')
    # a tile of the texture is four blocks of authored height, with a weld every two, so it repeats three
    # and a quarter times up the shaft and the rust it carries does not repeat on every can
    cylinder(mesh, faces, (0.0, H_POLE / 2.0, 0.0), 'y', R_BASE, H_POLE / 2.0, sides=ROUND, uv_scale=1.0,
             uv_along=H_POLE / TOWER_TILE, caps=faces, taper=R_TOP / R_BASE)

    flange = mesh.faces('pole', 'flange')
    top = R_TOP + TOP_FLANGE
    # a washer round the shaft's head, a hair below its top face so the two are not in one plane
    base = H_POLE - TOP_FLANGE_DROP - TOP_FLANGE_H
    lathe(mesh, flange, (0.0, 0.0, 0.0),
          [(top, base), (top, base + TOP_FLANGE_H), (R_TOP, base + TOP_FLANGE_H), (R_TOP, base), (top, base)],
          sides=ROUND, uv_scale=2.0, uv_along=0.3)


def foot(mesh):
    """Everything at the bottom that must not stretch with the shaft: plinth, flange, bolts, door, plates."""
    concrete = mesh.faces('pole_base', 'concrete')
    cylinder(mesh, concrete, (0.0, PLINTH_H / 2.0, 0.0), 'y', PLINTH_R, PLINTH_H / 2.0, sides=ROUND,
             uv_scale=2.6, uv_along=0.045, caps=concrete)

    flange = mesh.faces('pole_base', 'flange')
    cylinder(mesh, flange, (0.0, PLINTH_H + FLANGE_H / 2.0, 0.0), 'y', FLANGE_R, FLANGE_H / 2.0, sides=ROUND,
             uv_scale=5.0, uv_along=0.5, caps=flange)
    top = PLINTH_H + FLANGE_H
    # a bolt circle: M48 nuts, drawn well over size because a true one is a tenth of a pixel
    for i in range(BOLTS):
        a = 2.0 * math.pi * (i + 0.5) / BOLTS
        cylinder(mesh, flange, (BOLT_R * math.cos(a), top + 0.007, BOLT_R * math.sin(a)), 'y', 0.0115, 0.007,
                 sides=HEX, uv_scale=0.2, caps=flange, phase=a)

    # the door: a welded frame standing proud of the shaft and a leaf let into it, on the front (+z) side
    y0, y1 = DOOR_SILL, DOOR_SILL + DOOR_H
    reach = shaft_radius(y0)            # the shaft's radius where the door is: it moves two thousandths over it
    front = reach + DOOR_FRAME
    half = DOOR_W / 2.0 + DOOR_FRAME
    back = reach - 0.040
    steel = {'*': 'flange'}
    for lo, hi in (((-half, y0 - DOOR_FRAME, back), (half, y0, front)),             # the sill
                   ((-half, y1, back), (half, y1 + DOOR_FRAME, front)),             # the head
                   ((-half, y0, back), (-DOOR_W / 2.0, y1, front)),                 # the jambs
                   ((DOOR_W / 2.0, y0, back), (half, y1, front))):
        clad_box(mesh, 'pole_base', lo, hi, steel, uv=tile_uv(lo, hi, 0.5))
    leaf_lo, leaf_hi = (-DOOR_W / 2.0, y0, back), (DOOR_W / 2.0, y1, front - 0.004)
    clad_box(mesh, 'pole_base', leaf_lo, leaf_hi, {'*': 'flange', 'south': 'door'},
             uv=dict(tile_uv(leaf_lo, leaf_hi, 0.5), south=(0.0, 0.0, 1.0, 1.0)))
    # three hinges down the left jamb, and the lever handle and its escutcheon on the right
    for y in (y0 + 0.03, (y0 + y1) / 2.0, y1 - 0.045):
        slab(mesh, 'pole_base', 'flange', (-DOOR_W / 2.0 - 0.004, y, front - 0.002),
             (-DOOR_W / 2.0 + 0.010, y + 0.018, front + 0.006), tile=0.2)
    slab(mesh, 'pole_base', 'flange', (0.030, (y0 + y1) / 2.0 - 0.012, front - 0.004),
         (0.046, (y0 + y1) / 2.0 + 0.012, front + 0.004), tile=0.2)
    slab(mesh, 'pole_base', 'flange', (0.016, (y0 + y1) / 2.0 - 0.004, front),
         (0.046, (y0 + y1) / 2.0 + 0.004, front + 0.012), tile=0.2)

    # the rating plate to the left of the door and the danger sign to the right, each flat on the shaft
    for x, tile_at in ((-0.150, DECAL_RATING), (0.150, DECAL_DANGER)):
        rise = math.sqrt(max(0.0, shaft_radius(0.25) ** 2 - x * x))
        lo, hi = (x - 0.026, 0.250 - 0.024, rise - 0.006), (x + 0.026, 0.250 + 0.024, rise + 0.004)
        clad_box(mesh, 'pole_base', lo, hi, {'*': 'flange', 'south': 'plate'},
                 uv=dict(tile_uv(lo, hi, 0.5), south=tile_at))


# ------------------------------------------------------------------ the nacelle

def section(z, half, floor, roof, upper, lower, k=5):
    """A rounded rectangle across the nacelle at z: clockwise from the top left corner, seen from the front."""
    ring = []
    for centre_x, centre_y, radius, start in ((half - upper, roof - upper, upper, 90.0),
                                              (half - lower, floor + lower, lower, 0.0),
                                              (-half + lower, floor + lower, lower, -90.0),
                                              (-half + upper, roof - upper, upper, 180.0)):
        for step in range(k + 1):
            a = math.radians(start - 90.0 * step / k)
            ring.append((centre_x + radius * math.cos(a), NACELLE_Y + centre_y + radius * math.sin(a), z))
    return ring


def hull_rings():
    return [section(z, half, floor, roof, upper, lower) for z, half, floor, roof, upper, lower in HULL]


def nacelle(mesh):
    """The canopy and what is built into or onto it: grilles, plates, the roof cooler, hatch, sensors, beacon."""
    paint = mesh.faces('motor', 'nacelle')
    belly = mesh.faces('motor', 'flange')

    # the underside is bare plate, not paint: whatever faces down is the flange material
    skin(mesh, lambda normal: belly if normal[1] < -0.95 else paint, hull_rings(), tile=0.8)
    rings = hull_rings()
    cap(mesh, paint, rings[0], (0.0, 0.0, -1.0), tile=0.8)
    cap(mesh, paint, rings[-1], (0.0, 0.0, 1.0), tile=0.8)

    steel = mesh.faces('motor', 'flange')
    # the main-bearing housing the hub bolts to, standing out of the front face
    cylinder(mesh, steel, (0.0, AXIS_Y, HULL_FRONT + 0.02), 'z', NOSE_R, 0.02, sides=ROUND, uv_scale=2.0,
             uv_along=0.1, caps=steel)
    # and the main shaft's flange out of it into the hub: the spinner stopped 0.02 short of the housing, so the
    # whole rotor hung in front of the nacelle on nothing.  An object of its own so the canopy's box (the
    # contract's motor) and the hub's (the pivot) stay what they were; turned round the rotor axis, it is the
    # same at every angle, so it need not turn
    shaft = mesh.faces('motor_shaft', 'flange')
    cylinder(mesh, shaft, (0.0, AXIS_Y, (SHAFT_FROM + SHAFT_TO) / 2.0), 'z', SHAFT_R, (SHAFT_TO - SHAFT_FROM) / 2.0,
             sides=ROUND, uv_scale=2.0, uv_along=0.1, caps=shaft)
    # the louvred intake grilles on each flank, framed, and a bank of them on the back face
    for side in (-1, 1):
        wall = side * HULL[2][1]
        near, far = (wall - 0.004, wall + 0.008) if side > 0 else (wall - 0.008, wall + 0.004)
        lo, hi = (min(near, far), NACELLE_Y + 0.15, -0.34), (max(near, far), NACELLE_Y + 0.44, 0.02)
        slab(mesh, 'motor', 'flange', lo, hi, tile=0.4, sides={('east' if side > 0 else 'west'): 'vent'})
        for bar_lo, bar_hi in (((lo[0], lo[1] - 0.014, lo[2] - 0.014), (hi[0], lo[1], hi[2] + 0.014)),
                               ((lo[0], hi[1], lo[2] - 0.014), (hi[0], hi[1] + 0.014, hi[2] + 0.014)),
                               ((lo[0], lo[1], lo[2] - 0.014), (hi[0], hi[1], lo[2])),
                               ((lo[0], lo[1], hi[2]), (hi[0], hi[1], hi[2] + 0.014))):
            bar_lo = (bar_lo[0] - 0.002 * side, bar_lo[1], bar_lo[2])
            bar_hi = (bar_hi[0] + 0.002 * side, bar_hi[1], bar_hi[2])
            slab(mesh, 'motor', 'flange', bar_lo, bar_hi, tile=0.3)
        # the manufacturer's plate, forward of the grille on the same flank
        plate_lo = (min(wall - 0.004, wall + 0.006), NACELLE_Y + 0.34, 0.16)
        plate_hi = (max(wall - 0.004, wall + 0.006), NACELLE_Y + 0.46, 0.28)
        face = 'east' if side > 0 else 'west'
        slab(mesh, 'motor', 'flange', plate_lo, plate_hi, tile=0.3, sides={face: 'plate'},
             windows={face: DECAL_LOGO})
    back = HULL[0][0]
    slab(mesh, 'motor', 'flange', (-0.11, NACELLE_Y + 0.15, back - 0.008), (0.11, NACELLE_Y + 0.38, back),
         tile=0.4, sides={'north': 'vent'})

    hatch(mesh)
    cooler(mesh)
    sensors(mesh)
    beacon(mesh)


def hatch(mesh):
    """The crane hatch in the roof: a raised cover on two hinges with a lifting handle."""
    top = ROOF_Y
    slab(mesh, 'motor_hatch', 'nacelle', (-0.095, top - 0.004, 0.04), (0.095, top + 0.016, 0.27), tile=0.4)
    for x in (-0.062, 0.062):
        slab(mesh, 'motor_hatch', 'flange', (x - 0.014, top - 0.002, 0.020), (x + 0.014, top + 0.022, 0.046),
             tile=0.2)
    slab(mesh, 'motor_hatch', 'flange', (-0.04, top + 0.016, 0.22), (0.04, top + 0.026, 0.236), tile=0.2)


def cooler(mesh):
    """The roof-mounted cooler: louvred on three sides, a rim round the deck and two fan stacks in it."""
    base = ROOF_Y - 0.020
    lo, hi = (-0.19, base, -0.82), (0.19, ROOF_Y + 0.160, -0.36)
    slab(mesh, 'motor_cooler', 'nacelle', lo, hi, tile=0.5,
         sides={'east': 'vent', 'west': 'vent', 'north': 'vent'})
    top = hi[1]
    # corner posts and the rim that stands round the deck, all clear of the louvres' own planes
    for x in (lo[0] - 0.006, hi[0] - 0.008):
        for z in (lo[2] - 0.006, hi[2] - 0.008):
            slab(mesh, 'motor_cooler', 'flange', (x, base, z), (x + 0.014, top, z + 0.014), tile=0.2)
    for rim_lo, rim_hi in (((lo[0], top, lo[2]), (hi[0], top + 0.012, lo[2] + 0.012)),
                           ((lo[0], top, hi[2] - 0.012), (hi[0], top + 0.012, hi[2])),
                           ((lo[0], top, lo[2]), (lo[0] + 0.012, top + 0.012, hi[2])),
                           ((hi[0] - 0.012, top, lo[2]), (hi[0], top + 0.012, hi[2]))):
        slab(mesh, 'motor_cooler', 'flange', rim_lo, rim_hi, tile=0.2)

    # two fan stacks: a collar of steel, turned as one piece, with the guard a little way down its throat
    wall = mesh.faces('motor_cooler', 'flange')
    guard = mesh.faces('motor_cooler', 'vent')
    for z in (-0.67, -0.49):
        lathe(mesh, wall, (0.0, top, z), [(0.072, 0.0), (0.072, 0.026), (0.062, 0.026), (0.062, 0.0),
                                          (0.072, 0.0)], sides=FITTING, uv_scale=1.0, uv_along=0.3)
        cylinder(mesh, guard, (0.0, top + 0.014, z), 'y', 0.0615, 0.002, sides=FITTING, uv_scale=1.0,
                 uv_along=0.1, caps=guard)


def sensors(mesh):
    """Cup anemometer and wind vane on two masts at the back of the cooler, and a lightning rod between."""
    top = ROOF_Y + 0.160 + 0.012
    steel = mesh.faces('motor_sensors', 'flange')
    plastic = mesh.faces('motor_sensors', 'sensor')
    z = -0.775
    for x, height in ((-0.115, 0.13), (0.115, 0.13), (0.0, 0.17)):
        cylinder(mesh, steel, (x, top + height / 2.0, z), 'y', 0.007, height / 2.0, sides=FITTING,
                 uv_scale=0.1, caps=steel)
    # a ball on the lightning rod
    dome(mesh, steel, (0.0, top + 0.17, z), 0.014, rings=4)

    # the anemometer: three arms and a cup on each, turned to the same hand
    cx, cy = -0.115, top + 0.13
    cylinder(mesh, plastic, (cx, cy + 0.006, z), 'y', 0.016, 0.006, sides=FITTING, uv_scale=0.4, caps=plastic)
    for k in range(3):
        a = math.radians(90.0 + 120.0 * k)
        dx, dz = math.cos(a), math.sin(a)
        reach = 0.052
        tube(mesh, plastic, [(cx + dx * 0.014, cy + 0.007, z + dz * 0.014),
                             (cx + dx * reach, cy + 0.007, z + dz * reach)], 0.0035, sides=6, caps=plastic)
        cylinder(mesh, plastic, (cx + dx * (reach + 0.008), cy + 0.007, z + dz * (reach + 0.008)), 'y', 0.015,
                 0.016, sides=FITTING, uv_scale=0.5, caps=plastic)

    # the vane: a counterweight on the nose, a tail with a fin and a boss on the mast
    vx, vy = 0.115, top + 0.13
    cylinder(mesh, plastic, (vx, vy + 0.010, z + 0.000), 'y', 0.014, 0.006, sides=FITTING, uv_scale=0.4,
             caps=plastic)
    box(mesh, plastic, (vx - 0.0025, vy + 0.004, z - 0.070), (vx + 0.0025, vy + 0.030, z + 0.004), uv_scale=0.2)
    box(mesh, plastic, (vx - 0.0025, vy + 0.016, z - 0.070), (vx + 0.0025, vy + 0.044, z - 0.040), uv_scale=0.2)
    cylinder(mesh, plastic, (vx, vy + 0.012, z + 0.040), 'z', 0.008, 0.032, sides=FITTING, uv_scale=0.3,
             caps=plastic)


def beacon(mesh):
    """The aviation warning light, on the roof ahead of the hatch."""
    steel = mesh.faces('motor_beacon', 'flange')
    lens = mesh.faces('motor_beacon', 'beacon')
    cylinder(mesh, steel, (0.0, ROOF_Y + 0.008, 0.40), 'y', 0.034, 0.008, sides=FITTING, uv_scale=0.4, caps=steel)
    cylinder(mesh, lens, (0.0, ROOF_Y + 0.0235, 0.40), 'y', 0.026, 0.0075, sides=FITTING, uv_scale=1.0,
             caps=lens)
    dome(mesh, lens, (0.0, ROOF_Y + 0.031, 0.40), 0.026)


# ------------------------------------------------------------------ the hub

def spinner(mesh):
    """The spinner: a bullet of glass-fibre, a solid of revolution about the rotor axis (the contract's pivot)."""
    faces = mesh.faces('rotate_2', 'hub')
    profile = [(0.0, SPIN_Z0), (0.215, SPIN_Z0)]
    corner = 0.055
    for step in range(1, 7):                     # the rolled edge round the back
        a = math.radians(-90.0 + 90.0 * step / 6.0)
        profile.append((SPIN_R - corner + corner * math.cos(a), SPIN_Z0 + corner + corner * math.sin(a)))
    profile.append((SPIN_R, SPIN_Z1))
    steps = 22
    nose = SPIN_TIP - SPIN_Z1
    for i in range(1, steps + 1):
        t = i / steps
        # an ellipse blended with a cone: round at the very tip, ogive over the rest
        r = SPIN_R * (math.sqrt(max(0.0, 1.0 - t * t)) * 0.55 + (1.0 - t) * 0.45)
        profile.append((r, SPIN_Z1 + nose * t))
    lathe_z(mesh, faces, (0.0, AXIS_Y, 0.0), profile, sides=ROUND, uv_scale=1.0, uv_along=1.0)


# ------------------------------------------------------------------ the blades

def _tables():
    radii = [row[0] for row in BLADE_TABLE]
    return (pchip(radii, [row[1] for row in BLADE_TABLE]),
            pchip(radii, [row[2] for row in BLADE_TABLE]),
            pchip(radii, [row[3] for row in BLADE_TABLE]))


CHORD, THICKNESS, TWIST = _tables()


def blend(r):
    """0 where the blade is still a round root, 1 where it is a full airfoil."""
    return smoothstep((r - 0.35) / 1.50)


def camber_at(xi, m):
    if xi < CAMBER_AT:
        return m / CAMBER_AT ** 2 * (2.0 * CAMBER_AT * xi - xi * xi)
    return m / (1.0 - CAMBER_AT) ** 2 * ((1.0 - 2.0 * CAMBER_AT) + 2.0 * CAMBER_AT * xi - xi * xi)


def airfoil(r):
    """One section as (chord fraction, height in chords) points: trailing edge, round the suction side to the
    leading edge, and back along the pressure side.  A NACA four-digit thickness over a camber line, blended
    into a circle of one chord's diameter towards the root.

    u runs 0 at the trailing edge, 0.5 at the leading edge and 1 back at the trailing edge, so the leading
    edge sits at the same u at every station and the texture can put the erosion shield there.
    """
    b = blend(r)
    m = CAMBER * smoothstep((r - 0.9) / 1.2)
    t = THICKNESS(r)
    upper, lower = [], []
    for j in range(AIRFOIL_HALF + 1):
        theta = math.pi * j / AIRFOIL_HALF
        xi = (1.0 + math.cos(theta)) / 2.0
        aero = 5.0 * t * (0.2969 * math.sqrt(xi) - 0.1260 * xi - 0.3516 * xi ** 2 + 0.2843 * xi ** 3
                          - 0.1015 * xi ** 4)
        round_root = math.sqrt(max(0.0, xi * (1.0 - xi)))
        half = (1.0 - b) * round_root + b * aero
        centre = b * camber_at(xi, m)
        upper.append((xi, centre + half))
        lower.append((xi, centre - half))

    return upper + list(reversed(lower[:-1]))


def blade_frame(phi):
    """The unit vectors a blade at angle phi is built on: along it, the way it moves, and forwards.

    Clockwise seen from the front means that at the top a blade moves towards +x: the leading edge faces
    that way.  Forwards leans back by the precone, so a section stays square to the blade.
    """
    p = math.radians(phi)
    radial = (math.cos(p), math.sin(p), 0.0)
    moving = (math.sin(p), -math.cos(p), 0.0)
    axis = add(mul(radial, math.cos(CONE)), (0.0, 0.0, math.sin(CONE)))
    forward = add(mul(radial, -math.sin(CONE)), (0.0, 0.0, math.cos(CONE)))
    return axis, moving, forward


def blade_ring(phi, r, span):
    """The points of one section in space, and each one's (u, v)."""
    axis, moving, forward = blade_frame(phi)
    root = (0.0, AXIS_Y, BLADE_Z)
    bend = PREBEND * max(0.0, (r - 1.0) / (ROTOR_R - 1.0)) ** 2
    pitch_axis = add(add(root, mul(axis, r)), mul(forward, bend))
    chord = CHORD(r)
    twist = math.radians(TWIST(r))
    b = blend(r)
    held = 0.5 + (0.30 - 0.5) * b          # where along the chord the blade's own axis passes
    along = mul(add(mul(moving, math.cos(twist)), mul(forward, math.sin(twist))), -1.0)
    lift = sub(mul(moving, math.sin(twist)), mul(forward, math.cos(twist)))

    outline = airfoil(r)
    points = [add(pitch_axis, add(mul(along, (xi - held) * chord), mul(lift, eta * chord)))
              for xi, eta in outline]

    # u from the arc length of each surface, so the leading edge is always at one half
    u = []
    half = AIRFOIL_HALF
    lengths = [0.0]
    for i in range(1, len(outline)):
        lengths.append(lengths[-1] + math.dist(outline[i], outline[i - 1]))
    upper, lower = lengths[half], lengths[-1] - lengths[half]
    for i in range(len(outline)):
        u.append(0.5 * lengths[i] / upper if i <= half else 0.5 + 0.5 * (lengths[i] - upper) / lower)
    # texture row zero is the top: v is 1 at the tip and 0 at the root
    return points, u, (r - ROOT_R0) / (span - ROOT_R0)


def blade(mesh, phi, span):
    """One blade, lofted station by station from the root to the tip, with both ends capped."""
    faces = mesh.faces('rotate_1', 'blade')
    rings = [blade_ring(phi, r, span) for r in BLADE_STATIONS[:-1] + (span,)]
    columns = len(rings[0][0])

    for k in range(len(rings) - 1):
        (a, ua, va), (b, ub, vb) = rings[k], rings[k + 1]
        centre = mean(a + b)
        for i in range(columns):
            j = (i + 1) % columns
            corners = (a[i], a[j], b[j], b[i])
            normal = unit(cross(sub(corners[1], corners[0]), sub(corners[3], corners[0])))
            if dot(normal, sub(mean(corners), centre)) < 0.0:
                normal = mul(normal, -1.0)
            # the closing quad across the trailing edge takes one column of the texture, not all of it
            u0, u1 = ua[i], (ua[j] if j else 1.0)
            w0, w1 = ub[i], (ub[j] if j else 1.0)
            mesh.quad(faces, corners, normal, ((u0, va), (u1, va), (w1, vb), (w0, vb)))

    for ring, direction in ((rings[0], -1.0), (rings[-1], 1.0)):
        points, u, v = ring
        axis = blade_frame(phi)[0]
        normal = mul(axis, direction)
        centre = mean(points)
        for i in range(columns):
            j = (i + 1) % columns
            mesh.quad(faces, (centre, points[i], points[j]), normal,
                      [(0.5, v), (u[i], v), (u[j] if j else 1.0, v)])

    # the pitch bearing's seal: a rubber boot round the root where it leaves the spinner
    seal = mesh.faces('rotate_1', 'seal')
    axis, moving, forward = blade_frame(phi)
    root = (0.0, AXIS_Y, BLADE_Z)
    ring_tube(mesh, seal, add(root, mul(axis, SEAL_FROM)), add(root, mul(axis, SEAL_TO)), SEAL_R, moving,
              sides=ROUND)


def ring_tube(mesh, faces, start, end, radius, reference, sides=ROUND):
    """A round tube between two points whose first vertex is always along ``reference``.

    modellib.tube picks its own reference, so the same ring on three blades is turned a different way on
    each and the three are not one part three times; this takes it from the blade's own frame instead.
    """
    along = unit(sub(end, start))
    one = unit(sub(reference, mul(along, dot(reference, along))))
    two = cross(along, one)
    for i in range(sides):
        j = (i + 1) % sides
        a0, a1 = 2.0 * math.pi * i / sides, 2.0 * math.pi * j / sides
        out0 = add(mul(one, math.cos(a0)), mul(two, math.sin(a0)))
        out1 = add(mul(one, math.cos(a1)), mul(two, math.sin(a1)))
        corners = (add(start, mul(out0, radius)), add(start, mul(out1, radius)),
                   add(end, mul(out1, radius)), add(end, mul(out0, radius)))
        mid = unit(add(out0, out1))
        u0, u1 = i / sides * 3.0, (i + 1) / sides * 3.0
        mesh.quad(faces, corners, mid, ((u0, 0.0), (u1, 0.0), (u1, 0.5), (u0, 0.5)))
        for ring, direction in ((0, -1.0), (1, 1.0)):
            point = start if ring == 0 else end
            mesh.quad(faces, (point, add(point, mul(out0, radius)), add(point, mul(out1, radius))),
                      mul(along, direction), [(0.5, 0.5), (0.5 + 0.5 * math.cos(a0), 0.5 + 0.5 * math.sin(a0)),
                                              (0.5 + 0.5 * math.cos(a1), 0.5 + 0.5 * math.sin(a1))])


def tip_span():
    """The blade's length along its own axis, which puts the farthest vertex exactly ROTOR_R out in the plane."""
    span = ROTOR_R / math.cos(CONE)
    for _ in range(8):
        points = []
        for r in BLADE_STATIONS[:-1] + (span,):
            if r == span:
                points += blade_ring(90.0, r, span)[0]
        reach = max(math.hypot(p[0], p[1] - AXIS_Y) for p in points)
        span += ROTOR_R - reach
    return span


def blades(mesh):
    span = tip_span()
    for phi in BLADE_ANGLES:
        blade(mesh, phi, span)


# ------------------------------------------------------------------ the insulator

def insulator(mesh):
    """The wire's fitting: a pin insulator on a galvanised post, on a plate of its own beside the tower foot.

    Never scaled and never turned by the nacelle's yaw: it stays where it was authored.  Only the
    porcelain is a fitting; the post and plate are steel in the same object.
    """
    porcelain = mesh.faces('insulator', 'insulator_porcelain')
    steel = mesh.faces('insulator', 'insulator_steel')
    x, z = INSULATOR_AT
    cylinder(mesh, steel, (x, PLATE_H / 2.0, z), 'y', PLATE_R, PLATE_H / 2.0, sides=FITTING, uv_scale=0.6,
             caps=steel)
    spindle_foot = INSULATOR_BASE_Y - 0.34 * INSULATOR_D * 0.78
    cylinder(mesh, steel, (x, (PLATE_H + spindle_foot) / 2.0, z), 'y', POST_R, (spindle_foot - PLATE_H) / 2.0,
             sides=FITTING, uv_scale=0.3, caps=steel)
    for k in range(4):
        a = math.radians(45.0 + 90.0 * k)
        cylinder(mesh, steel, (x + 0.072 * math.cos(a), PLATE_H + 0.006, z + 0.072 * math.sin(a)), 'y', 0.012,
                 0.006, sides=HEX, uv_scale=0.2, caps=steel, phase=a)
    pin_insulator(mesh, porcelain, steel, (x, INSULATOR_BASE_Y, z), INSULATOR_D)

    # The cable's way from the tower to the post: a precast trough at grade from under the post's plate to the
    # tower's axis.  Never scaled, like the insulator, so whatever size the renderer draws the foot at, the trough
    # runs into its plinth: on the C130 it is all but buried in it, on the SW-10 it is what ties a post 0.53 from
    # the axis to a tower 0.085 across.  DUCT_H keeps its top off every machine's plinth and flange top (0.045 and
    # 0.075 times the nacelle scale), so no size of the six puts two faces in one plane.
    duct = mesh.faces('insulator_duct', 'concrete')
    reach = math.hypot(x, z)
    along = (x / reach, 0.0, z / reach)
    frame_box(mesh, duct, (0.0, 0.0, 0.0), (along, (0.0, 1.0, 0.0), (-along[2], 0.0, along[0])),
              (0.0, 0.0, -DUCT_W / 2.0), (reach - PLATE_R + 0.02, DUCT_H, DUCT_W / 2.0), uv_scale=2.0)


# ------------------------------------------------------------------ the whole

def pivot(mesh):
    """A zero-size marker at the hub's centre, the way every other mod model marks a hinge."""
    mesh.marker(mesh.faces('pivot_rotor', 'flange'), (0.0, AXIS_Y, HUB_Z))


def turbine():
    mesh = Mesh()
    tower(mesh)
    foot(mesh)
    nacelle(mesh)
    spinner(mesh)
    blades(mesh)
    insulator(mesh)
    pivot(mesh)
    return mesh


# ------------------------------------------------------------------ what the contract reads off the mesh

def corners(mesh, prefix, material=None, exact=False):
    """Every vertex a group's faces use, for the objects whose name starts with ``prefix`` (or is it)."""
    out = []
    for name, mat, faces in mesh.objects:
        if (name == prefix if exact else name.startswith(prefix)) and (material is None or mat == material):
            for face in faces:
                out += [mesh.v[index[0] - 1] for index in face]
    return out


def box_of(points):
    return (tuple(min(p[i] for p in points) for i in range(3)), tuple(max(p[i] for p in points) for i in range(3)))


def centre_of(points):
    lo, hi = box_of(points)
    return tuple((lo[i] + hi[i]) / 2.0 for i in range(3))


def contract(mesh):
    """The figures docs/turbine-model.md quotes, measured off the mesh and not typed."""
    spin_lo, spin_hi = box_of(corners(mesh, 'rotate_2'))
    hub = centre_of(corners(mesh, 'rotate_2'))
    motor_lo, motor_hi = box_of(corners(mesh, 'motor', exact=True))
    every = [p for name, _, faces in mesh.objects if name.startswith('motor') for f in faces for p in
             [mesh.v[i[0] - 1] for i in f]]
    pole = corners(mesh, 'pole', exact=True)
    blade_points = corners(mesh, 'rotate_1')
    porcelain = corners(mesh, 'insulator', 'insulator_porcelain')
    return {
        'hub': hub,
        'spinner': (spin_lo, spin_hi),
        'rotor_radius': max(math.hypot(p[0] - hub[0], p[1] - hub[1]) for p in blade_points),
        'rotor_radius_3d': max(math.dist(p, hub) for p in blade_points),
        'blades_box': box_of(blade_points),
        'motor': (motor_lo, motor_hi),
        'nacelle_floor': box_of(every)[0][1],
        'nacelle_all': box_of(every),
        'pole_height': box_of(pole)[1][1] - box_of(pole)[0][1] if pole else 0.0,
        'pole_box': box_of(pole),
        'porcelain': box_of(porcelain),
        'porcelain_centre': centre_of(porcelain),
        'far_corner': max(math.hypot(p[0], p[2]) for p in corners(mesh, 'motor', exact=True)),
    }


# ------------------------------------------------------------------ the renderer's transform, for the proofs

def hub_and_floor(path):
    """The two numbers the renderer reads off the model: the spinner's centre, and the nacelle's lowest point."""
    import objlib
    faces = objlib.read(path)
    spin = [p for f in faces if f.group.startswith('rotate_2') for p in f.points]
    floor = min(p[1] for f in faces if f.group.startswith('motor') for p in f.points)
    return centre_of(spin), floor


def group_matrix(group, n, rotor_scale, nacelle_scale, hub, floor, rotor=0.0, yaw=0.0):
    """The point mapper the renderer applies to one group (models.md 8.3, with the clean-room fixes).

    ``n`` tower segments.  pole is stretched to n blocks and scaled radially by the nacelle scale; pole_base
    scaled uniformly; insulator left alone; nacelle, blades and hub carried to the top of the tower and,
    unlike the old renderer, yawed about the tower axis - the foot never is.  ``rotor`` is degrees clockwise
    seen from the front.
    """
    def turn(point, degrees, about=(0.0, 0.0, 0.0), axis='y'):
        a = math.radians(degrees)
        x, y, z = sub(point, about)
        if axis == 'y':
            x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
        else:
            x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
        return add((x, y, z), about)

    top = n + nacelle_scale * (hub[1] - floor)
    hub_at = (0.0, top, nacelle_scale * hub[2])

    if group.startswith('pole_base'):
        return lambda p: mul(p, nacelle_scale)
    if group.startswith('pole'):
        return lambda p: (p[0] * nacelle_scale, p[1] * n / H_POLE, p[2] * nacelle_scale)
    if group.startswith('insulator'):
        return lambda p: p
    if group.startswith('rotate_1') or group.startswith('rotate_2'):
        scale = rotor_scale if group.startswith('rotate_1') else nacelle_scale
        return lambda p: turn(add(turn(add(mul(sub(p, hub), scale), hub_at), -rotor, hub_at, 'z'), (0, 0, 0)),
                              yaw)
    return lambda p: turn((p[0] * nacelle_scale, n + (p[1] - floor) * nacelle_scale, p[2] * nacelle_scale), yaw)


# ------------------------------------------------------------------ the hitboxes

# How many nested boxes stand in for the cone's round section.  Each is a rectangle with its corners on the
# circle, and its quarter-turned twin; the union is a staircase that never leaves the circle, and the circle
# 0.90 times as wide is always inside it.
TOWER_BOXES = (10.0, 20.0, 30.0, 40.0)
TOWER_FILL = 0.90


def tower_radius(index, n, scale):
    """The shaft's drawn radius at the middle of tower block ``index`` (0 is the foot) of an n-block tower."""
    return scale * shaft_radius((index + 0.5) * H_POLE / n)


def tower_boxes(radius):
    """One tower block's section as (half width x, half width z) pairs about the axis, in blocks."""
    out = []
    for degrees in TOWER_BOXES:
        a = math.radians(degrees)
        for pair in ((radius * math.cos(a), radius * math.sin(a)), (radius * math.sin(a), radius * math.cos(a))):
            if not any(math.dist(pair, other) < 1e-9 for other in out):
                out.append(pair)
    return out


def cell_box(lo, hi, scale):
    """An authored box (x, z about the tower axis, y up from the nacelle's floor) as the block's own shape.

    Scaled by the nacelle scale and cut to the cell, in blocks from the cell's corner: the turbine block is one
    cell and the part of the nacelle outside it collides with nothing.
    """
    clamp = lambda v: max(0.0, min(1.0, v))                                    # noqa: E731
    return ((clamp(0.5 + lo[0] * scale), clamp(lo[1] * scale), clamp(0.5 + lo[2] * scale)),
            (clamp(0.5 + hi[0] * scale), clamp(hi[1] * scale), clamp(0.5 + hi[2] * scale)))


def pixels(box):
    """A cell box as Block.box() takes it: sixteenths, x0 y0 z0 x1 y1 z1."""
    return 'Block.box(%s)' % ', '.join('%.2f' % (16.0 * v) for v in (box[0][0], box[0][1], box[0][2],
                                                                    box[1][0], box[1][1], box[1][2]))


def hitboxes(mesh):
    """The figures the Java declares, measured off the mesh: the nacelle and cooler boxes, the insulator's."""
    floor = box_of(corners(mesh, 'motor', exact=True))[0][1]
    nacelle = box_of(corners(mesh, 'motor', exact=True))
    cooler = box_of(corners(mesh, 'motor_cooler', exact=True))
    insulator = box_of(corners(mesh, 'insulator', exact=True))
    return {
        'nacelle': nacelle,
        'cooler': cooler,
        'floor': floor,
        'insulator': insulator,
    }


def java(mesh):
    """The hitbox tables, as the Java would declare them."""
    shapes = hitboxes(mesh)
    floor = shapes['floor']

    def authored(box):
        return ((box[0][0], box[0][1] - floor, box[0][2]), (box[1][0], box[1][1] - floor, box[1][2]))

    nacelle, cooler = authored(shapes['nacelle']), authored(shapes['cooler'])
    print('// the tower: a cone, drawn %.3f across at the foot and %.3f at the head, %.1f blocks authored high'
          % (2 * R_BASE, 2 * R_TOP, H_POLE))
    print('static final double TOWER_BASE_RADIUS = %.3f, TOWER_TOP_RADIUS = %.3f, TOWER_AUTHORED_HEIGHT = %.1f;'
          % (R_BASE, R_TOP, H_POLE))
    print('static final double[] TOWER_BOX_ANGLES = {%s};   // degrees: each is a rectangle and its quarter turn'
          % ', '.join('%.0f' % a for a in TOWER_BOXES))
    print('// block i of n, scale s: radius = s * (BASE + (TOP - BASE) * (i + 0.5) / n), then for each angle a the\n'
          '// boxes (r cos a, r sin a) and (r sin a, r cos a) about the axis, the block\'s full height\n')
    print('// the foot block under a turbine also holds the insulator, one box, never scaled:')
    insulator = shapes['insulator']
    print('static final VoxelShape INSULATOR = %s;'
          % pixels(((0.5 + insulator[0][0], insulator[0][1], 0.5 + insulator[0][2]),
                    (0.5 + insulator[1][0], insulator[1][1], 0.5 + insulator[1][2]))))
    print('\n// the turbine block: the canopy and the cooler, scaled by the nacelle scale and cut to the cell')
    for ident, diameter, exaggeration, low, high in CATALOGUE:
        _, scale = scales(diameter, exaggeration, small=ident == 'sw_10')
        print('//   %-8s scale %.4f' % (ident, scale))
        print('static final VoxelShape %s_NACELLE = Shapes.or(%s,\n\t\t%s);'
              % (ident.upper(), pixels(cell_box(nacelle[0], nacelle[1], scale)),
                 pixels(cell_box(cooler[0], cooler[1], scale))))


# ------------------------------------------------------------------ output

def main():
    mesh = turbine()
    if '--java' in sys.argv:
        java(mesh)
        return
    if '--contract' in sys.argv:
        for key, value in contract(mesh).items():
            print('%-16s %s' % (key, value))
        return

    directory = os.path.join(OUT, NAME)
    # one normal a corner on the curved surfaces: the blades, the spinner, the shaft were lit as flat strips
    mesh.smooth()
    mesh.write(os.path.join(directory, NAME + '.obj'), NAME + '.mtl', 'gen_turbine_models.py')
    write_mtl(os.path.join(directory, NAME + '.mtl'), mesh.materials(), MATERIALS, 'gen_turbine_models.py')
    vertices, faces = mesh.stats()
    groups = ['%s_%s' % (o, m) for o, m, f in mesh.objects if f]
    print('%-14s %5d vertices, %5d faces, %2d groups' % (NAME, vertices, faces, len(groups)))
    print('    ' + ', '.join(groups))


if __name__ == '__main__':
    main()
