# Getting started

← [Back to the README](../README.md) · [Items](items.md) · [Photovoltaics](photovoltaics.md) · [Telemetry](telemetry.md) · [Integrations](integrations.md)

## The items

Everything is crafted at a normal crafting table or smelted in a furnace. What gates a power station is
the depth of the tree, not a special bench: a tracker wants a torque tube and a slew drive, the slew drive
wants a gear set and a motor core round a bearing, and the bearing comes back down to plate and nuggets.
[Items](items.md) has the whole list and the parts tree.

| Family | Items |
|---|---|
| **Tools** | Power Wrench; three conductor reels — Aerial Bundled Cable (low voltage), Medium-Voltage Conductor, High-Voltage Conductor |
| **Wind** | Turbine Tower; six turbines from the 10 kW Cube SW-10 to the 4 MW Cube C130-4.0 |
| **Solar** | six Meridian arrays (flat tables, fixed racks, a single-axis and a dual-axis tracker); four Volterra inverters, 10 kW to 2.5 MW; three combiner boxes; the Cabria SC-6 string cable and DT-240 trunk; the Meteorological Mast; the Plant Controller |
| **Grid** | Electric Cabin, Utility Pole, Power Box (the kiosk at the end of a line); Machine and Substation Transformers; Disconnector and Circuit Breaker; Suspension, Tension and Terminal lattice towers |
| **Components** | Circuit Board, CPU, Glass Screen, Insulator, Metal Casing, Motor Core, and thirty-two parts |

Three habits make everything else easier:

* **The Power Wrench reads everything.** Right-click a machine and its panel opens, or — for a machine with no
  panel of its own — a card with the power passing through it. It aims at the machine's model, not at a block, so
  it reaches the insulator at the top of a tower from the ground (16 blocks).
* **An empty hand operates, an item in the hand does not.** A disconnector is thrown, a combiner isolated and a
  pole's arms configured with an empty hand. With a reel in your hand the click goes to the reel, so you can string
  a span onto a switch without opening it.
* **Large machines fill the cells round them.** A cabin, a pole, a transformer, a tower or the plant controller
  stands in invisible cells; you cannot build inside them, and breaking any of them breaks the machine. Every
  machine faces you when you place it.

## Two networks

**Direct current is laid on the ground.** The string and trunk cables are blocks: they join whatever is next
to them, and a machine finds what it is connected to by following the copper. One block of cable is 10 m and
the loss in it is real. See [Photovoltaics](photovoltaics.md).

**Alternating current is strung between fittings.** Hold a conductor reel, right-click an insulator, then
right-click a second one: a span is strung between the two and hangs with its own sag. Each conductor goes on
its own voltage class only:

| Reel | Class | Longest span | Blocks of span per reel |
|---|---|---|---|
| Aerial Bundled Cable (NFA2X 4×70) | low, 1 kV | 40 blocks | 4 |
| Medium-Voltage Conductor (Aster 228) | medium, 33 kV | 90 blocks | 8 |
| High-Voltage Conductor (Curlew ACSR, quad) | high, 420 kV | 160 blocks | 16 |

A span costs one reel per started stretch of that length, and is free in creative. Breaking a machine takes
its spans down and gives the reels back at the end they were strung from.

The server checks everything again and says why it refuses: a fitting that is not built for that class, ends
that are further apart than the conductor spans, not enough reel, a pair already joined, more than 16 spans on
one fitting. Used on a block that is not a machine, a reel lays a **run** of that conductor on the ground
instead: each run block carries one dead-end fitting, which a span can be strung to.

## Who feeds whom

A span carries power only between machines that may feed each other. Strung the wrong way round it is still
made — nothing is refused — but it carries nothing, which is the most common way to end up with a grid
reading 0 kW.

```
turbine ─┐
         ├─► machine transformer ─┐
inverter ┘                         ├─► cabin ─► pole ─► … ─► pole ─► power box ─► FE
         └────────────────────────►┘      │
                                           └─► substation transformer ─► tower ─► tower …
```

| A… | feeds |
|---|---|
| turbine | a cabin, another turbine, an inverter, either transformer |
| inverter | a cabin, another inverter, either transformer |
| cabin | a pole, a lattice tower, either transformer, a run |
| pole | another pole, a power box, a run |
| machine transformer | a cabin, either transformer, a run |
| substation transformer | a lattice tower, a run — nothing else: this is where a transmission line starts |
| lattice tower | another tower, a cabin, a pole, either transformer, a run |
| run | a run of the same conductor, a tower, a pole, a cabin, a power box, either transformer |
| power box | nobody: it is the end of the line |

A **disconnector or breaker** has no place of its own: it passes power both ways while it is closed and stops
it while it is open, so it goes *in* a line, never at the end of one. And every pair still has to share a
conductor class: a tower takes high voltage only and a pole low or medium, so a tower and a pole are on each
other's lists and still cannot be joined. Coming off a transmission line takes a second substation transformer
and a medium-voltage run.

## Building your first grid

### 1. Find wind

Read the wind off a turbine's own panel with the wrench, or put up a Meteorological Mast. Each model has its
own cut-in (3 to 4 m/s, see [below](#turbine-thresholds)), and the weather gives more wind with height.

### 2. Tower and turbine

Stack **Turbine Tower** blocks and place the turbine on top. Each model is certified for a range of tower
heights and refuses any other, telling you which: SW-10 2–4 blocks, C52-0.85 4–7, C80-2.0 6–10, C90-3.0 8–11,
C112-3.0 8–12, C130-4.0 9–13. Nothing is built taller than 16.

### 3. Turbine → cabin

The turbine's insulator stands **beside the foot of the tower**, and it takes the medium-voltage conductor.
With a Medium-Voltage Conductor in hand, right-click it, then the cabin's **input** insulator.

> **Which insulator?** The cabin has two, one at each end of its roof. The **output** is at the end with the
> door — the end that faced you when you placed it — and the **input** is at the far end. A generator strung to
> the output gets a span that carries nothing.

### 4. Cabin → poles

From the cabin's output, string low or medium voltage to any of a Utility Pole's eight insulators, and on from
pole to pole. A pole is one busbar: anything that reaches one of its insulators reaches all eight.

### 5. Pole → power box

The **Power Box** takes the Aerial Bundled Cable only, on the insulator **on its roof**. A box can stand on the
ground or, placed against the side of a block, hang on the wall.

### 6. Consumers

The power box turns what reaches it into **Forge Energy at 50 FE per kW** and pushes it to the **six blocks
touching it**, keeping what nobody takes in a buffer of 100 000 FE. Within its **field** — a sphere of
`powerBoxRadius` blocks, 5 by default — it also switches on every block with a `powered` or `lit` state (lamps,
rails, doors, whatever they are) and puts back only those it switched when the power goes, and it shares the
power among the block entities that offer the mod's own consumer capability.

## Losses

Every hop between two machines delivers

```
efficiency = max(0.1, 1 − (length × 0.01) / (1 + 0.35 × (spans − 1)))
```

* **About 1% per block**, the length measured between the two machines' blocks: a 30-block hop keeps about 70%.
* **Parallel spans help.** Several spans between the same two machines (on different fittings) share the
  penalty: two cut it by about a quarter, three by about two fifths.
* **A hop never delivers less than a tenth** of what entered it, however long.
* **Where a line divides, the power divides equally** between the branches. Several generators add up.
* The conductor decides what may be strung and how far, not what is lost.

## The Power Wrench

Right-click a machine — or point at one in the air, up to 16 blocks away — and a panel opens. None of them pauses
the game, so you can leave one open and watch the values move.

| Machine | Panel |
|---|---|
| Wind turbine (or any block of its tower) | output, wind, rotor and yaw; stop and start, redstone mode, a curtailment slider |
| Inverter | DC and AC side, efficiency, cabinet temperature, strings; stop and start, redstone mode, power factor, a power limit |
| PV array | irradiance, module temperature, what it delivers; on a tracker, the mode and the angle |
| Combiner box, Meteorological Mast | readings only |
| Plant Controller | its mode (off, limit, redstone) and its setpoint |
| Everything else | a card with the power passing through it; a power box adds the energy it holds, a pole its offsets |

A **Utility Pole** also has a configuration screen, opened by right-clicking it **with an empty hand**: it moves
and turns the pole's head (offset up to a block, yaw, and up to 45° of tilt). The wires follow, because the
drawing, the pointing and the wire anchors all go through the same transform.

## Turbine thresholds

| Model | Nameplate | Cut-in | Rated | Storm derating from | Cut-out |
|---|---|---|---|---|---|
| SW-10 | 10 kW | 3.0 m/s | 13.9 m/s | — | 25 m/s |
| C52-0.85 | 850 kW | 4.0 m/s | 14.9 m/s | 22 m/s | 25 m/s |
| C80-2.0 | 2 MW | 4.0 m/s | 14.6 m/s | 22 m/s | 25 m/s |
| C90-3.0 | 3 MW | 3.5 m/s | 15.5 m/s | 22 m/s | 25 m/s |
| C112-3.0 | 3 MW | 3.0 m/s | 13.3 m/s | 22 m/s | 25 m/s |
| C130-4.0 | 4 MW | 3.0 m/s | 13.25 m/s | 22.5 m/s | 25 m/s |

Above the storm threshold a turbine derates instead of tripping. At cut-out — or on a three-second gust above
30 m/s — it brakes and feathers its blades, and it comes back on its full curve once the wind has dropped to
22 m/s. A turbine also loses power when it is not pointed into the wind: it yaws to follow, but only corrects
past a 7.5° deadband, so it normally sits slightly off and gives up a few percent.

→ [The full power curves](telemetry.md#3-the-catalogue)

## Wind surges

In rough air a turbine puts disturbances down the line — surges, brownouts and drop-outs — which weaken at
every hop. The **power box** reacts: a severe surge that lasts can trip its breaker for three seconds, a
brownout scales what it supplies, and a drop-out cuts its consumers off for as long as it lasts. Every consumer
receives the same information in its delivery event and may act on it.

There is no game rule for this; it is always on.

## Running on the real clock

```
/gamerule renewablesRealTimeClock true
```

The sun stands where the real sun stands: six in the evening outside is six in the evening in the world, a day
takes a day, and a second of play is a second. The weather slows with it, so a front takes a couple of real days
to pass instead of forty minutes.

It is here because a power system is something you watch over hours. Sleeping still heals, still sets a spawn
and still clears the weather — it just cannot move the sun any more.

## For developers

To make your own block consume power from the grid, implement `IElectricPowerConsumer` and expose it through
`RenewablesCapabilities.ELECTRIC_CONSUMER` on a block entity inside a power box's field. Power is in kW, and
`onPowerSupplied` also hands you a `PowerDeliveryEvent` carrying surge, brownout and disconnect information.

To take energy the other way, in Joules or Forge Energy, see [Integrations](integrations.md).
