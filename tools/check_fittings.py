#!/usr/bin/env python3
"""fittings.json says what the models say, and says it for every fitting a machine is defined with.

    python3 tools/check_fittings.py            # exit 1 on a problem
    python3 tools/check_fittings.py --strict   # a definition whose model is not on disk yet is a problem too

The server never loads a model: where a wire ends on a machine comes from this file, so a stale or incomplete one is
a wire that hangs in the wrong place (or nowhere) on the server while the client draws the machine correctly.  Three
ways it goes wrong, each checked here:

**Stale.**  A model was regenerated and the table was not.  The file is rebuilt in memory by gen_fittings.py and must
be byte for byte what is on disk.

**Incomplete.**  ModelDefinitions names the groups a machine hangs wires on, in the order a stored span indexes them.
Every one of those must be a group of that machine's model and have a box here - a literal list, or a numbered run
(``bushing_1_porcelain`` .. ``bushing_N_porcelain``), where N is a literal or comes from the catalogue: the
transformer's bushing count, a tower's circuits times three, a switch's poles times two.  A numbered run must also *end*
at N, so a model drawn with a seventh insulator that no definition lists is caught.

**Nonsense.**  A box that is not finite, or whose corners are the wrong way round.

A definition whose OBJ is not on disk (the wind turbine's, until the new model is made) cannot be checked and is
reported; --strict makes that an error, for the day the model is meant to be there.  The same shortfall is what
check_obj_loading.py reports as a fitting no model draws.
"""

import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_fittings                                                              # noqa: E402

JAVA = os.path.join(gen_fittings.ROOT, 'src', 'main', 'java', 'io', 'github', 'simonerecchia', 'renewables')
CATALOG = os.path.join(JAVA, 'catalog')


def read(path):
    with open(path) as handle:
        return handle.read()


def balanced(text, start):
    """The text from the open bracket at ``start`` to its matching close, brackets included."""
    depth = 0
    for index in range(start, len(text)):
        if text[index] == '(':
            depth += 1
        elif text[index] == ')':
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    raise SystemExit('check_fittings: unbalanced brackets in ModelDefinitions.java')


def definitions():
    """Every ``new ModelDefinition(...)`` in ModelDefinitions.java as (model or None, [literal names], [(prefix, suffix, count or None)])."""
    source = read(gen_fittings.DEFINITIONS)
    found = []
    for match in re.finditer(r'new ModelDefinition\(', source):
        body = balanced(source, match.end() - 1)

        model = re.search(r'^\(model\("([a-z0-9_]+/[a-z0-9_]+)"\)', body)
        literals = re.findall(r'List\.of\(([^)]*)\)', body)
        names = [name for group in literals for name in re.findall(r'"([a-z0-9_]+)"', group)]
        runs = []
        for prefix, suffix, count in re.findall(r'numbered\("([a-z0-9_]+)",\s*"([a-z0-9_]+)",\s*([^)]*(?:\([^)]*\))?[^)]*)\)', body):
            count = count.strip()
            runs.append((prefix, suffix, int(count) if count.isdigit() else None, count))
        found.append((model.group(1) if model else None, names, runs, body))

    return found


def catalogue_counts():
    """How many fittings the catalogue gives each family's models: model -> N."""
    counts = {}

    transformers = read(os.path.join(CATALOG, 'TransformerCatalog.java'))
    for model, bushings in re.findall(r'"(tx_[a-z0-9_]+)",\s*(\d+)\)\)', transformers):
        counts[model] = int(bushings)

    towers = read(os.path.join(CATALOG, 'TowerCatalog.java'))
    for model, circuits in re.findall(r'id\("(lattice_[a-z0-9_]+)"\),\s*"[^"]*",\s*Duty\.\w+,\s*[\d_.]+,\s*(\d+),', towers):
        counts[model] = int(circuits) * 3

    switches = read(os.path.join(CATALOG, 'SwitchgearCatalog.java'))
    for poles, model in re.findall(r'[\d_.]+,\s*(\d+),\s*[\d_.]+,\s*"(mv_[a-z0-9_]+)"\)\)', switches):
        counts[model] = int(poles) * 2

    return counts


def main():
    strict = '--strict' in sys.argv
    problems = 0
    pending = 0

    text = gen_fittings.document()
    current = read(gen_fittings.OUTPUT) if os.path.exists(gen_fittings.OUTPUT) else None
    if current is None:
        print('MISSING   %s does not exist: run tools/gen_fittings.py' % os.path.relpath(gen_fittings.OUTPUT, gen_fittings.ROOT))
        return 1
    if current != text:
        print('STALE     %s is not what the models give: run tools/gen_fittings.py' % os.path.relpath(gen_fittings.OUTPUT, gen_fittings.ROOT))
        problems += 1

    table = json.loads(current)
    if table.get('format') != gen_fittings.FORMAT:
        print('FORMAT    fittings.json says format %r, the code reads %d' % (table.get('format'), gen_fittings.FORMAT))
        problems += 1

    models = table.get('models', {})
    for resource, groups in models.items():
        for name, box in groups.items():
            low, high = box['min'], box['max']
            if len(low) != 3 or len(high) != 3 or not all(math.isfinite(v) for v in low + high) \
                    or any(a > b for a, b in zip(low, high)):
                print('BOX       %s %s: min %s max %s is not a box' % (resource, name, low, high))
                problems += 1

    counts = catalogue_counts()
    family_seen = set()

    for model, names, runs, body in definitions():
        if model is not None:
            resource = '%s:models/%s.obj' % (gen_fittings.NAMESPACE, model)
            groups = models.get(resource)
            if groups is None:
                if not os.path.exists(os.path.join(gen_fittings.ASSETS, 'models', *model.split('/')) + '.obj'):
                    print('PENDING   %s is named by ModelDefinitions but is not on disk: its fittings are not checked' % resource)
                    pending += 1
                else:
                    print('ABSENT    %s is on disk and not in fittings.json' % resource)
                    problems += 1
                continue

            wanted = list(names)
            for prefix, suffix, count, _ in runs:
                if count is None:
                    print('COUNT     %s: the length of the run %s...%s is not a literal here' % (resource, prefix, suffix))
                    problems += 1
                    continue
                wanted += ['%s%d%s' % (prefix, i, suffix) for i in range(1, count + 1)]

            for name in wanted:
                if name not in groups:
                    print('NO GROUP  %s has no group "%s" that its definition hangs a wire on' % (resource, name))
                    problems += 1
            continue

        # a family: the model comes from the specification, the number of fittings from the catalogue
        for prefix, suffix, count, expression in runs:
            for family_model, number in sorted(counts.items()):
                resource = '%s:models/%s/%s.obj' % (gen_fittings.NAMESPACE, family_model, family_model)
                groups = models.get(resource)
                if groups is None or '%s1%s' % (prefix, suffix) not in groups:
                    continue

                family_seen.add(family_model)
                for i in range(1, number + 1):
                    if '%s%d%s' % (prefix, i, suffix) not in groups:
                        print('NO GROUP  %s has no group "%s%d%s" and the catalogue says %d fittings'
                              % (resource, prefix, i, suffix, number))
                        problems += 1
                extra = '%s%d%s' % (prefix, number + 1, suffix)
                if extra in groups:
                    print('EXTRA     %s draws "%s", which the catalogue (%d fittings) never lists: a wire could not be hung on it'
                          % (resource, extra, number))
                    problems += 1

    for model in sorted(set(counts) - family_seen):
        print('PENDING   the catalogue lists %s but no family definition matched it' % model)
        problems += 1

    if problems or (strict and pending):
        print('\n%d problem(s)%s' % (problems, ', %d model(s) pending' % pending if pending else ''))
        return 1

    print('fittings.json is what the models say, and complete for every definition%s'
          % (' (%d model(s) pending)' % pending if pending else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
