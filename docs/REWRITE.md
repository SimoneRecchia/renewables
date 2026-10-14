# Renewables — piano di riscrittura

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
| `models/wind_turbine/wind_turbine.obj` | **3 305 righe su 23 381 ancora sue**: da rifare (nuova turbina) |
| `textures/item/` cab, circuit_board, cpu, insulator, metal_casing, motor_core, power_box, screen, utility_pole, wind_turbine | icone d'inventario: da rifare |
| `textures/block/plastic2.png` | da rifare |
| `icon.png` | da rifare |
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
