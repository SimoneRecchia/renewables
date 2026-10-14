#!/usr/bin/env python3
"""The data the mod's core needs that no other generator writes: the language file, the item models of the
components, the wrench and the conductor reels, and the recipes for the machines and components that sit
outside the parts tree.

    python3 tools/gen_core_data.py        # run this first, then gen_crafting.py

What is NOT here, on purpose: the blockstate, loot table, tag entry and item model of the pole, the cabin,
the power box, the turbine tower, the wind turbine and the machine shell.  gen_crafting.MACHINES already owns
every machine the mod draws itself, and check_generated_assets.py fails a file with two writers.  This file
owns the rest.

The language file has one writer, this file, and every key in it one owner: LANG_ENTRIES below, or
gen_crafting.lang_entries() (the parts out of PartCatalog, BLOCK_NAMES, MESSAGES).  A key in both tables, a name
or a grey line for something the mod does not register, an item with no name and a literal key the Java asks
for and the file lacks all stop it before it writes.
"""

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_crafting                                                             # noqa: E402

RESOURCES = os.path.join('src', 'main', 'resources')
ASSETS = os.path.join(RESOURCES, 'assets', 'renewables')
DATA = os.path.join(RESOURCES, 'data', 'renewables')

LANG = os.path.join(ASSETS, 'lang', 'en_us.json')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')
RECIPES = os.path.join(DATA, 'recipes')

SHAPED = 'minecraft:crafting_shaped'

# Every key and every English text, in the order of docs/spec/entry.md appendix A.  Typographic quotes and
# the symbols (middle dot, multiplication sign, squared, diameter, dashes, degree) are part of the text.
LANG_ENTRIES = [

    # ---- creative tab
    ("itemGroup.renewables.main", "Renewables"),

    # ---- items that are not blocks
    ("item.renewables.power_wrench", "Power Wrench"),
    ("item.renewables.circuit_board", "Circuit Board"),
    ("item.renewables.cpu", "CPU"),
    ("item.renewables.screen", "Glass Screen"),
    ("item.renewables.insulator", "Insulator"),
    ("item.renewables.metal_casing", "Metal Casing"),
    ("item.renewables.motor_core", "Motor Core"),
    ("item.renewables.abc_conductor", "Aerial Bundled Cable"),
    ("item.renewables.mv_conductor", "Medium-Voltage Conductor"),
    ("item.renewables.hv_conductor", "High-Voltage Conductor"),

    # ---- tooltips: the grey line of each item and the dynamic lines
    ("tooltip.renewables.power_wrench", "Right click a device to view more information."),
    ("tooltip.renewables.utility_pole", "Supports up to eight live insulators."),
    ("tooltip.renewables.electric_cabin", "One insulator is the input, the other feeds the grid."),
    ("tooltip.renewables.power_box", "Turns whatever power a low-voltage line brings it into FE, and powers nearby Renewables API devices."),
    ("tooltip.renewables.circuit_board", "Printed board used in every controller."),
    ("tooltip.renewables.cpu", "Simple control unit for Renewables builds."),
    ("tooltip.renewables.screen", "A backlit glass display on its own board: the face of the met station's logger."),
    ("tooltip.renewables.insulator", "Ceramic insulator used for attaching wires to a device."),
    ("tooltip.renewables.metal_casing", "Pressed sheet metal: the shell of a cabin, a power box and a junction box."),
    ("tooltip.renewables.motor_core", "Rotor coil for the turbine head and other powered parts."),
    ("tooltip.renewables.power.amount", "Power: %s kW"),
    ("tooltip.renewables.power.fe", "FE: %s / %s FE/t"),
    ("tooltip.renewables.turbine.class", "IEC class %s"),
    ("tooltip.renewables.turbine.nameplate", "%s · rotor ⌀ %s m"),
    ("tooltip.renewables.turbine.wind", "Cut-in %s · rated %s · cut-out %s m/s"),
    ("tooltip.renewables.turbine.tower", "Tower %s–%s segments (hub %s–%s m)"),
    ("tooltip.renewables.turbine_tower", "Stack it to the height you want, then seat a turbine on top. Above 16 blocks a bare pile folds under anything that steps on it, above 20 as it is built."),
    ("tooltip.renewables.pv.mounting", "%s"),
    ("tooltip.renewables.pv.nameplate", "%s DC · %s× %s"),
    ("tooltip.renewables.pv.module", "%s · %s · %s · %s"),
    ("tooltip.renewables.pv.laminate", "%s × %s × %s mm · %s kg"),
    ("tooltip.renewables.pv.strings", "%s string(s) of %s · %s Voc · %s per string"),
    ("tooltip.renewables.pv.ground", "Covers %s of the ground · %s m² of modules"),
    ("tooltip.renewables.pv.bifacial", "Bifacial: the back is worth %s of the front"),
    ("tooltip.renewables.pv.tracker", "%s · %s axis/axes · ±%s° · tracks a sun down to %s°"),
    ("tooltip.renewables.pv.needs_inverter", "Right-click with string cable to fit its leads, then run the cable to a combiner or an inverter."),
    ("tooltip.renewables.inverter.nameplate", "%s AC · %s apparent"),
    ("tooltip.renewables.inverter.dc", "Accepts %s DC · %s× the AC nameplate"),
    ("tooltip.renewables.inverter.mppt", "%s MPPT · %s–%s V window · starts at %s V"),
    ("tooltip.renewables.inverter.efficiency", "Peak %s · European %s"),
    ("tooltip.renewables.inverter.thermal", "Full output to %s°C · %s · %s overnight"),
    ("tooltip.renewables.met_station", "Nine instruments on one bus: global and diffuse irradiance, a reference cell in the plane of the array, albedo, air and module temperature, wind speed and direction, and snow. One mast serves a whole plant."),
    ("tooltip.renewables.dc_cable.rating", "%s mm² · %s A in free air · %s V DC"),
    ("tooltip.renewables.dc_cable.trench", "Sneak to dig it in: %s A buried, and the spoil comes back."),
    ("tooltip.renewables.dc_cable.string", "Strings to a combiner or an inverter. Right-click an array to fit its leads."),
    ("tooltip.renewables.dc_cable.trunk", "A combiner box to an inverter. Too heavy to plug into an array."),
    ("tooltip.renewables.combiner.ways", "%s ways · gPV holders to %s A · %s V DC"),
    ("tooltip.renewables.combiner.output", "%s A load-break switch · %s"),
    ("tooltip.renewables.combiner.monitoring", "Per-string current monitoring."),
    ("tooltip.renewables.combiner.use", "On a post, or right-click a central inverter to fit it inside. Empty hand throws the switch."),
    ("tooltip.renewables.abc_conductor", "NFA2X 4x70. Four insulated cores on a messenger, 1 kV. Poles and street lines."),
    ("tooltip.renewables.mv_conductor", "Aster 228 bare alloy conductor, 33 kV. One per phase, on pin insulators."),
    ("tooltip.renewables.hv_conductor", "Curlew ACSR in a quad bundle, 420 kV. Four sub-conductors on spacers, for lattice towers."),
    ("tooltip.renewables.conductor.reach", "%s blocks a reel, %s blocks a span at most"),
    ("tooltip.renewables.transformer.reading", "Ratio %s  in %s kW  loss %s kW"),

    # ---- block names (their items use them too)
    ("block.renewables.utility_pole", "Utility Pole"),
    ("block.renewables.electric_cabin", "Electric Cabin"),
    ("block.renewables.power_box", "Power Box"),
    ("block.renewables.wind_turbine", "Cube C130-4.0"),
    ("block.renewables.sw_10", "Cube SW-10"),
    ("block.renewables.c52_085", "Cube C52-0.85"),
    ("block.renewables.c80_20", "Cube C80-2.0"),
    ("block.renewables.c90_30", "Cube C90-3.0"),
    ("block.renewables.c112_30", "Cube C112-3.0"),
    ("block.renewables.turbine_tower", "Turbine Tower"),
    ("block.renewables.solar_panel", "Meridian FT-415"),
    ("block.renewables.pv_flat_430", "Meridian FT-430"),
    ("block.renewables.pv_tilt_580", "Meridian TR-580"),
    ("block.renewables.pv_tilt_530", "Meridian TR-530"),
    ("block.renewables.pv_track_700", "Meridian HX-700"),
    ("block.renewables.pv_dual_440", "Meridian AE-440"),
    ("block.renewables.inverter_10", "Volterra VX-10K"),
    ("block.renewables.inverter_110", "Volterra VX-110K"),
    ("block.renewables.inverter_350", "Volterra VX-350K"),
    ("block.renewables.inverter_2500", "Volterra VC-2500K"),
    ("block.renewables.dc_string_cable", "Cabria SC-6 Solar Cable"),
    ("block.renewables.dc_trunk_cable", "Cabria DT-240 DC Trunk"),
    ("block.renewables.met_station", "Meteorological Mast"),
    ("block.renewables.pv_combiner_6", "Volterra CB-6"),
    ("block.renewables.pv_combiner_16", "Volterra CB-16"),
    ("block.renewables.pv_combiner_32", "Volterra CB-32"),
    ("block.renewables.machine_shell", "Machine Shell"),

    # ---- screens
    ("screen.renewables.power_info.title", "Power Diagnostics"),
    ("screen.renewables.power_info.world_unavailable", "World unavailable"),
    ("screen.renewables.power_info.block_removed", "Block removed"),
    ("screen.renewables.power_info.no_power_data", "No power data available"),
    ("screen.renewables.power_info.offsets", "Offsets: (%s, %s, %s)"),
    ("screen.renewables.power_info.yaw_pitch", "Yaw / Pitch (deg): (%s, %s)"),
    ("screen.renewables.utility_pole_config.title", "Utility Pole Configuration"),
    ("screen.renewables.utility_pole_config.offset_x", "Offset X:"),
    ("screen.renewables.utility_pole_config.offset_y", "Offset Y:"),
    ("screen.renewables.utility_pole_config.offset_z", "Offset Z:"),
    ("screen.renewables.utility_pole_config.yaw", "Yaw (deg):"),
    ("screen.renewables.utility_pole_config.pitch", "Pitch (deg):"),
    ("screen.renewables.wind_turbine.title", "Turbine Control"),
    ("screen.renewables.wind_turbine.iec", "IEC %s"),
    ("screen.renewables.wind_turbine.geometry", "Rotor ⌀ %s m · hub %s m · 3 blades"),
    ("screen.renewables.wind_turbine.curve", "Cut-in %s · rated %s · cut-out %s m/s"),
    ("screen.renewables.wind_turbine.power", "Active power"),
    ("screen.renewables.wind_turbine.wind", "Wind at hub"),
    ("screen.renewables.wind_turbine.tower", "Tower %s/%s segments"),
    ("screen.renewables.wind_turbine.limit", "Limit %s (%s%%)"),
    ("screen.renewables.wind_turbine.stop", "Stop"),
    ("screen.renewables.wind_turbine.start", "Start"),
    ("screen.renewables.wind_turbine.redstone", "Redstone: %s"),
    ("screen.renewables.wind_turbine.redstone.disabled", "off"),
    ("screen.renewables.wind_turbine.redstone.high", "high"),
    ("screen.renewables.wind_turbine.redstone.low", "low"),
    ("screen.renewables.wind_turbine.state.running", "Running"),
    ("screen.renewables.wind_turbine.state.curtailed", "Running, output limited"),
    ("screen.renewables.wind_turbine.state.cut_out", "Stopped — wind above cut-out"),
    ("screen.renewables.wind_turbine.state.stopped_by_player", "Stopped — by hand"),
    ("screen.renewables.wind_turbine.state.stopped_by_computer", "Stopped — by computer"),
    ("screen.renewables.wind_turbine.state.stopped_by_redstone", "Stopped — by redstone"),
    ("screen.renewables.wind_turbine.redstone.tip.disabled", "Redstone is ignored. The turbine runs whatever the signal does."),
    ("screen.renewables.wind_turbine.redstone.tip.high", "Runs only while receiving a redstone signal."),
    ("screen.renewables.wind_turbine.redstone.tip.low", "Runs only while not receiving a redstone signal."),
    ("screen.renewables.wind_turbine.tower_gain", "One block taller: %s output"),
    ("screen.renewables.pv.stop", "Stop"),
    ("screen.renewables.pv.start", "Start"),
    ("screen.renewables.pv.redstone", "Redstone: %s"),
    ("screen.renewables.pv.limit", "Limit %s (%s%%)"),
    ("screen.renewables.pv.active_power", "Active power"),
    ("screen.renewables.pv.state.stopped_by_player", "Stopped — by hand"),
    ("screen.renewables.pv.state.stopped_by_computer", "Stopped — by computer"),
    ("screen.renewables.pv.state.stopped_by_redstone", "Stopped — by redstone"),
    ("screen.renewables.pv_inverter.title", "Inverter"),
    ("screen.renewables.pv_inverter.mppt_count", "%s MPPT"),
    ("screen.renewables.pv_inverter.ratings", "%s AC · accepts %s DC · %s peak"),
    ("screen.renewables.pv_inverter.window", "MPPT %s–%s V · starts at %s V"),
    ("screen.renewables.pv_inverter.dc_input", "DC input"),
    ("screen.renewables.pv_inverter.cabinet", "Cabinet"),
    ("screen.renewables.pv_inverter.energy", "Today %s · lifetime %s"),
    ("screen.renewables.pv_inverter.plant", "%s arrays · %s/%s strings · DC/AC %s"),
    ("screen.renewables.pv_inverter.grid", "%s V · %s A · %s Hz · PF %s · %s kvar"),
    ("screen.renewables.pv_inverter.pf", "PF %s"),
    ("screen.renewables.pv_inverter.pf.tip", "Power factor the grid is asking for. Below unity the machine supplies reactive power, and at full output it gives up active power to do it."),
    ("screen.renewables.pv_inverter.state.mppt", "Tracking the maximum power point"),
    ("screen.renewables.pv_inverter.state.clipping", "Clipping — more DC on offer than can pass"),
    ("screen.renewables.pv_inverter.state.derating", "Derating — too hot to run at nameplate"),
    ("screen.renewables.pv_inverter.state.curtailed", "Curtailed — held at the setpoint"),
    ("screen.renewables.pv_inverter.state.standby", "Standby — waiting for the strings"),
    ("screen.renewables.pv_inverter.state.no_arrays", "No arrays wired in"),
    ("screen.renewables.pv_array.title", "Array"),
    ("screen.renewables.pv_array.module", "%s× %s · %s · %s"),
    ("screen.renewables.pv_array.strings", "%s strings of %s at %s V · GCR %s"),
    ("screen.renewables.pv_array.poa", "Plane"),
    ("screen.renewables.pv_array.performance", "Performance ratio"),
    ("screen.renewables.pv_array.tracker", "%s"),
    ("screen.renewables.pv_array.fixed", "Fixed mounting"),
    ("screen.renewables.pv_array.losses", "Dirt %s · snow %s · beam %s · rows %s"),
    ("screen.renewables.pv_array.conditions", "Cell %s°C · air %s°C · wind %s m/s · AOI %s°"),
    ("screen.renewables.pv_array.wiring", "%s at %s · %s"),
    ("screen.renewables.pv_array.wired", "wired to an inverter"),
    ("screen.renewables.pv_array.unwired", "no inverter in range"),
    ("screen.renewables.pv_array.angle", "Hand angle %s°"),
    ("screen.renewables.pv_array.mode", "Tracker: %s"),
    ("screen.renewables.pv_array.mode.auto", "auto"),
    ("screen.renewables.pv_array.mode.manual", "hand"),
    ("screen.renewables.pv_array.mode.stow", "stow"),
    ("screen.renewables.pv_array.mode.tip.auto", "Following the sun, backtracking when the rows would shade each other, and stowing itself for wind and snow."),
    ("screen.renewables.pv_array.mode.tip.manual", "Held at the angle on the slider. The controller will not move it."),
    ("screen.renewables.pv_array.mode.tip.stow", "Commanded flat and kept there. What to set before a storm you can see coming."),
    ("screen.renewables.pv_array.state.producing", "Producing"),
    ("screen.renewables.pv_array.state.backtracking", "Backtracking — giving up beam to keep the rows clear"),
    ("screen.renewables.pv_array.state.shaded", "Shaded — something is standing over it"),
    ("screen.renewables.pv_array.state.row_shaded", "Row shading — the row in front is in the way"),
    ("screen.renewables.pv_array.state.snow", "Buried — snow on the modules"),
    ("screen.renewables.pv_array.state.dark", "Dark"),
    ("screen.renewables.pv_array.stow.none", "Tracking the sun"),
    ("screen.renewables.pv_array.stow.night", "Night stow"),
    ("screen.renewables.pv_array.stow.wind", "Wind stow — flat, so the gust has no leverage"),
    ("screen.renewables.pv_array.stow.snow", "Snow stow — standing up to shed it"),
    ("screen.renewables.pv_array.stow.diffuse", "Diffuse mode — flat, to see the whole sky"),
    ("screen.renewables.pv_array.stow.commanded", "Stowed — commanded"),
    ("screen.renewables.met_station.title", "Meteorological Mast"),
    ("screen.renewables.met_station.instruments", "%s instruments"),
    ("screen.renewables.met_station.global", "Global"),
    ("screen.renewables.met_station.plane", "Plane"),
    ("screen.renewables.met_station.diffuse", "Diffuse"),
    ("screen.renewables.met_station.reference_cell", "Ref cell"),
    ("screen.renewables.met_station.diffuse_fraction", "Diffuse fraction"),
    ("screen.renewables.met_station.clearness", "Clearness"),
    ("screen.renewables.met_station.albedo", "Albedo"),
    ("screen.renewables.met_station.sun", "Sun"),
    ("screen.renewables.met_station.air_temp", "Air"),
    ("screen.renewables.met_station.module_temp", "Module"),
    ("screen.renewables.met_station.wind_speed", "Wind"),
    ("screen.renewables.met_station.wind_dir", "Direction"),
    ("screen.renewables.met_station.snow_distance", "Snow distance"),
    ("screen.renewables.met_station.snow_height", "Snow height"),
    ("screen.renewables.met_station.fitted", "Fitted instruments  (* on the array)"),
    ("screen.renewables.met_station.reference", "Reference array at %s, %s, %s"),
    ("screen.renewables.met_station.no_reference", "No array in range: the plane and module readings are the mast’s own"),
    ("screen.renewables.pv_inverter.state.out_of_window", "%s V is outside the %s–%s V window"),
    ("screen.renewables.pv_array.state.no_collector", "Not wired — no cable or wired row reaches it"),
    ("screen.renewables.pv_array.run", "Run %s · %s buried · %s lost"),
    ("screen.renewables.pv_combiner.title", "Combiner Box"),
    ("screen.renewables.pv_combiner.rating", "%s · %s V DC · holders to %s A"),
    ("screen.renewables.pv_combiner.ways", "Ways used"),
    ("screen.renewables.pv_combiner.bus", "Bus %s · %s · %s to the cabinet"),
    ("screen.renewables.pv_combiner.trunk", "Trunk %s · %s buried · %s lost"),
    ("screen.renewables.pv_combiner.wired", "Output wired to a cabinet."),
    ("screen.renewables.pv_combiner.unwired", "No trunk out — run one to an inverter."),
    ("screen.renewables.pv_combiner.state.closed", "Closed — feeding the cabinet"),
    ("screen.renewables.pv_combiner.state.isolated", "Isolated — switch open, strings still live"),
    ("screen.renewables.pv_combiner.state.no_inverter", "No inverter on the trunk"),
    ("screen.renewables.pv_combiner.state.empty", "No strings wired in"),
    ("screen.renewables.pv_combiner.refusal.insulation", "A row was left out: string volts above this box"),
    ("screen.renewables.pv_combiner.refusal.fuse", "A row was left out: its fuse will not fit these holders"),
    ("screen.renewables.pv_combiner.refusal.ways", "A row was left out: every way is used"),
    ("screen.renewables.pv_combiner.refusal.output_current", "A row was left out: the output is full"),
    ("screen.renewables.pv_inverter.terminals", "DC terminals: %s"),
    ("screen.renewables.pv_inverter.terminals.strings", "plugs — strings straight in, no combiner"),
    ("screen.renewables.pv_inverter.terminals.both", "plugs and busbars — strings or a combiner trunk"),
    ("screen.renewables.pv_inverter.terminals.busbars", "busbars only — a trunk, or fit a combiner box in it"),
    ("screen.renewables.pv_inverter.terminals.fitted", "combiner fitted — strings straight in"),
    ("screen.renewables.plant_controller.title", "Plant Controller"),
    ("screen.renewables.plant_controller.subtitle", "Site dispatch and telemetry"),
    ("screen.renewables.plant_controller.reach", "%s blocks"),
    ("screen.renewables.plant_controller.output", "Plant output"),
    ("screen.renewables.plant_controller.units", "Units %s · running %s · held %s"),
    ("screen.renewables.plant_controller.setpoint", "Setpoint %s of %s"),
    ("screen.renewables.plant_controller.comms", "Comparator %s of 15"),
    ("screen.renewables.plant_controller.mode", "Mode: %s"),
    ("screen.renewables.plant_controller.mode.off", "watching only"),
    ("screen.renewables.plant_controller.mode.limit", "holding a setpoint"),
    ("screen.renewables.plant_controller.mode.redstone", "following redstone"),
    ("screen.renewables.plant_controller.limit", "Setpoint %s  ·  %s of the plant"),
    ("screen.renewables.plant_controller.state.no_units", "Nothing in range: no turbine or inverter to dispatch"),
    ("screen.renewables.plant_controller.state.watching", "Watching: every unit keeps its own limit"),
    ("screen.renewables.plant_controller.state.limiting", "Holding the plant to its setpoint"),
    ("screen.renewables.plant_controller.state.free", "Every unit free to make what it can"),

    # ---- chat and action-bar messages
    ("message.renewables.wire.failed_insulator", "Failed to resolve one of the insulators."),
    ("message.renewables.wire.too_far", "Those insulators are too far apart (%s m > %s m)."),
    ("message.renewables.wire.connected", "Connected insulators %s <-> %s."),
    ("message.renewables.wire.chunk_unloaded", "Chunk at (%s, %s, %s) is not loaded."),
    ("message.renewables.wire.no_permission", "You cannot interact with (%s, %s, %s)."),
    ("message.renewables.wire.anchor_unavailable", "That device is still initializing; try again in a moment."),
    ("message.renewables.turbine.tower_height", "%s is built for %s to %s blocks of tower. This one stands %s."),
    ("message.renewables.tower.collapsed", "The steel groans, and the tower folds under its own weight."),
    ("message.renewables.combiner.already_fused", "A string inverter already has fused string terminals — that is what it is."),
    ("message.renewables.combiner.already_fitted", "This cabinet already has a DC section fitted."),
    ("message.renewables.wire.not_enough", "Not enough conductor: this span needs %s of %s"),
    ("message.renewables.wire.already_connected", "Those two fittings are already joined"),
    ("message.renewables.wire.no_conductor", "Hold a reel of conductor to string a span."),
    ("message.renewables.wire.aim_at_insulator", "Aim at an insulator."),
    ("message.renewables.wire.out_of_reach", "Too far away to string that."),
    ("message.renewables.wire.too_many", "No more spans can be strung there: a fitting carries at most %s."),
    ("message.renewables.machine.no_room", "There is not enough room here: the machine would stand inside something at (%s, %s, %s)."),

    # ---- game rule
    ("gamerule.renewablesRealTimeClock", "Follow the real-world clock"),
    ("gamerule.renewablesRealTimeClock.description", "The sun stands where the real sun stands: a day takes a day, and sleeping no longer moves it."),

    # ---- PV mountings
    ("mounting.renewables.flat", "flat table"),
    ("mounting.renewables.fixed_tilt", "fixed tilt, 25°"),
    ("mounting.renewables.single_axis", "horizontal single-axis tracker"),
    ("mounting.renewables.dual_axis", "azimuth-elevation dual-axis tracker"),

    # ---- inverter cooling
    ("cooling.renewables.natural", "convection cooled"),
    ("cooling.renewables.fan", "fan cooled"),
    ("cooling.renewables.forced_air", "forced air"),
]

# Item models.  Everything is a flat sprite on item/generated; only the wrench is held like a tool.
# (item, parent, sprite)
FLAT_ITEMS = [
    ('power_wrench', 'minecraft:item/handheld', 'power_wrench'),
    ('abc_conductor', 'minecraft:item/generated', 'abc_conductor'),
    ('mv_conductor', 'minecraft:item/generated', 'mv_conductor'),
    ('hv_conductor', 'minecraft:item/generated', 'hv_conductor'),
    ('circuit_board', 'minecraft:item/generated', 'circuit_board'),
    ('cpu', 'minecraft:item/generated', 'cpu'),
    ('screen', 'minecraft:item/generated', 'screen'),
    ('insulator', 'minecraft:item/generated', 'insulator'),
    ('metal_casing', 'minecraft:item/generated', 'metal_casing'),
    ('motor_core', 'minecraft:item/generated', 'motor_core'),
]

# One letter, one ingredient, in every recipe below.  The letters that name the mod's own parts mean here what
# they mean in gen_crafting.py (C the circuit board, U the CPU, I the insulator, M the metal casing, K the
# motor core, R the screen, B the busbar); the vanilla letters are the same too, and the two new ones - v and
# W - are letters that file does not use.
INGREDIENTS = {
    'i': 'minecraft:iron_ingot',
    'n': 'minecraft:iron_nugget',
    'c': 'minecraft:copper_ingot',
    'r': 'minecraft:redstone',
    'g': 'minecraft:gold_nugget',
    'q': 'minecraft:quartz',
    'y': 'minecraft:glass_pane',
    'v': 'minecraft:stone',
    'W': 'minecraft:glass',
    'C': 'renewables:circuit_board',
    'U': 'renewables:cpu',
    'I': 'renewables:insulator',
    'M': 'renewables:metal_casing',
    'B': 'renewables:busbar',
}

# (result, count, pattern).  The first five are the foundation everything else stands on, made from vanilla
# materials only; the machines after them are the ones the parts tree does not cover.
CORE_RECIPES = [
    # a printed board: redstone traces, copper pads, gold contacts
    ('circuit_board', 2, ['rrr', 'cgc']),
    # a die on a board, with iron pins
    ('cpu', 1, ['qqq', 'rCr', 'nnn']),
    # glass, a redstone backlight, and a board to drive it
    ('screen', 1, ['yyy', 'yry', 'nCn']),
    # a ring of iron: the body of a cabinet or a nacelle
    ('metal_casing', 2, ['iii', 'i i', 'iii']),
    # a copper winding round an iron core
    ('motor_core', 1, [' c ', 'cic', ' c ']),
    # a ceramic body held on iron pins: glass stands in for the porcelain
    ('insulator', 3, ['n n', 'nWn', 'n n']),
    ('power_wrench', 1, ['i', 'i', 'i']),
    # three insulators on a stone-footed post
    ('utility_pole', 2, ['III', ' v ', ' v ']),
    # two casings a side round the insulators, boards to either side of a busbar
    ('electric_cabin', 1, ['MIM', 'CBC', 'MIM']),
    ('power_box', 1, ['MMM', 'CUC', 'MIM']),
]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def lang_table():
    """Every key of the language file with its owner: (table, problems)."""
    table, owner, problems = {}, {}, []
    for source, entries in (('gen_core_data.LANG_ENTRIES', LANG_ENTRIES),
                            ('gen_crafting.lang_entries()', gen_crafting.lang_entries())):
        for key, text in entries:
            if key in owner:
                problems.append('%s is written by %s and by %s' % (key, owner[key], source))
            owner[key] = source
            table[key] = text

    return table, problems


# The keys that name one registered thing: <kind>.renewables.<registry id>.
NAMED = re.compile(r'(item|block|tooltip)\.renewables\.([a-z0-9_]+)$')


def lang_problems(table):
    """Keys nothing reads, and things with no key."""
    items, blocks = gen_crafting.registered()
    java = gen_crafting.java_source()
    problems = []
    for key in table:
        match = NAMED.match(key)
        if match is None:
            continue
        kind, name = match.groups()
        # a block's item takes the block's name, so item.<id> of a block item is never read
        if kind == 'item' and (name not in items or name in blocks):
            problems.append('%s names no item that reads it' % key)
        elif kind == 'block' and name not in blocks:
            problems.append('%s names no block' % key)
        elif kind == 'tooltip' and not items.get(name) and '"%s"' % key not in java:
            problems.append('%s is a grey line no item shows' % key)

    for name, reads_tooltip in items.items():
        key = ('block.renewables.' if name in blocks else 'item.renewables.') + name
        if key not in table:
            problems.append('%s has no name: %s' % (name, key))
        if reads_tooltip and 'tooltip.renewables.' + name not in table:
            problems.append('%s shows tooltip.renewables.%s, which is not written' % (name, name))
    for name in blocks:
        if 'block.renewables.' + name not in table:
            problems.append('block %s has no name' % name)

    # a key spelled out whole in the Java; a prefix ending in a dot is completed at run time
    for key in sorted(set(re.findall(r'"((?:item|block|tooltip|screen|message|mounting|cooling|itemGroup)'
                                     r'\.renewables\.[a-z0-9_.]*[a-z0-9_])"', java))):
        if key not in table:
            problems.append('the Java asks for %s and the language file lacks it' % key)
    return problems


def lang():
    table, problems = lang_table()
    problems += lang_problems(table)
    if problems:
        for line in problems:
            print('    PROBLEM %s' % line)
        raise SystemExit('gen_core_data: %d problem(s) in the language file, nothing written' % len(problems))

    write(LANG, table)
    return len(table)


def item_models():
    for name, parent, sprite in FLAT_ITEMS:
        write(os.path.join(ITEM_MODELS, name + '.json'),
              {'parent': parent, 'textures': {'layer0': 'renewables:item/' + sprite}})

    return len(FLAT_ITEMS)


def recipes():
    for result, count, pattern in CORE_RECIPES:
        letters = {c for row in pattern for c in row if c != ' '}
        unknown = sorted(c for c in letters if c not in INGREDIENTS)
        if unknown:
            raise SystemExit('gen_core_data: no ingredient for %s in %s' % (', '.join(unknown), result))

        recipe = {'type': SHAPED, 'pattern': pattern,
                  'key': {c: {'item': INGREDIENTS[c]} for c in sorted(letters)},
                  'result': {'item': 'renewables:' + result}}
        if count > 1:
            recipe['result']['count'] = count

        write(os.path.join(RECIPES, result + '.json'), recipe)

    return len(CORE_RECIPES)


def main():
    print('%d language keys, %d item models, %d recipes' % (lang(), item_models(), recipes()))


if __name__ == '__main__':
    main()
