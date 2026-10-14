#!/usr/bin/env python3
"""The boxes of every group of every machine model, for the code that never loads a model.

    python3 tools/gen_fittings.py            # write data/renewables/fittings.json
    python3 tools/gen_fittings.py --check    # exit 1 if the file on disk is not what would be written

The server does not load OBJ files, but it has to know where a wire ends on a pole, how tall a fitting is, where a
tower's chain hangs.  Those answers are in the models, so they are read out of the models here, once, into a file the
jar carries and client and server both read (docs/ARCHITECTURE.md section 2; RP.model.PartBoxes).

One entry per *model*, keyed by the OBJ resource a ModelDefinition names - four inverters share a model, so they
share an entry.  Under it, one box per group of that model, not only the fittings: it is cheap, and the next thing
the server needs a size for should not mean a new generator.  Boxes are in the model's own frame: blocks, x and z
centred on the block's axis, y up from its floor, the machine as authored.

**The group keys are the game's.**  A group is ``object_material``, or just the material in a file that names no
object; faces before the first ``o`` belong to ``default``; a face with no ``usemtl`` has the material ``default``;
an object written twice is one object (docs/spec/models.md section 2.2).  ``objlib`` reads the faces, this file
applies the grouping, and the JUnit test ModelFilesTest reads the same files with the game's own reader and compares
every box to this file - which is what keeps the two readers one reader.

Models that are not machines are left out: the cable and conductor-run pieces are baked into chunks by Forge, not
drawn from here, and are the ones a block-model JSON names.  A model a ModelDefinition names that is not on disk
yet (the wind turbine's, until the new one is made) is reported and skipped, and the file is regenerated when it
arrives.
"""

import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import objlib                                                                    # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'renewables')
OUTPUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'renewables', 'fittings.json')
DEFINITIONS = os.path.join(ROOT, 'src', 'main', 'java', 'io', 'github', 'simonerecchia', 'renewables', 'model',
                           'ModelDefinitions.java')

FORMAT = 1
NAMESPACE = 'renewables'
DECIMALS = 6


def chunk_models():
    """The OBJ paths a block-model JSON names: those go through Forge's loader and are never drawn from here."""
    named = set()
    for path in glob.glob(os.path.join(ASSETS, 'models', 'block', '*.json')):
        reference = json.load(open(path)).get('model')
        if reference:
            named.add(os.path.normpath(os.path.join(ASSETS, *reference.split(':', 1)[-1].split('/'))))

    return named


def machine_models():
    """Every machine OBJ on disk, as (resource, path), sorted by resource."""
    chunked = chunk_models()
    found = []
    for path in sorted(glob.glob(os.path.join(ASSETS, 'models', '*', '*.obj'))):
        if os.path.normpath(path) in chunked:
            continue
        relative = os.path.relpath(path, ASSETS).replace(os.sep, '/')
        found.append(('%s:%s' % (NAMESPACE, relative), path))

    return sorted(found)


def group_boxes(path):
    """Group key -> (min, max), in the order the file first uses each group."""
    faces = objlib.read(path)
    # objlib calls "no o yet" and "no usemtl yet" 'none'; the game calls both 'default'
    named = any(face.group not in ('none', 'default') for face in faces)

    boxes = {}
    for face in faces:
        material = 'default' if face.material in ('none', '') else face.material
        obj = 'default' if face.group == 'none' else face.group
        key = '%s_%s' % (obj, material) if named else material
        points = boxes.setdefault(key, [])
        points.extend(face.points)

    out = {}
    for key, points in boxes.items():
        low, high = objlib.bounds(points)
        out[key] = (low, high)

    return out


def number(value):
    """A coordinate as the shortest text that is exact to the file's six decimals, and never '-0.0'."""
    rounded = round(value, DECIMALS) + 0.0
    return repr(rounded)


def document():
    lines = ['{', '  "format": %d,' % FORMAT, '  "models": {']
    models = machine_models()
    for index, (resource, path) in enumerate(models):
        lines.append('    "%s": {' % resource)
        groups = list(group_boxes(path).items())
        for number_of, (key, (low, high)) in enumerate(groups):
            comma = ',' if number_of < len(groups) - 1 else ''
            lines.append('      "%s": { "min": [%s], "max": [%s] }%s' % (
                key, ', '.join(number(v) for v in low), ', '.join(number(v) for v in high), comma))
        lines.append('    }%s' % (',' if index < len(models) - 1 else ''))

    lines.extend(['  }', '}', ''])
    return '\n'.join(lines)


def expected_models():
    """The OBJ resources ModelDefinitions names with a literal, so a missing one can be reported."""
    source = open(DEFINITIONS).read()
    return sorted({'%s:models/%s.obj' % (NAMESPACE, name)
                   for name in re.findall(r'"([a-z0-9_]+/[a-z0-9_]+)"', source)})


def main():
    text = document()
    on_disk = {resource for resource, _ in machine_models()}
    for resource in expected_models():
        if resource not in on_disk:
            print('gen_fittings: %s is named by ModelDefinitions but is not on disk: skipped, '
                  'regenerate when it exists' % resource)

    if '--check' in sys.argv:
        current = open(OUTPUT).read() if os.path.exists(OUTPUT) else None
        if current != text:
            print('gen_fittings: %s is out of date, run tools/gen_fittings.py' % os.path.relpath(OUTPUT, ROOT))
            return 1
        return 0

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, 'w') as handle:
        handle.write(text)
    print('gen_fittings: %d models, %d bytes -> %s' % (len(on_disk), len(text), os.path.relpath(OUTPUT, ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
