#!/usr/bin/env python3
"""Nothing a model draws floats, and no member of a frame ends in the air.

    python3 tools/check_connectivity.py                    # every model
    python3 tools/check_connectivity.py lattice_tension    # the models in the directories named
    python3 tools/check_connectivity.py --verbose          # and every body behind a finding

A *body* is a run of faces joined by shared vertices - one primitive, usually: a strut, a box, a cylinder.  Two
bodies are in contact when their surfaces come within TOL of each other or one is inside the other.  That is
measured exactly, triangle against triangle (vertex to face, edge to edge, edge through face), with a bounding
volume tree per body so only triangles that can touch are ever compared; "inside" is the winding number of a
closed body.  Every finding is a failure:

  FLOATING   a body in touch with nothing that reaches the ground (or the wall, or a block's edge): it hangs in air
  DETACHED   a body that stands on the ground but is not part of the machine - a second object beside it
  DANGLING   an end of a bar (a body BAR_RATIO times longer than it is wide: a member, a brace, a rod, a cable)
             that rests on nothing: the lattice member that stops in mid air
  DOUBLE     two bars along one line for most of their length: one member drawn twice, which flickers
  BURIED     a body wholly inside another: geometry nobody can ever see
  PIVOT      a pivot_* marker on nothing: the hinge the renderer turns a part about is in mid air

A model is checked in every state its renderer draws it in (the harness of a row, a switch's flag, a kiosk on a
wall) and in every pose that moves one part against another (the turbine at each catalogue size, the inverter at
each cabinet scale), because a part can touch its support in one and not in the other.  What is free by nature
is declared in FREE_ENDS and SEPARATE with the reason, so an exception is a decision rather than a blind spot.
"""

import collections
import itertools
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import objlib                                                                    # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'renewables')
MODELS = os.path.join(ASSETS, 'models')

# A sixty-fourth of a block: a quarter of a pixel.  Two parts closer than this touch.
TOL = 1.0 / 64.0
TOL2 = TOL * TOL
# A body this many times longer than it is wide is a bar, and both its ends must rest on something.
BAR_RATIO = 4.0
# How far in from a bar's end its support may be, as a multiple of the bar's width.
END_ZONE = 1.0
# Triangles per leaf of a body's tree.
LEAF = 6

# Ends that are free by nature: (model, group prefix, which end, reason).  'up' frees only the upper end of a
# bar that stands; 'any' either end; 'out' the end further from the model's vertical axis.
FREE_ENDS = (
    ('wind_turbine', 'rotate_1', 'out', 'a blade is a cantilever from its pitch bearing: the tip is free'),
    ('met_mast', 'mast', 'up', 'the lightning finial stands on the mast head: its tip is free'),
    ('plant_controller', 'antenna', 'up', 'a whip antenna stands on its base'),
)

# Bodies that are deliberately a separate object: (model, group prefix, reason).  A conditional group is not
# in here: it is checked in every state it is drawn in.
SEPARATE = ()


# ---------------------------------------------------------------- vector arithmetic, inlined where it is hot

def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def point_triangle_d2(p, a, b, c):
    """The squared distance from a point to a triangle (Ericson, Real-Time Collision Detection 5.1.5)."""
    abx, aby, abz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    acx, acy, acz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    apx, apy, apz = p[0] - a[0], p[1] - a[1], p[2] - a[2]
    d1 = abx * apx + aby * apy + abz * apz
    d2 = acx * apx + acy * apy + acz * apz
    if d1 <= 0.0 and d2 <= 0.0:
        return apx * apx + apy * apy + apz * apz
    bpx, bpy, bpz = p[0] - b[0], p[1] - b[1], p[2] - b[2]
    d3 = abx * bpx + aby * bpy + abz * bpz
    d4 = acx * bpx + acy * bpy + acz * bpz
    if d3 >= 0.0 and d4 <= d3:
        return bpx * bpx + bpy * bpy + bpz * bpz
    vc = d1 * d4 - d3 * d2
    if vc <= 0.0 and d1 >= 0.0 and d3 <= 0.0:
        v = d1 / (d1 - d3)
        qx, qy, qz = apx - v * abx, apy - v * aby, apz - v * abz
        return qx * qx + qy * qy + qz * qz
    cpx, cpy, cpz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    d5 = abx * cpx + aby * cpy + abz * cpz
    d6 = acx * cpx + acy * cpy + acz * cpz
    if d6 >= 0.0 and d5 <= d6:
        return cpx * cpx + cpy * cpy + cpz * cpz
    vb = d5 * d2 - d1 * d6
    if vb <= 0.0 and d2 >= 0.0 and d6 <= 0.0:
        w = d2 / (d2 - d6)
        qx, qy, qz = apx - w * acx, apy - w * acy, apz - w * acz
        return qx * qx + qy * qy + qz * qz
    va = d3 * d6 - d5 * d4
    if va <= 0.0 and (d4 - d3) >= 0.0 and (d5 - d6) >= 0.0:
        w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
        qx = bpx - w * (c[0] - b[0])
        qy = bpy - w * (c[1] - b[1])
        qz = bpz - w * (c[2] - b[2])
        return qx * qx + qy * qy + qz * qz
    denom = 1.0 / (va + vb + vc)
    v, w = vb * denom, vc * denom
    qx = apx - abx * v - acx * w
    qy = apy - aby * v - acy * w
    qz = apz - abz * v - acz * w
    return qx * qx + qy * qy + qz * qz


def segment_segment_d2(p1, q1, p2, q2):
    """The squared distance between two segments (Ericson 5.1.9)."""
    d1 = (q1[0] - p1[0], q1[1] - p1[1], q1[2] - p1[2])
    d2 = (q2[0] - p2[0], q2[1] - p2[1], q2[2] - p2[2])
    r = (p1[0] - p2[0], p1[1] - p2[1], p1[2] - p2[2])
    a = _dot(d1, d1)
    e = _dot(d2, d2)
    f = _dot(d2, r)
    if a <= 1e-18 and e <= 1e-18:
        return _dot(r, r)
    if a <= 1e-18:
        s, t = 0.0, min(1.0, max(0.0, f / e))
    else:
        c = _dot(d1, r)
        if e <= 1e-18:
            t, s = 0.0, min(1.0, max(0.0, -c / a))
        else:
            b = _dot(d1, d2)
            denom = a * e - b * b
            s = min(1.0, max(0.0, (b * f - c * e) / denom)) if denom > 1e-18 else 0.0
            t = (b * s + f) / e
            if t < 0.0:
                t, s = 0.0, min(1.0, max(0.0, -c / a))
            elif t > 1.0:
                t, s = 1.0, min(1.0, max(0.0, (b - c) / a))
    dx = r[0] + d1[0] * s - d2[0] * t
    dy = r[1] + d1[1] * s - d2[1] * t
    dz = r[2] + d1[2] * s - d2[2] * t
    return dx * dx + dy * dy + dz * dz


def segment_crosses(p, q, a, b, c):
    """Whether the segment pq passes through the triangle abc (Moller-Trumbore, parameter in [0, 1])."""
    e1 = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    e2 = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    d = (q[0] - p[0], q[1] - p[1], q[2] - p[2])
    h = _cross(d, e2)
    det = _dot(e1, h)
    if abs(det) < 1e-14:
        return False                    # parallel: the vertex and edge distances decide it
    inverse = 1.0 / det
    s = (p[0] - a[0], p[1] - a[1], p[2] - a[2])
    u = inverse * _dot(s, h)
    if u < 0.0 or u > 1.0:
        return False
    k = _cross(s, e1)
    v = inverse * _dot(d, k)
    if v < 0.0 or u + v > 1.0:
        return False
    t = inverse * _dot(e2, k)
    return 0.0 <= t <= 1.0


def triangles_touch(first, second):
    """Whether two triangles come within TOL of each other.

    Exact: two disjoint triangles are nearest at a vertex and a face or at two edges, and two that meet have an
    edge of one through the other.
    """
    a0, a1, a2 = first
    b0, b1, b2 = second
    for p in first:
        if point_triangle_d2(p, b0, b1, b2) <= TOL2:
            return True
    for p in second:
        if point_triangle_d2(p, a0, a1, a2) <= TOL2:
            return True
    edges_a = ((a0, a1), (a1, a2), (a2, a0))
    edges_b = ((b0, b1), (b1, b2), (b2, b0))
    for p, q in edges_a:
        if segment_crosses(p, q, b0, b1, b2):
            return True
    for p, q in edges_b:
        if segment_crosses(p, q, a0, a1, a2):
            return True
    for p, q in edges_a:
        for r, s in edges_b:
            if segment_segment_d2(p, q, r, s) <= TOL2:
                return True
    return False


def box_of(points):
    xs, ys, zs = [p[0] for p in points], [p[1] for p in points], [p[2] for p in points]
    return (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))


def boxes_meet(a, b, slack=TOL):
    return (a[0] <= b[3] + slack and b[0] <= a[3] + slack and a[1] <= b[4] + slack and b[1] <= a[4] + slack
            and a[2] <= b[5] + slack and b[2] <= a[5] + slack)


def box_inside(inner, outer):
    return all(outer[i] <= inner[i] + TOL for i in range(3)) and all(inner[i + 3] <= outer[i + 3] + TOL
                                                                      for i in range(3))


# ---------------------------------------------------------------- bodies

class Body:
    """One run of faces joined by shared vertices."""

    def __init__(self, index, triangles, groups):
        self.index = index
        self.triangles = triangles
        self.groups = groups
        self.boxes = [box_of(t) for t in triangles]
        self.box = (min(b[0] for b in self.boxes), min(b[1] for b in self.boxes), min(b[2] for b in self.boxes),
                    max(b[3] for b in self.boxes), max(b[4] for b in self.boxes), max(b[5] for b in self.boxes))
        self._tree = None
        self._closed = None
        self._shape = None

    @property
    def name(self):
        return '+'.join(sorted(self.groups))

    def tree(self):
        """A bounding volume tree over the triangles: (box, left, right, leaf triangle indices) per node."""
        if self._tree is None:
            nodes = []
            centres = [((b[0] + b[3]) * 0.5, (b[1] + b[4]) * 0.5, (b[2] + b[5]) * 0.5) for b in self.boxes]

            def make(ids):
                box = (min(self.boxes[i][0] for i in ids), min(self.boxes[i][1] for i in ids),
                       min(self.boxes[i][2] for i in ids), max(self.boxes[i][3] for i in ids),
                       max(self.boxes[i][4] for i in ids), max(self.boxes[i][5] for i in ids))
                at = len(nodes)
                nodes.append(None)
                if len(ids) <= LEAF:
                    nodes[at] = (box, -1, -1, ids)
                    return at
                spread = [max(centres[i][k] for i in ids) - min(centres[i][k] for i in ids) for k in range(3)]
                axis = spread.index(max(spread))
                ids = sorted(ids, key=lambda i: centres[i][axis])
                half = len(ids) // 2
                left = make(ids[:half])
                right = make(ids[half:])
                nodes[at] = (box, left, right, None)
                return at

            make(list(range(len(self.triangles))))
            self._tree = nodes
        return self._tree

    def closed(self):
        """Whether every edge is shared by an even number of faces: a body with an inside."""
        if self._closed is None:
            count = collections.Counter()
            for a, b, c in self.triangles:
                for p, q in ((a, b), (b, c), (c, a)):
                    count[(p, q) if p < q else (q, p)] += 1
            self._closed = all(n % 2 == 0 for n in count.values())
        return self._closed

    def winding(self, point):
        """The generalised winding number of a point: about 1 inside a closed body, about 0 outside."""
        total = 0.0
        px, py, pz = point
        for a, b, c in self.triangles:
            ax, ay, az = a[0] - px, a[1] - py, a[2] - pz
            bx, by, bz = b[0] - px, b[1] - py, b[2] - pz
            cx, cy, cz = c[0] - px, c[1] - py, c[2] - pz
            la = math.sqrt(ax * ax + ay * ay + az * az)
            lb = math.sqrt(bx * bx + by * by + bz * bz)
            lc = math.sqrt(cx * cx + cy * cy + cz * cz)
            det = ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)
            div = la * lb * lc + (ax * bx + ay * by + az * bz) * lc + (ax * cx + ay * cy + az * cz) * lb + \
                (bx * cx + by * cy + bz * cz) * la
            total += 2.0 * math.atan2(det, div)
        return total / (4.0 * math.pi)

    def contains(self, point):
        return self.closed() and abs(self.winding(point)) > 0.5

    def shape(self):
        """(centre, axis, length, width, low, high): the line a bar lies along, from its vertices' spread."""
        if self._shape is None:
            points = list({p for t in self.triangles for p in t})
            n = float(len(points))
            centre = tuple(sum(p[k] for p in points) / n for k in range(3))
            cov = [[0.0] * 3 for _ in range(3)]
            for p in points:
                d = (p[0] - centre[0], p[1] - centre[1], p[2] - centre[2])
                for i in range(3):
                    for j in range(3):
                        cov[i][j] += d[i] * d[j]
            # the box's longest side to start from, then power iteration onto the largest spread
            span = [self.box[k + 3] - self.box[k] for k in range(3)]
            axis = [1.0 if k == span.index(max(span)) else 0.3 for k in range(3)]
            for _ in range(60):
                nxt = [sum(cov[i][j] * axis[j] for j in range(3)) for i in range(3)]
                size = math.sqrt(sum(c * c for c in nxt)) or 1.0
                axis = [c / size for c in nxt]
            along = [_dot(_sub(p, centre), axis) for p in points]
            low, high = min(along), max(along)
            radial = 0.0
            for p, t in zip(points, along):
                d = _sub(p, centre)
                off = (d[0] - axis[0] * t, d[1] - axis[1] * t, d[2] - axis[2] * t)
                radial = max(radial, math.sqrt(_dot(off, off)))
            # the section's two extents, across the axis: a plate is wide one way and thin the other
            seed = (1.0, 0.0, 0.0) if abs(axis[0]) < 0.9 else (0.0, 1.0, 0.0)
            second = _cross(axis, seed)
            size = math.sqrt(_dot(second, second))
            second = tuple(c / size for c in second)
            third = _cross(axis, second)
            spans = []
            for direction in (second, third):
                values = [_dot(_sub(p, centre), direction) for p in points]
                spans.append(max(values) - min(values))
            self._shape = (centre, tuple(axis), high - low, 2.0 * radial, low, high, min(spans))
        return self._shape[:6]

    def thickness(self):
        """The thinner of the two extents across the axis."""
        self.shape()
        return self._shape[6]

    def is_bar(self):
        _, _, length, width, _, _ = self.shape()
        return length >= 4.0 * TOL and length >= BAR_RATIO * max(width, 1e-9)


def touching(first, second, triangles_a=None):
    """Whether two bodies come within TOL, by walking both trees at once."""
    tree_a = first.tree() if triangles_a is None else None
    tree_b = second.tree()
    if triangles_a is not None:
        # a handful of loose triangles (a bar's clipped end) against one body's tree
        for triangle in triangles_a:
            box = box_of(triangle)
            stack = [0]
            while stack:
                node = tree_b[stack.pop()]
                if not boxes_meet(box, node[0]):
                    continue
                if node[3] is None:
                    stack.append(node[1])
                    stack.append(node[2])
                    continue
                for j in node[3]:
                    if boxes_meet(box, second.boxes[j]) and triangles_touch(triangle, second.triangles[j]):
                        return True
        return False

    stack = [(0, 0)]
    while stack:
        i, j = stack.pop()
        node_a, node_b = tree_a[i], tree_b[j]
        if not boxes_meet(node_a[0], node_b[0]):
            continue
        leaf_a, leaf_b = node_a[3] is not None, node_b[3] is not None
        if leaf_a and leaf_b:
            for p in node_a[3]:
                box = first.boxes[p]
                if not boxes_meet(box, node_b[0]):
                    continue
                triangle = first.triangles[p]
                for q in node_b[3]:
                    if boxes_meet(box, second.boxes[q]) and triangles_touch(triangle, second.triangles[q]):
                        return True
        elif leaf_a or (not leaf_b and _volume(node_b[0]) > _volume(node_a[0])):
            stack.append((i, node_b[1]))
            stack.append((i, node_b[2]))
        else:
            stack.append((node_a[1], j))
            stack.append((node_a[2], j))
    return False


def _volume(box):
    return (box[3] - box[0] + TOL) * (box[4] - box[1] + TOL) * (box[5] - box[2] + TOL)


def in_contact(first, second):
    """Surfaces within TOL, or one body inside the other."""
    if not boxes_meet(first.box, second.box):
        return False
    small, large = (first, second) if _volume(first.box) <= _volume(second.box) else (second, first)
    if touching(small, large):
        return True
    return box_inside(small.box, large.box) and large.contains(small.triangles[0][0])


# ---------------------------------------------------------------- reading a model into bodies

def bodies_of(faces):
    """The faces, cut into bodies by shared vertices.  ``faces`` is (group, points) in the pose to check."""
    parent = {}

    def find(k):
        while parent[k] != k:
            parent[k] = parent[parent[k]]
            k = parent[k]
        return k

    kept = []
    for group, points in faces:
        triangles = []
        for i in range(1, len(points) - 1):
            a, b, c = points[0], points[i], points[i + 1]
            n = _cross(_sub(b, a), _sub(c, a))
            if _dot(n, n) > 1e-20:
                triangles.append((a, b, c))
        if not triangles:
            continue
        kept.append((group, triangles))
        keys = [p for t in triangles for p in t]
        for k in keys:
            parent.setdefault(k, k)
        root = find(keys[0])
        for k in keys[1:]:
            other = find(k)
            if other != root:
                parent[other] = root

    by_root = collections.OrderedDict()
    for group, triangles in kept:
        entry = by_root.setdefault(find(triangles[0][0]), ([], set()))
        entry[0].extend(triangles)
        entry[1].add(group)
    return [Body(i, triangles, groups) for i, (triangles, groups) in enumerate(by_root.values())]


def contacts(bodies):
    """Every pair of bodies in contact, found by sweeping their boxes along x and testing what overlaps."""
    order = sorted(range(len(bodies)), key=lambda i: bodies[i].box[0])
    edges = collections.defaultdict(set)
    for n, i in enumerate(order):
        a = bodies[i]
        for j in order[n + 1:]:
            b = bodies[j]
            if b.box[0] > a.box[3] + TOL:
                break
            if boxes_meet(a.box, b.box) and in_contact(a, b):
                edges[i].add(j)
                edges[j].add(i)
    return edges


# ---------------------------------------------------------------- the findings

def components(members, edges):
    """The connected sets of ``members`` through ``edges``."""
    seen, out = set(), []
    for start in members:
        if start in seen:
            continue
        stack, part = [start], []
        seen.add(start)
        while stack:
            k = stack.pop()
            part.append(k)
            for other in edges.get(k, ()):
                if other in members and other not in seen:
                    seen.add(other)
                    stack.append(other)
        out.append(part)
    return out


def free_end_allowed(model, body, which):
    for name, prefix, end, _ in FREE_ENDS:
        if model == name and any(g.startswith(prefix) for g in body.groups) and (end == 'any' or end == which):
            return True
    return False


def separate_allowed(model, body):
    return any(model == name and any(g.startswith(prefix) for g in body.groups) for name, prefix, _ in SEPARATE)


# Groups that are the end of a cable laid in the next block: drawn only when one arrives, lying on the ground and
# running out through the block's side to meet it.  Such a piece is part of that run, not of this machine.
ARRIVING = ('harness', 'entry')


def arriving(part, bodies):
    """Whether a separate piece is a cable arriving from the next block: only cable groups, through the side."""
    if not all(any(g.startswith(ARRIVING) for g in bodies[k].groups) for k in part):
        return False
    return any(max(abs(p[0]), abs(p[2])) >= 0.5 - TOL for k in part for t in bodies[k].triangles for p in t)


def clip(triangle, axis, centre, at, keep_above):
    """The part of a triangle on one side of the plane across ``axis`` at ``at``, as triangles."""
    def side(p):
        t = _dot(_sub(p, centre), axis) - at
        return t if keep_above else -t

    polygon = list(triangle)
    out = []
    for k in range(len(polygon)):
        p, q = polygon[k], polygon[(k + 1) % len(polygon)]
        sp, sq = side(p), side(q)
        if sp >= 0.0:
            out.append(p)
        if (sp >= 0.0) != (sq >= 0.0):
            t = sp / (sp - sq)
            out.append(tuple(p[i] + (q[i] - p[i]) * t for i in range(3)))
    return [(out[0], out[i], out[i + 1]) for i in range(1, len(out) - 1)]


def end_supported(body, bodies, members, high_end, anchored):
    """Whether one end of a bar rests on another body, the ground, or whatever else anchors this model."""
    centre, axis, length, width, low, high = body.shape()
    zone = max(END_ZONE * width, 2.0 * TOL)
    at = high - zone if high_end else low + zone
    piece = [t for triangle in body.triangles for t in clip(triangle, axis, centre, at, high_end)]
    if not piece:
        return True
    if any(anchored(p) for t in piece for p in t):
        return True
    tip = tuple(centre[k] + axis[k] * (high if high_end else low) for k in range(3))
    box = box_of([p for t in piece for p in t])
    for k in members:
        other = bodies[k]
        if other is body or not boxes_meet(box, other.box):
            continue
        if touching(None, other, triangles_a=piece) or (box_inside(box, other.box) and other.contains(tip)):
            return True
    return False


def doubled(first, second):
    """Two bars along one line, overlapping for most of the shorter one."""
    ca, aa, la, wa, lowa, higha = first.shape()
    cb, ab, lb, wb, lowb, highb = second.shape()
    if abs(_dot(aa, ab)) < 0.999:
        return False
    offset = _sub(cb, ca)
    along = _dot(offset, aa)
    across = _sub(offset, tuple(aa[k] * along for k in range(3)))
    # coaxial to within half the thinner section: two fins a few hundredths apart are two fins
    if math.sqrt(_dot(across, across)) > 0.5 * min(first.thickness(), second.thickness()):
        return False
    sign = 1.0 if _dot(aa, ab) > 0 else -1.0
    b_low, b_high = sorted((along + sign * lowb, along + sign * highb))
    overlap = min(higha, b_high) - max(lowa, b_low)
    return overlap > 0.5 * min(la, lb) and abs(wa - wb) < 0.5 * max(wa, wb)


def check_view(model, label, bodies, edges, members, anchored, single, report, verbose):
    """The findings for one state of one model: the bodies in ``members`` as the renderer draws them together."""
    members = set(members)
    problems = 0
    parts = components(members, edges)
    grounded = [p for p in parts if any(any(anchored(pt) for t in bodies[k].triangles for pt in t) for k in p)]
    main = max(grounded or parts, key=lambda p: sum(_volume(bodies[k].box) for k in p)) if parts else []

    for part in parts:
        if part is main:
            continue
        on_ground = part in grounded
        if on_ground and not single:
            continue
        if arriving(part, bodies):
            continue
        loose = [k for k in part if not separate_allowed(model, bodies[k])]
        if not loose:
            continue
        kind = 'DETACHED' if on_ground else 'FLOATING'
        names = collections.Counter(g for k in loose for g in bodies[k].groups)
        box = (min(bodies[k].box[0] for k in loose), min(bodies[k].box[1] for k in loose),
               min(bodies[k].box[2] for k in loose), max(bodies[k].box[3] for k in loose),
               max(bodies[k].box[4] for k in loose), max(bodies[k].box[5] for k in loose))
        report('%-9s %s [%s]: %d bod%s of %s, at (%.3f %.3f %.3f)..(%.3f %.3f %.3f)'
               % (kind, model, label, len(loose), 'y' if len(loose) == 1 else 'ies',
                  ', '.join(sorted(names)), *box))
        problems += 1

    for k in sorted(members):
        body = bodies[k]
        if not body.is_bar():
            continue
        centre, axis, length, width, low, high = body.shape()
        for high_end in (False, True):
            tip = tuple(centre[i] + axis[i] * (high if high_end else low) for i in range(3))
            other = tuple(centre[i] + axis[i] * (low if high_end else high) for i in range(3))
            if tip[1] > other[1] + 0.5 * length * 0.966 and free_end_allowed(model, body, 'up'):
                continue
            if math.hypot(tip[0], tip[2]) > math.hypot(other[0], other[2]) and free_end_allowed(model, body, 'out'):
                continue
            if free_end_allowed(model, body, 'any'):
                continue
            if not end_supported(body, bodies, members, high_end, anchored):
                report('DANGLING  %s [%s]: %s, a bar %.3f long and %.3f wide, ends on nothing at (%.3f %.3f %.3f)'
                       % (model, label, body.name, length, width, *tip))
                problems += 1

    bars = [k for k in sorted(members) if bodies[k].is_bar()]
    for n, i in enumerate(bars):
        for j in bars[n + 1:]:
            if j in edges.get(i, ()) and doubled(bodies[i], bodies[j]):
                report('DOUBLE    %s [%s]: %s and %s are one member drawn twice, near (%.3f %.3f %.3f)'
                       % (model, label, bodies[i].name, bodies[j].name, *bodies[i].shape()[0]))
                problems += 1
    return problems


def buried(model, bodies, edges, report):
    """Bodies wholly inside another closed body."""
    problems = 0
    for i, body in enumerate(bodies):
        for j in edges.get(i, ()):
            outer = bodies[j]
            if _volume(outer.box) <= _volume(body.box) or not box_inside(body.box, outer.box):
                continue
            points = list({p for t in body.triangles for p in t})
            step = max(1, len(points) // 12)
            if outer.closed() and all(abs(outer.winding(p)) > 0.5 and
                                      not any(point_triangle_d2(p, *t) <= TOL2 for t in outer.triangles)
                                      for p in points[::step]):
                report('BURIED    %s: %s is wholly inside %s and can never be seen' % (model, body.name, outer.name))
                problems += 1
                break
    return problems


def pivots(model, faces, bodies, report):
    """Every pivot_* marker sits on, or in, something that is drawn."""
    problems = 0
    for group, points in faces:
        if not group.startswith('pivot_'):
            continue
        point = points[0]
        near = [b for b in bodies if boxes_meet(box_of([point]), b.box)]
        if not any(any(point_triangle_d2(point, *t) <= TOL2 for t in b.triangles) or b.contains(point)
                   for b in near):
            report('PIVOT     %s: %s at (%.3f %.3f %.3f) is on nothing' % (model, group, *point))
            problems += 1
    return problems


# ---------------------------------------------------------------- the models and their states

def chunk_models():
    """The OBJ files a block-model JSON names: framed corner-at-origin, and read the way Forge reads them."""
    import gen_fittings
    return {os.path.normpath(p) for p in gen_fittings.chunk_models()}


def on_floor(point):
    return point[1] <= TOL


def in_block_frame(point):
    """A chunk piece rests on its floor or runs on into the next block, through one of its faces."""
    return point[1] <= TOL or point[0] <= TOL or point[0] >= 1.0 - TOL or point[2] <= TOL or point[2] >= 1.0 - TOL \
        or point[1] >= 1.0 - TOL


def scaled(factor, keep=lambda group: False):
    return lambda group, p: p if keep(group) else (p[0] * factor, p[1] * factor, p[2] * factor)


def turbine_poses():
    """The turbine as the renderer poses it for each catalogue machine at its shortest and tallest tower.

    The rotor and the nacelle scale together (uniformly, so a contact at the authored size holds at every smaller
    one) except on the SW-10, whose rotor is scaled apart from its hub - so the whole machine is posed for the
    authored C130 and the SW-10, and only the foot, which every size moves against the fixed insulator, for the rest.
    """
    import gen_turbine_models as turbine

    path = os.path.join(MODELS, 'wind_turbine', 'wind_turbine.obj')
    hub, floor = turbine.hub_and_floor(path)
    out = []
    for ident, diameter, exaggeration, low, high in turbine.CATALOGUE:
        rotor, nacelle = turbine.scales(diameter, exaggeration, small=ident == 'sw_10')
        for n in (low, high):
            whole = ident == 'sw_10' and n == low
            keep = (lambda g: True) if whole else (lambda g: g.startswith('pole') or g.startswith('insulator'))

            def pose(group, p, n=n, rotor=rotor, nacelle=nacelle):
                return turbine.group_matrix(group, n, rotor, nacelle, hub, floor)(p)
            out.append(('%s n=%d' % (ident, n), keep, pose))
    return out


def views(model, groups):
    """(label, groups drawn, how a point is posed, what anchors it, whether it must be one object) per state.

    States that differ only in which groups are drawn share one pose, so the contacts are measured once a pose.
    """
    identity = (lambda group, p: p)
    drawn = [g for g in groups if not g.startswith('pivot_')]
    out = []
    if model in ('pv_flat', 'pv_tilt', 'pv_track', 'pv_dual'):
        import check_row_cabling as cabling
        for label, args in cabling.states(model in cabling.TRACKED):
            out.append(('row ' + label, [g for g in drawn if cabling.drawn(g, *args)], identity, on_floor, True))
    elif model == 'pv_inverter':
        import re
        source = open(os.path.join(ROOT, 'src', 'main', 'java', 'io', 'github', 'simonerecchia', 'renewables',
                                   'api', 'power', 'InverterSpec.java')).read()
        body = source[source.index('double renderScale()'):]
        body = body[:body.index('\n\t}')]
        figures = sorted({float(f) for f in re.findall(r'return (\d\.\d+);', body)})
        for factor in figures:
            pose = scaled(factor, keep=lambda g: g.startswith('entry'))
            for lower in ('section', 'blank'):
                base = [g for g in drawn if not g.startswith('entry') and not g.startswith(
                    'blank' if lower == 'section' else 'section')]
                out.append(('scale %.2f, %s, no cable' % (factor, lower), base, pose, on_floor, True))
                out.append(('scale %.2f, %s, cable on every side' % (factor, lower),
                            base + [g for g in drawn if g.startswith('entry')], pose, on_floor, True))
    elif model == 'pv_combiner':
        base = [g for g in drawn if not g.startswith('entry')]
        out.append(('no cable', base, identity, on_floor, True))
        out.append(('cable on every side', drawn, identity, on_floor, True))
    elif model in ('mv_breaker', 'mv_disconnector'):
        for flag, other in (('flag_shut', 'flag_open'), ('flag_open', 'flag_shut')):
            out.append((flag, [g for g in drawn if not g.startswith(other)], identity, on_floor, True))
    elif model == 'power_box':
        out.append(('on the ground', drawn, identity, on_floor, True))
        hanging = [g for g in drawn if not (g.startswith('plinth') or g.startswith('conduit'))]
        backset = 0.28
        out.append(('on a wall', hanging, identity, lambda p: p[2] >= 0.5 - backset - TOL, True))
    elif model == 'wind_turbine':
        out.append(('C130 as authored', drawn, identity, on_floor, True))
        for label, keep, pose in turbine_poses():
            out.append((label, [g for g in drawn if keep(g)], pose, on_floor, True))
    else:
        out.append(('', drawn, identity, on_floor, True))
    return out


def block_obj(reference):
    """``renewables:block/x`` to the OBJ its forge:obj block model names, or None for a plain JSON model."""
    path = os.path.join(ASSETS, 'models', 'block', reference.split(':', 1)[1].split('/', 1)[1] + '.json')
    model = json.load(open(path)).get('model')
    return os.path.normpath(os.path.join(ASSETS, *model.split(':', 1)[1].split('/'))) if model else None


def composed_states(blockstate):
    """Every distinct state of a multipart blockstate, as the (OBJ, y turn) pieces it draws together.

    A run's arm ends at its hub and its hub at the arm, so neither piece can be judged alone: the arm's inner end
    is a bar ending on nothing until the hub is drawn with it.  States that are one another turned are one state.
    """
    data = json.load(open(os.path.join(ASSETS, 'blockstates', blockstate + '.json')))
    if 'multipart' not in data:
        return []
    values = collections.OrderedDict()
    for part in data['multipart']:
        for key, value in part.get('when', {}).items():
            values.setdefault(key, set()).update(value.split('|'))
    keys = sorted(values)
    seen, out = set(), []
    for combo in itertools.product(*[sorted(values[k]) for k in keys]):
        state = dict(zip(keys, combo))
        pieces = []
        for part in data['multipart']:
            if all(state[k] in v.split('|') for k, v in part.get('when', {}).items()):
                apply = part['apply'][0] if isinstance(part['apply'], list) else part['apply']
                obj = block_obj(apply['model'])
                if obj is None or apply.get('x', 0):
                    return []
                pieces.append((obj, apply.get('y', 0) % 360))
        shape = min(tuple(sorted((obj, (y + turn) % 360) for obj, y in pieces)) for turn in (0, 90, 180, 270))
        if not pieces or shape in seen:
            continue
        seen.add(shape)
        out.append((','.join('%s=%s' % kv for kv in state.items()), pieces))
    return out


def turned(point, y):
    """A point of a block model turned the way a blockstate's ``y`` turns it (render_blocks.placed)."""
    x, z = point[0] - 0.5, point[2] - 0.5
    for _ in range((y // 90) % 4):
        x, z = -z, x
    return (x + 0.5, point[1], z + 0.5)


def report_lines(lines):
    """One line a finding, not one a state: a member that dangles in every state of a row is one fault."""
    shown = collections.OrderedDict()
    for line in lines:
        key = line.split(' [')[0] + line.split(']', 1)[-1] if ' [' in line else line
        shown.setdefault(key, []).append(line)
    for same in shown.values():
        more = len(same) - 1
        print('    ' + same[0] + ('   (and %d more state%s)' % (more, 's' if more > 1 else '') if more else ''))
    return len(shown)


def check_run(blockstate, states, verbose):
    """A run's blockstate, every distinct state composed the way the game draws it."""
    lines = []
    cache = {}
    for label, pieces in states:
        faces = []
        for obj, y in pieces:
            if obj not in cache:
                cache[obj] = objlib.forge_only(objlib.read(obj))
            piece = os.path.splitext(os.path.basename(obj))[0]
            faces += [('%s.%s' % (piece, f.group), [turned(p, y) for p in f.points]) for f in cache[obj]]
        bodies = bodies_of(faces)
        edges = contacts(bodies)
        members = [b.index for b in bodies]
        check_view(blockstate, label, bodies, edges, members, in_block_frame, False, lines.append, verbose)
    return report_lines(lines)


def machine_paths(wanted):
    """Every machine OBJ (not a chunk piece), as (directory, path)."""
    chunked = chunk_models()
    found = []
    for directory in sorted(os.listdir(MODELS)):
        folder = os.path.join(MODELS, directory)
        if (wanted and directory not in wanted) or not os.path.isdir(folder):
            continue
        for name in sorted(os.listdir(folder)):
            path = os.path.join(folder, name)
            if name.endswith('.obj') and os.path.normpath(path) not in chunked:
                found.append((directory, path))
    return found


def check(directory, path, verbose):
    faces = objlib.read(path)
    groups = list(dict.fromkeys(f.group for f in faces))
    lines = []
    report = lines.append

    by_pose = collections.OrderedDict()
    for label, drawn, pose, anchored, single in views(directory, groups):
        by_pose.setdefault(id(pose), (pose, []))[1].append((label, drawn, anchored, single))

    for pose, states in by_pose.values():
        wanted = set(g for _, drawn, _, _ in states for g in drawn)
        posed = [(f.group, [pose(f.group, p) for p in f.points]) for f in faces if f.group in wanted]
        bodies = bodies_of(posed)
        edges = contacts(bodies)
        for label, drawn, anchored, single in states:
            members = [b.index for b in bodies if b.groups & set(drawn)]
            check_view(directory, label, bodies, edges, members, anchored, single, report, verbose)

    # what is buried and where the hinges are is a fact of the file, so it is read off the file as authored
    authored = bodies_of([(f.group, f.points) for f in faces if not f.group.startswith('pivot_')])
    buried(directory, authored, contacts(authored), report)
    pivots(directory, [(f.group, f.points) for f in faces], authored, report)
    return report_lines(lines)


def main():
    verbose = '--verbose' in sys.argv
    wanted = [a for a in sys.argv[1:] if not a.startswith('--')]
    total = 0
    for directory, path in machine_paths(wanted):
        print('%s' % os.path.relpath(path, MODELS))
        total += check(directory, path, verbose)

    covered = set()
    for name in sorted(os.listdir(os.path.join(ASSETS, 'blockstates'))):
        blockstate = name[:-5]
        states = composed_states(blockstate)
        if not states or (wanted and blockstate not in wanted):
            covered.update(obj for _, pieces in states for obj, _ in pieces)
            continue
        covered.update(obj for _, pieces in states for obj, _ in pieces)
        print('%s, %d distinct states' % (blockstate, len(states)))
        total += check_run(blockstate, states, verbose)

    for obj in sorted(chunk_models() - covered):
        print('    UNUSED    %s is a block model no blockstate draws' % os.path.relpath(obj, MODELS))
        total += 1

    print('\n%s' % ('every body rests on something, and every bar end on a node' if total == 0
                    else '%d problem(s)' % total))
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main())
