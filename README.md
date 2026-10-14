# Renewables

Realistic renewable energy for Minecraft 1.20.1 (Forge): solar parks, wind farms and the grid that carries them.

Every placeable thing is a real machine, drawn and simulated the way the real one works. A turbine has a
datasheet power curve, yaws into a wind that comes from a weather model, and derates in a storm. A
photovoltaic array is modules on a mounting, wired in strings to an inverter that tracks their maximum power
point. Power leaves them on spans of real conductors, crosses cabins, poles, transformers and lattice towers,
loses a little on every hop, and ends at a kiosk that hands it to the rest of the modpack as Forge Energy.

* **Minecraft** 1.20.1, **Forge** 47.4.0 or later, **Java** 17
* Optional: **Mekanism** 10.4 (Joules straight from the generators), **CC:Tweaked** 1.109 or later (SCADA from
  a computer)

## How the grid works

There are two networks, as in a real plant.

**Direct current is laid on the ground.** String cable and trunk cable are blocks: they join whatever is next
to them, and a machine finds what it is wired to by following the copper. One block is 10 m, and the loss in
the copper is on the panels.

**Alternating current is strung between fittings.** Hold a conductor reel, right-click an insulator, then a
second one, and a span hangs between them. Three conductors, each for its own voltage class — aerial bundled
cable at 1 kV (spans up to 40 blocks), a medium-voltage conductor at 24 kV (90), a 400 kV quad bundle (160) —
and every fitting says which class it takes. Every server tick the grid carries what the generators make down
the spans: about 1% is lost per block of span, parallel spans share the loss, and where a line divides the
power divides equally.

### Wind: turbine → cabin → pole → kiosk

1. Stack **Turbine Tower** blocks and put a turbine on top. Six models, from the 10 kW SW-10 to the 4 MW
   C130-4.0, each certified for its own range of tower heights.
2. String **medium voltage** from the insulator at the foot of the tower to the **input** of an **Electric
   Cabin** — the end of its roof away from the door.
3. From the cabin's **output** (the door end), string low or medium voltage to **Utility Poles** — eight
   insulators each, one busbar — and from pole to pole.
4. The last span, in **aerial bundled cable**, goes to the insulator on the roof of a **Power Box**. The box
   pushes Forge Energy (50 FE per kW) into the six blocks touching it, keeps 100 000 FE in a buffer, and
   powers every lamp, rail or door and every mod consumer within its field.

A span strung the wrong way round — a generator onto a cabin's output, a pole onto a cabin — is made but
carries nothing. [Getting started](docs/getting-started.md#who-feeds-whom) has the whole table.

### Solar: array → combiner → inverter

1. Place **arrays** in rows: flat ballasted tables, fixed racks, a single-axis tracker, a dual-axis pedestal.
   An array produces nothing until an inverter has claimed it over cable.
2. Lay **string cable** from the rows to an inverter with string terminals, or to a **combiner box**; lay
   **trunk cable** from the combiners to a central inverter.
3. The **inverter** (10 kW to 2.5 MW) tracks the strings, clips at its nameplate, derates in the heat, and
   holds a power factor. Its AC insulator takes medium voltage to a cabin or a **Machine Transformer**.
4. A **Meteorological Mast** measures the sky for the whole plant; a **Plant Controller** holds every inverter
   and turbine within 64 blocks to a plant-wide limit.

### Substation and transmission

A **Substation Transformer** takes 33 kV in on three bushings and sends 400 kV out on three more, to
**Suspension**, **Tension** and **Terminal** lattice towers strung with the quad bundle. A **Disconnector**
(which refuses to open on load) and a **Circuit Breaker** (which trips by itself above its rating) go in any
medium-voltage line and are thrown with an empty hand or by redstone. Coming back off a 400 kV line takes a
second substation transformer and a medium-voltage **run** laid on the ground, because a tower and a pole share
no conductor.

The **Power Wrench** reads all of it: right-click a machine, or point at one up to 16 blocks away, and its panel
opens.

## Integrations

* **Forge Energy.** The Power Box offers FE on every side and pushes it to its neighbours. Turbines and
  inverters also offer their output directly, on their bottom face and the face away from their front — for a
  turbine, at the lowest block of its tower.
* **Mekanism.** The same generator faces speak Mekanism's Joules (`IStrictEnergyHandler`) at 125 J per kW, the
  same scale as the Power Box's 50 FE per kW at Mekanism's 2.5 J per FE. What other mods take is taken from a
  per-tick budget, and only the rest goes down the wires, so no Joule is spent twice.
* **ComputerCraft.** Four peripherals — `renewables_wind_turbine`, `renewables_pv_inverter`,
  `renewables_pv_array`, `renewables_met_station` — with tick-consistent SCADA telemetry (each tag marked
  measured, derived or simulated), Mekanism-compatible generator methods, stop/start, curtailment, redstone
  mode, power factor and tracker control.

Both mods are optional and only compiled against; Renewables runs without either.
→ [Integrations](docs/integrations.md) · [ComputerCraft API](docs/telemetry.md)

## Configuration

**Server**, per world, in `<world>/serverconfig/Renewables/server.toml`. Values are read when they are used, so
an edit takes effect in a running world.

| Section | Key | Default | Range | What it does |
|---|---|---|---|---|
| `power` | `powerBoxRadius` | `5` | 1–16 | radius in blocks of a Power Box's field |
| `energy` | `externalEnergyEnabled` | `true` | | whether generators offer energy to other mods at all |
| `energy` | `turbineExportFraction` | `1.0` | 0–1 | share of a turbine's nameplate it may offer other mods |
| `energy` | `turbineMaxJoulesPerTick` | `0.0` | 0–1e9 | absolute ceiling after the fraction, in J/tick; `0` = none |
| `energy` | `pvExportFraction` | `1.0` | 0–1 | the same share, for an inverter's AC nameplate |
| `energy` | `pvMaxJoulesPerTick` | `0.0` | 0–1e9 | the same ceiling, for an inverter; `0` = none |
| `photovoltaics` | `maxCableRun` | `32` | 2–256 | longest run of DC cable, in blocks, a machine follows |

With the defaults a generator offers other mods everything it makes, and whatever they do not take stays on the
wires.

**Client**, in `config/Renewables/config.toml`:

| Key | Default | Range | What it does |
|---|---|---|---|
| `previewSnapping` | `true` | | the preview of a span being strung snaps to the exact insulator positions |
| `wireDrawDistance` | `128` | 16–512 | how far away, in blocks, a span is still drawn |

**Game rule:** `/gamerule renewablesRealTimeClock true` (default `false`) ties the world's sun to the real clock:
a day takes a day, and the weather slows with it.

## Building from source

You need a JDK 17. The Gradle wrapper fetches everything else.

```bash
./gradlew build        # compiles, runs the JUnit tests, writes build/libs/renewables-<version>.jar
./gradlew runClient    # a development client, with Mekanism and CC:Tweaked loaded
./gradlew runServer    # a headless development server in run/
```

Models, textures, recipes, blockstates and the language file are generated by the Python 3 scripts in
`tools/` and checked by the `tools/check_*.py` scripts; nothing under `src/main/resources` is edited by hand.
[CLAUDE.md](CLAUDE.md) is the guide to working on the mod: how models and hitboxes are built, which generator
owns which file, what must pass before a change is done, and where the code is.

## Documentation

* [Getting started](docs/getting-started.md) — the items, the two networks, your first grid, the wrench, the turbines' thresholds
* [Items](docs/items.md) — everything in the mod, what it connects to, the parts tree, and what is missing
* [Photovoltaics](docs/photovoltaics.md) — how a plant is wired, the physics, the catalogue, trackers, shading
* [Integrations](docs/integrations.md) — Forge Energy, Mekanism, ComputerCraft
* [Telemetry](docs/telemetry.md) — the ComputerCraft API, every tag, the turbine catalogue
* [The models](docs/model-audit.md) and [the turbine model](docs/turbine-model.md) — how the geometry, textures and hitboxes are made
* In Italian: [game guide](docs/GUIDA.md), [a complete site](docs/IMPIANTO.md), [a 579 kW solar plant](docs/IMPIANTO-SOLARE.md),
  [architecture](docs/ARCHITECTURE.md), [how the code base was rewritten](docs/REWRITE.md)

## License

To be decided.
