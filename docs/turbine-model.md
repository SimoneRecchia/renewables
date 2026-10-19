# La turbina eolica: modello, scale, isolatore, hitbox

Modello disegnato da zero da `tools/gen_turbine_models.py`: ogni riga, ogni vertice e ogni texture viene da un generatore.
Questo file è il **contratto tra il modello e chi lo usa** (renderer, `fittings.json`, forme dei blocchi). Tutte le
cifre qui sotto sono misurate dal modello da `tools/gen_turbine_models.py --contract`, `tools/check_turbine.py --table`
e `tools/gen_turbine_models.py --java`, e i checker le rimisurano a ogni giro: se una cifra si sposta, la costruzione
fallisce finché questo file non è aggiornato.

```bash
python3 tools/gen_turbine_models.py        # models/wind_turbine/wind_turbine.{obj,mtl}   (1.4 MB, 11 942 facce)
python3 tools/gen_turbine_textures.py      # le undici texture turbine_*.png
python3 tools/check_turbine.py [--table]   # il contratto, la chiusura delle superfici, i margini di ogni macchina
python3 tools/check_hitboxes.py [--java]   # torre conica e navicella contro il modello
python3 tools/render_blocks.py turbine turbine_head turbine_foot ...   # e poi *guardare* build/render/
```

## 1. Un OBJ per sei macchine

C'è **un solo** `wind_turbine/wind_turbine.obj`, la C130-4.0 in scala 1:1 (un blocco = 10 m). Le altre cinque sono la
stessa mesh scalata dal renderer con le scale del catalogo (par. 5). Motivi: il contratto di `wind.md` par. 15 è
costruito su questo (le scale del par. 5: `s_r` per il rotore, `s_n` per navicella e mozzo, `s_t` per la torre, e un solo elenco di
raccordi); sei OBJ da 1.4 MB sarebbero 8 MB di mesh che differiscono solo per un fattore; e le sei macchine sono
davvero la stessa macchina a taglie diverse (stessa famiglia di pale, stesso mozzo). Il prezzo è che la SW-10 ha
la stessa faccia delle altre (raffreddatore, plance, sensori in scala 0.25), che è quello che già dice `wind.md`
(par. 19, punto 13).

## 2. Frame, unità, facing, verso

* Unità: 1.0 = 1 blocco = 16 px = 10 m. Origine sull'asse della torre (x = z = 0), **y = 0 al piede della torre**
  (faccia inferiore del primo blocco torre, livello del suolo). Nessuna geometria sotto y = 0.
* Facing "authored" **SUD**: il rotore sta in **+z** (davanti), +x a destra guardando il modello da davanti, +y su.
* **Verso di rotazione: orario visto da davanti.** Le pale hanno il bordo d'attacco dal lato del moto orario (quella
  in alto ha il bordo d'attacco verso +x): il renderer deve ruotare `rotate_1` e `rotate_2` attorno all'asse z con
  angolo **negativo** (`Axis.ZP.rotationDegrees(-angolo)`). Con angolo positivo le pale girerebbero col bordo
  d'uscita davanti. (Decisione aperta n. 3 di `wind.md` par. 23: risolta così.)
* Posa iniziale (angolo 0): una pala dritta in su, le altre a 210° e 330° (contati antiorari da +x, visti da davanti).
  Il contratto non fissa la posa: tre pale a 120° sono indistinguibili ogni 120°.

## 3. Cos'è, in numeri (C130-4.0, 1:1)

| parte | blocchi | metri reali | note |
|---|---:|---:|---|
| torre | alta 13.0 (altezza authored) | 130 | cono a 80 lati, raggio 0.340 al piede, 0.190 in cima: 6.8 m e 3.8 m di diametro |
| flangia di base | raggio 0.400, spessa 0.030 | 8.0 m, 0.3 m | su uno zoccolo di cemento raggio 0.420 alto 0.045; 48 dadi esagonali su un cerchio di raggio 0.372 |
| porta | 0.130 x 0.300 (con telaio 0.158 x 0.328) | 1.3 x 3.0 m | sul lato +z, soglia a 0.095; cerniere, maniglia, griglia di ventilazione in basso |
| targhette | 0.052 x 0.048 | | a sinistra (dati di targa) e a destra (pericolo, freccia IEC 60417-5036) della porta |
| navicella (`motor`) | 0.558 x 0.600 x 1.548 | 5.6 x 6.0 x 15.5 m | z da -0.888 a +0.660; poggia a y = 13.0; asse del rotore a **metà altezza** (13.30) |
| raffreddatore sul tetto | 0.38 x 0.18 x 0.46 | | feritoie su tre lati, due ventole; più i sensori: vento a coppe, banderuola, parafulmine |
| mozzo / spinner (`rotate_2`) | raggio 0.270, lungo 0.720 (z da 0.680 a 1.400) | 5.4 m | solido di rivoluzione a 80 lati, tre gusci con tre giunti meridiani |
| flangia dell'albero lento (`motor_shaft`) | raggio 0.100, z da 0.650 a 0.710 | 2.0 m | esce dal supporto del cuscinetto (`motor`, fino a z 0.660) ed entra nello spinner (da z 0.680): senza, il rotore stava 0.02 davanti alla navicella senza toccarla |
| pale (`rotate_1`) | lunghe 6.33 (dalla radice), raggio in punta **6.500** dal punto H | 63 m, rotore 130 m | corda 0.30 alla radice, 0.415 al massimo (r = 1.8), 0.06 in punta; svergolamento 20° → 0.2° |
| isolatore | porcellana ⌀ 0.18, alta 0.14 | 1.8 m | non scalato; par. 6 |

Le pale: profilo NACA a quattro cifre su linea media curva (camber 3.5 % al 40 %), spessore relativo da 45 % alla radice
(dove la sezione è un cerchio di diametro uguale alla corda, e si raccorda) a 13 % in punta; asse di passo al 30 %
della corda; precono 2.5° e pre-flessione di 0.20 in punta in avanti; lato in depressione sottovento. Ventotto
sezioni per pala, 65 punti per sezione. La radice porta un anello di tenuta (cuscinetto di passo) di raggio 0.19, largo
0.04, a cavallo della pelle dello spinner. Il passo **non** è disegnato: le tre pale sono un
solo oggetto rigido.

Dimensioni complete e angoli per sezione: `BLADE_TABLE` in `tools/gen_turbine_models.py`.

## 4. Gruppi, pivot, assi, materiali

Il renderer chiama gruppo la coppia **oggetto + "_" + materiale** (`models.md` par. 6). Un solo `o` per nome, più
`usemtl` sotto lo stesso `o` (`check_obj_loading.py` lo prova).

| oggetto | gruppi (oggetto_materiale) → texture | trasformazione (dopo la posa 14.1 di `wind.md`) |
|---|---|---|
| `pole` | `pole_tower` → `turbine_tower`, `pole_flange` → `turbine_flange` | scala (s_t, n / **13.0**, s_t) dal piede, con s_t la scala della torre (par. 5). **Altezza authored = 13.0** (il riquadro del gruppo è y 0..13.0): era 12.43 |
| `pole_base` | `pole_base_concrete` → `pole_concrete`, `_flange` → `turbine_flange`, `_door` → `turbine_door`, `_plate` → `turbine_plate` | **scala uniforme s_t dal piede**, mai stirata (par. 4.1) |
| `motor` | `motor_nacelle` → `turbine_nacelle`, `motor_flange`, `motor_vent` → `turbine_louvre`, `motor_plate` → `turbine_plate` | traslazione a y = n, scala uniforme s_n, traslazione di -b. **b = 13.0** (minimo y di tutti i gruppi `motor*`, ed è anche il minimo del solo `motor_nacelle`) |
| `motor_shaft`, `motor_hatch`, `motor_cooler`, `motor_sensors`, `motor_beacon` | `_flange`, `_nacelle`, `_vent`, `_sensor` → `turbine_sensor`, `_beacon` → `turbine_beacon` | come la navicella (qualunque nome che non cominci per `pole`, `insulator`, `rotate_1`, `rotate_2`, `pivot_`). `motor_shaft` è un solido di rivoluzione attorno all'asse del rotore, quindi non serve ruotarlo |
| `rotate_2` | `rotate_2_hub` → `turbine_hub` | traslazione all'hub, scala uniforme **s_n**, rotazione attorno a z, traslazione di -H |
| `rotate_1` | `rotate_1_blade` → `turbine_blade`, `rotate_1_seal` → `turbine_seal` | traslazione all'hub, scala uniforme **s_r**, rotazione attorno a z, traslazione di -H |
| `insulator` | `insulator_insulator_porcelain` → `porcelain_brown`, `insulator_insulator_steel` → `pv_steel` | **mai scalato**: solo la traslazione (o, 0, o) lungo la sua diagonale, o = `TurbineGeometry.insulatorOffset(s_t)` (par. 6) |
| `insulator_duct` | `insulator_duct_concrete` → `pole_concrete` | come `insulator` (il prefisso basta): il cunicolo dal palo dell'isolatore all'asse della torre (par. 6) |
| `pivot_rotor` | `pivot_rotor_flange` (tre vertici coincidenti, non si disegna) | nessuna; segna H, per gli strumenti |

**Pivot dei gruppi rotanti: H = (0.000, 13.300, 1.040)**, centro del riquadro di `rotate_2`; **asse = la retta parallela a
z per H**. `rotate_2` è un solido di rivoluzione attorno a quell'asse (il riquadro è quadrato in x/y e centrato su H:
`check_turbine.py` lo prova), quindi H sta sull'asse. Posizione reale dell'hub: `(0, n + s_n·(H.y − b), s_n·H.z)` =
`(0, n + 0.300·s_n, 1.040·s_n)`; ripiego se manca `rotate_2`: `(0, 13.30, 1.04)` (era `(0, 12.65, 0.95)`).
Il raggio delle pale è **esattamente 6.500** in pianta dal punto H (diametro authored **13.0**, era 13.044), quindi
`s_r = D / 10 · esagerazione / 13.0` e la C130 è davvero 1:1 (`s_r = 1.0000`, non 0.9966).

### 4.1 Cosa il renderer deve fare e che il vecchio non faceva

1. **`pole_base*` si scala uniformemente** (s_t, s_t, s_t), non come `pole`. Il prefisso `pole_base` va riconosciuto
   *prima* del prefisso `pole`. Se non lo si fa, `pole_base` segue la scala non uniforme di `pole`: porta, flangia e
   dadi si schiacciano di n/13 in altezza (a n = 9 sono il 69 %, a n = 2 il 15 %), e il modello resta comunque
   leggibile, ma la porta della SW-10 diventa una fessura.
2. **Lo yaw non si applica mai a `pole*` né a `insulator*`.** La torre ha la porta e l'isolatore sta fuori asse: se
   ruotassero col vento la porta girerebbe attorno alla torre e il cavo si staccherebbe dall'isolatore. Lo yaw
   (attorno all'asse verticale della torre, x = z = 0) vale solo per navicella, `rotate_1` e `rotate_2`.
   (`wind.md` par. 14.2 lo raccomanda già; `models.md` 8.3 punto 2 lo applica a tutto e va corretto.)
3. Il picking dell'isolatore ignora lo yaw dinamico (come in `models.md` 11.3): sta fermo al piede.
4. Rotazione del rotore con angolo negativo (par. 2).
5. `gen_turbine_models.group_matrix()` è l'unica descrizione eseguibile di tutto questo; `render_blocks.py` e
   `check_turbine.py` la usano, e il renderer Java può essere confrontato con essa.

Torre nuda (blocco torre senza turbina): disegna solo i gruppi `pole*`, senza facing, altezza H, scala radiale
`w = clamp(0.147 · H, 0.25, 2.0)` (`TurbineGeometry.bareTowerScale`); `pole` come sopra con la costante 13.0, `pole_base`
a (w, w, w).

## 5. Scala per modello

Tre scale, tutte in `TurbineSpec` / `TurbineGeometry` e, uguali, in `gen_turbine_models.py`:

* **rotore** `s_r = D/10 · esagerazione / 13.0`: il rotore resta vero al metro, una C90 è larga nove blocchi;
* **navicella e mozzo** `s_n = 2 · s_r` (`NACELLE_EXAGGERATION`), la SW-10 mai sotto 0.25. A un blocco ogni 10 m la
  navicella vera della C130 è alta 0.6 blocchi, un terzo di un giocatore: si leggeva come un puntino in cima al palo;
* **torre** `s_t = clamp(0.147 · n, min(0.25, s_n), s_n)` (`TurbineGeometry.towerScale`): cresce con l'altezza della
  pila, così una torre alta supera il blocco di diametro al piede, e si ferma alla scala della navicella, dove la testa
  del fusto (raggio 0.19 · s_t) sta sotto il pavimento piatto della navicella (0.19 · s_n) come nel modello authored.

| modello | s_r | s_n | navicella L x l x h (blocchi) | torre: n → s_t, diametro al piede | asse a y = | in avanti dall'asse torre |
|---|---:|---:|---|---|---|---:|
| SW-10 | 0.1777 | 0.3554 | 0.55 x 0.20 x 0.21 | 2 → 0.294, 0.20; 3-4 → 0.355, 0.24 | n + 0.107 | 0.370 |
| C52-0.85 | 0.4000 | 0.8000 | 1.24 x 0.45 x 0.48 | 4 → 0.588, 0.40; 6-7 → 0.800, 0.54 | n + 0.240 | 0.832 |
| C80-2.0 | 0.6154 | 1.2308 | 1.91 x 0.69 x 0.74 | 6 → 0.882, 0.60; 9-10 → 1.231, 0.84 | n + 0.369 | 1.280 |
| C90-3.0 | 0.6923 | 1.3846 | 2.14 x 0.77 x 0.83 | 8 → 1.176, 0.80; 10-11 → 1.385, 0.94 | n + 0.415 | 1.440 |
| C112-3.0 | 0.8615 | 1.7231 | 2.67 x 0.96 x 1.03 | 8 → 1.176, 0.80; 12 → 1.723, 1.17 | n + 0.517 | 1.792 |
| C130-4.0 | 1.0000 | 2.0000 | 3.10 x 1.12 x 1.20 | 9 → 1.323, 0.90; 13 → 1.911, 1.30 | n + 0.600 | 2.080 |

Margini (da `check_turbine.py --table`, provati su ogni macchina alla torre minima e massima, per un giro intero
di rotore): la punta più bassa sta a 0.95 (SW-10, n = 2), 1.64, 2.37, 3.92, 2.92, 3.10 (C130, n = 9) blocchi dal
suolo, nessuna sotto 0.80; la minima distanza pala-fusto va da 0.255 (SW-10) a 1.45 (C130); lo sbalzo orizzontale
massimo dall'asse della torre è 1.23, 2.78, 4.27, 4.80, 5.98, 6.94. Con lo spinner due volte più grande del rotore le
radici delle pale stanno dentro lo spinner a ogni scala, e l'anello di tenuta resta nascosto.

## 6. L'isolatore (il raccordo dei cavi)

Un solo raccordo, **indice 0**: gruppo `insulator_insulator_porcelain` (la parte interattiva è solo la porcellana; il
gruppo `insulator_insulator_steel` è perno, palo e piastra, e non è un raccordo). Isolatore a perno ANSI 55-4 di
`modellib.pin_insulator` (16 lati), su un palo di acciaio zincato e una piastra imbullonata, in piedi **accanto
all'imbasamento della torre, in vista**, 0.25 sopra il suolo: non più sotto terra.

Nel frame del modello (y dal piede della torre, x e z dall'asse):

| | min | max |
|---|---|---|
| riquadro della porcellana (`PartBox` di `fittings.json`) | (0.285, 0.250, 0.285) | (0.465, 0.390, 0.465) |
| **centro = ancoraggio del cavo** (`wind.md` 15.7 / `models.md` 11.6, per le turbine il centro) | (0.375, 0.320, 0.375) | |
| cima della porcellana (se l'architettura preferisse la cima) | (0.375, 0.390, 0.375) | |
| piastra di base (acciaio) | raggio 0.10, spessa 0.012, centrata in (0.375, 0.375) | |

* **Non si scala**: ⌀ 0.18 (2.9 px) per tutte e sei le macchine. Scalato sulla SW-10 sarebbe 0.05 (mezzo pixel,
  impuntabile). **Si sposta** invece quando il piede è largo: la sua piastra resta 0.02 fuori dallo zoccolo (raggio
  0.42 · s_t), quindi da s_t ≈ 0.98 in su l'isolatore e il cunicolo scivolano lungo la diagonale di
  `o = TurbineGeometry.insulatorOffset(s_t)` per asse (0.28 sulla C130 a 13 blocchi). Lo stesso `o` lo applicano il
  renderer, il picker (`GroupPoses`), l'ancoraggio del cavo (`WindTurbineBlockEntity.localAnchor`) e il collisore del
  piede (`TurbineTowerBlock`).
* **Il cunicolo (`insulator_duct`) lo lega alla torre a ogni scala.** Il palo era un oggetto a sé appoggiato al suolo:
  sulla C130 a 0.01 dallo zoccolo, sulle altre cinque staccato dalla torre (sulla SW-10 di 0.33 blocchi), e
  `check_connectivity.py` lo segnala come DETACHED in ogni posa che non è la C130. La soluzione è geometrica e non
  chiede nulla al renderer: un cunicolo prefabbricato in calcestruzzo a filo del suolo, largo 0.05 e alto 0.010, dal
  bordo della piastra del palo fino all'asse della torre (lungo la diagonale, lontano dalla porta). Non è scalato, come
  l'isolatore, quindi qualunque sia la scala del piede entra nello zoccolo: sulla C130 ne resta in vista un centimetro,
  sulla SW-10 è ciò che porta il cavo dalla torre al palo. L'altezza 0.010 sta sotto la cima dello zoccolo
  (0.045 · s_t, 0.013 al minimo) per tutte le altezze di tutte le macchine, quindi nessuna faccia è complanare. Il
  renderer disegna **tutti** i gruppi `insulator*` con la sola traslazione di sopra; la torre nuda
  (`TurbineTowerRenderer`, solo `pole*`) non lo disegna, come non disegna l'isolatore.
* Il collisore dell'isolatore (par. 7) è il riquadro del solo oggetto `insulator`: il cunicolo è alto un quarto di
  pixel e non collide.
* **Non ruota con lo yaw** (par. 4.1) **ma ruota col facing del blocco** (`ModelFacing`, come tutto il modello). Su
  una torre stretta sta dentro la cella del piede; su una larga ne esce verso l'angolo, e il collisore del piede esce
  con lui (la forma del blocco torre è dinamica, quindi conta come forma grande). Il clic a vuoto lo trova comunque:
  `FittingSelector` cerca le macchine vicine con il raggio del picker.
* **Raggiungibile**: porcellana da y = 0.25, cioè 4 px sopra il suolo, e fuori dal fusto (0.53 dall'asse contro 0.42
  dello zoccolo). Da un occhio a 1.62 di altezza e 3 blocchi dall'asse la si vede da **74 %** dei 360° (`check_turbine.py`).
  Resta valida la raccomandazione di `wind.md` 16.2: un clic con la bobina su qualunque blocco della struttura
  seleziona il raccordo, con distanza misurata dal piede.
* Anche sul server (nessun OBJ caricato): dato da mettere in `fittings.json`, uguale per le sei macchine; il punto
  mondo è `piede + R_facing(0.375, 0.320, 0.375)` con il piede a `y_turbina − n`.

## 7. Hitbox

`python3 tools/gen_turbine_models.py --java` stampa le tabelle; `check_hitboxes.py` ne prova la coerenza col modello
(il fusto disegnato ha raggio 0.340 al piede e 0.190 in cima, per s_t; la collisione non esce mai più di 0.18 px dal
cono, su tutte le 26 altezze di tutte le macchine).

**Torre: segue il cono vero, non un cubo.** Il blocco torre i-esimo (0 = piede) di una torre di n blocchi con scala s = s_t
(par. 5) ha raggio medio `r = s·(0.34 + (0.19 − 0.34)·(i + 0.5)/n)` (0.34 e 0.19 sono i raggi authored; l'altezza authored
13.0 si stira a n). La sezione è una scalinata di rettangoli con gli spigoli sul cerchio di raggio r: per ogni angolo
a in {10°, 20°, 30°, 40°} i due rettangoli di semilati (r cos a, r sin a) e (r sin a, r cos a) attorno all'asse, per
tutta l'altezza del blocco (8 scatole). L'unione riempie sempre il cerchio di raggio 0.90 r e non esce mai da r.
Collisione e selezione sono la stessa forma. Esempio, C130 su 13 segmenti (s_t = 1.911): raggi 0.639, 0.617 ... 0.374,
cioè i primi blocchi escono dalla cella, cosa che una forma dinamica può fare; SW-10 su 2 (s_t = 0.294): 0.089 e 0.067.
La scalinata dipende dal blocco, dall'altezza della pila e dal modello sopra: il blocco la calcola (e conviene
memorizzarla per raggio arrotondato al decimo di pixel).

**Piede della torre sotto una turbina**: la forma del blocco in basso aggiunge un'unica scatola per l'isolatore, non
scalata: `Block.box(12.40, 0.00, 12.40, 15.60, 6.25, 15.60)` nel frame authored SUD (da ruotare col facing), spostata di
`insulatorOffset(s_t)` in x e z come il disegno (par. 6); è ciò che il giocatore punta. Lo zoccolo e la flangia
(0.7 px) non collidono.

**Blocco turbina**: la navicella e il raffreddatore, scalati da `s_n` e tagliati alla cella (la parte fuori cella non
collide): `Block.box` in sedicesimi, frame authored SUD. La forma segue lo **yaw** della navicella, non il facing del
blocco (che è solo lo yaw iniziale): a passi di 5°, ogni scatola girata come la gira il renderer e tagliata in otto
strisce lungo x, ciascuna profonda quanto la scatola girata in quella striscia (`TurbineGeometry.nacelleBoxes(spec,
yaw)`); ai quarti di giro è esattamente la tabella qui sotto ruotata.

```
SW-10    scale 0.3554  navicella (6.42, 0.00, 2.95, 9.59, 3.41, 11.75)    raffreddatore (6.89, 3.30, 3.30, 9.11, 4.47, 5.99)
C52-0.85 scale 0.8000  navicella (4.44, 0.00, 0.00, 11.58, 7.68, 16.00)   raffreddatore (5.49, 7.42, 0.00, 10.51, 10.06, 3.47)
C80-2.0  scale 1.2308  navicella (2.53, 0.00, 0.00, 13.51, 11.82, 16.00)  raffreddatore (4.14, 11.42, 0.00, 11.86, 15.48, 1.03)
C90-3.0  scale 1.3846  navicella (1.84, 0.00, 0.00, 14.20, 13.29, 16.00)  raffreddatore (3.66, 12.85, 0.00, 12.34, 16.00, 0.16)
C112-3.0 scale 1.7231  navicella (0.34, 0.00, 0.00, 15.72, 16.00, 16.00)  raffreddatore fuori dalla cella
C130-4.0 scale 2.0000  navicella (0.00, 0.00, 0.00, 16.00, 16.00, 16.00)  raffreddatore fuori dalla cella
```

Formula (per qualunque altra scala): `cella = clamp(0.5 + authored · s_n)` in x e z e `y = altezza authored · s_n`, con la
navicella x ±0.279, z −0.888..0.660, y 0..0.600 e il raffreddatore x ±0.190, z −0.820..−0.360, y 0.580..0.760 (y dal
pavimento della navicella). Sostituiscono la vecchia formula `min(0.5, 1.575·s_n)` / `min(1.0, 0.95·s_n)`, che
dava un cubo largo come il blocco. Il resto (mozzo, pale, sportello, faro, sensori, coda a sbalzo) non collide e non si
seleziona; le pale attraversano i blocchi.

## 8. Texture

Tutte generate da codice con `texlib` (`tools/gen_turbine_textures.py`); nessuna texture disegnata a mano. L'acciaio verniciato è RAL 7035; ruggine ai giunti di saldatura, sporco leggero,
gocciolature, macchie d'olio, nessun disegno a mano. I due materiali dell'isolatore e il cemento dello zoccolo sono
texture del progetto scritte da `gen_block_textures.py`, che questo generatore non rivendica.

| file | px | cosa |
|---|---:|---|
| `turbine_tower.png` | 1024 | lamiera d'acciaio verniciata, saldature circonferenziali ogni 2 blocchi e longitudinali alternate; una copia = 4 blocchi di altezza authored (`TOWER_TILE`) e un giro del fusto |
| `turbine_blade.png` | 1024 | gelcoat su fibra di vetro; u gira attorno alla sezione (bordo d'uscita a 0 e 1, **d'attacco a 0.5** con la protezione antierosione), v lungo la pala (punta in alto, rossa); piccole erosioni più fitte verso la punta |
| `turbine_nacelle.png` | 512 | pannelli verniciati con rivetti ai giunti, sporco sotto le giunzioni; una copia = 0.8 blocchi |
| `turbine_hub.png` | 512 | spinner a tre gusci: tre giunti meridiani e un anello |
| `turbine_flange.png` | 512 | acciaio strutturale scuro: flange, fondo navicella, telai, cerniere |
| `turbine_louvre.png` | 512 | persiane su rete scura (raffreddatore, griglie): un disegno ripetibile, non un quadro |
| `turbine_door.png` | 512 | battente: pannello stampato e feritoia, disegnato nelle proporzioni 0.13 x 0.30 della porta |
| `turbine_plate.png` | 512 | quattro quarti quadrati: targa dati, cartello di pericolo, marchio del costruttore, targa liscia (`DECAL_*` nel generatore) |
| `turbine_seal.png` | 128 | gomma nera nervata, l'anello del cuscinetto di passo |
| `turbine_sensor.png`, `turbine_beacon.png` | 128 | plastica dei sensori; lente rossa del faro |

Risoluzioni scelte come da `CLAUDE.md` par. 2: 1024 per ciò che il giocatore guarda da vicino (fusto, pala), 512 per
le lamiere, 128 per ciò che è largo pochi centimetri. `check_model_textures.py wind_turbine`: niente FLICKER, SLICED,
OVAL, ALLSIDES; `check_generated_assets.py`: un generatore per file, nessun file sotto i 128 px.

## 9. Quanto costa

11 547 vertici, 11 942 facce (pale e anelli di tenuta 6 375, spinner 2 400, fusto 478, basamento 868, navicella e suoi
accessori 1 212, flangia dell'albero 158, isolatore 444, cunicolo 6). `ROUND` = 80 lati per fusto, flangia, zoccolo,
spinner, anelli di tenuta, albero; `FITTING` = 16 per isolatore, collari, alberi, ventole; `HEX` = 6 per i dadi.

**Normali per vertice.** Prima di scrivere, `Mesh.smooth()` dà a ogni faccia una normale per angolo: la media delle
facce attorno a quel vertice che piegano meno di 40° (`modellib.CREASE`). Il caricatore della mod legge la normale per
angolo e illumina ogni vertice con essa, quindi pale, spinner, fusto e navicella si sfumano invece di mostrare le bande
delle sezioni; il bordo d'uscita della pala, i tappi e i dadi esagonali restano spigoli. La geometria è invariata.

## 10. Cosa ho cambiato fuori dal modello

* `tools/modellib.py`, `Mesh.quad`: toglie gli spigoli ripetuti prima di calcolare l'avvolgimento. Un angolo ripetuto tra
  i **primi due** punti dava area nulla e l'intera faccia veniva scartata: ogni `lathe` che parte dall'asse perdeva il
  disco di partenza (ne mancava uno sotto ogni isolatore a perno, e uno sul fondo dello spinner). Cambia i byte di
  `met_mast`, `pv_inverter` (36 facce in più), `tx_machine`, `tx_substation`, che sono stati rigenerati.
* `tools/gen_insulators.py` rimosso: l'isolatore lo disegna `gen_turbine_models.py`.
* `check_winding.py`, `check_generated_assets.py`, `check_model_textures.py`
  (la turbina in elenco, legge anche `gen_turbine_textures.py` per le primitive tonde), `check_obj_loading.py`,
  `check_hitboxes.py` (`turbine()`, e salta ciò che legge Java non ancora scritto: vedi `docs/spec/QUESTIONS.md`).
* `tools/render_blocks.py`: dodici scene `turbine*` che posano il modello con `group_matrix`.
