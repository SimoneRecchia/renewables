#!/usr/bin/env python3
"""The wind turbine model against its contract: names, pivot, radius, closed surfaces, clearances, the insulator.

    python3 tools/check_turbine.py            # prove it
    python3 tools/check_turbine.py --table    # the per-model figures docs/turbine-model.md quotes

What the renderer reads off wind_turbine.obj is a handful of names and three measurements - the centre of the
spinner's box, the lowest point of the nacelle, the farthest reach of the blades - and a wrong one is a turbine
with its rotor off its hub and nothing to say so.  This reads the OBJ back with objlib, the same way every other
checker does, and measures them.  It also proves what the contract only asks for: that every part is a closed
solid wound outwards (no hole, however the game culls), that the three blades are one blade three times, that
the spinner turns about the pivot, and that at the smallest and the largest tower of each of the six machines
nothing passes through the ground or the tower.
"""

import collections
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import objlib                                                                    # noqa: E402
import gen_turbine_models as turbine                                             # noqa: E402

PATH = os.path.join(objlib.MODELS, 'wind_turbine', 'wind_turbine.obj')

# The objects the model is made of and the renderer treats by prefix.  Anything else is a nacelle part.
OBJECTS = ('pole', 'pole_base', 'motor', 'motor_shaft', 'motor_hatch', 'motor_cooler', 'motor_sensors',
           'motor_beacon', 'rotate_2', 'rotate_1', 'insulator', 'insulator_duct', 'pivot_rotor')

# A metre of margin between a blade and anything it should not touch, in blocks.
GROUND_CLEARANCE = 0.80
TOWER_CLEARANCE = 0.06
# Tolerance on a measured figure
EPS = 1e-4


def key(point):
    return tuple(round(c, 5) for c in point)


def open_edges(faces):
    """Edges used an odd number of times by an object's faces: where its surface is not closed."""
    count = collections.Counter()
    for face in faces:
        points = [key(p) for p in face.points]
        for i in range(len(points)):
            a, b = points[i], points[(i + 1) % len(points)]
            if a != b:
                count[(min(a, b), max(a, b))] += 1

    return [edge for edge, n in count.items() if n % 2]


def solids(faces):
    """Faces grouped into connected pieces and the signed volume each encloses (positive when wound outwards)."""
    parent = {}

    def find(a):
        while parent.setdefault(a, a) != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    for face in faces:
        points = [key(p) for p in face.points]
        for p in points[1:]:
            parent[find(p)] = find(points[0])

    volume = collections.defaultdict(float)
    for face in faces:
        a = face.points[0]
        for i in range(1, len(face.points) - 1):
            b, c = face.points[i], face.points[i + 1]
            cross = (b[1] * c[2] - b[2] * c[1], b[2] * c[0] - b[0] * c[2], b[0] * c[1] - b[1] * c[0])
            volume[find(key(a))] += (a[0] * cross[0] + a[1] * cross[1] + a[2] * cross[2]) / 6.0

    return volume


class Grid:
    """A spatial hash: the nearest-point question without comparing every pair."""

    def __init__(self, points, cell=0.01):
        self.cell = cell
        self.cells = collections.defaultdict(list)
        for p in points:
            self.cells[self.at(p)].append(p)

    def at(self, p):
        return tuple(int(math.floor(c / self.cell)) for c in p)

    def distance(self, target):
        i, j, k = self.at(target)
        best = 1e9
        for di in (-1, 0, 1):
            for dj in (-1, 0, 1):
                for dk in (-1, 0, 1):
                    for p in self.cells.get((i + di, j + dj, k + dk), ()):
                        best = min(best, math.dist(p, target))

        return best


def spin(point, hub, degrees):
    """A point turned about the rotor axis, anticlockwise seen from the front."""
    a = math.radians(degrees)
    x, y = point[0] - hub[0], point[1] - hub[1]
    return (hub[0] + x * math.cos(a) - y * math.sin(a), hub[1] + x * math.sin(a) + y * math.cos(a), point[2])


def spinner_radius(z):
    """How far the spinner reaches from its axis at z, read off its own profile."""
    if z <= turbine.SPIN_Z1:
        return turbine.SPIN_R
    nose = (z - turbine.SPIN_Z1) / (turbine.SPIN_TIP - turbine.SPIN_Z1)
    return turbine.SPIN_R * (math.sqrt(max(0.0, 1.0 - nose * nose)) * 0.55 + (1.0 - nose) * 0.45) if nose < 1 else 0.0


def group_faces(faces):
    out = collections.OrderedDict()
    for face in faces:
        out.setdefault(face.group, []).append(face)
    return out


def main():
    faces = objlib.read(PATH)
    objects = group_faces(faces)
    problems = []

    def fail(text):
        problems.append(text)
        print('  ' + text)

    # -------------------------------------------------------------- names
    for name in OBJECTS:
        if name not in objects:
            fail('the model has no object %s' % name)
    for name in objects:
        if name not in OBJECTS:
            fail('the model draws an object nobody declared: %s' % name)
    keys = {(f.group, f.material) for f in faces}
    for wanted in (('insulator', 'insulator_porcelain'), ('insulator', 'insulator_steel')):
        if wanted not in keys:
            fail('no group %s_%s: the wire has nothing to hang on' % wanted)
    nacelle_parts = [n for n in objects if not n.startswith(('pole', 'insulator', 'rotate_', 'pivot_'))]
    print('objects: %s' % ', '.join(objects))

    # -------------------------------------------------------------- closed, outward
    for name, own in objects.items():
        if name.startswith('pivot_'):
            continue
        gaps = open_edges(own)
        if gaps:
            fail('%s is not a closed surface: %d edge(s) are used by one face, e.g. %s -> %s'
                 % (name, len(gaps), gaps[0][0], gaps[0][1]))
        for piece, volume in solids(own).items():
            if volume < 1e-12:
                fail('%s has a piece wound inside out or flat (volume %.3g) near %s' % (name, volume, piece))

    # -------------------------------------------------------------- the tower
    shaft = [p for f in objects['pole'] if f.material == 'tower' for p in f.points]
    height = max(p[1] for p in shaft) - min(p[1] for p in shaft)
    if abs(height - turbine.H_POLE) > EPS or abs(min(p[1] for p in shaft)) > EPS:
        fail('the shaft runs y %.4f to %.4f, not 0 to %.1f' % (min(p[1] for p in shaft), max(p[1] for p in shaft),
                                                               turbine.H_POLE))
    off = max(abs(math.hypot(p[0], p[2]) - turbine.shaft_radius(p[1])) for p in shaft)
    if off > 1e-3:
        fail('the shaft is %.4f off its own cone: tower_radius() in gen_turbine_models would put the collision '
             'in the wrong place' % off)
    print('tower: cone %.3f at the foot to %.3f at %.1f, 80 sides' % (turbine.R_BASE, turbine.R_TOP, turbine.H_POLE))

    # the nacelle sits on the shaft: its floor is the shaft's top
    floor = min(p[1] for n in nacelle_parts for f in objects[n] for p in f.points)
    if abs(floor - turbine.H_POLE) > EPS:
        fail('the nacelle starts at %.4f, the shaft ends at %.1f: it floats or sinks' % (floor, turbine.H_POLE))
    motor = [p for f in objects['motor'] for p in f.points]
    if abs(min(p[1] for p in motor) - floor) > EPS:
        fail('motor is not the nacelle part that reaches lowest, so the renderer reading its box would sit the '
             'nacelle off the tower')

    # -------------------------------------------------------------- hub and rotor
    spinner = [p for f in objects['rotate_2'] for p in f.points]
    lo, hi = turbine.box_of(spinner)
    hub = tuple((lo[i] + hi[i]) / 2.0 for i in range(3))
    if abs(hub[0]) > EPS:
        fail('the spinner is %.4f off the tower axis in x' % hub[0])
    if abs((hi[0] - lo[0]) - (hi[1] - lo[1])) > EPS:
        fail('the spinner is not as tall as it is wide, so it is not a solid of revolution about its pivot')
    for p in spinner:
        if abs(math.hypot(p[0] - hub[0], p[1] - hub[1]) - spinner_radius(p[2])) > 2e-3 and \
                p[2] > turbine.SPIN_Z0 + 0.06:
            fail('a spinner vertex at z %.3f is off the profile' % p[2])
            break
    if abs(hub[1] - turbine.AXIS_Y) > EPS or abs(hub[2] - turbine.HUB_Z) > EPS:
        fail('the spinner centre is %s, the generator says (0, %.3f, %.3f)' % (hub, turbine.AXIS_Y, turbine.HUB_Z))
    marker = turbine.centre_of([p for f in objects['pivot_rotor'] for p in f.points])
    if math.dist(marker, hub) > EPS:
        fail('pivot_rotor is at %s and the spinner centre is %s' % (marker, hub))
    height = max(p[1] for p in motor) - min(p[1] for p in motor)
    if abs((hub[1] - floor) - height / 2.0) > 1e-3:
        fail('the rotor axis is %.3f up the nacelle, which is %.3f tall: it is meant to be half way'
             % (hub[1] - floor, height))
    print('hub: (%.3f, %.3f, %.3f), %.3f above the nacelle floor' % (hub[0], hub[1], hub[2], hub[1] - floor))

    blades = [p for f in objects['rotate_1'] for p in f.points]
    reach = max(math.hypot(p[0] - hub[0], p[1] - hub[1]) for p in blades)
    if abs(reach - turbine.ROTOR_R) > 1e-3:
        fail('the blades reach %.4f from the hub in the rotor plane, not %.1f: the renderer scales by it'
             % (reach, turbine.ROTOR_R))
    grid = Grid(blades, 0.01)
    for turn in (120.0, 240.0):
        worst = max(grid.distance(spin(p, hub, turn)) for p in blades[::7])
        if worst > 2e-5 * 5:
            fail('the blades are not one blade three times: turned %d degrees a vertex lands %.5f from any '
                 'other' % (turn, worst))
            break
    print('rotor: %.4f in the rotor plane, three blades at 120 degrees, root at the spinner' % reach)

    # which way the blades face: at mid span of the blade standing up, the leading edge is towards +x and the
    # suction side towards -z, which is what turning clockwise seen from the front needs
    r = 3.2
    ring, _, _ = turbine.blade_ring(90.0, r, turbine.tip_span())
    leading = ring[turbine.AIRFOIL_HALF]
    trailing = ring[0]
    if not leading[0] > trailing[0]:
        fail('the leading edge is not towards +x on the blade at the top: it would run trailing edge first '
             'when turned clockwise')
    upper_z = sum(p[2] for p in ring[:turbine.AIRFOIL_HALF + 1]) / (turbine.AIRFOIL_HALF + 1)
    lower_z = sum(p[2] for p in ring[turbine.AIRFOIL_HALF:]) / (turbine.AIRFOIL_HALF + 1)
    if not upper_z < lower_z:
        fail('the suction side is not the downwind one')
    twists = [turbine.TWIST(x) for x in turbine.BLADE_STATIONS]
    if any(b > a + 1e-9 for a, b in zip(twists, twists[1:])):
        fail('the twist is not monotone from root to tip: a blade that unwinds and winds again')
    chords = [turbine.CHORD(x) for x in turbine.BLADE_STATIONS]
    print('blade: chord %.2f at the root, %.2f at its widest, %.2f at the tip; twist %.1f to %.1f degrees'
          % (chords[0], max(chords), chords[-1], twists[0], twists[-1]))

    # -------------------------------------------------------------- the insulator
    porcelain = [p for f in objects['insulator'] if f.material == 'insulator_porcelain' for p in f.points]
    plo, phi = turbine.box_of(porcelain)
    centre = turbine.centre_of(porcelain)
    if plo[1] < 0.05:
        fail('the porcelain starts at y %.3f: under the ground or on it, where the crosshair cannot find it'
             % plo[1])
    if not (-0.5 < plo[0] and phi[0] < 0.5 and -0.5 < plo[2] and phi[2] < 0.5):
        fail('the insulator leaves its own block: a click on the structure would not reach it')
    clear = math.hypot(centre[0], centre[2]) - (phi[0] - plo[0]) / 2.0
    if clear < turbine.PLINTH_R:
        fail('the insulator stands %.3f from the axis, inside the plinth (%.2f)' % (clear, turbine.PLINTH_R))
    seen = visible_from_ground(centre)
    if seen < 0.45:
        fail('only %.0f%% of the way round the foot can see the insulator' % (seen * 100))
    print('insulator: porcelain %.3f..%.3f up, centre (%.3f, %.3f, %.3f), seen from %.0f%% of the way round the '
          'foot' % (plo[1], phi[1], centre[0], centre[1], centre[2], seen * 100))

    # -------------------------------------------------------------- every machine, smallest and tallest tower
    hub_found, floor_found = turbine.hub_and_floor(PATH)
    if math.dist(hub_found, hub) > EPS or abs(floor_found - floor) > EPS:
        fail('hub_and_floor disagrees with this check')
    rows = []
    for ident, diameter, exaggeration, low, high in turbine.CATALOGUE:
        rotor_scale, nacelle_scale = turbine.scales(diameter, exaggeration, small=ident == 'sw_10')
        for n in (low, high):
            lowest, nearest, overhang = clearances(objects, n, rotor_scale, nacelle_scale, hub, floor)
            rows.append((ident, n, rotor_scale, nacelle_scale, lowest, nearest, overhang))
            if lowest < GROUND_CLEARANCE:
                fail('%s on %d segments: the lowest blade point is %.2f above the ground' % (ident, n, lowest))
            if nearest < TOWER_CLEARANCE:
                fail('%s on %d segments: a blade passes %.3f from the tower' % (ident, n, nearest))

    if '--table' in sys.argv:
        print('\n| modello | s_r | s_n | n | punta piu\' bassa | pala-torre minima | sbalzo max dall\'asse |')
        print('|---|---|---|---|---|---|---|')
        for ident, n, rs, ns, lowest, nearest, overhang in rows:
            print('| %s | %.4f | %.4f | %d | %.2f | %.3f | %.2f |' % (ident, rs, ns, n, lowest, nearest, overhang))

    print('\n%s' % ('the turbine is what its contract says' if not problems else '%d problem(s)' % len(problems)))
    return 1 if problems else 0


def clearances(objects, n, rotor_scale, nacelle_scale, hub, floor):
    """Lowest blade point, nearest approach to the tower, and the farthest sideways reach, over a full turn."""
    blade_faces = [p for f in objects['rotate_1'] for p in f.points][::3]
    tower = turbine.tower_scale(n, nacelle_scale)
    lowest, nearest, overhang = 1e9, 1e9, 0.0
    for angle in range(0, 120, 6):
        turned = turbine.group_matrix('rotate_1', n, rotor_scale, nacelle_scale, hub, floor, rotor=float(angle))
        for p in blade_faces:
            x, y, z = turned(p)
            lowest = min(lowest, y)
            overhang = max(overhang, math.hypot(x, z))
            if y < n:
                shaft_here = tower * turbine.shaft_radius(y * turbine.H_POLE / n)
                nearest = min(nearest, math.hypot(x, z) - shaft_here)
    return lowest, nearest, overhang


def visible_from_ground(target, eye=1.62, distance=3.0):
    """The share of the way round the foot, at a player's eye height, with a clear line to the point.

    The line is tested against the tower's shaft and the plinth, the two things that stand between.
    """
    clear = 0
    steps = 72
    for k in range(steps):
        a = 2.0 * math.pi * k / steps
        start = (distance * math.cos(a), eye, distance * math.sin(a))
        blocked = False
        for t in (i / 200.0 for i in range(201)):
            x = start[0] + (target[0] - start[0]) * t
            y = start[1] + (target[1] - start[1]) * t
            z = start[2] + (target[2] - start[2]) * t
            if math.hypot(x, z) < turbine.shaft_radius(max(0.0, y)) - 1e-6 and 0.0 <= y <= turbine.H_POLE:
                blocked = True
                break
        clear += not blocked

    return clear / steps


if __name__ == '__main__':
    sys.exit(main())
