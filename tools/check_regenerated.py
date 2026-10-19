#!/usr/bin/env python3
"""Proves the tree is what the generators write: runs every one of them and compares src/ before and after.

    python3 tools/check_regenerated.py

Every model, texture, recipe, blockstate, table and language key under src/ is written by a script in tools/
(CLAUDE.md section 6), so a file edited by hand, or a generator changed and not run, is a difference here.  The
generators run in GENERATORS order, which is section 6's; one that exits non-zero fails the check, because several
of them are checkers too.  Differences are left in place for `git diff` to show.

A PNG is compared by its pixels, not its bytes: zlib on Linux and on macOS compress the same pixels differently.
And a channel may differ by one level: libm differs in the last bit between platforms, so a texture value that
lands on .5 rounds either way (Linux against macOS: 52 of 13 million channels, in five textures).  A texture
drawn by hand or left behind by a changed generator differs by far more than that.
"""

import hashlib
import os
import struct
import subprocess
import sys
import time
import zlib

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
SOURCE = 'src'

# CLAUDE.md section 6, in its order: textures before the models that name them, gen_core_data before gen_crafting,
# every OBJ before gen_fittings reads them, the shape tables before splice_tables pastes them.
GENERATORS = [
    'gen_block_textures.py', 'gen_pv_textures.py', 'gen_item_sprites.py', 'gen_icon.py',
    'gen_pv_models.py', 'gen_grid_models.py', 'gen_cab_models.py', 'gen_cable_models.py', 'gen_trunk_models.py',
    'gen_core_data.py', 'gen_crafting.py',
    'gen_turbine_models.py', 'gen_turbine_textures.py', 'gen_tower_models.py', 'gen_transformer_models.py',
    'gen_switch_models.py', 'gen_conductor_models.py', 'gen_control_models.py',
    'gen_fittings.py', 'splice_tables.py', 'gen_example_plant.py', 'gen_example_site.py',
]


def pixels(data):
    """(width, height, colour type, unfiltered rows) of a PNG, or None if it is not one this can read."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    pos, header, idat = 8, None, b''
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        payload = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'IDAT':
            idat += payload
        pos += 12 + length

    width, height, depth, colour, _, _, interlace = header
    channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(colour)
    if depth != 8 or channels is None or interlace:
        return None

    raw, stride, rows, previous = zlib.decompress(idat), width * channels, [], bytearray(width * channels)
    for y in range(height):
        start = y * (stride + 1)
        kind, line = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        # texlib writes filter 0, so this loop is only walked for a PNG some other tool wrote
        if kind:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b, c = previous[i], previous[i - channels] if i >= channels else 0
                if kind == 1:
                    line[i] = (line[i] + a) & 0xff
                elif kind == 2:
                    line[i] = (line[i] + b) & 0xff
                elif kind == 3:
                    line[i] = (line[i] + (a + b) // 2) & 0xff
                elif kind == 4:
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xff
        rows.append(bytes(line))
        previous = line
    return width, height, colour, b''.join(rows)


# How far apart one channel of a regenerated PNG may be from the tree's: the last-bit rounding of libm.
PIXEL_TOLERANCE = 1


def fingerprint(path):
    """What a file is, for this comparison: (hash, pixels) - the pixels only for a PNG, to compare within tolerance."""
    data = open(path, 'rb').read()
    if path.endswith('.png'):
        decoded = pixels(data)
        if decoded is not None:
            width, height, colour, body = decoded
            return 'png %dx%d/%d %s' % (width, height, colour, hashlib.sha256(body).hexdigest()), body
    return hashlib.sha256(data).hexdigest(), None


def same(before, after):
    """Whether two fingerprints are one file: equal, or a PNG of the same size within PIXEL_TOLERANCE."""
    if before[0] == after[0]:
        return True
    if before[1] is None or after[1] is None or before[0].split()[1] != after[0].split()[1]:
        return False
    return all(abs(x - y) <= PIXEL_TOLERANCE for x, y in zip(before[1], after[1]))


def snapshot():
    """{path: fingerprint} for every file under src/: about 60 MB, most of it the pixels of the block textures."""
    out = {}
    for folder, _, names in os.walk(SOURCE):
        for name in names:
            path = os.path.join(folder, name)
            out[path] = fingerprint(path)
    return out


def main():
    os.chdir(ROOT)
    before = snapshot()
    failed = []
    for name in GENERATORS:
        start = time.time()
        run = subprocess.run([sys.executable, os.path.join(TOOLS, name)], capture_output=True, text=True)
        print('%-28s %5.1f s%s' % (name, time.time() - start, '' if run.returncode == 0 else '  FAILED'))
        if run.returncode != 0:
            failed.append(name)
            print('\n'.join('    ' + line for line in (run.stdout + run.stderr).strip().splitlines()[-20:]))

    after = snapshot()
    changed = sorted(path for path in before.keys() & after.keys() if not same(before[path], after[path]))
    added = sorted(after.keys() - before.keys())
    removed = sorted(before.keys() - after.keys())
    for label, paths in (('changed', changed), ('written, and was not in the tree', added),
                         ('removed', removed)):
        for path in paths:
            print('  %s %s' % (path, label))

    problems = len(failed) + len(changed) + len(added) + len(removed)
    if problems:
        print('\n%d generator(s) failed, %d file(s) differ from what the generators write'
              % (len(failed), len(changed) + len(added) + len(removed)))
        return 1

    print('\n%d files under src/, every one what its generator writes' % len(after))
    return 0


if __name__ == '__main__':
    sys.exit(main())
