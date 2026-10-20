# Renewables — piano di riscrittura

## Stato

**La riscrittura è completata.** Ogni file Java con logica dell'altro progetto elencato più sotto è stato
riscritto da zero da specifiche di comportamento, con struttura e nomi nuovi (`docs/ARCHITECTURE.md`); i file che
erano interamente dell'autore sono stati portati nei package nuovi. Nessuna classe, libreria o asset di quel
progetto è nel repo. Il resto di questo file è il registro storico dell'inventario da cui si è partiti.

Dove stanno ora i sottosistemi riscritti (radice `io.github.simonerecchia.renewables`):

| sottosistema | prima (registro sotto) | ora |
|---|---|---|
| ingresso della mod, registri, rete | `Electricity`, `ElectricityClient`, `ElectricityNetworking`, i payload | `Renewables`, `RenewablesClient`, `registry` (`ModBlocks`, `ModItems`, `ModBlockEntities`, `ModTabs`), `net` (`Net`, `ServerHandlers`, `PanelGuard`, `ClientBridge`, `net.packet`) |
| configurazione, eventi del server | `ElectricityServerConfig`, `ElectricityClientConfig` | `core` (`ServerConfig`, `ClientConfig`, `ModGameRules`, `RealTimeClock`, `ServerEvents`) |
| la rete elettrica | `PowerNetwork` | `grid` (`Grid`, `LevelGrid`, `GridSolver`, `GridMath`, `FlowRules`, `DisturbanceModel`, `EnergyBudgets`, `GeneratorBudget`) |
| campate, id degli isolatori | `WireManager`, `InsulatorIdRegistry`, `InsulatorPartHelper` | `grid` (`WireLedger`, `WireData`, `SpanStore`, `SpanCodec`, `SpanPlanner`, `InsulatorIds`, `IdPool`, `FittingTypes`) |
| base delle macchine con isolatori | `FittedBlockEntity`, `MachineShellBlock` | `block` (`MachineBlock`, `MachineBlockEntity`, `MachineShell`, `MachineShellBlock`); inverter e tratti posati compresi |
| palo, cabina, kiosk, sezionatori, tralicci, trasformatori | i rispettivi `*Block` / `*BlockEntity` | `block` (stessi nomi, più `PowerShare`, `FePool`, `FieldSwitch`, `PowerFieldShape`, `SwitchLogic`, `TransformerFlow`) |
| turbina, torre, meteo | `WindTurbineBlock(Entity)`, `TurbineTowerBlock`, `GlobalWeatherManager` | `block` (`WindTurbineBlock(Entity)`, `TurbineTowerBlock(Entity)`), `wind` (`TurbineDynamics`, `TurbineGeometry`), `weather` (`LevelWeather`) |
| tabella dei modelli, ancoraggi | `ObjDefinitions`, `ObjBoundingBoxRegistry`, `ObjTransforms` | `model` (`ModelDefinitions`, `PartBoxes` su `data/renewables/fittings.json`, `ExtraPose`) |
| caricamento OBJ, picking | `ObjModel`, `ObjLoader`, `ObjRaycaster`, `ObjBlockRegistry`, `ObjInteractionRegistry`, la libreria `renderix` | `client.model` (`ObjReader`, `MtlReader`, `ObjMesh`, `ModelBuilder`, `MeshModel`, `ModelCache`, `ObjModelLoader`, `ModelBindings`, `PartPicker`, `PartFrame`, `PartSearch`, `Rays`) |
| disegno delle macchine | `ObjRendererBase`, `ObjRenderUtil`, `ObjRenderContext`, i renderer di palo, cabina, kiosk, traliccio, turbina | `client.render` (`MachineRenderer`, `RenderContext`, `RenderLifecycle`, `FrameClock`, un renderer per famiglia) |
| cavi lato client, input, chiave | `WireRenderer`, `WireManagerClient`, `WirePhysics`, `InsulatorLookup`, `WireAnchorHelper`, `WireInteractionEvents`, gli hook client | `client.wire` (`ClientWires`, `InsulatorIndex`, `WireRenderer`, `WireProfile`, `WireLod`, `LinkMachine`, …), `client.hooks`, `client.events`, `client.ClientBlockEntities` |
| schermate | `PowerInfoScreen`, `UtilityPoleConfigScreen` | `client.screen` (`PowerInfoScreen`, `UtilityPoleConfigScreen`, `PoleConfigText`) |
| oggetti, API pubblica | `PowerWrenchItem`, `ConductorItem`, `TooltipItem`, `TooltipBlockItem`, `PowerDeliveryEvent`, `IElectricPowerConsumer`, `ElectricityCapabilities` | `item`, `api.power` (`RenewablesCapabilities`) |

Asset: tutti generati dagli script di `tools/` — palo e kiosk da `gen_grid_models.py`, cabina da
`gen_cab_models.py`, turbina da `gen_turbine_models.py` e `gen_turbine_textures.py` (`plastic2.png` non esiste
più), icone d'inventario da `gen_item_sprites.py`, pannelli GUI da `gen_pv_textures.py`, i `.mtl`
con i loro modelli. Verificato: eseguendo ogni generatore su una copia dell'albero si ottiene
`src/main/resources` identico byte per byte. Nessun file audio.

Verifiche: `./gradlew build` (compilazione e 320 test JUnit), i `tools/check_*.py`, e il server headless
(`./gradlew runServer`, prove via RCON con `tools/rcon.py`).

## Registro storico: l'inventario di partenza

Renewables nasce da un fork di [dooji2/electricity](https://github.com/dooji2/electricity) (codice GPL v3, asset
All Rights Reserved), ma **non ne è più un fork**: l'obiettivo è che nel repo non resti nulla di dooji, né codice né
asset. Questo file è l'inventario di quello che va rifatto e la regola con cui farlo.

## Regola di riscrittura

* Il codice si riscrive **da una descrizione del comportamento** (le guide in `docs/`, i test, il gioco in esecuzione),
  mai guardando il file originale e riformulandolo. Struttura, nomi e architettura sono nuovi.
* Il codice che `git blame` attribuisce a SimoneRecchia **e** che non poggia su file di dooji si può portare così com'è
  (con il rename di package). Un file con anche una sola riga di logica di dooji si riscrive.
* Nessun modello, texture o audio di dooji entra nel repo. Le texture dell'inventario si rifanno in stile Minecraft,
  leggibili a 16 px.
* Non si porta `renderix` (libreria di dooji): il caricamento dei modelli OBJ si riscrive da zero.

## Stato attuale del vecchio progetto (misurato con `git blame -w -M -C`)

| | dooji | SimoneRecchia |
|---|---|---|
| righe Java (tutte) | 6 263 | 17 235 |
| righe Java di logica (no package/import/commenti/parentesi) | 3 143 | 8 977 |
| file Java con ≥1 riga di logica di dooji | 72 su 179 | |
| file Java senza alcuna riga di dooji | 48 su 179 | |

Audio: **nessun file audio** nel progetto, niente da togliere.

## Asset aggiunti in origine da dooji (da sostituire)

| file | nota |
|---|---|
| `models/utility_pole/utility_pole.obj` | oggi 13 righe su 9 379 ancora sue: già rifatto |
| `models/electric_cab/cab.obj` | oggi 1 riga su 7 616: già rifatto |
| `models/power_box/power_box.obj` | oggi 2 righe su 2 084: già rifatto |
| `models/wind_turbine/wind_turbine.obj` | **rifatto da zero**: `tools/gen_turbine_models.py`, vedi `docs/turbine-model.md` |
| `textures/item/` (tutti e settanta) | **rifatti da zero** in stile vanilla: `tools/gen_item_sprites.py` |
| `textures/block/plastic2.png` | **eliminata**: la turbina ha le sue (`tools/gen_turbine_textures.py`) |
| `icon.png` | **sostituita** da un'illustrazione fornita a parte, 512 px in palette da 256 colori (l'unico file non generato) |
| 46 file `.mtl` | compagni degli OBJ: si rigenerano con i modelli |

## File Java con logica di dooji (da riscrivere), ordinati per righe

Colonne: righe di logica di dooji / righe di logica di SimoneRecchia. Percorsi relativi a `com/dooji/` nel vecchio progetto.

| dooji | tue | file |
|---:|---:|---|
| 347 | 18 | `electricity/block/PowerBoxBlockEntity.java` |
| 253 | 60 | `electricity/main/power/PowerNetwork.java` |
| 248 | 254 | `electricity/block/WindTurbineBlockEntity.java` |
| 171 | 32 | `electricity/block/UtilityPoleBlockEntity.java` |
| 149 | 0 | `electricity/client/render/obj/ObjModel.java` |
| 147 | 11 | `electricity/client/render/obj/ObjRaycaster.java` |
| 140 | 25 | `electricity/block/ElectricCabinBlockEntity.java` |
| 125 | 82 | `electricity/main/wire/WireManager.java` |
| 125 | 56 | `electricity/client/wire/WireRenderer.java` |
| 87 | 160 | `electricity/main/Electricity.java` |
| 81 | 0 | `electricity/client/UtilityPoleConfigScreen.java` |
| 75 | 0 | `renderix/RenderixLoader.java` |
| 67 | 69 | `electricity/client/render/obj/ObjRendererBase.java` |
| 60 | 7 | `electricity/client/wire/WireManagerClient.java` |
| 58 | 22 | `electricity/client/events/WireInteractionEvents.java` |
| 50 | 0 | `renderix/RenderixFace.java` |
| 47 | 35 | `electricity/client/hooks/PowerWrenchClientHooks.java` |
| 43 | 1 | `electricity/client/hooks/WireClientHooks.java` |
| 39 | 12 | `electricity/client/screen/PowerInfoScreen.java` |
| 37 | 53 | `electricity/block/UtilityPoleBlock.java` |
| 36 | 0 | `renderix/RenderixMesh.java` |
| 36 | 51 | `electricity/client/render/block/WindTurbineRenderer.java` |
| 34 | 36 | `electricity/block/WindTurbineBlock.java` |
| 34 | 36 | `electricity/block/ElectricCabinBlock.java` |
| 33 | 9 | `electricity/client/ElectricityClient.java` |
| 31 | 39 | `electricity/block/PowerBoxBlock.java` |
| 30 | 0 | `renderix/RenderixSplitter.java` |
| 28 | 1 | `electricity/client/wire/InsulatorLookup.java` |
| 27 | 76 | `electricity/main/network/ElectricityNetworking.java` |
| 25 | 0 | `renderix/RenderixMaterials.java` |
| 25 | 1 | `electricity/client/wire/WireAnchorHelper.java` |
| 25 | 8 | `electricity/client/render/obj/ObjRenderUtil.java` |
| 24 | 28 | `electricity/wire/InsulatorPartHelper.java` |
| 21 | 4 | `electricity/wire/InsulatorIdRegistry.java` |
| 21 | 0 | `electricity/main/network/payloads/CreateWireFromInsulatorsPayload.java` |
| 20 | 3 | `electricity/client/render/obj/ObjBoundingBoxRegistry.java` |
| 20 | 0 | `electricity/client/TrackedBlockEntities.java` |
| 20 | 89 | `electricity/block/FittedBlockEntity.java` |
| 18 | 0 | `electricity/item/PowerWrenchItem.java` |
| 15 | 44 | `electricity/block/SwitchgearBlockEntity.java` |
| 15 | 31 | `electricity/block/LatticeTowerBlockEntity.java` |
| 14 | 62 | `electricity/main/ElectricityServerConfig.java` |
| 14 | 15 | `electricity/client/render/obj/ObjBlockRegistry.java` |
| 14 | 0 | `electricity/client/ElectricityClientConfig.java` |
| 13 | 0 | `renderix/RenderixTessellator.java` |
| 13 | 46 | `electricity/main/weather/GlobalWeatherManager.java` |
| 13 | 63 | `electricity/main/registry/ObjDefinitions.java` |
| 13 | 0 | `electricity/client/wire/WirePhysics.java` |
| 12 | 9 | `electricity/main/network/payloads/WireConnectionPayload.java` |
| 11 | 0 | `electricity/main/network/payloads/SyncWiresPayload.java` |
| 10 | 0 | `renderix/RenderixMaterial.java` |
| 10 | 0 | `electricity/main/network/payloads/UpdateUtilityPoleConfigPayload.java` |
| 10 | 0 | `electricity/client/render/obj/ObjLoader.java` |
| 10 | 56 | `electricity/block/MachineShellBlock.java` |
| 9 | 0 | `electricity/main/network/payloads/PowerUpdatePayload.java` |
| 8 | 0 | `electricity/item/TooltipItem.java` |
| 8 | 0 | `electricity/item/TooltipBlockItem.java` |
| 8 | 2 | `electricity/client/render/obj/ObjTransforms.java` |
| 8 | 0 | `electricity/api/power/PowerDeliveryEvent.java` |
| 7 | 3 | `electricity/client/render/block/UtilityPoleRenderer.java` |
| 7 | 16 | `electricity/client/render/block/PowerBoxRenderer.java` |
| 7 | 3 | `electricity/client/render/block/ElectricCabinRenderer.java` |
| 6 | 42 | `electricity/block/TransformerBlockEntity.java` |
| 5 | 0 | `electricity/client/hooks/UtilityPoleClientHooks.java` |
| 5 | 0 | `electricity/api/power/IElectricPowerConsumer.java` |
| 5 | 0 | `electricity/api/power/ElectricityCapabilities.java` |
| 4 | 33 | `electricity/item/ConductorItem.java` |
| 4 | 73 | `electricity/block/TurbineTowerBlock.java` |
| 3 | 4 | `electricity/client/render/obj/ObjInteractionRegistry.java` |
| 2 | 0 | `electricity/client/render/obj/ObjRenderContext.java` |
| 2 | 8 | `electricity/client/render/block/LatticeTowerRenderer.java` |
| 1 | 100 | `electricity/block/SwitchgearBlock.java` |
