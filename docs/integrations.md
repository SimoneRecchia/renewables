# Integrations

← [Back to the README](../README.md) · [ComputerCraft API reference](telemetry.md) · [Photovoltaics](photovoltaics.md) · [Getting started](getting-started.md)

Both integrations are optional: Mekanism and CC:Tweaked are compile-time only dependencies, so the mod builds
and runs without either. Every reference to their types is confined to one class each
(`compat/energy/MekanismEnergyBridge` and `compat/computercraft/CCTweakedPeripherals`), reached only behind a
`ModList` check.

---

## Mekanism and Forge Energy

### The exchange rate

**1 kW = 125 J/tick = 50 FE/tick.**

Mekanism's default is 2.5 J per FE, and the Power Box hands out 50 FE per kW, so the wire network, the Power
Box and the generators all agree on one scale.

### The Power Box

The end of every line exposes **Forge Energy** (extract only) on all six sides and **pushes** what reaches it to
the six blocks touching it, at most 2 000 FE per neighbour per tick. What nobody takes is kept in a buffer of
100 000 FE, and a neighbour draws on the buffer before it touches the flow. Mekanism's own Forge Energy
compatibility carries that into Universal Cables, so a cable against a Power Box carries power with no
Mekanism code of ours involved.

### The generators

A **turbine** and an **inverter** expose their output directly, as **Joules** through Mekanism's
`IStrictEnergyHandler` and as **Forge Energy** for everything else, on two faces: the **bottom** and the face
**away from the one it faces** (its front faces whoever placed it). That mirrors Mekanism's own generators —
cables belong at the foot of a machine, not halfway up it.

* A turbine's machine block sits at the top of its tower, so the tower answers for it: the **lowest tower
  block** exposes the turbine's capability, on the same two faces, and the turbine pushes from there.
* An inverter answers on its own block.

Each tick a generator **pushes** to whatever is adjacent to those faces, splitting fairly between several
acceptors and offering to the others what one turns down, the way a Mekanism generator emits rather than
waiting to be pulled from. Mekanism's Joule handler is preferred over Forge Energy when both are present, so a
transfer is not needlessly rounded to whole FE.

### Why nothing is double-spent

The wire network reads a generator's output every tick and distributes it. If other mods' cables were simply
allowed to take from the same machine, the same Joule would be handed out twice.

So generation opens a **budget** each tick. Other mods' cables claim from it, and the wire network is given
whatever is left. Nothing carries over between ticks, so there is no stored energy to lose or duplicate. Block
entities tick before the grid runs, so the wires see the remainder in the same tick rather than one tick late.

### The configuration

In `<world>/serverconfig/Renewables/server.toml`, section `[energy]`:

| Key | Default | What it does |
|---|---|---|
| `externalEnergyEnabled` | `true` | whether generators offer anything to other mods at all |
| `turbineExportFraction` | `1.0` | the share of a turbine's **nameplate** it may offer, 0 to 1 |
| `turbineMaxJoulesPerTick` | `0.0` | an absolute ceiling in J/tick applied after the fraction; `0` means none |
| `pvExportFraction` | `1.0` | the same share, for an inverter's AC nameplate |
| `pvMaxJoulesPerTick` | `0.0` | the same ceiling, for an inverter; `0` means none |

A fraction rather than a fixed number of Joules, because the machines run from 10 kW to 4 MW and any absolute cap
would either strangle the big ones or hand the small ones too much. With the defaults a generator offers
**everything it makes**, and whatever other mods do not take stays on the wires. At nameplate that is:

| Machine | J/tick |
|---|---|
| SW-10 (10 kW) | 1 250 |
| C52-0.85 | 106 250 |
| C80-2.0 | 250 000 |
| C90-3.0, C112-3.0 | 375 000 |
| C130-4.0 | 500 000 |
| VX-10K / VX-110K / VX-350K / VC-2500K | 1 250 / 13 750 / 44 000 / 312 500 |

A pack that wants the wires to stay the main way power moves lowers the fraction: at `0.25` a C130 gives other
mods at most 1 MW and keeps the rest on the line. The values are read each time they are used, so a change takes
effect without a restart, and a world keeps whatever it has saved.

### Mekanism-compatible methods

The ComputerCraft peripherals of the turbine and the inverter answer to the method names Mekanism uses on its
own generators, so a program written for a Mekanism generator reads them unchanged: `getProductionRate`,
`getMaxOutput`, `getEnergy`, `getMaxEnergy`, `getEnergyNeeded`, `getEnergyFilledPercentage`,
`isBlacklistedDimension`. Redstone control uses Mekanism's names too — `getRedstoneMode` / `setRedstoneMode`
with `DISABLED`, `HIGH`, `LOW`.

---

## ComputerCraft

Four peripheral types: `renewables_wind_turbine`, `renewables_pv_inverter`, `renewables_pv_array` (which also
answers to `renewables_solar_panel`) and `renewables_met_station`.

### Attaching

Place a **Computer** against the machine. A turbine is reached from its machine block at the top **or from any
block of its tower**, the foot included — a real turbine gathers its cables at the tower base, which is where you
will build. The photovoltaic blocks each answer on their own block.

For distance, put a **Wired Modem** on the block and run **network cable** to a computer somewhere else, then
right-click the modem to attach it.

```lua
local t = peripheral.find("renewables_wind_turbine")
print(t.getActivePower() .. " kW")
```

### What a turbine reports

64 signals modelled on real turbine SCADA tags — power, wind, yaw, rotor and generator speed, phase voltages
and currents, blade pitch, gearbox and bearing temperatures, hydraulic pressures, vibration.

They are not all equal, and `getTelemetryKinds()` says which is which:

| Kind | Count | Meaning |
|---|---|---|
| `MEASURED` | 15 | Read from mod state or the world |
| `DERIVED` | 16 | Computed by a relation that holds on a real machine |
| `SIMULATED` | 33 | Instrumentation the mod does not model |

The simulated values track load and ambient temperature through a first-order lag, so they ramp like a thermal
mass rather than snapping. They behave correctly — but there is no energy balance behind them and **no failure
modes**, so a gearbox will never overheat and an alarm written against those tags will never fire. Watchdogs
belong on the measured signals.

### Control

| | |
|---|---|
| `stop()` / `start()` | Brake on and off |
| `setActivePowerLimit(kW)` | Curtail without braking |
| `setRedstoneMode(mode)` | `DISABLED`, `HIGH`, `LOW` |

Stopping and curtailing are different: a curtailed turbine keeps turning and still reads `isRunning() == true`;
only the brake stops it. The four reasons a turbine can be down — your command, a player at its panel, redstone,
and a storm shutdown — are reported separately, so a program can tell its own action from the machine protecting
itself. The inverter has the same controls, plus a power factor.

### Threading

The telemetry — `getTelemetry()`, `getTelemetryKinds()` and every generated `get<Tag>()` — comes off the computer
thread and is served from an immutable snapshot the machine publishes once per tick: no lock, no wait for a tick
boundary, and no chance of reading a mix of values from two different ticks. The other read methods read the
machine's current state.

Methods that *change* a machine run on the server thread instead, because they mark the block entity dirty and
push state to clients.

→ [Full API reference, every tag with units and ranges](telemetry.md)
