# Renewables — architettura

Minecraft 1.20.1, Forge 47.4.0, Java 17, mappe `official`. Radice dei package:
`io.github.simonerecchia.renewables` (nel testo: `RP`). Namespace, mod id, chiavi lang: `renewables`.

Principio: **un solo posto per ogni responsabilità, e i sottosistemi si parlano solo attraverso i contratti della
sezione 3**. Niente stato statico senza padrone: le campate, i modelli, i buffer e il canale di rete hanno ciascuno un
proprietario e un'interfaccia.

Le colonne "stato" e i proprietari (F, G, M, C, B, W) e le note `STUB` registrano come è stato costruito il codice: oggi
tutti i contratti sono implementati. Le firme sotto sono quelle attuali.

## 1. Mappa dei package

| package | contenuto | stato |
|---|---|---|
| `RP` | `Renewables` (ingresso della mod, `MOD_ID`, `LOGGER`), `RenewablesClient` (init solo client, gestori dei messaggi server->client) | F (C riempie il client) |
| `RP.registry` | `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModTabs`: registri differiti e mappe per spec-id | F |
| `RP.core` | `ServerConfig`, `ClientConfig`, `ModGameRules`, `RealTimeClock`, `ServerEvents` (login, tick, arresto) | F (RealTimeClock/ModGameRules portati) |
| `RP.api`, `RP.api.power` | tipi pubblici: specifiche, telemetria, capability, eventi | portato (clean) |
| `RP.catalog` | cataloghi delle specifiche (turbine, inverter, parti, …) | portato (clean) |
| `RP.weather` | atmosfera, ombreggiamento, irraggiamento (clean) + `LevelWeather` (gestore per livello) | clean + W |
| `RP.telemetry` | simulatori di telemetria | portato (clean) |
| `RP.compat.*` | Forge Energy, Mekanism, ComputerCraft | portato (clean) |
| `RP.grid` | rete elettrica e cavi: `InsulatorHost`, `WireConnection` (clean), `InsulatorIds`, `WireLedger`, `SpanPlanner`, `Grid`, `GridSolver`, `FittingTypes`, eventi | G (F crea gli stub) |
| `RP.net`, `RP.net.packet` | canale `Net`, tutti i messaggi | F (+G per i gestori dei cavi) |
| `RP.model` | dati di modello comuni a client e server: `ModelDefinition`, `ModelDefinitions`, `PartBox`, `PartBoxes` | F tabella, M ancoraggi |
| `RP.block` | blocchi e block entity (le famiglie PV, trasformatori, tralicci sono portate dal clean) | B, W (F crea le basi) |
| `RP.item` | item | B (portati i clean) |
| `RP.client` | `ClientBlockEntities`, input, hook | C |
| `RP.client.wire` | `ClientWires`, `InsulatorIndex`, disegno dei cavi, anteprima | C |
| `RP.client.model` | lettore OBJ/MTL, `MeshModel`, cache, picking, `ModelBindings` | M |
| `RP.client.render` | `MachineRenderer` (base), `RenderContext`, un renderer per famiglia | M |
| `RP.client.screen` | schermate | portate (clean) + C (PowerInfoScreen, UtilityPoleConfigScreen) |
| `RP.client.preview` | strumento di anteprima di sviluppo | portato (clean) |

Proprietari: **F** = fondazione (un agente, prima), **G** = rete elettrica, **M** = modelli e renderer, **C** = client dei
cavi/input/schermate, **B** = blocchi della rete e oggetti, **W** = turbina, torre, meteo, nuovo modello 3D della turbina.
G, M, C, B, W lavorano in parallelo su worktree separati partendo dal commit di F e toccano **solo i propri file**.

## 2. Come si inserisce Minecraft

* Registrazioni con `DeferredRegister` nell'ordine della specifica `entry.md` §3; i cataloghi non dipendono mai dai registri.
* Tutto ciò che è solo client sta in `RP.client.**` ed è raggiunto da codice comune solo tramite `DistExecutor` /
  `@OnlyIn(Dist.CLIENT)`. Il server dedicato non deve mai caricare una classe client.
* Il **server non carica modelli OBJ**. Gli ancoraggi dei cavi (dove si attacca un filo a un isolatore) e le dimensioni
  delle parti di cui il server ha bisogno sono dati comuni (`RP.model.PartBoxes`), generati da `tools/gen_fittings.py`
  (compito di M) nel file `src/main/resources/data/renewables/fittings.json` e letti da client e server dal jar. Il client
  usa l'OBJ solo per disegnare e per il picking. Se il file manca, `PartBoxes` risponde "assente" a ogni domanda e scrive
  un solo avviso nel log; gli ancoraggi diventano allora non disponibili (messaggio `anchor_unavailable`).

  **Formato di `fittings.json`.** Una tabella per *modello*, non per blocco: i quattro inverter condividono un OBJ e
  quindi una voce. La chiave è la risorsa dell'OBJ come la dichiara `ModelDefinition.obj()`; sotto, un oggetto per
  gruppo del modello (non solo gli isolatori: tutti quelli di cui il server può aver bisogno), con gli estremi del
  riquadro nel frame del modello (blocchi; x e z centrati sull'asse verticale del blocco, y dal piano inferiore,
  macchina rivolta come disegnata). Gli ancoraggi si calcolano da qui con `PartBox.top()` (cima della porcellana) o,
  per i tralicci, da `min`/`max` (vedi `grid-blocks.md` §1.3.1).

  ```json
  {
    "format": 1,
    "models": {
      "renewables:models/pv_inverter/pv_inverter.obj": {
        "insulator_porcelain": { "min": [-0.071, 1.640, -0.071], "max": [0.071, 1.790, 0.071] }
      },
      "renewables:models/utility_pole/utility_pole.obj": {
        "insulator_1_porcelain": { "min": [-1.52, 5.21, -0.08], "max": [-1.37, 5.46, 0.07] },
        "insulator_2_porcelain": { "min": [-0.91, 5.21, -0.08], "max": [-0.76, 5.46, 0.07] }
      }
    }
  }
  ```
  I nomi dei gruppi sono quelli che `ModelDefinitions` elenca in `insulators` (`FittingTypes.nameOf`).
* La rete elettrica gira **per ogni livello** del server (non solo l'Overworld); gli id degli isolatori sono unici su
  tutto il server e persistenti (`SavedData`).

## 3. Contratti (chi chiama cosa)

F ha creato questi tipi (le firme sotto sono quelle **reali** del commit di F); il proprietario li riempie **senza
cambiarne le firme** (si possono aggiungere metodi). Gli stub non fanno nulla di dannoso e il server li carica senza
errori. Se serve una modifica, annotarla in `docs/spec/CONTRACT-CHANGES.md`. Ogni stub porta il commento
`STUB: owner X` nel proprio Javadoc.

### 3.1 `RP.grid` (G)
```java
final class InsulatorIds {                     // id interi unici e persistenti sul server   [STUB: owner G, contatore in memoria]
    static void claim(int[] ids);              // sostituisce ogni slot <= 0 con un id nuovo
    static void adopt(int id);                 // un id letto dal salvataggio è in uso
    static void release(int[] ids);            // il blocco sparisce
}
final class WireLedger {                       // tutte le campate salvate, per livello      [STUB: owner G]
    static List<WireConnection> spans(ServerLevel level);                     // per la sync di login
    static void removeAttachedTo(ServerLevel level, int[] insulatorIds);      // rimuove e rimborsa
    static void onServerStarted(MinecraftServer server);                      // legge i dati salvati di ogni livello
    static void onServerStopping(MinecraftServer server);                     // segna da salvare
}
final class Grid {                             // la simulazione, una per livello            [STUB: owner G]
    static void tick(ServerLevel level);       // fine tick, dopo i block entity, per OGNI livello
    static void recalcNow(ServerLevel level);  // es. dopo aver azionato un interruttore
    static void clear();                       // all'arresto: nessuno stato statico sopravvive al mondo
}
record SpanRequest(int startId, int endId, BlockPos startPos, BlockPos endPos,
                   String startFittingType, String endFittingType, String startPowerType, String endPowerType) {}
final class SpanPlanner {                      // validazione e creazione di una campata (grid.md §6)   [STUB: owner G]
    static void request(ServerPlayer player, SpanRequest request);
}
final class FittingTypes {                     // REALE. Stringhe salvate nei mondi: mai cambiarle
    static final String UTILITY_POLE, ELECTRIC_CABIN, POWER_BOX, WIND_TURBINE, PV_INVERTER, LATTICE_TOWER,
                        TRANSFORMER, GROUND_CONDUCTOR, SWITCHGEAR;
    static final String POWER_INPUT, POWER_OUTPUT, POWER_BIDIRECTIONAL;
    static @Nullable String nameOf(@Nullable ModelDefinition def, int index);   // nome del gruppo dell'isolatore index
}
```
`InsulatorHost` e `WireConnection` (clean) restano come sono. Il gestore del pacchetto ha già scartato id <= 0, id uguali,
stringhe troppo lunghe e giocatori spettatori prima di chiamare `SpanPlanner.request`; tutto il resto della catena di
controlli di `client-net.md` §11.3 è di G. `Net.sendToLevel(level, new SpanChangedPayload(span, created))` è come G
annuncia una campata creata o rimossa; `Net.sendToTracking(level, pos, new PowerUpdatePayload(pos, kw))` come manda la potenza.

### 3.2 `RP.net` (F, REALE)
```java
final class Net {
    static final String PROTOCOL = "1";                          // controllata ai due lati: versione diversa = rifiutato
    static void init();                                          // al setup comune; id dei messaggi = posizione in init()
    static void sendToServer(Object message);
    static void sendToPlayer(ServerPlayer player, Object message);
    static void sendToAll(Object message);                       // ogni dimensione: preferire sendToLevel
    static void sendToLevel(ServerLevel level, Object message);  // giocatori di una dimensione
    static void sendToTracking(ServerLevel level, BlockPos pos, Object message);   // giocatori che tracciano il chunk
    static void syncWires(ServerPlayer player);                  // sync completa a pacchetti da 500 campate
}
```
Canale `renewables:main`. Messaggi (l'id è la posizione; i nuovi in coda): 0 `WireSyncPayload(replace, spans)`,
1 `PoleConfigPayload`, 2 `CreateSpanPayload`, 3 `TurbineControlPayload`, 4 `SolarControlPayload`,
5 `SpanChangedPayload(span, created)`, 6 `PowerUpdatePayload(blockPos, powerKw)`, 7 `PlantControlPayload`.
I gestori client->server stanno in `ServerHandlers`, tutti i comandi di pannello passano da `PanelGuard.find(...)`
(né spettatore né in avventura, al più `PanelGuard.PER_TICK` = 4 comandi per macchina e giocatore a tick, chunk caricato,
bordo del mondo, `mayInteract`, tipo del block entity, distanza <= `PanelGuard.REACH` = 18 blocchi misurata all'asse
della macchina). I gestori server->client entrano nel codice solo client da `ClientBridge` (via
`DistExecutor`) verso `RenewablesClient.onWireSync / onSpanChanged / onPowerUpdate`.
Un'azione fuori intervallo arriva come `null` nel payload e il server scarta il messaggio.

### 3.3 `RP.model` (comune; F la tabella, M gli ancoraggi)
```java
record ModelDefinition(ResourceLocation obj, Direction authored, List<String> insulators) {}   // REALE
final class ModelDefinitions {                                  // REALE: la tabella di entry.md §4.6
    static void register(Block block, ModelDefinition def);
    static void registerAll(Map<?, ? extends Supplier<? extends Block>> blocks, Function<Block, ModelDefinition> build);
    static @Nullable ModelDefinition of(Block block);           // si riempie da sola al primo accesso
    static List<Block> blocks();
    static void bootstrap();                                    // idempotente, chiamato al setup comune
}
record PartBox(Vector3f min, Vector3f max) { Vector3f center(); Vector3f top(); }   // REALE, frame del modello, in blocchi
final class PartBoxes { static @Nullable PartBox box(Block block, String groupName); static void reload(); }   // REALE, legge fittings.json (§2)
```

### 3.4 `RP.client.*` (C, M)
```java
// RP.client.wire (C)
final class InsulatorIndex { static void register(InsulatorHost h); static void register(InsulatorHost h, int[] ids);
                             static void unregister(int[] ids); static @Nullable InsulatorHost find(int id); static void clear(); }   // funzionante
final class ClientWires    { static void sync(List<WireConnection> spans, boolean replace);   // [STUB: owner C]
                             static void add(WireConnection span); static void remove(WireConnection span);
                             static void invalidate(int[] insulatorIds); static void clear(); }
// RP.client (C)
final class ClientBlockEntities { static void track(BlockEntity be); static void untrack(BlockEntity be);
                                  static <T extends BlockEntity> List<T> of(Class<T> type); static void clear(); }   // funzionante, indice per classe
// RP.client.model (M)
final class ModelBindings { static void register(Block b); static void registerAll(Map<?, RegistryObject<Block>> m); }   // [STUB: owner M]
final class MeshModel { public final Map<String, Group> groups; }   // [STUB: owner M] i renderer clean leggono solo groups.keySet()
// RP.client.render (M)
final class RenderContext { MeshModel model(); ResourceLocation texture(); int packedLight(); }   // la proiezione e i buffer della
                                                                // macchina ci viaggiano dentro, visibili solo a MachineRenderer
abstract class MachineRenderer {                                // possiede i buffer GPU: una cache per tipo di block entity
    interface Draw<T extends BlockEntity> { void draw(T machine, RenderContext context, PoseStack poseStack); }
    protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent e, Class<T> type, double maxDistanceSq,
            Function<BlockState, Direction> facing, Direction authored [, Draw<T> draw [, Predicate<T> filter
            [, BiFunction<T, MeshModel, AABB> bounds]]]);           // fuori dal frustum non si disegna
    public static boolean inRange(BlockEntity machine, Vec3 camera); // i fili non pendono da ciò che non si disegna
    protected static void renderGrouped(RenderContext context, Map<String, Matrix4f> poses);
    protected static void renderGrouped(RenderContext context, PoseStack stack, Predicate<String> visible);
    public static void releaseAll();                            // ricarica risorse, logout, cambio livello
    protected static Vec3 pivot(MeshModel m, String name, Vec3 fallback);
    protected static void cleanupAngles(Map<BlockPos, Float> angles, Set<BlockPos> seen);
    protected static Set<String> cableEntries(Level level, BlockPos pos, Direction authored, Direction facing, String prefix);
    protected static boolean entryVisible(String groupName, String prefix, Set<String> live);
    protected static boolean cableArrives(Level level, BlockPos pos, Direction side);
}
```
`RenewablesClient.onClientSetup` chiama l'`init()` dei sette renderer che esistono (array, inverter, combiner, stazione
meteo, controllore, sezionatore, trasformatore); M aggiunge qui palo, cabina, kiosk, traliccio e turbina.

### 3.5 Configurazione (`RP.core.ServerConfig`, `ClientConfig`; F, REALE)
Metodi statici di `ServerConfig`: `maxCableRun()`, `externalEnergyEnabled()`, `pvExportFraction()`,
`pvMaxJoulesPerTick()`, `turbineExportFraction()`, `turbineMaxJoulesPerTick()`, `powerBoxRadius()`,
`arraySearchRadius()` (alias di `maxCableRun()`: la chiave omonima non esiste più) — chiavi, default e range da
`entry.md` §8. I due "massimo J/tick" tornano `Double.MAX_VALUE` quando il valore configurato è 0. `ClientConfig.previewSnapping()`.

### 3.6 Registri (`RP.registry`, F, REALE)
`ModBlocks`, `ModItems`, `ModBlockEntities`, `ModTabs` (38 blocchi, 76 item, 14 tipi di BE, 1 tab). Le mappe di famiglia
hanno chiave = id di specifica del catalogo (`renewables:c130_40`), ordine del catalogo:
`ModBlocks.{PV_ARRAYS, PV_INVERTERS, DC_CABLES, PV_COMBINERS, LATTICE_TOWERS, GROUND_CONDUCTORS, TRANSFORMERS, SWITCHGEARS, TURBINES}`
(`Map<ResourceLocation, RegistryObject<Block>>`), `ModItems.{CONDUCTORS, PV_ARRAYS, PV_INVERTERS, DC_CABLES, PV_COMBINERS, LATTICE_TOWERS,
TRANSFORMERS, SWITCHGEARS, TURBINES}` (`...RegistryObject<Item>`), `ModItems.PARTS` (per id stringa). Singoli: `ModBlocks.UTILITY_POLE,
ELECTRIC_CABIN, POWER_BOX, WIND_TURBINE, MACHINE_SHELL, TURBINE_TOWER, MET_STATION, PLANT_CONTROLLER`; `ModItems.POWER_WRENCH,
UTILITY_POLE, ELECTRIC_CABIN, POWER_BOX, WIND_TURBINE, CIRCUIT_BOARD, CPU, SCREEN, INSULATOR, METAL_CASING, MOTOR_CORE,
TURBINE_TOWER, MET_STATION, PLANT_CONTROLLER`; `ModBlockEntities.{UTILITY_POLE, ELECTRIC_CABIN, POWER_BOX, LATTICE_TOWER,
TRANSFORMER, GROUND_CONDUCTOR, WIND_TURBINE, TURBINE_TOWER, PV_ARRAY, PV_INVERTER, PV_COMBINER, MET_STATION, PLANT_CONTROLLER,
SWITCHGEAR}`. Un registro di famiglia non va mai riassegnato: i blocchi si registrano in `static {}` nell'ordine del catalogo.

### 3.7 Blocchi comuni (F, REALE) e stub dei proprietari
* `RP.block.MachineBlockEntity` — base di **tutti** i BE con isolatori, inverter e tratti posati a terra compresi: ids
  (reclamati **in `onLoad` lato server**, mai nel costruttore), ancoraggi, potenza ricevuta, salvataggio, pacchetto di
  aggiornamento, tracking client e `InsulatorIndex`/`ClientWires` via `DistExecutor`. L'ancoraggio è
  `computeAnchor(index)` = `ExtraPose.toWorld(localAnchor(index), …)`: una sottoclasse sovrascrive solo
  `localAnchor` (il punto nel frame del modello; di default la cima del riquadro da `fittings.json`, letto con
  `fittingBox(index)`), e `fittingCount()` se non ha una definizione di modello (il tratto posato). `refreshAnchors()`
  va chiamato quando qualcosa li sposta.
* `RP.block.MachineBlock` — base dei blocchi macchina con FACING (piazzamento opposto allo sguardo), forma di render invisibile.
* `RP.block.MachineShellBlock` — la cella-guscio (`grid-blocks.md` §1.2), REALE, con il clean `MachineShell`: stato
  `dx/dy/dz` con bias, forma dinamica dalla tabella dell'host, clic/pick/rottura inoltrati, guscio orfano -> aria. Nei tag
  `mineable/pickaxe` e `needs_iron_tool`.
* STUB owner B: `UtilityPoleBlock(+Entity)`, `ElectricCabinBlock(+Entity)`, `PowerBoxBlock(+Entity)`, `SwitchgearBlock(+Entity)`,
  `LatticeTowerBlockEntity`, `TransformerBlockEntity`, item `PowerWrenchItem`, `ConductorItem`. I BE stub hanno già `feeds`,
  `takesConductor`, `powerType` della tabella di `grid-blocks.md` §11. `UtilityPoleBlockEntity.applyConfig(ox, oy, oz, yawRad, pitchRad)`
  è ciò che chiama il pacchetto del palo.
* STUB owner W: `WindTurbineBlock(+Entity)`, `TurbineTowerBlock` (le due query di colonna `countBelow`/`findTurbineAbove` sono già reali),
  `LevelWeather` (clima calmo e costante).
* `RP.item.TooltipItem`, `TooltipBlockItem`: reali, una riga grigia da una chiave lang.
* `RP.api.power`: aggiunti `IElectricPowerConsumer`, `RenewablesCapabilities.ELECTRIC_CONSUMER`, `PowerDeliveryEvent` (l'API pubblica
  del consumatore di potenza; la capability è registrata al `RegisterCapabilitiesEvent`).

### 3.8 Turbina → renderer (W espone, M usa)
`WindTurbineBlockEntity` espone, per il disegno: `spec()`, `towerHeight()`, `yawDegrees(float partialTick)`,
`rotorAngleDegrees(float partialTick)`, `bladePitchDegrees()`, `isStopped()` (nello stub restituiscono zero/vero). Il contratto
del modello 3D è `wind.md` §15.

### 3.9 Meteo (W)
`LevelWeather.get(ServerLevel)`, `.sample(BlockPos pos, int towerBlocks)` → `WeatherSnapshot`, `.siteAt(BlockPos ground)` → `SiteConditions`,
`LevelWeather.tick(level)` (inizio tick), `remove(level)`, `clear()`. F li chiama da `ServerEvents`.

## 4. Decisioni sulle ambiguità delle specifiche

Valgono dove le specifiche lasciavano scelta; chi implementa non le riapre.

1. **Rete e cavi.** Si adottano tutti i controlli lato server marcati "RACCOMANDATO": portata del giocatore, bobina
   obbligatoria (niente cavo gratuito di default), id ≤ 0 e stessa id ai due capi rifiutati, massimo di campate per
   isolatore, nessun NaN. La rete simula ogni livello. Gli id sono persistenti.
2. **Ancoraggi nel server.** Tabella comune `fittings.json` (sez. 2). Il palo con scostamento/inclinazione applica la
   stessa trasformazione al disegno, agli ancoraggi e al picking.
3. **Interruttori e redstone.** Il sezionatore/interruttore reagisce al **fronte** del segnale, non al livello, così
   l'azionamento a mano non viene annullato a ogni aggiornamento dei vicini.
4. **Palo, limiti dei valori ricevuti.** scostamento ±1 blocco, imbardata ±180°, inclinazione ±45°; valori non finiti
   rifiutati.
5. **Guscio.** `machine_shell` entra nel tag del piccone; rompere un guscio apre la macchina.
6. **Piazzamento di macchine grandi.** Si controlla che tutte le celle siano libere prima di piazzare; altrimenti
   il piazzamento è rifiutato con messaggio.
7. **Kiosk.** Il campo di potenza spento riporta `powered`/`lit` solo sui blocchi che il campo aveva acceso. L'accumulo di FE
   nel buffer funziona davvero.
8. **Trasformatori.** Le perdite calcolate si applicano al flusso della rete.
9. **Turbina.** Il rotore guarda **controvento** (la direzione del vento è convertita nello yaw del modello solo al
   disegno); l'id della C130-4.0 resta `wind_turbine`; il nuovo modello ha l'isolatore **raggiungibile** (non interrato);
   la redstone si legge al piede della torre; il limite di potenza ricevuto dal pannello è validato (finito, 0..nominale);
   un'azione di rete sconosciuta viene ignorata (non alterna il freno).
10. **Cavi, disegno.** Si usa `discesa = sag × lunghezza della campata`; si taglia fuori frustum e oltre 128 blocchi;
    sync completa all'ingresso, al cambio di dimensione e al respawn.
11. **Modelli.** Un `.obj`/`.mtl` mancante o una texture assente non manda in crash: modello di ripiego e un solo
    messaggio nel log; i buffer GPU si liberano alla ricarica delle risorse; le animazioni usano il tempo, non i frame.
12. **README e docs.** Fa fede il comportamento del codice descritto nelle specifiche, non le guide vecchie. I default di
    export di turbine/inverter sono quelli reali (frazione 1.0, nessun tetto) e la documentazione va aggiornata.

## 5. Regole di lavoro

* Clean-room: `docs/spec/CLEANROOM-RULES.md` (cartella locale, non versionata).
* Ogni agente lavora in un worktree e committa sul proprio branch; non tocca file di altri proprietari.
* Prima di consegnare: `./gradlew build` verde nel proprio worktree. Il nome dei test, se ci sono, è `*Test`.
* Stile: come il codice portato (tab, commenti in inglese che spiegano il perché, niente riferimenti a progetti esterni).
* Niente `System.out`; log con `Renewables.LOGGER`.

## 6. Contratti aggiunti dopo la fondazione

```java
// RP.model (comune, scritto dall'architetto): la trasformazione extra del palo, UNA sola per disegno, picking e ancoraggi.
interface ExtraPose { Vec3 poseOffset(); float poseYawDegrees(); float posePitchDegrees();
                      static Vec3 toWorld(Vec3 local, BlockPos pos, Direction authored, Direction facing, @Nullable ExtraPose pose); }
// UtilityPoleBlockEntity (B) la implementa. MachineRenderer / PartPicker (M) e gli ancoraggi (B) chiamano toWorld.

// RP.client.model.PartPicker (M) - picking sui box per gruppo (models.md §11). Tutti i metodi sono client-only.
final class PartPicker {
    static boolean isPointed(Level level, BlockPos pos, String part, Vec3 origin, Vec3 direction);          // op. 1
    static @Nullable String pointedFitting(Level level, BlockPos pos, Vec3 origin, Vec3 direction);        // op. 2: raccordi, il più vicino
    static @Nullable String pointedAnyPart(Level level, BlockPos pos, Vec3 origin, Vec3 direction);        // op. 2, ripiego
    static @Nullable Vec3 partCenter(Level level, BlockPos pos, String part);                              // op. 3
    static @Nullable Vec3 nearestHit(Level level, BlockPos pos, Vec3 origin, Vec3 direction);              // op. 4: punto d'ingresso
}
// Se la block entity in pos implementa ExtraPose, il picker la applica (stessa trasformazione del disegno), e poi
// la posa di ogni gruppo che il suo renderer ha registrato in RP.client.model.GroupPoses (scala dell'inverter, turbina
// alla sua scala col fusto stirato, torre nuda solo fusto): si punta ciò che è disegnato.
// Facing e authored si ricavano dal blockstate e da ModelDefinitions.

// RP.client.wire.ClientWires (C) espone per le campate: all(), pending(), ecc.; InsulatorIndex.find(id) -> InsulatorHost.
// RP.client.render: ogni renderer ha static void init() (registra i modelli) chiamata da RenewablesClient.
```
