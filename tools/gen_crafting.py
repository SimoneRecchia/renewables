#!/usr/bin/env python3
"""Recipes, their recipe-book advancements, part item models, blockstates, loot tables and the tool tags; and the
language keys of the parts, which gen_core_data writes.

    python3 tools/gen_crafting.py

Every file is planned first and checked before any is written (tree_problems): every item the mod registers has
a recipe, an item model whose sprite is on disk and a name; every block a blockstate and, but the shell, a loot
table; every part is used; no two recipes share a grid; every recipe has the advancement that puts it in the
recipe book; and nothing is left on disk in recipes/ or advancements/ that no generator writes.  Any of those
exits 1 with nothing written.
"""

import json
import os
import re
import sys

DATA = os.path.join('src', 'main', 'resources', 'data', 'renewables', 'recipes')
ITEM_MODELS = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'models', 'item')
BLOCKSTATES = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'blockstates')
LOOT = os.path.join('src', 'main', 'resources', 'data', 'renewables', 'loot_tables', 'blocks')
TAGS = os.path.join('src', 'main', 'resources', 'data', 'minecraft', 'tags', 'blocks')
SPRITES = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures')
JAVA = os.path.join('src', 'main', 'java', 'io', 'github', 'simonerecchia', 'renewables')
CATALOGUE = os.path.join(JAVA, 'catalog', 'PartCatalog.java')

SHAPED = 'minecraft:crafting_shaped'

# Every ingredient letter used below and in gen_core_data.CORE_RECIPES, resolved once, so a letter means the same
# thing in every recipe of the mod: X is always a gearbox, G always a bridge.  A '#' is an item tag, and the
# vanilla stock is named by tag wherever one says the same thing - charcoal is a coal, and a modpack's iron,
# copper, quartz, slime and glass are accepted as vanilla's own are.
ITEMS = {
    # vanilla
    'i': '#forge:ingots/iron',
    'n': '#forge:nuggets/iron',
    'c': '#forge:ingots/copper',
    'o': '#minecraft:coals',
    'r': '#forge:dusts/redstone',
    'g': '#forge:nuggets/gold',
    'q': '#forge:gems/quartz',
    'a': '#forge:sand',
    'y': '#forge:glass_panes/colorless',
    'W': '#forge:glass/colorless',
    'v': '#forge:stone',
    # no tag holds honeycomb
    'h': 'minecraft:honeycomb',
    'm': '#forge:slimeballs',
    'l': '#forge:storage_blocks/iron',
    # the mod's own older parts
    'C': 'renewables:circuit_board',
    'U': 'renewables:cpu',
    'I': 'renewables:insulator',
    'M': 'renewables:metal_casing',
    'K': 'renewables:motor_core',
    'R': 'renewables:screen',
    # raw stock
    'P': 'renewables:steel_plate',
    'S': 'renewables:steel_section',
    'A': 'renewables:tempered_glass',
    'F': 'renewables:resin',
    'O': 'renewables:copper_coil',
    'N': 'renewables:silicon_ingot',
    'Z': 'renewables:silicon_wafer',
    'B': 'renewables:busbar',
    # components
    'V': 'renewables:solar_cell',
    'd': 'renewables:bypass_diode',
    'j': 'renewables:mc4_connector',
    'J': 'renewables:junction_box',
    'w': 'renewables:power_module',
    'k': 'renewables:capacitor_bank',
    'e': 'renewables:magnetic_core',
    'b': 'renewables:control_board',
    'Y': 'renewables:bearing',
    'H': 'renewables:gear_set',
    'f': 'renewables:gpv_fuse',
    'Q': 'renewables:load_break_switch',
    'p': 'renewables:sensor_head',
    # assemblies
    'L': 'renewables:pv_laminate',
    'E': 'renewables:enclosure',
    'D': 'renewables:dc_section',
    'G': 'renewables:inverter_bridge',
    'T': 'renewables:mounting_rack',
    't': 'renewables:torque_tube',
    'u': 'renewables:slew_drive',
    'x': 'renewables:generator_set',
    'X': 'renewables:gearbox',
    's': 'renewables:turbine_blade',
    'z': 'renewables:long_blade',
}

# (result, count, type, pattern) - the tree, top to bottom.
RECIPES = [
    # ------------------------------------------------------------------------- raw stock
    ('steel_plate', 3, SHAPED, ['iii', 'ooo']),
    ('steel_section', 2, SHAPED, ['P', 'P']),
    ('busbar', 2, SHAPED, ['ccc']),
    ('copper_coil', 1, SHAPED, ['BBB', ' n ']),
    # a film of polymer and a wax: what a laminate is glued together with and what a blade's fibre is
    # bound in
    ('resin', 2, SHAPED, ['mh']),
    # sawn from the ingot, which is where every cell and every switching die starts
    ('silicon_wafer', 4, SHAPED, ['N']),

    # ------------------------------------------------------------------------ components
    ('solar_cell', 3, SHAPED, ['ZZZ', ' r ', 'BBB']),
    ('bypass_diode', 2, SHAPED, ['q', 'r', 'g']),
    ('mc4_connector', 4, SHAPED, ['II', 'BB']),
    # three diodes, because a sixty-cell module has three substrings and each one gets its own
    ('junction_box', 1, SHAPED, [' d ', 'dMd', ' j ']),
    # switching dies on a substrate over a baseplate
    ('power_module', 1, SHAPED, [' Z ', 'BCB', ' P ']),
    # a film capacitor bank in a steel can: polymer film, charge
    ('capacitor_bank', 1, SHAPED, ['FrF', 'FrF', 'PPP']),
    # laminated iron with a winding through it
    ('magnetic_core', 1, SHAPED, ['PPP', 'OOO', 'PPP']),
    ('control_board', 1, SHAPED, [' U ', 'CBC']),
    ('bearing', 2, SHAPED, [' P ', 'nPn', ' P ']),
    ('gear_set', 2, SHAPED, [' S ', 'SPS', ' S ']),
    # a glass barrel, silver element and sand filler
    ('gpv_fuse', 4, SHAPED, ['yay', ' B ']),
    # contacts, an arc chamber and the snap mechanism that makes it break rather than draw
    ('load_break_switch', 1, SHAPED, [' I ', 'BSB', ' H ']),
    ('sensor_head', 2, SHAPED, [' A ', 'CBC']),

    # ------------------------------------------------------------------------ assemblies
    ('pv_laminate', 1, SHAPED, ['AFA', 'VVV', 'SJS']),
    # eight plates and a gasket: an enclosure is mostly steel, which is why a cabinet costs what it does
    ('enclosure', 1, SHAPED, ['PPP', 'PIP', 'PPP']),
    ('dc_section', 1, SHAPED, ['fff', 'BQB', 'PPP']),
    ('inverter_bridge', 1, SHAPED, ['www', 'kbk', 'BeB']),
    ('mounting_rack', 1, SHAPED, ['S S', 'SSS', 'P P']),
    ('torque_tube', 1, SHAPED, ['SSS', 'YPY']),
    ('slew_drive', 1, SHAPED, [' H ', 'YKY', ' b ']),
    # stator windings round a rotor, in a frame
    ('generator_set', 1, SHAPED, ['OOO', 'ele', 'SSS']),
    ('gearbox', 1, SHAPED, ['HHH', 'YPY', 'PPP']),
    # glass fibre in resin over a steel spar, which is how a blade is actually built
    ('turbine_blade', 1, SHAPED, ['AAA', 'FSF', 'AAA']),
    # the same blade with more fibre on a stronger spar: a longer rotor for a quieter site
    ('long_blade', 1, SHAPED, ['AAA', 'FsF', 'AAA']),

    # ------------------------------------------------------------------- the line conductors
    ('abc_conductor', 8, SHAPED, ['BBB', 'FFF', 'SSS']),
    ('mv_conductor', 6, SHAPED, ['BBB', 'PPP']),
    ('hv_conductor', 4, SHAPED, ['BBB', 'SSS', 'BBB']),

    # --------------------------------------------------------------------------- the cables
    ('dc_string_cable', 6, SHAPED, ['BBB', 'FFF']),
    ('dc_trunk_cable', 4, SHAPED, ['BBB', 'FFF', 'PPP']),

    # --------------------------------------------------------------------------- the arrays
    ('solar_panel', 1, SHAPED, ['LLL', ' T ', 'jSj']),
    ('pv_flat_430', 1, SHAPED, ['LLL', 'PTP', 'jSj']),
    ('pv_tilt_530', 1, SHAPED, ['LLL', 'STS', 'jSj']),
    # the 580 W module is the large-format one, so it is the same rack with a fourth laminate of silicon
    ('pv_tilt_580', 1, SHAPED, ['LLL', 'STS', 'jLj']),
    ('pv_track_700', 1, SHAPED, ['LLL', 'tut', 'jbj']),
    # two slew drives, because a dual-axis frame has two axes to turn
    ('pv_dual_440', 1, SHAPED, ['LLL', 'uSu', 'jbj']),

    # ------------------------------------------------------------------------- the inverters
    # Bridges over an enclosure with a control board under it.
    ('inverter_10', 1, SHAPED, [' G ', ' E ', ' b ']),
    ('inverter_110', 1, SHAPED, ['GGG', ' E ', 'KbK']),
    ('inverter_350', 1, SHAPED, ['GGG', 'DED', 'KbK']),
    ('inverter_2500', 1, SHAPED, ['GGG', 'BEB', 'KbK']),

    # ------------------------------------------------------------------------- the combiners
    # the output, and the thirty-two way box is the one built to IP66.
    ('pv_combiner_6', 1, SHAPED, ['fff', ' E ', ' Q ']),
    ('pv_combiner_16', 1, SHAPED, ['fff', 'BEB', ' Q ']),
    ('pv_combiner_32', 1, SHAPED, ['fff', 'BEB', 'PQP']),

    # ------------------------------------------------------------------- the met station
    # A logger with a display on it, which is what the model draws and what put the screen back in the tree:
    # nothing else consumed one, so it was an item a player could craft and never use.
    ('met_station', 1, SHAPED, [' p ', 'pbp', 'SRS']),

    # -------------------------------------------------------------- the plant controller
    # A cabinet with a controller and a screen in it, over the switchgear it is wired to.  Two control
    # boards rather than one: the controller and the gateways behind the same door are two devices.
    ('plant_controller', 1, SHAPED, ['ScS', 'bEb', 'PQP']),

    # ------------------------------------------------------------------- the transformers
    # A tank, a core with windings on it, and the bushings the windings leave through.  The substation
    # unit is the same machine with radiators, a conservator and a tap changer on it.
    ('tx_machine', 1, SHAPED, ['III', 'OeO', 'PPP']),
    ('tx_substation', 1, SHAPED, ['III', 'ete', 'PQP']),

    # ------------------------------------------------------------------------ the switches
    # A disconnector is three post insulators a side, a copper blade and a frame: no arc-breaking parts at
    # all, which is exactly why it cannot be opened under load.  A breaker is the same posts over three
    # load-break interrupters and the mechanism that throws them.
    ('mv_disconnector', 1, SHAPED, ['III', 'BSB', 'SSS']),
    ('mv_breaker', 1, SHAPED, ['III', 'QQQ', 'PbP']),

    # ---------------------------------------------------------------- the transmission towers
    # Steel sections, plate gussets and the insulator strings the phases hang from.  The duty is what
    # costs: a suspension tower is only holding weight, a tension tower is braced for the difference
    # between two pulls, and a terminal tower takes all of it from one side.
    ('lattice_suspension', 1, SHAPED, ['SIS', 'SPS', 'S S']),
    ('lattice_tension', 1, SHAPED, ['SIS', 'SPS', 'SPS']),
    ('lattice_terminal', 1, SHAPED, ['SIS', 'PPP', 'SPS']),

    # ------------------------------------------------------------------- towers and turbines
    ('turbine_tower', 2, SHAPED, ['PPP', 'n n', 'PPP']),
    # Three blades over a drivetrain, and the drivetrain is where the machines differ.
    ('sw_10', 1, SHAPED, ['sss', ' x ', 'SbS']),
    ('c52_085', 1, SHAPED, ['sss', 'XxY', 'ubE']),
    # long blades: an eighty metre rotor on the same drivetrain
    ('c80_20', 1, SHAPED, ['zzz', 'XxY', 'ubE']),
    # three megawatts: the generator doubles
    ('c90_30', 1, SHAPED, ['zzz', 'Xxx', 'ubE']),
    # the same generator pair as the C90 and no gearbox - a second main bearing instead
    # direct drive is, and the reason these two share a nameplate on different rotors
    ('c112_30', 1, SHAPED, ['zzz', 'Yxx', 'ubE']),
    # four megawatts, direct drive, and all generator
    ('wind_turbine', 1, SHAPED, ['zzz', 'xxx', 'ubE']),
]


def parts():
    """The parts, out of PartCatalog.java, so this file cannot disagree with the registry."""
    source = open(CATALOGUE).read()
    found = []
    for match in re.finditer(r'new Part\("([a-z0-9_]+)", Tier\.(\w+), "([^"]+)",\s*"([^"]+)"\)', source):
        found.append(match.groups())

    return found


def java_source():
    """Every Java file of the mod, concatenated: what a key or an id has to be found in."""
    text = []
    for root, _, names in sorted(os.walk(JAVA)):
        text += [open(os.path.join(root, name)).read() for name in sorted(names) if name.endswith('.java')]
    return '\n'.join(text)


def catalogue_ids(catalogue):
    """The spec ids a catalogue registers, in the order it writes them."""
    source = open(os.path.join(JAVA, 'catalog', catalogue + '.java')).read()
    return re.findall(r'new Part\("([a-z0-9_]+)"' if catalogue == 'PartCatalog' else r'\bid\("([a-z0-9_]+)"\)', source)


# A family loop in a register: `for (Spec spec : Catalog.all()) { ... }`, closed at the static block's indent.
FAMILY = re.compile(r'for \((?:\w+\.)?\w+ \w+ : (\w+)\.all\(\)\) \{(.*?)\n\t\t\}', re.S)


def register_ids(register, kind):
    """{id: whether its item shows tooltip.renewables.<id>} for every entry ModItems or ModBlocks registers.

    Read out of the register rather than listed here, so an item added in Java is one this file checks.  A literal
    registration, the block()/plain() helpers (which always show the tooltip), and one entry per spec in each
    family loop - less the spec a loop skips with `continue` because a single above registered it by name (the C130,
    which is wind_turbine).
    """
    source = open(os.path.join(JAVA, 'registry', register + '.java')).read()
    out = {name: False for name in re.findall(r'%s\.register\("([a-z0-9_]+)"' % kind, source)}
    out.update({name: True for name in re.findall(r'\b(?:block|plain)\("([a-z0-9_]+)"', source)})
    for catalogue, body in FAMILY.findall(source):
        catalogue_source = open(os.path.join(JAVA, 'catalog', catalogue + '.java')).read()
        skipped = set()
        for constant in re.findall(r'spec == %s\.(\w+)\)' % catalogue, body):
            skipped |= set(re.findall(r'\b%s = \w+\.register\(new \w+\(\s*id\("([a-z0-9_]+)"' % constant,
                                      catalogue_source))

        suffix = re.search(r'getPath\(\) \+ "([a-z0-9_]+)"', body)
        made = re.findall(r'new (\w+)\(', body)
        shows = 'tooltip.renewables.' in body or 'plain(' in body or any(
            '"tooltip.renewables." + spec.id()' in open(path).read()
            for path in (os.path.join(JAVA, 'item', cls + '.java') for cls in made) if os.path.exists(path))
        for spec_id in catalogue_ids(catalogue):
            if spec_id not in skipped:
                out[spec_id + (suffix.group(1) if suffix else '')] = shows
    return out


def registered():
    """(items, blocks): every registry id the mod has, items mapped to whether they show their own tooltip."""
    return register_ids('ModItems', 'ITEMS'), set(register_ids('ModBlocks', 'BLOCKS'))


def ingredient(name):
    """An ingredient as a recipe or a trigger names it: {'tag': ...} for a '#' name, {'item': ...} otherwise."""
    return {'tag': name[1:]} if name.startswith('#') else {'item': name}


def key_for(pattern):
    """The key map for a pattern: every letter in it, resolved through ITEMS."""
    letters = {c for row in pattern for c in row if c != ' '}
    unknown = sorted(c for c in letters if c not in ITEMS)
    if unknown:
        raise SystemExit('gen_crafting: no ingredient for %s' % ', '.join(unknown))

    return {c: ingredient(ITEMS[c]) for c in sorted(letters)}


def shaped(result, count, pattern):
    recipe = {'type': SHAPED, 'pattern': pattern, 'key': key_for(pattern), 'result': {'item': 'renewables:' + result}}
    if count > 1:
        recipe['result']['count'] = count
    return recipe


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def name_of(entry):
    """'#forge:ingots/iron' or 'renewables:busbar' for one key entry or ingredient."""
    return '#' + entry['tag'] if 'tag' in entry else entry['item']


def grid_of(pattern, key):
    """A recipe's grid as the game matches it: the ingredients, laid out, with the letters resolved away."""
    rows = tuple(tuple('.' if c == ' ' else name_of(key[c]) for c in row) for row in pattern)
    return min(rows, tuple(row[::-1] for row in rows))


def ingredients_of(recipe):
    """Every distinct ingredient a recipe takes, by name."""
    if 'key' in recipe:
        return sorted({name_of(entry) for entry in recipe['key'].values()})
    return [name_of(recipe['ingredient'])]


ADVANCEMENTS = os.path.join('src', 'main', 'resources', 'data', 'renewables', 'advancements', 'recipes')


def advancement(recipe_id, recipe):
    """What puts a recipe in the recipe book: holding any one of its ingredients, as vanilla's own recipes do.

    In 1.20.1 a recipe with no advancement is in the book only after it has been made once by hand, and with
    doLimitedCrafting it can never be made at all.  The reward is the recipe; the trigger is never shown.
    """
    criteria = {}
    for name in ingredients_of(recipe):
        predicate = {'tag': name[1:]} if name.startswith('#') else {'items': [name]}
        criteria['has_' + re.sub(r'[^a-z0-9]+', '_', name.split(':', 1)[1])] = {
            'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [predicate]}}
    criteria['has_the_recipe'] = {'trigger': 'minecraft:recipe_unlocked',
                                  'conditions': {'recipe': 'renewables:' + recipe_id}}
    return {'parent': 'minecraft:recipes/root', 'criteria': criteria, 'requirements': [sorted(criteria)],
            'rewards': {'recipes': ['renewables:' + recipe_id]}, 'sends_telemetry_event': False}


def advancement_path(recipe_id, blocks):
    """recipes/machines/<id> for something placed, recipes/items/<id> for everything held."""
    return os.path.join(ADVANCEMENTS, 'machines' if recipe_id in blocks else 'items', recipe_id + '.json')


# The two furnace steps, which are the only recipes that are not a bench: (result, ingredient letter).
SMELTING = [('tempered_glass', 'W'), ('silicon_ingot', 'q')]


def recipe_files():
    """{path: recipe} for every recipe this file owns."""
    out = {os.path.join(DATA, result + '.json'): shaped(result, count, pattern)
           for result, count, _, pattern in RECIPES}
    for result, letter in SMELTING:
        out[os.path.join(DATA, result + '.json')] = {
            'type': 'minecraft:smelting', 'ingredient': ingredient(ITEMS[letter]),
            'result': 'renewables:' + result, 'experience': 0.1, 'cookingtime': 200}
    return out


def tree_problems(own, core):
    """Everything the tree must be, checked on what is about to be written, before any of it is.

    own and core are {path: data} for this file and gen_core_data.  A row taken out of RECIPES leaves its item
    with no recipe and its old JSON on disk, and both are reported: the tree used to call itself consistent
    with the VC-2500K uncraftable.
    """
    problems = []
    items, blocks = registered()
    planned = dict(core)
    planned.update(own)

    results = [result for result, _, _, _ in RECIPES] + [result for result, _ in SMELTING]
    for result in sorted({r for r in results if results.count(r) > 1}):
        problems.append('%s is the result of more than one row of RECIPES' % result)

    recipes = {path: data for path, data in planned.items() if os.path.dirname(path) == DATA}
    made, grids, smelted = {}, {}, {}
    for path, recipe in sorted(recipes.items()):
        name = os.path.basename(path)[:-5]
        result = recipe['result']['item'] if isinstance(recipe['result'], dict) else recipe['result']
        made.setdefault(result.split(':')[1], []).append(name)
        for entry in ingredients_of(recipe):
            if entry.startswith('renewables:') and entry.split(':')[1] not in items:
                problems.append('%s takes %s, which the mod does not register' % (name, entry))
        if 'pattern' in recipe:
            grid = grid_of(recipe['pattern'], recipe['key'])
            if grid in grids:
                problems.append('%s and %s have the same grid, so one of them is uncraftable' % (grids[grid], name))
            grids[grid] = name
        else:
            source = ingredients_of(recipe)[0]
            if source in smelted:
                problems.append('%s and %s smelt the same thing' % (smelted[source], name))
            smelted[source] = name

    used = {entry.split(':')[1] for recipe in recipes.values() for entry in ingredients_of(recipe)
            if entry.startswith('renewables:')}
    for part_id, _, _, _ in parts():
        if part_id not in used:
            problems.append('%s is made and never used' % part_id)

    import gen_core_data
    lang, _ = gen_core_data.lang_table()
    for item in sorted(items):
        if item not in made:
            problems.append('%s has no recipe' % item)
        model_path = os.path.join(ITEM_MODELS, item + '.json')
        model = planned.get(model_path) or (json.load(open(model_path)) if os.path.exists(model_path) else None)
        if model is None:
            problems.append('%s has no item model' % item)
        else:
            sprite = model.get('textures', {}).get('layer0', '').split(':')[-1]
            if not os.path.exists(os.path.join(SPRITES, sprite + '.png')):
                problems.append('%s shows textures/%s.png, which is not on disk' % (item, sprite))
        if ('block.renewables.' if item in blocks else 'item.renewables.') + item not in lang:
            problems.append('%s has no name' % item)

    for block in sorted(blocks):
        state = os.path.join(BLOCKSTATES, block + '.json')
        if state not in planned and not os.path.exists(state):
            problems.append('block %s has no blockstate' % block)
        if block != 'machine_shell' and os.path.join(LOOT, block + '.json') not in planned:
            problems.append('block %s has no loot table' % block)

    # what is on disk and nobody writes any more: the recipe of a row taken out, its advancement
    for folder in (DATA, ADVANCEMENTS):
        for root, _, names in os.walk(folder):
            for name in names:
                path = os.path.join(root, name)
                if name.endswith('.json') and path not in planned:
                    problems.append('%s is on disk and no generator writes it' % path)

    for path in recipes:
        recipe_id = os.path.basename(path)[:-5]
        if advancement_path(recipe_id, blocks) not in planned:
            problems.append('%s has no advancement, so the recipe book never shows it' % recipe_id)
    return problems


# A machine drawn by the mod's own OBJ renderer has an INVISIBLE render shape, so its blockstate only
# needs to name a particle texture - one variant a facing, all four the same.  Its item is a flat sprite.
# Every machine the mod draws itself, with the particle it breaks into and the sprite its item shows.
#
# RenderShape.INVISIBLE means the blockstate's model is *only* the break particle, so every one of these is
# one unconditional multipart.  Not a variant per facing: a variants block has to name every state the block
# has, and the kiosk has a `mounted` and a switch has an `open` that four `facing=` keys never named - so
# half of each one's states resolved to no model at all.
#
# The sprite is not always the block's own name, because a family shares one drawing: four inverters are one
# cabinet and six arrays are four mountings.  None means the block has no item - the shell cell is placed by
# its machine and never held.
MACHINES = {
    'utility_pole': ('stone_particle', 'utility_pole'),
    'electric_cabin': ('stone_particle', 'cab'),
    'power_box': ('stone_particle', 'power_box'),
    'turbine_tower': ('stone_particle', 'turbine_tower'),
    'met_station': ('metal_particle', 'met_station'),
    'plant_controller': ('metal_particle', 'plant_controller'),
    'machine_shell': ('metal_particle', None),
    'sw_10': ('stone_particle', 'sw_10'),
    'c52_085': ('stone_particle', 'c52_085'),
    'c80_20': ('stone_particle', 'c80_20'),
    'c90_30': ('stone_particle', 'c90_30'),
    'c112_30': ('stone_particle', 'c112_30'),
    'wind_turbine': ('stone_particle', 'wind_turbine'),
    'solar_panel': ('glass_particle', 'pv_flat'),
    'pv_flat_430': ('glass_particle', 'pv_flat'),
    'pv_tilt_530': ('glass_particle', 'pv_tilt'),
    'pv_tilt_580': ('glass_particle', 'pv_tilt'),
    'pv_track_700': ('glass_particle', 'pv_track'),
    'pv_dual_440': ('glass_particle', 'pv_dual'),
    'inverter_10': ('metal_particle', 'pv_inverter'),
    'inverter_110': ('metal_particle', 'pv_inverter'),
    'inverter_350': ('metal_particle', 'pv_inverter'),
    'inverter_2500': ('metal_particle', 'pv_inverter'),
    'pv_combiner_6': ('metal_particle', 'pv_combiner'),
    'pv_combiner_16': ('metal_particle', 'pv_combiner'),
    'pv_combiner_32': ('metal_particle', 'pv_combiner'),
    'tx_machine': ('metal_particle', 'tx_machine'),
    'tx_substation': ('metal_particle', 'tx_substation'),
    'lattice_suspension': ('stone_particle', 'lattice_suspension'),
    'lattice_tension': ('stone_particle', 'lattice_tension'),
    'lattice_terminal': ('stone_particle', 'lattice_terminal'),
    'mv_disconnector': ('metal_particle', 'mv_disconnector'),
    'mv_breaker': ('metal_particle', 'mv_breaker'),
}

# The names and tooltips for what is not a part: a block is named here rather than by hand.  None is a block
# with no item, so no tooltip ever shows; gen_core_data refuses a grey line nothing reads.
BLOCK_NAMES = {
    # A run of conductor has a name even though nothing ever shows it: a block with no name key reads as
    # its own registry id in an advancement, in a death message, and in the F3 screen.
    'abc_conductor_run': ('Bundle Run', None),
    'mv_conductor_run': ('Medium-Voltage Run', None),
    'hv_conductor_run': ('Transmission Run', None),
    'tx_machine': ('Machine Transformer',
                   'Steps a generator\u2019s 800 V up to the 33 kV collector, at 2.5 MVA. A turbine or an inverter may also '
                   'feed a cabin without one.'),
    'tx_substation': ('Substation Transformer',
                      'Steps 33 kV up to 400 kV. The transmission line starts here.'),
    'lattice_suspension': ('Suspension Tower', 'Holds a 400 kV line up. Nine towers in ten are one.'),
    'lattice_tension': ('Tension Tower', 'Takes the difference between the pulls either side of it.'),
    'lattice_terminal': ('Terminal Tower', 'Takes the whole pull of a line from one side, on the widest foot.'),
    'mv_disconnector': ('Disconnector',
                        'Makes a gap in a 33 kV line that you can see. Will not open under load: open the '
                        'breaker first.'),
    'plant_controller': ('Plant Controller',
                        'Holds every turbine and inverter within 64 blocks to one setpoint, shared out by '
                        'rating. A comparator on it reads how loaded the plant is.'),
    'mv_breaker': ('Circuit Breaker',
                   'Breaks a 33 kV line under load, and trips itself above 36 MW. Its contacts are in a '
                   'vacuum, so all it shows is a flag.'),
}


# Which machines need iron and up: the heavy steel - a tower, a transformer, a switch and every machine that was
# already here.  Every other machine (arrays, inverters, combiners, the met mast, the controller) needs stone and
# up, as docs/GUIDA.md section 9 tells a player: without needs_stone_tool a wooden pickaxe was enough.
NEEDS_IRON = ('utility_pole', 'electric_cabin', 'power_box', 'turbine_tower',
              'sw_10', 'c52_085', 'c80_20', 'c90_30', 'c112_30', 'wind_turbine',
              'lattice_suspension', 'lattice_tension', 'lattice_terminal',
              'tx_machine', 'tx_substation', 'mv_disconnector', 'mv_breaker')


# The lines a machine says to a player.  Here rather than by hand in the language file for the reason the
# block names are: a key with no entry reads as its own id in the corner of the screen.
MESSAGES = {
    'message.renewables.switch.under_load':
        'There is load on it. A disconnector cannot break current \u2014 open the breaker first.',
    'message.renewables.switch.opened': 'Open. The gap is visible from the outside.',
    'message.renewables.switch.closed': 'Closed.',
    'message.renewables.wire.wrong_class':
        'Neither of those fittings is built for %s. Match the conductor to the voltage.',
}


# What a broken block gives back: itself, unless it is a run of conductor, which gives back the reel.
# A machine drops itself, so the table is the register - every machine in MACHINES, plus the runs, which
# are not machines and drop the reel they were laid from rather than a block of their own.
#
# It was a hand-written list of ten, and the mod has thirty-two machines: the twenty-six older tables sat on
# disk with no generator behind them, which is the drift CLAUDE.md section 6 is about, and the *next* machine
# added simply got no loot table.  With requiresCorrectToolForDrops() that block breaks into nothing.
RUN_DROPS = {
    'abc_conductor_run': 'abc_conductor',
    'mv_conductor_run': 'mv_conductor',
    'hv_conductor_run': 'hv_conductor',
    'dc_string_cable': 'dc_string_cable',
    'dc_trunk_cable': 'dc_trunk_cable',
}


def lang_entries():
    """The (key, text) pairs this file owns.  gen_core_data writes them: the language file has one writer."""
    out = []
    for part_id, _, name, tooltip in parts():
        out += [('item.renewables.' + part_id, name), ('tooltip.renewables.' + part_id, tooltip)]
    for block_id, (name, tooltip) in BLOCK_NAMES.items():
        out.append(('block.renewables.' + block_id, name))
        if tooltip is not None:
            out.append(('tooltip.renewables.' + block_id, tooltip))
    return out + list(MESSAGES.items())


def drops():
    out = {name: name for name in MACHINES if name != 'machine_shell'}
    out.update(RUN_DROPS)
    return out


DROPS = drops()


def loot_tables():
    """One drop per block this file owns.  Without one a block breaks into nothing, silently."""
    return {os.path.join(LOOT, block_id + '.json'): {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': 'renewables:' + item_id}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
    } for block_id, item_id in DROPS.items()}


def tool_tags():
    """Which tool every machine needs, written from MACHINES so a new one cannot be left out.

    A block with requiresCorrectToolForDrops() and no mineable tag breaks into nothing whatever you hit it
    with, and its loot table never fires. Seven of them were in that state - three towers, two transformers
    and both switches - because the tag was written by hand and the machines were added after it.
    """
    # The shell is in both tags.  Every machine that fills cells is iron-tier, and a cell that breaks as the
    # machine does has to be as quick to break and as particular about its tool, or breaking the cell by hand
    # would be a way round the pickaxe the machine asks for.
    return {
        os.path.join(TAGS, 'mineable', 'pickaxe.json'):
            {'replace': False, 'values': sorted('renewables:' + name for name in MACHINES)},
        os.path.join(TAGS, 'needs_iron_tool.json'):
            {'replace': False, 'values': sorted('renewables:' + name for name in NEEDS_IRON + ('machine_shell',))},
        os.path.join(TAGS, 'needs_stone_tool.json'):
            {'replace': False, 'values': sorted('renewables:' + name for name in needs_stone())},
    }


def needs_stone():
    """Every machine not in NEEDS_IRON: the shell is iron-tier with the machines that fill cells."""
    return [name for name in MACHINES if name not in NEEDS_IRON and name != 'machine_shell']


def machine_assets():
    """The blockstate and item model for every machine the mod draws itself - see MACHINES."""
    out = {}
    for name, (particle, sprite) in MACHINES.items():
        out[os.path.join(BLOCKSTATES, name + '.json')] = {
            'multipart': [{'apply': {'model': 'renewables:block/' + particle}}]}
        if sprite is not None:
            out[os.path.join(ITEM_MODELS, name + '.json')] = {
                'parent': 'minecraft:item/generated', 'textures': {'layer0': 'renewables:item/' + sprite}}
    return out


def planned():
    """{path: data} for every file this generator writes."""
    out = machine_assets()
    out.update(loot_tables())
    out.update(tool_tags())
    # one item model per part: a flat sprite, like every other ingredient in the game
    for part_id, _, _, _ in parts():
        out[os.path.join(ITEM_MODELS, part_id + '.json')] = {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': 'renewables:item/' + part_id}}

    recipes = recipe_files()
    out.update(recipes)
    _, blocks = registered()
    for path, recipe in recipes.items():
        recipe_id = os.path.basename(path)[:-5]
        out[advancement_path(recipe_id, blocks)] = advancement(recipe_id, recipe)
    return out


def main():
    import gen_core_data
    own = planned()
    problems = tree_problems(own, gen_core_data.planned())
    if problems:
        for line in problems:
            print('    PROBLEM %s' % line)
        print('%d problem(s) in the tree: nothing written' % len(problems))
        sys.exit(1)

    for path, data in own.items():
        write(path, data)

    recipes = [path for path in own if os.path.dirname(path) == DATA]
    print('%d parts, %d recipes (%d furnace), %d advancements, %d machine blockstates, %d loot tables, '
          '%d pickaxe-mineable' % (len(parts()), len(recipes), len(SMELTING),
                                   sum(1 for path in own if path.startswith(ADVANCEMENTS)), len(MACHINES),
                                   len(DROPS), len(MACHINES)))
    print('the tree is consistent: every item made, named, drawn and in the recipe book')


if __name__ == '__main__':
    main()
