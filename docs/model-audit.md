# The models, the textures and the hitboxes

← [Back to the README](../README.md) · [Photovoltaics](photovoltaics.md) · [Getting started](getting-started.md)

Everything a player can place is now built from the same figures, at the same level of detail, with
its collision cut from its own geometry. This is what that means and how it is kept true.

## What was wrong, in a player's words

Two things, said about the game rather than about the code.

**"The turbine and the cabin look right and the rest doesn't."** Those two are a modelling package's
output over 1024-pixel textures; everything else was either boxes with 128-pixel drawings on them or, in
a couple of cases, a cube with a picture of the object on all six faces. The
difference is not resolution. It is that a photograph of concrete has structure at every scale -
blotches, aggregate, form lines, a rust stain - and a drawing of concrete has structure at exactly
one.

**"You walk into air beside things."** Collision was a hand-written box per machine, and a box is
wrong in both directions at once: too big where the machine is thin and absent where it is not.

## The two numbers everything round now uses

The turbine answers this, because the turbine is the model the rest were brought up to:

| part | sides |
|---|---|
| its tower, 1.24 blocks across | **80** |
| its insulator, 0.53 across | **32** |

So `modellib.ROUND` is 80 and `modellib.FITTING` is 32. A principal body gets 80 — the pole shaft,
the met mast, a torque tube, a pedestal column, a lattice tower's peak, a kiosk bushing's core. A fitting gets
32 — an insulator shed, a cable gland, an instrument body, a fan hub.

What deliberately keeps fewer is the geometry that is genuinely faceted, where more sides would not
be smoother but wrong: a **bolt head is a hexagon**, a pier is an **I**, a crossarm is a **channel**,
a luminaire housing is a **casting with flat fins on it**.

## Object by object

Each of these was looked up before it was drawn, and each carries the parts a photograph of the real
thing shows from ten metres away — and nothing smaller, because at a block to ten metres a bonding
jumper is one pixel of noise.

| object | what it is now | was |
|---|---|---|
| **Utility pole** | A spun concrete pole, tapered, mould seam and rust bloom, two galvanised channel crossarms with strap plates and braces, eight brown-glazed porcelain pin insulators, climbing steps, an earth strap, a number plate | A square post with two flat bars, 2364 faces of which nearly all were the insulators |
| **Power box** | A pad-mounted kiosk: cast plinth, sheet body, two moss-green door leaves with louvres low down, a crowned hood with a drip edge, an HV label, a roof bushing, a cable conduit | 43 faces. A cuboid with a door drawn on one side, hugging the west edge of its block and hanging outside it |
| **Flat array** | A ballasted table: precast blocks, channel perimeter, two rails, two whole modules, mid and end clamps | Four leg stubs, a frame, two plates |
| **Fixed rack** | Driven I-section piers, a bolted cleat on each, channel purlins open side down, a diagonal brace per row, four rails up the slope, clamps at every module edge | Four posts and a plate at 25° |
| **Single-axis tracker** | A 1P row: an I pier, a slew drive with a finned geared motor, an 80-sided torque tube, module rails across it with saddles, one whole module either side of the drive bay | A post, a 16-sided tube, four part-modules and purlins running the wrong way |
| **Dual-axis tracker** | A tapered pedestal on a gusseted base, a slew ring, a central elevation bearing with its worm box, a torque tube, two whole modules | A column, two yoke arms, four part-modules |
| **Inverter** | A station machine: channel plinth, corner posts, two door leaves (one carrying the interface, one plain), a crowned hood with lifting eyes, two guarded roof fans, side louvre banks, a DC compartment with fuse ways and glands, an AC bushing | A box, one door, one grille, one fan |
| **Combiner** | An enclosure on an 80-sided post with two bracket cleats, a rain hood, a window over the fuse ways, a rotary isolator, six glands and a trunk gland | A box on a 12-sided post |
| **Met mast** | Two tapered tube sections with a coupling, a hinged base plate on anchor bolts, a logger enclosure, a boom with three radiometers on levelling feet under glass domes, a six-plate radiation shield, a snow gauge, cups and vane, a lightning finial | A tube, three cup-and-disc instruments, a four-plate stack |
| **DC cables** | The pair as it is: two conductors, clips, armour bands on the trunk, and a trench of disturbed spoil | Same shape, 16-pixel textures |

The **turbine** and the **electric cabin** are untouched. They are what the rest was measured
against.

## The textures

`tools/texlib.py` is what changed. It has three things the old drawing code did not:

* **Value noise over octaves that wraps at the tile edge.** A surface gets blotches *and* grain *and*
  the faint unevenness between them. White noise has one scale by construction; a real sheet has all
  of them.
* **Soft edges.** A bolt head is round rather than a stepped square, and a swage line can sit at 12.3
  pixels instead of at 12.
* **One light direction for the whole mod**, over the viewer's left shoulder, so a bevel, a dome, a
  louvre blade and a hex head are all lit the same way. A drawn highlight is what makes a flat pixel
  read as a raised edge.

On top of those sit the materials — brushed aluminium, hot-dip galvanising with its spangle,
powder coat with its orange peel, cast concrete with aggregate, sawn wood, cable rubber, fired
porcelain glaze — and the weathering: dirt in the low places, rain streaks down vertical faces, rust
bleed at a steel edge.

Sizes are chosen by how close a player gets to the face, not by uniformity:

| texture | size | why |
|---|---|---|
| `pv_module` | 1024 | 144 half-cut cells with chamfered corners, three tinned ribbons each, a glass reflection over all of it. It is the surface a player looks at from a metre |
| the sheets, coats, doors, vents, concrete, benches, lenses | 512 | Read from a few metres |
| the profiles, plates, cable, instruments, domes | 256 | Small faces, or repeated along something long |
| `pv_switch` | 128 | A handle boss three centimetres across |

`tools/gen_block_textures.py` writes all of them; `tools/gen_item_sprites.py` keeps the item sprites and
`tools/gen_pv_textures.py` the panel layouts, where sixteen hard pixels is the right answer.

**The item icons of the rebuilt placeables are renders of the models themselves**, at 128 pixels, on
transparent ground. Three of them used to be painted by hand from models that have since been rebuilt
from scratch, so a kiosk in the inventory was grey with one door while the one in the world is green
with two. A sprite taken from the model cannot drift.

## The hitboxes

Every placeable's collision is cut from its own geometry, and `tools/check_hitboxes.py` reads both
back and fails if they have moved apart:

```
$ python3 tools/check_hitboxes.py
the models and the tables agree, to a hundredth of a pixel
```

Three things had to change for that to be possible.

**A group per part.** The shape is cut from each object in the OBJ, so an object holding two parts at
different heights has a bounding box that fills the air between them. The pole's eight insulator
spindles and its seven climbing steps were one `hardware` group, whose box was a slab four blocks
wide and five tall through the middle of the pole; a tracked row's two module bays were one group,
whose box covered the drive between them. They are one object each now, which the clearance checker
wanted anyway.

**Slabs where something slopes.** A tilted plane's bounding box is not the plane: a rack at 25° has a
box seven pixels tall over its whole footprint, so a player walked into a wall of air in front of its
low edge and stood on air above it. Each group is now sliced into two-pixel slabs and each slab
clipped to the geometry, which gives a staircase that follows the slope — the same way the game draws
one. Only where the top climbs or falls all the way across: an 80-sided cone's top goes up and comes
back down, so a cone is one box, within half a pixel of the truth.

**Quarter-pixel parts count.** A ballasted table is two pixels tall in total, so a threshold of a
whole pixel threw away its ballast, its frame and its glass and left it with no collision at all. A
kiosk's plinth and its rain hood are both under a pixel and both plainly visible.

Cable is the case worth stating, because it looks like a mistake and is not: a surface run's outline
is now the pair itself — two pixels across and one tall, plus an arm per connected side and the climb
where it goes up a wall — rather than a slab the width of the block. **Collision stays empty**, so a
player does not catch a boot on every metre of their own plant.

### What cannot be cut to the model, and why

Two of the eleven, and in both cases the alternative is worse:

* **A tracked row and a pedestal frame** turn through the day. Their collision is the fixed body cut
  from the model plus the volume the plane sweeps, because a shape cut from the flat position would
  let a player fall through the row every afternoon.
* **The turbine** carries its own tower in the model, which in the world is a stack of
  `turbine_tower` blocks with their own collision, and a hundred metres of turning rotor above it
  that nothing should be able to stand on.

`check_hitboxes.py` prints both cases with the reason rather than passing them silently.

## Which way a machine faces, and the three places that have to agree

The renderer poses the model, the block entity turns the points its wires hang from, and the
collision cells turn with it. One fact, three readers, so it is declared once as `AUTHORED` on the
block and the arithmetic lives once in `ModelFacing`.

**Every model the mod generates now faces north**, which is one fewer special case than before: the
pole's old geometry was *mirrored* rather than turned, so north and south came out swapped and it
needed a rotation table of its own that nothing else used and that three separate pieces of code had
to know about. That table is gone. The power box was authored east and off-centre; it is north and
centred. Only the turbine and the cabin still face south and east, because they are unchanged.

`check_hitboxes.py` fails if a renderer works the authored facing out for itself instead of reading
its block's constant.

## What the checkers cover

| script | what it proves |
|---|---|
| `check_hitboxes.py` | every machine collides with exactly the shape it draws, to a hundredth of a pixel, and prints the tables the blocks declare |
| `check_model_textures.py` | no coplanar faces that both show, no geometry inside other geometry, no bordered picture sub-sampled, no picture on all six faces of a box, and texel density measured along each face's own u and v |
| `check_pv_clearance.py` | no moving part can pass through a fixed one at any angle |
| `check_connectivity.py` | nothing floats, no member of a frame ends in the air, nothing is drawn twice or buried - see below |
| `check_gui_fits.py` | no two lines of a panel overlap, and no line is behind a widget |
| `preview_models.py` | draws the geometry from three angles, for the half no rule can decide |

All four pass with nothing outstanding. Two of them learned something in this pass:

* the density check used to pair a face's u with its *longest world axis*, which is right for a box
  and backwards for the side of a cylinder — there u runs round the tube over a chord a few
  thousandths of a block wide. Every 80-sided prism in the mod therefore read as stretched by a
  factor of two hundred, and the one real case would have been lost among them. It now measures the
  world distance along the face's own u and v, and skips the wedge-shaped quads a cap is fanned into.
* the coplanar check knows which parts are never drawn together - a tracked row's cable run and its
  end plugs are the case, since the renderer draws one or the other and never both,
  one per glow state, and the renderer draws exactly one of them.

## Nothing floats: `check_connectivity.py`

"Aste che non sono collegate a niente": a lattice member stopping in mid air, a disc hanging round a rod it never
touches. None of the checkers above could see it, because none of them asks what holds a part up. This one does.

```bash
python3 tools/check_connectivity.py                    # every machine, and every state of every run
python3 tools/check_connectivity.py lattice_tension    # one
```

**How it measures.** A *body* is a run of faces joined by shared vertices - one primitive, usually. Two bodies are in
contact when their surfaces come within a sixty-fourth of a block (a quarter of a pixel) or one is inside the other.
That is decided exactly, triangle against triangle - vertex to face, edge to edge, edge through face - with a
bounding-volume tree per body so only triangles that can touch are ever compared; "inside" is the generalised winding
number of a closed body. Pure Python, eleven seconds for the whole mod. What it reports, each a failure:

| finding | meaning |
|---|---|
| `FLOATING` | a body in touch with nothing that reaches the ground (or the wall, or a block's edge) |
| `DETACHED` | a body on the ground that is not part of the machine: a second object beside it |
| `DANGLING` | an end of a bar (four times longer than wide: a member, a brace, a rod, a cable) resting on nothing |
| `DOUBLE` | two bars along one line for most of their length: one member drawn twice |
| `BURIED` | a body wholly inside another, which nobody can ever see |
| `PIVOT` | a `pivot_*` marker on nothing: the hinge a part turns about is in mid air |

A model is checked in **every state its renderer draws** (the 32 harness states of a row, a switch's two flags, a kiosk
on a wall without its plinth, the inverter with and without its DC section and cables) and in **every pose that moves
one part against another** (the turbine posed by `group_matrix` for all six machines at their shortest and tallest
tower, the inverter at each of its three cabinet scales, with the cable entries unscaled). A run's pieces are never
judged alone: each distinct state of its multipart blockstate is composed the way the game composes it, because an
arm's inner end is only supported once the hub is drawn with it. What is free by nature is declared in the tool with
its reason - a blade tip, a lightning finial, a whip antenna, a cable arriving from the next block - and nothing else.

### What it found, model by model

| model | fault | what was done |
|---|---|---|
| **lattice towers** (all three) | every porcelain disc floated: an open cone round a rod it never touched; crossarm ties and struts ended at the middle of a body face, where there is no member; the upper arm's ties ended in the air beside the peak; the number plate hung in the middle of a face; the terminal tower's "stays" were rigid struts to a pad, which no lattice tower has | redrawn from a real tower (below) |
| **tension/terminal towers** | the jumper's ends stopped short of the clamps | redrawn: quad jumper into terminal blocks under the dead-end clamps |
| **ground conductor runs** | a dead end's compression clamps were open tubes floating round the conductor's end; a tee's and a cross's legs stopped 0.8 px short of the clamp joining them; an offcut's upper sub-conductors overhung their spacers | clamp bodies closed, the conductor runs into them; the hub carries each leg into its groove; spacers near the cut ends |
| **circuit breaker** | the bushings' sheds were open bands round their stalks, not touching them | closed discs the stalk passes through (also the epoxy column's sheds) |
| **disconnector** | the torque shaft ran 0.12 past the last hinge with nothing to carry it | ends inside the first hinge boss (and not flush with it: that plane flickered) |
| **dual-axis tracker** | the slew drive and everything it carries stood on air 2 mm above the column, open underneath | a head flange on the column, the slew collar closed; `pivot_azimuth` now inside it |
| **machine transformer** | the oil-level dial and the drain valve stood 2 cm off the tank | each on a boss welded to the wall |
| **substation transformer** | every radiator bank stood off the tank on pipes drawn *along* the wall; two banks sat on the tank's stiffeners | headers go straight into the tank through a flanged valve; banks between the stiffeners; panels thinned (below) |
| **plant control cabinet** | a DIN rail and five modules drawn inside a closed steel cabinet | removed: nothing could ever see them |
| **utility pole** | the braces ended in the hollow of the channel, bolted to nothing; each arm ran half a block past its outer insulator | braces up to the channel's web; arms end a hand past the insulator |
| **wind turbine** | the spinner stopped 0.02 short of the main-bearing housing: the rotor hung in front of the nacelle on nothing; on every machine but the C130 the insulator post stood on the ground on its own | a main-shaft flange (`motor_shaft`) from the housing into the hub; a cable trough (`insulator_duct`) from the post to the tower - see docs/turbine-model.md |

Two checkers learned from it. `check_model_textures` caught the disconnector shaft cut flush with its boss, and
`check_pv_clearance` that the tracker's first head flange was wider than the modules allow at full tilt.

### The towers, drawn from a real one

The three duties are a **2 x 380 kV Donaumast** - the most common German 380 kV tower - with the figures a real one
has, scaled once (`gen_tower_models.SCALE`, 54 m to 11.5 blocks: a block is 4.7 m of tower).

| | figure | source |
|---|---|---|
| height to the earth wire | 54 m | "Die übliche Höhe des weithin verbreiteten Donaumastes beträgt etwa 54 m" - [netzausbau.de, Freileitungen](https://plus.netzausbau.de/N2000/DE/Technik/Freileitungen/freileitungen-node.html) |
| lower crossarm across its tips | 32 m (phases at ±16 m and ±8 m) | "bei einem Donaumast sind es etwa 32 m" - same page |
| arrangement | two phases a side on the lower arm, one on the upper, one earth wire at the peak | [Amprion, Freileitung](https://www.amprion.net/%C3%9Cbertragungsnetz/Technologie/Freileitung/) |
| conductor | quad bundle, 4 x 565-AL1/72-ST1A, at 420 kV highest operating voltage | [TenneT datasheet, 380-kV Adlkofen-Matzenhof, Musterberechnung Donaumast](https://www.regierung.niederbayern.bayern.de/mam/service/planfeststellungsverfahren/beschluesse/20240813_adlkofen_matzenhof/u_16_01_2__16.1.2_musterberechnung_donaumast_2_x_380_kv_bei_100__nennlast.pdf) |
| lower crossarm at 34 m | 15 m ground clearance (same datasheet) + about 14 m of sag on a 400 m span + a 4.4 m string | the datasheet's minimum clearance; Amprion's 300-500 m spans |
| upper crossarm 10.5 m above the lower, phase at ±11 m | between the two lower phases, above the 2.8 m phase-earth clearance of EN 50341-1 for 420 kV | EN 50341-1, clearances D_el / D_pp |
| suspension string | I-string, 22 x U160BS (146 mm pitch, 280 mm disc): 3.2 m of porcelain, 4.4 m arm to bundle | IEC 60305 for the units; IEC TS 60815-2 for the count: 420 kV x 20 mm/kV (medium pollution) / 380 mm a unit = 22 |
| tension string | double string, 2 x 23 x U210B (170 mm, 300 mm) a side, grading ring, four compression dead-ends | IEC 60305; a 380 kV double tension string is "DA 380 kV, 7.30 m" in TenneT's own plans |
| bundle at the tower | four clamps on a square yoke frame at ConductorCatalog's own spacing, so a span lands in them | ConductorCatalog (the same 0.075 the wire is drawn with, 0.35 m at the tower's scale - a real quad is 0.40-0.45 m) |
| foot | 9.6 m square (suspension), 12.4 m (tension), 14.4 m (terminal), each leg on a concrete pier | a tension and a terminal tower take a longitudinal pull a suspension tower never sees, so they are wider and cross-braced; no lattice transmission tower is guyed, so the terminal tower's stays are gone |

**Every member runs from node to node.** The legs and the arm chords are rolled angles (two flanges, heel outwards);
the bracing lies on a gusset plate inside the leg's flange, an arm's lacing on its chords' flanges; plan crosses keep
the section square at the arm levels; a suspension string hangs from a plate bolted under the transverse at its
station, a tension string from a strain plate under the chord's node. A suspension tower is single-braced, a tension or
terminal tower cross-braced in every panel. The peak carries the earth wire's clamp (suspension) or two strain clamps
(tension; one on a terminal tower).

**What a span lands on changed with it.** `LatticeTowerBlockEntity` lands a bundle in the middle of the phase's clamp
group (`clamp_N_fitting` in `fittings.json`): under a suspension string, between a tension tower's two dead-ends, at
the outer end of a terminal tower's. It was the underside of the porcelain, which with a real string is half a metre
of hardware above the conductor. Anchors in the model's frame (`gen_tower_models.py --anchors`):

| phase | suspension | tension | terminal |
|---|---|---|---|
| 1 / 4 (lower outer) | ∓3.407, 6.310, 0 | ∓3.407, 7.165, 0 | ∓3.407, 7.163, -1.175 |
| 2 / 3 (lower inner) | ∓1.704, 6.310, 0 | ∓1.704, 7.165, 0 | ∓1.704, 7.163, -1.418 |
| 5 / 6 (upper) | ∓2.343, 8.546, 0 | ∓2.343, 9.401, 0 | ∓2.343, 9.399, -1.175 |

A tension tower's two dead-ends are one fitting, so both of its spans end at the middle of the arm; ending each on its
own side needs the anchor to know which way its span leaves, which is the wire code's to decide.

**What it costs.** 10 029 faces (suspension), 30 819 (tension: 552 porcelain units in 24 strings), 14 889 (terminal),
against the turbine's 11 942. The collision tables are one a duty now: a terminal tower's foot is half again as wide
as a suspension tower's, so they share no cell.

### The other realism fixes

* **Substation transformer radiators**: panel radiators to EN 50216-6 / DIN 42559 are 520 mm panels of 1-1.2 mm sheet at
  a 45-50 mm pitch on two headers, each header into the tank through a valve ([Akgün radiator data](https://akgunradiator.com.tr/en/?p=1938)).
  The panels were drawn 6 cm thick at a 7 cm pitch - a solid block; they are 2 cm thick now, so the air gap shows.
* **Utility pole**: a crossarm ends a hand past its outer insulator; half a block of bare channel past it carried nothing.
* **Control cabinet**: the louvre lets air over a DIN rail behind a shut steel door, so the rail is not drawn.

### Curved surfaces shade curved

`modellib.Mesh.smooth` gives every face one normal per corner - the faces round that vertex that bend less than 40°
(`modellib.CREASE`) - and every machine generator runs it. The mod's loader reads a normal per corner and lights each
vertex with it (`client/model/ModelBuilder`, `MachineRenderer.GroupBuffer`), so the turbine's blades, spinner and tower,
the pole's shaft, a torque tube, a disc of porcelain shade round instead of as strips; a hexagon (60°), a box's edge, a
cap on a cylinder and a blade's trailing edge keep their crease. Geometry is untouched: every OBJ's vertices and faces
are byte for byte what they were, only `vn` changed. `render_blocks.py` now lights per corner too, so the renders show it.

## Rendering with the game itself

`tools/preview_models.py` draws flat-coloured geometry, which answers where things are and not what
they look like. The only thing that can answer the second question is the game, and the reason is
worth stating because it decides what is possible: **almost nothing in this mod is a vanilla JSON
block model.** An array is an OBJ drawn by Java with a matrix per group, groups that appear only once
a reel of cable has been worked in, a tracker angle computed from the sun, and a per-product scale.
Every off-the-shelf renderer reads vanilla JSON models. None of them runs that Java.

So the mod renders itself. `PreviewStage` is a client-only dev tool that builds a stage, steps the
camera round it and calls the same screenshot the F2 key does:

```
/preview renewables:pv_tilt_530 pair
```

`pair` puts a second machine behind the subject and a run of cable in front of it, which is where
half the questions live. Five views come out per machine — front, side, three-quarter, plan, and the
eye level of somebody walking past, which is where anything floating or sunk shows up — into
`run/screenshots/`.

For a whole catalogue unattended, which is what makes it usable from a terminal:

```bash
JAVA_TOOL_OPTIONS="-Drenewables.preview=all" ./gradlew runClient
```

Two things it needs: a world with cheats on (it works by sending commands), and a display. It is a
*client* tool, so a machine whose screen is asleep cannot run it — GLFW has no monitor to open a
window on, and the game will not start at all.

## The three model formats, and which gets which

| | OBJ drawn by Java | OBJ in a vanilla block model | vanilla JSON boxes |
|---|---|---|---|
| **who** | the turbine, the cabin, the pole, the kiosk, the arrays, the inverter, the combiner, the mast, the lattice towers | the string cable | the trunk cable |
| **why** | it moves, or it is scaled per product, or a wire hangs off a named part of it | it is round *and* laid by the hundred | it is all boxes and not yet redrawn |

The middle column is the one worth knowing about. A vanilla JSON model **cannot express a cylinder**: an
element with a rotation is a *union* rather than an intersection, so three boxes at 22.5° make a
twelve-pointed star and not a twelve-sided prism. Two attempts at a cable out of axis-aligned boxes
proved it — a flat painted bar read as a ribbon, a stack of stepped boxes read as a square duct.

But a cable is laid by the hundred, and the machines' pipeline draws one block entity at a time. So the
string cable's pieces are real swept tubes loaded through **Forge's own OBJ block-model loader**
(`"loader": "forge:obj"`), which bakes them into the chunk mesh like any other block model: full geometry,
and two hundred cables cost what two hundred blocks cost.

That path has one trap, and it is silent. A block model's texture is a **sprite in an atlas**, so a uv
past one does not tile — it samples whatever sprite the atlas put next door. The first version ran its v
to 1.9 along every tube and came out with white bands across the cable, and none of it showed in a
previewer, which binds one texture at a time and wraps. `gen_cable_models.py` now refuses to write a
piece with any uv outside its sprite.

## Regenerating any of it

```bash
python3 tools/gen_block_textures.py     # every block texture
python3 tools/gen_pv_textures.py        # GUI panels
python3 tools/gen_item_sprites.py       # item sprites
python3 tools/gen_pv_models.py          # the arrays, the inverter, the combiner, the mast
python3 tools/gen_grid_models.py        # the pole and the kiosk
python3 tools/gen_tower_models.py       # the three lattice towers
python3 tools/gen_turbine_models.py     # the wind turbine and its insulator, one mesh for all six machines
python3 tools/gen_cable_models.py       # the cables (--java prints the shape tables)
python3 tools/gen_crafting.py           # recipes, part item models, blockstates, loot, tags
```

Everything is deterministic — every random-looking value comes from a hash of the coordinates and a
salt — so running a generator twice produces byte-identical files and a rebuild is not a diff.
