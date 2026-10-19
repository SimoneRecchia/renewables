#!/usr/bin/env python3
"""Recipes, their recipe-book advancements, part item models, blockstates, loot tables and the tool tags; and the
language keys of the parts, which gen_core_data writes.

    python3 tools/gen_crafting.py

Every machine's recipe costs what the real one weighs, at TONNES_PER_UNIT tonnes of steel, conductor metal, glass or
silicon to the ingot - the formula and its sources are under "what a recipe costs" below, and

    python3 tools/gen_crafting.py --costs

prints every machine's bill against its recipe without writing anything.

Every file is planned first and checked before any is written (tree_problems): every item the mod registers has
a recipe, an item model whose sprite is on disk and a name; every block a blockstate and, but the shell, a loot
table; every part is used; no two recipes share a grid; every recipe has the advancement that puts it in the
recipe book; nothing is left on disk in recipes/ or advancements/ that no generator writes; and every machine
costs its bill within COST_BAND (or no more than FLOOR over it), each family dearer as it grows.  Any of those exits
1 with nothing written.
"""

import collections
import json
import math
import os
import re
import sys

DATA = os.path.join('src', 'main', 'resources', 'data', 'renewables', 'recipes')
ITEM_MODELS = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'models', 'item')
BLOCKSTATES = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'blockstates')
LOOT = os.path.join('src', 'main', 'resources', 'data', 'renewables', 'loot_tables', 'blocks')
TAGS = os.path.join('src', 'main', 'resources', 'data', 'minecraft', 'tags', 'blocks')
SPRITES = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures')
# The C130 kept the registry name of the machine the mod started with (ModBlocks).
TURBINE_ALIASES = {'c130_40': 'wind_turbine'}
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
    '$': '#forge:storage_blocks/copper',
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
    '%': 'renewables:tower_section',
}

# (result, count, type, pattern) - the tree, top to bottom.  What each one costs is held to the real machine by
# cost_problems(); `python3 tools/gen_crafting.py --costs` prints the table.
RECIPES = [
    # ------------------------------------------------------------------------- raw stock
    # Stock comes out by the dozen: an ingot is TONNES_PER_UNIT of metal, and a plate is a twelfth of one.
    ('steel_plate', 12, SHAPED, ['iii', 'ooo']),
    ('steel_section', 2, SHAPED, ['P', 'P']),
    ('busbar', 24, SHAPED, ['ccc']),
    ('copper_coil', 2, SHAPED, ['BBB', ' n ']),
    # a film of polymer and a wax: what a laminate is glued together with and what a blade's fibre is bound in
    ('resin', 2, SHAPED, ['mh']),
    # sawn from the ingot, which is where every cell and every switching die starts
    ('silicon_wafer', 8, SHAPED, ['N']),

    # ------------------------------------------------------------------------ components
    ('solar_cell', 12, SHAPED, ['ZZZ', 'rBr']),
    ('bypass_diode', 4, SHAPED, ['Z', 'r', 'g']),
    ('mc4_connector', 8, SHAPED, ['II', 'BB']),
    # three diodes, one per substring, potted in resin
    ('junction_box', 4, SHAPED, [' d ', 'dFd', ' j ']),
    # switching dies on a substrate over a baseplate
    ('power_module', 8, SHAPED, [' Z ', 'CBC', ' P ']),
    # a film capacitor bank in a steel can: polymer film, charge
    ('capacitor_bank', 8, SHAPED, ['FrF', 'FrF', 'PPP']),
    # laminated iron with a winding through it
    ('magnetic_core', 16, SHAPED, ['PPP', 'OOO', 'PPP']),
    ('control_board', 4, SHAPED, [' U ', 'CBC']),
    ('bearing', 4, SHAPED, [' P ', 'nPn', ' P ']),
    ('gear_set', 4, SHAPED, [' S ', 'SPS', ' S ']),
    # a glass barrel, silver element and sand filler
    ('gpv_fuse', 8, SHAPED, ['yay', ' B ']),
    # contacts, an arc chamber and the snap mechanism that makes it break rather than draw
    ('load_break_switch', 4, SHAPED, [' I ', 'BSB', ' H ']),
    ('sensor_head', 2, SHAPED, [' y ', 'CBC']),

    # ------------------------------------------------------------------------ assemblies
    # Twelve to a craft: two front sheets make about 33 m2 of module, a sixth of a tonne of glass a laminate.
    ('pv_laminate', 12, SHAPED, ['AFA', 'VVV', 'SJS']),
    ('enclosure', 8, SHAPED, ['PPP', 'PIP', 'PPP']),
    ('dc_section', 4, SHAPED, ['fff', 'BQB', 'PPP']),
    ('inverter_bridge', 2, SHAPED, ['www', 'kbk', 'BeB']),
    ('mounting_rack', 4, SHAPED, ['S S', 'SSS', 'P P']),
    ('torque_tube', 8, SHAPED, ['SSS', 'YPY']),
    ('slew_drive', 4, SHAPED, [' H ', 'YKY', ' b ']),
    # A megawatt of generator: windings round a rotor on a cast frame.  The utility machines take one a megawatt
    # or so, which is where their copper comes from.
    ('generator_set', 1, SHAPED, ['OcO', 'ele', 'SSS']),
    ('gearbox', 1, SHAPED, ['HHH', 'YlY', 'PPP']),
    # glass fibre in resin over a steel spar, which is how a blade is actually built
    ('turbine_blade', 1, SHAPED, ['A', 'F', 'S']),
    # the same blade with more fibre on a stronger spar: a longer rotor for a quieter site
    ('long_blade', 1, SHAPED, ['AFA', 'AsA', 'AFA']),
    # Eight blocks of steel and the cables inside: 145 t of tower.  A utility machine brings its tower in its own
    # recipe, because a turbine_tower block carries every machine, the SW-10 included - see real_tonnes().
    ('tower_section', 1, SHAPED, ['lll', 'lOl', 'lll']),

    # ------------------------------------------------------------------- the line conductors
    # A reel is one phase.  The quad bundle is solid metal: 1.3 t a reel, where the other two are a few kilograms.
    ('abc_conductor', 8, SHAPED, ['BBB', 'FFF', 'SSS']),
    ('mv_conductor', 6, SHAPED, ['BBB', 'PPP']),
    ('hv_conductor', 4, SHAPED, ['ccc', 'SSS', 'ccc']),

    # --------------------------------------------------------------------------- the cables
    ('dc_string_cable', 6, SHAPED, ['BBB', 'FFF']),
    ('dc_trunk_cable', 4, SHAPED, ['BBB', 'FFF', 'PPP']),

    # --------------------------------------------------------------------------- the arrays
    # Laminates by the glass a block really carries (two for the forty-four-module tables and the thin film, one
    # for the rest), over its mounting: a ballast tray, a fixed rack, a torque tube, a pedestal head.
    ('solar_panel', 1, SHAPED, ['LL', 'SS', 'jj']),
    ('pv_flat_430', 1, SHAPED, ['LL', 'PP', 'jj']),
    ('pv_tilt_530', 1, SHAPED, ['LL', 'T ', 'jj']),
    ('pv_tilt_580', 1, SHAPED, [' L ', ' T ', 'j j']),
    ('pv_track_700', 1, SHAPED, [' L ', 'tut', 'jbj']),
    ('pv_dual_440', 1, SHAPED, [' L ', 'uSu', 'jbj']),

    # ------------------------------------------------------------------------- the inverters
    # Power electronics by the rating - a module for the wall box, a module and its fans for the 110, a bridge
    # and two fused DC sections for the 350, five bridges for the container - over a cabinet and a board.
    ('inverter_10', 1, SHAPED, [' w ', 'kEe', ' b ']),
    ('inverter_110', 1, SHAPED, [' w ', 'kEe', 'KbK']),
    ('inverter_350', 1, SHAPED, [' G ', 'DED', 'KbK']),
    ('inverter_2500', 1, SHAPED, ['GGG', 'DED', 'GbG']),

    # ------------------------------------------------------------------------- the combiners
    # Fuse ways over an enclosure with a load-break switch under it; the thirty-two way box is built to IP66.
    ('pv_combiner_6', 1, SHAPED, ['fff', ' E ', ' Q ']),
    ('pv_combiner_16', 1, SHAPED, ['fff', 'BEB', ' Q ']),
    ('pv_combiner_32', 1, SHAPED, ['fff', 'BEB', 'PQP']),

    # A logger with a display on it, which is what the model draws and what put the screen back in the tree.
    ('met_station', 1, SHAPED, [' p ', 'pbp', 'SRS']),
    # A cabinet with a controller and the gateways behind the same door, over the switchgear it is wired to.
    ('plant_controller', 1, SHAPED, ['SBS', 'bEb', 'PQP']),

    # ------------------------------------------------------------------- the transformers
    # Bushings over a core and its windings, in a tank.  The 63 MVA unit is iron and copper by the block.
    ('tx_machine', 1, SHAPED, ['III', 'OeO', 'PPP']),
    ('tx_substation', 1, SHAPED, ['III', 'l$l', 'lQl']),

    # A disconnector is post insulators, a copper blade and a frame; a breaker is the same posts over three
    # interrupters and the mechanism that throws them.
    ('mv_disconnector', 1, SHAPED, ['III', 'BSB', 'SSS']),
    ('mv_breaker', 1, SHAPED, ['III', 'QQQ', 'PbP']),

    # ---------------------------------------------------------------- the transmission towers
    # Fifty, seventy-five and a hundred tonnes of lattice: a suspension tower holds weight, a tension tower the
    # difference between two pulls, a terminal tower all of one.
    ('lattice_suspension', 1, SHAPED, ['SIS', 'lSl', 'S S']),
    ('lattice_tension', 1, SHAPED, ['SIS', 'lll', 'S S']),
    ('lattice_terminal', 1, SHAPED, ['lIl', 'lll', 'S S']),

    # ------------------------------------------------------------------- towers and turbines
    # Ten metres of the SW-10's guyed lattice a block: what every machine stands on.
    ('turbine_tower', 8, SHAPED, ['P P', 'PnP', 'P P']),
    # A 7 m rotor of pultruded fibre on an alternator: no blade item, no gearbox, no yaw drive.
    ('sw_10', 1, SHAPED, ['FSF', ' K ', 'SbS']),
    # Three blades over a gearbox, a generator a megawatt and a yaw drive, and the machine's tower in sections.
    ('c52_085', 1, SHAPED, ['sss', 'XxY', 'lu ']),
    ('c80_20', 1, SHAPED, ['zzz', 'xXx', ' % ']),
    ('c90_30', 1, SHAPED, ['zzz', 'XxX', 'x%u']),
    ('c112_30', 1, SHAPED, ['zzz', 'xXx', '%%u']),
    ('wind_turbine', 1, SHAPED, ['z%z', 'xXx', 'z%%']),
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


# ------------------------------------------------------------------------------ what a recipe costs
#
# The formula.  Every machine's recipe costs, in ingots, glass and quartz, what the real machine weighs in steel,
# conductor metal, glass and silicon, at TONNES_PER_UNIT tonnes an ingot:
#
#     units(material) = real tonnes of that material / TONNES_PER_UNIT
#
# real_tonnes() is the bill of materials, every figure from a datasheet or a published intensity (sources in
# docs/data-sources.md, "Costo delle ricette"); recipe_units() resolves a recipe down to vanilla stock; and
# cost_problems() holds each machine to its bill within COST_BAND, or, below FLOOR units, to no more than its bill
# plus FLOOR - an inverter weighs twenty kilograms, but its recipe still has a cabinet and a board in it.

# One iron ingot, one copper ingot, one glass block, one quartz: two tonnes of steel (and the cast iron and the
# structural aluminium the game has no metal for), of conductor metal (copper, and the aluminium of a line),
# of glass (and a blade's glass fibre), of silicon.  The one figure here that is a choice and not a fact: at one
# tonne the C130 would need more than a crafting grid holds; at four the arrays would vanish under the floor.
TONNES_PER_UNIT = 2.0
MATERIALS = ('steel', 'copper', 'glass', 'silicon')

# A recipe within this factor of its bill passes; FLOOR is the most a machine lighter than that may cost above it.
COST_BAND = 1.5
FLOOR = 3.0

# What one of each vanilla ingredient is, in units.  Coal, redstone, gold, sand, stone, slime and honeycomb are
# the chemistry and the electronics: they cost what they cost and weigh nothing here.
RAW = {
    '#forge:ingots/iron': {'steel': 1.0}, '#forge:nuggets/iron': {'steel': 1 / 9},
    '#forge:storage_blocks/iron': {'steel': 9.0},
    '#forge:ingots/copper': {'copper': 1.0}, '#forge:storage_blocks/copper': {'copper': 9.0},
    '#forge:glass/colorless': {'glass': 1.0}, '#forge:glass_panes/colorless': {'glass': 6 / 16},
    '#forge:gems/quartz': {'silicon': 1.0},
}


def recipe_units(recipes):
    """{item: {material: units}} for every item a recipe makes, resolved down to the vanilla stock in RAW.

    recipes is {path: recipe json}, gen_core_data's and this file's together.
    """
    made = {}
    for recipe in recipes.values():
        result = recipe['result']['item'] if isinstance(recipe['result'], dict) else recipe['result']
        count = recipe['result'].get('count', 1) if isinstance(recipe['result'], dict) else 1
        if 'pattern' in recipe:
            taking = collections.Counter(name_of(recipe['key'][c]) for row in recipe['pattern'] for c in row if c != ' ')
        else:
            taking = collections.Counter([name_of(recipe['ingredient'])])
        made[result] = (count, taking)

    memo = {}

    def units(name, path=()):
        if name in memo:
            return memo[name]
        if name in RAW:
            return RAW[name]
        if name not in made:
            return {}
        if name in path:
            raise SystemExit('gen_crafting: %s is made from itself' % name)
        count, taking = made[name]
        total = collections.Counter()
        for ingredient, n in taking.items():
            for material, amount in units(ingredient, path + (name,)).items():
                total[material] += amount * n / count
        memo[name] = dict(total)
        return memo[name]

    return {name.split(':')[1]: units(name) for name in made}


# ---- the bills of materials, in tonnes (docs/data-sources.md, "Costo delle ricette", has every source)

# A module by mass, IRENA/IEA-PVPS "End-of-Life Management: Solar Photovoltaic Panels" (2016): c-Si 76 % glass,
# 8 % aluminium (the frame, counted as steel), 5 % silicon, 1 % copper, 10 % polymer; CdTe 97 % glass.
MODULE_SHARES = {'c-Si': {'glass': 0.76, 'steel': 0.08, 'silicon': 0.05, 'copper': 0.01}, 'CDTE': {'glass': 0.97}}

# Mounting steel, t per kWp: a ballasted table's substructure (97 kg under 5.46 kWp), a fixed rack (5200 short
# tons for 71 MWdc), a single-axis row (1070 kg against a fixed rack's 962 for the same 26 modules).  A dual-axis
# pedestal is one tracker head whatever it carries: DEGERtraker 5000NT, 650 kg without its mast.
MOUNTING_T_PER_KWP = {'FLAT': 97 / 5460, 'FIXED_TILT': 5200 * 0.9072 / 71000,
                      'SINGLE_AXIS': 5200 * 0.9072 / 71000 * 1070 / 962}
DUAL_AXIS_HEAD = 0.65

# The wind line: NREL/TP-500-40566 (Fingersh, Hand, Laxson 2006), the advanced blade, the three-stage drive, and
# the baseline tower scaled to the 160 t Vestas publishes for a V90-3.0 on 80 m (which the same formulas give a
# 64 t nacelle and a 42 t rotor for, against 70 and 41).  Conductor metal 2.5 t/MW (USGS, Wilburn 2011).
TOWER_CALIBRATION = 160.0 / ((0.3973 * math.pi * 45 ** 2 * 80 - 1414) / 1000)
WIND_COPPER_T_PER_MW = 2.5
# The SW-10 is a Bergey Excel 10: 545 kg on the tower top, three blades 180 lb in their carton, and a 100 ft
# guyed lattice tower of 4000 lb - so a turbine_tower block, ten metres of it, is 0.59 t.
SMALL_WIND = {'head': 0.545, 'blades': 180 * 0.4536 / 1000}
SEGMENT_TONNES = 4000 * 0.4536 / 1000 / 3.048

# Datasheet masses: SUN2000-10KTL-M1 high-current, SG110CX, SG350HX, Sunny Central 2200-3000-EV (under 3400 kg).
INVERTER_TONNES = {'inverter_10': 0.0212, 'inverter_110': 0.089, 'inverter_350': 0.116, 'inverter_2500': 3.4}

# A 2500 kVA oil transformer, 5900 kg (Legrand); a 63 MVA one, 89.8 t of which 15.3 t oil and 58.5 t active part
# (SGB DOTR 63000/123).  The oil is not a material here; the windings are taken as a third of the active part.
TRANSFORMER_TONNES = {'tx_machine': 5.9, 'tx_substation': 89.8}
OIL_SHARE, ACTIVE_SHARE = 15.3 / 89.8, 58.5 / 89.8

# Eight new 380 kV masts, 600 t of steel (TenneT, Doerpen - West): 75 t a mast, shared out by duty.
LATTICE_TONNES = {'lattice_suspension': 50.0, 'lattice_tension': 75.0, 'lattice_terminal': 100.0}

# A reel's conductor, kg per metre of one phase: NFA2X 4x70 (its aluminium), Aster 228, four Curlew.
CONDUCTOR_KG_PER_M = {'abc_conductor': 4 * 70 * 2.703 / 1000, 'mv_conductor': 0.627, 'hv_conductor': 4 * 1.978}
CABLE_MM2 = {'dc_string_cable': 6, 'dc_trunk_cable': 240}

# Under the floor whatever they weigh, and estimated rather than looked up: a wall box, a mast, a cabinet, an
# outdoor switch, a pole, the steel of a prefabricated kiosk.
ESTIMATED_TONNES = {'pv_combiner_6': 0.012, 'pv_combiner_16': 0.045, 'pv_combiner_32': 0.08, 'met_station': 0.15,
                    'plant_controller': 0.25, 'mv_disconnector': 0.3, 'mv_breaker': 0.9, 'utility_pole': 0.8,
                    'electric_cabin': 3.0, 'power_box': 0.15}


def catalogue_source(name):
    return open(os.path.join(JAVA, 'catalog', name + '.java')).read()


def nrel_turbine(kw, diameter, rated_rpm, hub_m):
    """(rotor, nacelle, tower, blades) in tonnes for a three-stage geared machine: NREL/TP-500-40566 section 3."""
    radius = diameter / 2.0
    blade = 0.4948 * radius ** 2.53
    hub = 0.954 * blade + 5680.3
    pitch = (0.1295 * 3 * blade + 491.31) * 1.328 + 555.0
    nose = 18.5 * diameter - 520.5
    torque = kw / (2 * math.pi * rated_rpm / 60.0)
    nacelle = (70.94 * torque ** 0.759 + 6.47 * kw ** 0.9223 + 2.233 * diameter ** 1.953 * 1.125
               + 0.0142 * diameter ** 2.888 + 2 * (diameter * 8 / 600 - 0.033) * 0.0092 * diameter ** 2.5
               + 1.6 * 0.0009 * diameter ** 3.314 + (11.537 * kw + 3849.7) / 10 + 0.08 * kw)
    tower = TOWER_CALIBRATION * (0.3973 * math.pi * radius ** 2 * hub_m - 1414) / 1000
    return (3 * blade + hub + pitch + nose) / 1000, nacelle / 1000, tower, 3 * blade / 1000


def real_tonnes():
    """{registry id: {material: tonnes}} - the bill of materials every recipe is held to."""
    out = {}
    modules = {}
    for const, cell, mass, watts in re.findall(r'(\w+) = new PvModuleSpec\(\s*id\("\w+"\), "[^"]+", PvModuleSpec\.Cell\.(\w+), '
                                               r'\d+,\s*[\d.]+, [\d.]+, [\d.]+, ([\d.]+),\s*([\d.]+)',
                                               catalogue_source('PvModuleCatalog')):
        modules[const] = (cell, float(mass) / 1000, float(watts) / 1000)
    for array, module, mounting, per_string, strings in re.findall(
            r'id\("(\w+)"\), "[^"]+", PvModuleCatalog\.(\w+), PvMounting\.(\w+), [\w.]+,\s*(\d+), (\d+)\)',
            catalogue_source('PvCatalog')):
        cell, mass, kwp = modules[module]
        count = int(per_string) * int(strings)
        bill = {m: share * mass * count for m, share in MODULE_SHARES['CDTE' if cell == 'CDTE' else 'c-Si'].items()}
        rack = DUAL_AXIS_HEAD if mounting == 'DUAL_AXIS' else MOUNTING_T_PER_KWP[mounting] * kwp * count
        bill['steel'] = bill.get('steel', 0.0) + rack
        out[array] = bill

    for name, kw, diameter, rated, low, high, nacelle in re.findall(
            r'id\("(\w+)"\), "[^"]+", "[^"]*",\s*([\d.]+), ([\d.]+), [\d.]+,\s*[\d.]+, [\d.]+, [\d.]+,\s*[\d.]+, '
            r'([\d.]+), [\d.]+,\s*(\d+), (\d+),\s*[\d.]+, TurbineSpec\.Nacelle\.(\w+)', catalogue_source('TurbineCatalog')):
        kw, diameter, rated = float(kw), float(diameter), float(rated)
        segments = (int(low) + int(high)) / 2.0
        copper = WIND_COPPER_T_PER_MW * kw / 1000
        if nacelle == 'SMALL_WIND':
            total, blades = SMALL_WIND['head'], SMALL_WIND['blades']
        else:
            rotor, head, tower, blades = nrel_turbine(kw, diameter, rated, segments * 10.0)
            # the tower blocks it stands on carry SEGMENT_TONNES each; the machine brings the rest of its tower
            total = rotor + head + tower - segments * SEGMENT_TONNES
        out[TURBINE_ALIASES.get(name, name)] = {'steel': total - copper - blades, 'copper': copper, 'glass': blades}
    out['turbine_tower'] = {'steel': SEGMENT_TONNES}

    out.update({name: {'steel': tonnes} for name, tonnes in INVERTER_TONNES.items()})
    for name, tonnes in TRANSFORMER_TONNES.items():
        copper = tonnes * ACTIVE_SHARE / 3
        out[name] = {'steel': tonnes * (1 - OIL_SHARE) - copper, 'copper': copper}
    out.update({name: {'steel': tonnes} for name, tonnes in LATTICE_TONNES.items()})

    conductors = catalogue_source('ConductorCatalog')
    for name, blocks in re.findall(r'id\("(\w+)"\),.*?, (\d+)\)\);', conductors, re.S):
        out[name] = {'copper': CONDUCTOR_KG_PER_M[name] * int(blocks) * 10 / 1000}
    for name, mm2 in CABLE_MM2.items():
        # two conductors, plus and minus, ten metres a block
        out[name] = {'copper': 2 * mm2 * 8.96e-3 * 10 / 1000}
    out.update({name: {'steel': tonnes} for name, tonnes in ESTIMATED_TONNES.items()})
    return out


# Families that must cost more as they grow, in order of rating.
MONOTONE = [
    ['inverter_10', 'inverter_110', 'inverter_350', 'inverter_2500'],
    ['sw_10', 'c52_085', 'c80_20', 'c90_30', 'c112_30', 'wind_turbine'],
    ['tx_machine', 'tx_substation'],
    ['pv_combiner_6', 'pv_combiner_16', 'pv_combiner_32'],
    ['lattice_suspension', 'lattice_tension', 'lattice_terminal'],
]


def total(units):
    return sum(units.get(material, 0.0) for material in MATERIALS)


def cost_rows(recipes):
    """(item, bill in units, recipe in units) for every item with a bill."""
    have = recipe_units(recipes)
    return [(item, {m: t / TONNES_PER_UNIT for m, t in bill.items()}, have.get(item, {}))
            for item, bill in sorted(real_tonnes().items())]


def cost_problems(recipes):
    """Every machine against its bill: the formula at the top of this section."""
    problems = []
    rows = cost_rows(recipes)
    # a bill read out of a catalogue whose layout moved is a bill that silently is not there
    billed = {item for item, _, _ in rows}
    for item in sorted(set(DROPS.values()) - billed):
        problems.append('%s has no bill of materials, so nothing holds its recipe to the real thing' % item)
    for item, want, got in rows:
        # the two metals each, and everything together: a blade's glass is in the total (two blade classes for
        # five rotors cannot match each, see docs/data-sources.md)
        for material in ('total', 'steel', 'copper'):
            need = total(want) if material == 'total' else want.get(material, 0.0)
            cost = total(got) if material == 'total' else got.get(material, 0.0)
            if need >= FLOOR and not need / COST_BAND <= cost <= need * COST_BAND:
                problems.append('%s costs %.1f units of %s, and its bill is %.1f (%.0f t)'
                                % (item, cost, material, need, need * TONNES_PER_UNIT))
            elif need < FLOOR and cost > need + FLOOR:
                problems.append('%s costs %.1f units of %s, more than the floor over a bill of %.2f'
                                % (item, cost, material, need))
    costs = {item: total(got) for item, _, got in rows}
    for family in MONOTONE:
        for small, large in zip(family, family[1:]):
            if costs[large] < costs[small]:
                problems.append('%s costs less than %s (%.1f units against %.1f)' % (large, small, costs[large], costs[small]))
    return problems


def print_costs(recipes):
    print('%-20s %8s %8s   %s' % ('item', 'bill', 'recipe', 'by material, bill/recipe (units of %.0f t)' % TONNES_PER_UNIT))
    for item, want, got in cost_rows(recipes):
        parts = '  '.join('%s %.2f/%.2f' % (m[:2], want.get(m, 0.0), got.get(m, 0.0)) for m in MATERIALS
                          if want.get(m) or got.get(m))
        print('%-20s %8.2f %8.2f   %s' % (item, total(want), total(got), parts))


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

    # and what each machine costs, against what the real one weighs
    problems.extend(cost_problems(recipes))
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
# cabinet, six arrays are four mountings and the five C turbines are the C130.  None means the block has no item -
# the shell cell is placed by its machine and never held.
MACHINES = {
    'utility_pole': ('stone_particle', 'utility_pole'),
    'electric_cabin': ('stone_particle', 'cab'),
    'power_box': ('stone_particle', 'power_box'),
    'turbine_tower': ('stone_particle', 'turbine_tower'),
    'met_station': ('metal_particle', 'met_station'),
    'plant_controller': ('metal_particle', 'plant_controller'),
    'machine_shell': ('metal_particle', None),
    'sw_10': ('stone_particle', 'sw_10'),
    'c52_085': ('stone_particle', 'wind_turbine'),
    'c80_20': ('stone_particle', 'wind_turbine'),
    'c90_30': ('stone_particle', 'wind_turbine'),
    'c112_30': ('stone_particle', 'wind_turbine'),
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

# The photovoltaic rows, which snow has to be able to lie on.
PV_ARRAYS = ('solar_panel', 'pv_flat_430', 'pv_tilt_530', 'pv_tilt_580', 'pv_track_700', 'pv_dual_440')

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
        # Snow settles on a row: vanilla lets a layer lie only on a full top face, and no mounting has one, so the
        # snow model, the snow stow and the winter loss never happened. PvArrayBlock is forced solid for the same reason.
        os.path.join(TAGS, 'snow_layer_can_survive_on.json'):
            {'replace': False, 'values': sorted('renewables:' + name for name in PV_ARRAYS)},
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
    if '--costs' in sys.argv:
        core = {path: data for path, data in gen_core_data.planned().items() if os.path.dirname(path) == DATA}
        core.update({path: data for path, data in own.items() if os.path.dirname(path) == DATA})
        print_costs(core)
        return
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
