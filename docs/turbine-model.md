# La turbina eolica: modello, scale, isolatore, hitbox

Modello nuovo, disegnato da zero: nessuna riga, nessun vertice e nessuna texture del modello ereditato.
Questo file è il **contratto tra il modello e chi lo usa** (renderer, `fittings.json`, forme dei blocchi). Tutte le
cifre qui sotto sono misurate dal modello da `tools/gen_turbine_models.py --contract`, `tools/check_turbine.py --table`
e `tools/gen_turbine_models.py --java`, e i checker le rimisurano a ogni giro: se una cifra si sposta, la costruzione
fallisce finché questo file non è aggiornato.

```bash
python3 tools/gen_turbine_models.py        # models/wind_turbine/wind_turbine.{obj,mtl}   (1.4 MB, 11 778 facce)
python3 tools/gen_turbine_textures.py      # le undici texture turbine_*.png
python3 tools/check_turbine.py [--table]   # il contratto, la chiusura delle superfici, i margini di ogni macchina
python3 tools/check_hitboxes.py [--java]   # torre conica e navicella contro il modello
python3 tools/render_blocks.py turbine turbine_head turbine_foot ...   # e poi *guardare* build/render/
```

## 1. Un OBJ per sei macchine

C'è **un solo** `wind_turbine/wind_turbine.obj`, la C130-4.0 in scala 1:1 (un blocco = 10 m). Le altre cinque sono la
stessa mesh scalata dal renderer con le scale del catalogo (par. 5). Motivi: il contratto di `wind.md` par. 15 è
costruito su questo (due scale, `s_r` per il rotore e `s_n` per navicella, torre e mozzo, e un solo elenco di
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
| pale (`rotate_1`) | lunghe 6.33 (dalla radice), raggio in punta **6.500** dal punto H | 63 m, rotore 130 m | corda 0.30 alla radice, 0.415 al massimo (r = 1.8), 0.06 in punta; svergolamento 20° → 0.2° |
| isolatore | porcellana ⌀ 0.18, alta 0.14 | 1.8 m | non scalato; par. 6 |

Le pale: profilo NACA a quattro cifre su linea media curva (camber 3.5 % al 40 %), spessore relativo da 45 % alla radice
(dove la sezione è un cerchio di diametro uguale alla corda, e si raccorda) a 13 % in punta; asse di passo al 30 %
della corda; precono 2.5° e pre-flessione di 0.20 in punta in avanti; lato in depressione sottovento. Ventotto
sezioni per pala, 65 punti per sezione. La radice porta un anello di tenuta (cuscinetto di passo) di raggio 0.19, largo
0.04, a cavallo della pelle dello spinner. Il passo **non** è disegnato (come nell'originale): le tre pale sono un
solo oggetto rigido.

Dimensioni complete e angoli per sezione: `BLADE_TABLE` in `tools/gen_turbine_models.py`.

## 4. Gruppi, pivot, assi, materiali

Il renderer chiama gruppo la coppia **oggetto + "_" + materiale** (`models.md` par. 6). Un solo `o` per nome, più
`usemtl` sotto lo stesso `o` (`check_obj_loading.py` lo prova).

| oggetto | gruppi (oggetto_materiale) → texture | trasformazione (dopo la posa 14.1 di `wind.md`) |
|---|---|---|
| `pole` | `pole_tower` → `turbine_tower`, `pole_flange` → `turbine_flange` | scala (s_n, n / **13.0**, s_n) dal piede. **Altezza authored = 13.0** (il riquadro del gruppo è y 0..13.0): era 12.43 |
| `pole_base` | `pole_base_concrete` → `pole_concrete`, `_flange` → `turbine_flange`, `_door` → `turbine_door`, `_plate` → `turbine_plate` | **scala uniforme s_n dal piede**, mai stirata (par. 4.1) |
| `motor` | `motor_nacelle` → `turbine_nacelle`, `motor_flange`, `motor_vent` → `turbine_louvre`, `motor_plate` → `turbine_plate` | traslazione a y = n, scala uniforme s_n, traslazione di -b. **b = 13.0** (minimo y di tutti i gruppi `motor*`, ed è anche il minimo del solo `motor_nacelle`) |
| `motor_hatch`, `motor_cooler`, `motor_sensors`, `motor_beacon` | `_nacelle`, `_flange`, `_vent`, `_sensor` → `turbine_sensor`, `_beacon` → `turbine_beacon` | come la navicella (qualunque nome che non cominci per `pole`, `insulator`, `rotate_1`, `rotate_2`, `pivot_`) |
| `rotate_2` | `rotate_2_hub` → `turbine_hub` | traslazione all'hub, scala uniforme **s_n**, rotazione attorno a z, traslazione di -H |
| `rotate_1` | `rotate_1_blade` → `turbine_blade`, `rotate_1_seal` → `turbine_seal` | traslazione all'hub, scala uniforme **s_r**, rotazione attorno a z, traslazione di -H |
| `insulator` | `insulator_insulator_porcelain` → `porcelain_brown`, `insulator_insulator_steel` → `pv_steel` | **nessuna** matrice: resta com'è authored, al piede |
| `pivot_rotor` | `pivot_rotor_flange` (tre vertici coincidenti, non si disegna) | nessuna; segna H, per gli strumenti |

**Pivot dei gruppi rotanti: H = (0.000, 13.300, 1.040)**, centro del riquadro di `rotate_2`; **asse = la retta parallela a
z per H**. `rotate_2` è un solido di rivoluzione attorno a quell'asse (il riquadro è quadrato in x/y e centrato su H:
`check_turbine.py` lo prova), quindi H sta sull'asse. Posizione reale dell'hub: `(0, n + s_n·(H.y − b), s_n·H.z)` =
`(0, n + 0.300·s_n, 1.040·s_n)`; ripiego se manca `rotate_2`: `(0, 13.30, 1.04)` (era `(0, 12.65, 0.95)`).
Il raggio delle pale è **esattamente 6.500** in pianta dal punto H (diametro authored **13.0**, era 13.044), quindi
`s_r = D / 10 · esagerazione / 13.0` e la C130 è davvero 1:1 (`s_r = 1.0000`, non 0.9966).

### 4.1 Cosa il renderer deve fare e che il vecchio non faceva

1. **`pole_base*` si scala uniformemente** (s_n, s_n, s_n), non come `pole`. Il prefisso `pole_base` va riconosciuto
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
`w = clamp(0.25 + (H - 2) / 11 · 0.75, 0.25, 1.0)`; `pole` come sopra con la nuova costante 13.0, `pole_base` a (w, w, w).

## 5. Scala per modello

`s_r = D/10 · esagerazione / 13.0`; `s_n = s_r`, tranne la SW-10: `max(s_r, 0.25)`. Aggiornano la tabella di `wind.md` par. 2.

| modello | s_r | s_n | raggio punta R (blocchi) | navicella L x l x h (blocchi) | raggio torre piede / cima | asse a y = | in avanti dall'asse torre |
|---|---:|---:|---:|---|---|---|---:|
| SW-10 | 0.1777 | 0.2500 | 1.155 | 0.387 x 0.140 x 0.150 | 0.085 / 0.048 | n + 0.075 | 0.260 |
| C52-0.85 | 0.4000 | 0.4000 | 2.600 | 0.619 x 0.223 x 0.240 | 0.136 / 0.076 | n + 0.120 | 0.416 |
| C80-2.0 | 0.6154 | 0.6154 | 4.000 | 0.953 x 0.343 x 0.369 | 0.209 / 0.117 | n + 0.185 | 0.640 |
| C90-3.0 | 0.6923 | 0.6923 | 4.500 | 1.072 x 0.386 x 0.415 | 0.235 / 0.132 | n + 0.208 | 0.720 |
| C112-3.0 | 0.8615 | 0.8615 | 5.600 | 1.334 x 0.481 x 0.517 | 0.293 / 0.164 | n + 0.258 | 0.896 |
| C130-4.0 | 1.0000 | 1.0000 | 6.500 | 1.548 x 0.558 x 0.600 | 0.340 / 0.190 | n + 0.300 | 1.040 |

Margini (da `check_turbine.py --table`, provati su ogni macchina alla torre minima e massima, per un giro intero
di rotore): la punta più bassa sta a 0.92 (SW-10, n = 2), 1.52, 2.18, 3.71, 2.66, 2.80 (C130, n = 9) blocchi dal
suolo, nessuna sotto 0.80; la minima distanza pala-fusto va da 0.165 (SW-10) a 0.533 (C130); lo sbalzo orizzontale
massimo dall'asse della torre è 1.20, 2.66, 4.09, 4.60, 5.73, 6.65. Il rotore delle utility ha le radici dentro lo
spinner anche con `s_r ≠ s_n` (SW-10: 0.1777 contro 0.25; l'anello di tenuta sta nel cilindro dello spinner).

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

* **Non si scala con `s_n`**: ⌀ 0.18 (2.9 px) e posizione fissi per tutte e sei le macchine. Scalato sulla SW-10
  sarebbe 0.05 (mezzo pixel, impuntabile). Sulla SW-10 il palo sta a 0.53 dall'asse mentre la torre ha raggio 0.085:
  è un palo di derivazione del cavo, in piedi per conto suo; sulla C130 sta a 0.01 dallo zoccolo.
* **Non ruota con lo yaw** (par. 4.1) **ma ruota col facing del blocco** (`ModelFacing`, come tutto il modello): il
  punto sta dentro il blocco (|x|, |z| < 0.5), quindi resta nella cella del piede.
* **Raggiungibile**: porcellana da y = 0.25, cioè 4 px sopra il suolo, e fuori dal fusto (0.53 dall'asse contro 0.42
  dello zoccolo). Da un occhio a 1.62 di altezza e 3 blocchi dall'asse la si vede da **74 %** dei 360° (`check_turbine.py`).
  Resta valida la raccomandazione di `wind.md` 16.2: un clic con la bobina su qualunque blocco della struttura
  seleziona il raccordo, con distanza misurata dal piede.
* Anche sul server (nessun OBJ caricato): dato da mettere in `fittings.json`, uguale per le sei macchine; il punto
  mondo è `piede + R_facing(0.375, 0.320, 0.375)` con il piede a `y_turbina − n`.

## 7. Hitbox

`python3 tools/gen_turbine_models.py --java` stampa le tabelle; `check_hitboxes.py` ne prova la coerenza col modello
(il fusto disegnato ha raggio 0.340 al piede e 0.190 in cima; la collisione non esce mai più di 0.15 px dal cono,
su tutte le 26 altezze di tutte le macchine).

**Torre: segue il cono vero, non un cubo.** Il blocco torre i-esimo (0 = piede) di una torre di n blocchi con scala s
ha raggio medio `r = s·(0.34 + (0.19 − 0.34)·(i + 0.5)/n)` (0.34 e 0.19 sono i raggi authored; l'altezza authored
13.0 si stira a n). La sezione è una scalinata di rettangoli con gli spigoli sul cerchio di raggio r: per ogni angolo
a in {10°, 20°, 30°, 40°} i due rettangoli di semilati (r cos a, r sin a) e (r sin a, r cos a) attorno all'asse, per
tutta l'altezza del blocco (8 scatole). L'unione riempie sempre il cerchio di raggio 0.90 r e non esce mai da r.
Collisione e selezione sono la stessa forma. Esempio, C130 su 13 segmenti: raggi 0.334, 0.323, 0.311 ... 0.196 (un
blocco perde 0.0115); SW-10 su 2: 0.076 e 0.057, cioè un fusto largo 2.4 px sul primo blocco dove prima il collisore era 11 px.
La scalinata dipende dal blocco, dall'altezza della pila e dal modello sopra: il blocco la calcola (e conviene
memorizzarla per raggio arrotondato al decimo di pixel).

**Piede della torre sotto una turbina**: la forma del blocco in basso aggiunge un'unica scatola per l'isolatore, non
scalata: `Block.box(12.40, 0.00, 12.40, 15.60, 6.25, 15.60)` nel frame authored SUD (da ruotare col facing); è ciò che
il giocatore punta, e fa sì che un clic sull'isolatore cada su un blocco della struttura. Lo zoccolo e la flangia
(0.7 px) non collidono.

**Blocco turbina**: la navicella e il raffreddatore, scalati da `s_n` e tagliati alla cella (la parte fuori cella non
collide): `Block.box` in sedicesimi, frame authored SUD, da ruotare col facing.

```
SW-10    scale 0.2500  navicella (6.89, 0.00, 4.45, 9.12, 2.40, 10.64)    raffreddatore (7.22, 2.32, 4.70, 8.78, 3.14, 6.58)
C52-0.85 scale 0.4000  navicella (6.22, 0.00, 2.32, 9.79, 3.84, 12.22)    raffreddatore (6.75, 3.71, 2.71, 9.25, 5.03, 5.73)
C80-2.0  scale 0.6154  navicella (5.26, 0.00, 0.00, 10.76, 5.91, 14.50)   raffreddatore (6.07, 5.71, 0.00, 9.93, 7.74, 4.51)
C90-3.0  scale 0.6923  navicella (4.92, 0.00, 0.00, 11.10, 6.65, 15.31)   raffreddatore (5.83, 6.42, 0.00, 10.17, 8.71, 4.08)
C112-3.0 scale 0.8615  navicella (4.17, 0.00, 0.00, 11.86, 8.27, 16.00)   raffreddatore (5.30, 8.00, 0.00, 10.70, 10.83, 3.12)
C130-4.0 scale 1.0000  navicella (3.55, 0.00, 0.00, 12.48, 9.60, 16.00)   raffreddatore (4.86, 9.28, 0.00, 11.14, 12.58, 2.34)
```

Formula (per qualunque altra scala): `cella = clamp(0.5 + authored · s_n)` in x e z e `y = altezza authored · s_n`, con la
navicella x ±0.279, z −0.888..0.660, y 0..0.600 e il raffreddatore x ±0.190, z −0.820..−0.360, y 0.580..0.760 (y dal
pavimento della navicella). Sostituiscono la vecchia formula `min(0.5, 1.575·s_n)` / `min(1.0, 0.95·s_n)`, che
dava un cubo largo come il blocco. Il resto (mozzo, pale, sportello, faro, sensori, coda a sbalzo) non collide e non si
seleziona; le pale attraversano i blocchi.

## 8. Texture

Tutte generate da codice con `texlib` (`tools/gen_turbine_textures.py`); nessuna texture del modello ereditato
(`plastic2.png` non esiste più). L'acciaio verniciato è RAL 7035; ruggine ai giunti di saldatura, sporco leggero,
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

11 379 vertici, 11 778 facce (pale e anelli di tenuta 6 375, spinner 2 400, fusto 478, basamento 868, navicella e suoi
accessori 1 212, isolatore 444). `ROUND` = 80 lati per fusto, flangia, zoccolo, spinner, anelli di tenuta; `FITTING` =
16 per isolatore, collari, alberi, ventole; `HEX` = 6 per i dadi.

## 10. Cosa ho cambiato fuori dal modello

* `tools/modellib.py`, `Mesh.quad`: toglie gli spigoli ripetuti prima di calcolare l'avvolgimento. Un angolo ripetuto tra
  i **primi due** punti dava area nulla e l'intera faccia veniva scartata: ogni `lathe` che parte dall'asse perdeva il
  disco di partenza (ne mancava uno sotto ogni isolatore a perno, e uno sul fondo dello spinner). Cambia i byte di
  `met_mast`, `pv_inverter` (36 facce in più), `tx_machine`, `tx_substation` alla prossima rigenerazione; non li ho
  rigenerati.
* `tools/gen_insulators.py` rimosso: l'isolatore lo disegna `gen_turbine_models.py`.
* `check_winding.py` (via l'eccezione `INHERITED`), `check_generated_assets.py` (via `block/plastic2.png`), `check_model_textures.py`
  (la turbina in elenco, legge anche `gen_turbine_textures.py` per le primitive tonde), `check_obj_loading.py`,
  `check_hitboxes.py` (`turbine()`, e salta ciò che legge Java non ancora scritto: vedi `docs/spec/QUESTIONS.md`).
* `tools/render_blocks.py`: dodici scene `turbine*` che posano il modello con `group_matrix`.
