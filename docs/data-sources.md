# Fonti dei dati

Ogni numero fisico della mod confrontato con la realtà: il valore, da dove viene, e perché è quello. Dove un
prodotto ha un nome di fantasia (C130-4.0, HN-144-580, VX-110K…) il nome resta e i numeri sono quelli di una
macchina reale della stessa classe, citata qui. Le scelte che la realtà non decide (scala dei blocchi,
bilanciamento) sono in fondo, come domande; il costo delle ricette segue ora le masse reali a una scala scelta
(domanda 14).

Legenda: **corretto** = il valore o la formula sono cambiati; **verificato** = era già giusto e ora ha una fonte.
I numeri dei test citati sono calcolati a mano e scritti nei test JUnit.

Sezioni: rete AC · conduttori · trasformatori · quadri MT · tralicci · moduli FV · inverter · quadri di campo · cavi DC ·
stazione meteo · tracker · turbine · atmosfera · costo delle ricette · unità di gioco · lasciato com'è · da aggiornare nelle guide · domande.

## Rete AC: dove va la potenza e quanto se ne perde (`grid/GridMath`, `grid/GridSolver`, `grid/LevelGrid`)

### Prima (corretto)

* ogni campata perdeva l'**1 % per blocco**, qualunque fosse il conduttore e qualunque potenza portasse; più campate
  fra le stesse due macchine dividevano la penalità per 1 + 0,35·(n − 1) (numero senza fonte, e tre fasi contavano
  come tre cavi in parallelo); nessuna campata scendeva sotto il 10 %. 40 blocchi di cavo BT a 400 V e 160 blocchi di
  linea a 400 kV perdevano la stessa percentuale;
* la potenza di un generatore si spandeva su **ogni** macchina raggiungibile, divisa in parti uguali fra le
  diramazioni qualunque cosa ci fosse in fondo (un ramo morto prendeva la sua quota e la perdeva; aprire un
  interruttore su un ramo non mandava più potenza all'altro); dove due cammini si incontravano si teneva il
  **massimo** invece della somma; le letture delle linee erano la potenza *disponibile*, non la corrente che passa;
* le perdite dei trasformatori erano calcolate e mostrate al wrench ma la rete li attraversava come un palo (il
  Javadoc di `TransformerBlockEntity` e ARCHITECTURE.md §4.8 dicevano il contrario);
* la visita in profondità aveva un tetto di 10 000 passi e 64 salti: in una maglia 5×5 il palo (1,0) leggeva 1,4 kW
  e il simmetrico (0,1) 405 kW.

### Ora

**Dove va la potenza.** Ai **carichi** (i power box, `InsulatorHost.isLoad`) e nient'altro: una linea senza un carico
in fondo non porta corrente, e un ramo che non porta a nessun carico non prende nulla. I box non dichiarano una
domanda, quindi ogni carico assorbe come gli altri: la potenza di un generatore si divide in parti uguali fra i
carichi che raggiunge. Dal generatore, una ricerca di cammino minimo sul coefficiente di perdita ordina i nodi e
rende la rete un grafo a senso unico che si allontana da lui; ogni nodo è visitato una volta sola in quell'ordine:
quello che arriva è la **somma** di tutto ciò che è entrato, e quello che esce si divide fra le vie in proporzione ai
carichi ancora raggiungibili attraverso ciascuna. Su una linea o su un albero è esattamente la corrente che carichi
uguali richiederebbero; su una maglia è un'approssimazione simmetrica di Kirchhoff. In **ogni** topologia potenza
consegnata + perdite = potenza generata (test `aLosslessNetworkDeliversExactlyWhatIsGenerated`: 1500,0 kW su
1500 kW in una maglia 14×14 con due generatori e tre carichi; `whatTheLoadsReceiveNeverExceedsWhatIsGenerated`).
Niente più tetto di passi né di profondità: il costo è una ricerca di cammino minimo per generatore, e i due pali
simmetrici della maglia leggono lo stesso valore (`aMeshIsSolvedWholeAndSymmetrically`).

**Quanto si perde.** La perdita di una linea trifase è quella del conduttore:

    P_perdita = P² · R / (U² · cos²φ)        (R per fase, U concatenata; = 3·I²·R con I = P/(√3·U·cosφ))

* R = resistenza in c.c. a 20 °C del conduttore (Ω/km, per fase, fascio compreso) × lunghezza della campata in km,
  con la scala del progetto di **10 m a blocco** (`WorldConditions.METRES_PER_BLOCK`). `ConductorSpec.resistanceOhms`
  leggeva i blocchi come metri: corretto.
* U = tensione nominale della classe del conduttore (IEC 60038): **400 V** (BT), **33 kV** (MT, il collettore che
  i trasformatori della mod alimentano), **400 kV** (AT). È ora in `ConductorSpec.VoltageClass`.
* cos φ = 1: la rete trasporta solo potenza attiva, e un impianto lavora a cos φ 1 se il gestore non chiede altro.
* **Tre campate fra le stesse due macchine sono le tre fasi di un circuito**, non tre cavi in parallelo: perdono
  quanto una. Ogni tre campate fanno un circuito, e i circuiti sono in parallelo: k = n / (⌈n/3⌉ · Σ 1/kᵢ). Una
  campata sola vale un circuito intero, come la mod disegna una linea.
* **Limite di trasferimento**: una linea resistiva alimentata alla tensione nominale consegna al massimo U²/(4R),
  raggiunto con U²/(2R) in ingresso; oltre, più corrente abbassa solo la tensione in arrivo. Sostituisce il
  "pavimento" del 10 %.
* **Più generatori sulla stessa linea** perdono k·(P₁ + P₂)², non k·P₁² + k·P₂²: il solutore fa tre passate, e in
  ognuna ogni generatore calcola la perdita di una linea con il proprio flusso più quello che gli altri vi hanno
  messo nella passata precedente. Con un solo generatore è esatto alla prima. (Due flussi opposti sulla stessa
  linea sono contati ciascuno per sé, senza cancellarsi: la perdita è sovrastimata in quel caso raro.)
* **Trasformatori**: passano quello che entra meno P0 + Pk·(S/Sr)² (vedi sotto), con P0 contato una volta per tutti
  i generatori a monte (`InsulatorHost.losesInPassing` / `throughLossKw`). Le perdite a vuoto si pagano quando il
  trasformatore porta corrente verso un carico; senza carichi a valle non passa nulla e il generatore non consegna
  niente a nessuno.

Esempi (test `GridMathTest`, `GridSolverTest`, numeri calcolati a mano):

| campata | potenza | prima | ora |
|---|---|---|---|
| 10 blocchi (100 m) NFA2X 4×70 a 400 V, R = 0,0443 Ω | 100 kW | −10 % | −2,77 kW (−2,8 %) |
| 40 blocchi (400 m) dello stesso cavo, R = 0,177 Ω | 100 kW | −40 % | −11,1 kW (−11 %) |
| idem | 1 MW | −40 % (600 kW arrivati) | **226 kW** al massimo: il limite U²/4R |
| 90 blocchi (900 m) Aster 228 a 33 kV, R = 0,131 Ω | 3 MW | −90 % (fermato al 10 %) | −1,09 kW (−0,036 %) |
| 160 blocchi (1,6 km) Curlew quadruplo a 400 kV, R = 0,0218 Ω | 60 MW | −90 % | −0,49 kW (−0,0008 %) |
| catena del test manuale: turbina →(10 bl. MT)→ cabina →(20 bl. BT)→ palo →(10 bl. BT)→ box | 78,75 kW | 78,75 → 70,9 → 56,7 → 51,0 | 78,75 → 78,75 → 75,32 → 73,75 |

Fonti: P = √3·U·I·cosφ e P_perdita = 3·I²·R (qualsiasi testo di impianti elettrici, es. Kundur, *Power System
Stability and Control*, cap. 6); limite di trasferimento: massimo trasferimento di potenza su una resistenza in
serie a una sorgente di tensione fissa. Limiti di caduta di tensione per confronto: CEI 64-8 art. 525 (4 %),
EN 50160 (±10 % di Un).

## Conduttori aerei (`catalog/ConductorCatalog`)

| conduttore | grandezza | prima | ora | fonte |
|---|---|---|---|---|
| NFA2X 4×70 (BT) | R a 20 °C | 0,443 Ω/km | 0,443 (verificato) | HD 626; [Prakab](https://prakab.com/en/products/nfa2x), [Prysmian NF C 33-209](https://fr.prysmian.com/sites/fr.prysmian.com/files/media/documents/FT%20TORSADE%20AERIENNE%20DE%20RESEAU.pdf) |
| | portata | 185 A | **205 A** (80 °C) | idem (il torsade NF C 33-209 3×70+54,6 fa 213 A a 90 °C, 30 °C d'aria) |
| | Um | 1 kV | 1 kV (0,6/1 kV, verificato) | |
| Aster 228 (MT) | R a 20 °C | 0,146 Ω/km | 0,146 (verificato) | EN 50182 / NF C 34-125, [Ingelec](https://ingelec.com/sites/default/files/pdf/cable_alliage_d_aluminium_ags_cable_nu.pdf) |
| | portata | 555 A | **480 A** (25 K sopra 30 °C) | Ingelec; [SICAE](https://www.sicae-oise.fr/uploads/files/Structures%20des%20r%C3%A9seaux%20et%20Sch%C3%A9mas%20types%20de%20raccordement/Fichiers%20du%20R%C3%A9f%C3%A9rentiel%20Technique/caracteristiques-electriques-des-lignes-aeriennes-hta.pdf) dà 480 A d'inverno, 233 A d'estate |
| | Um | 24 kV | **36 kV** | è su un collettore a 33 kV: IEC 60038 accoppia 33 kV con Um 36 kV ([IEC 60038 tab. 3](https://law.resource.org/pub/us/cfr/ibr/inc/iec/iec.60038.2009.pdf)) |
| | massa, carico di rottura | — | non modellati: 627 kg/km, 74,05 kN, Ø 19,6 mm | Ingelec |
| Curlew ACSR ×4 (AT) | R a 20 °C | 0,0136 Ω/km (fascio) | 0,0136 (verificato: 0,0542/4) | [Prysmian](https://co.prysmian.com/sites/co.prysmian.com/files/media/documents/04%20ACSR%20ALUMINIUM%20CONDUCTOR%20STEEL%20REINFORCED_0.pdf) |
| | portata | 1150 A | **4184 A** (4 × 1046 A, 75 °C, 25 °C d'aria, 0,61 m/s) | idem; 1150 era appena un sottoconduttore |
| | sezione | 592 mm² | 591 mm² per sottoconduttore (523,7 Al + 67,7 acciaio, verificato) | |
| | massa, rottura, Ø | — | non modellati: 1978 kg/km, 162,9 kN, Ø 31,6 mm | idem |
| | passo del fascio | (disegno) | reale 400–450 mm ([Amprion](https://www.amprion.net/%C3%9Cbertragungsnetz/Technologie/Freileitung/)) | il disegno è volutamente fuori scala |

**Freccia e tiro** — non verificabili: la mod usa `sag × lunghezza` come freccia disegnata (un numero di disegno,
non meccanico). La freccia reale dipende da tiro, peso e temperatura (parabola f = w·L²/(8·T)); vedi domande.

**Campate massime** — non toccate, vedi domande: a 10 m/blocco BT 40 blocchi = 400 m (reale 50 m consigliati,
max ~90: [ENWL ES400O4](https://enwl.co.uk/globalassets/get-connected/cic/icpsidnos/g81-policy/policy-library-documents/overhead-lines/es400-o4---abc-lines-issue-4.pdf)),
MT 90 = 900 m (reale ~90–120 m), AT 160–200 = 1,6–2 km (reale 300–500 m, fino a ~700:
[netzausbau.de](https://www.netzausbau.de/N2000/DE/Technik/Freileitungen/freileitungen.html)).

## Trasformatori (`catalog/TransformerCatalog`, `api/power/TransformerSpec`)

**Formula** — **corretta**: la perdita a carico era una quota fissa del carico (1,1 % e 0,55 %): a metà carico
costava metà della perdita a pieno carico. È l'I²R degli avvolgimenti e costa un quarto. Ora
**P = P0 + Pk·(S/Sr)²** con Pk alla corrente nominale.

| | prima | ora | fonte |
|---|---|---|---|
| VT-2500 Pad, 2500 kVA 0,8/33 kV: P0 | 2,4 kW | **1,811 kW** | Reg. UE 548/2014 come modificato dal 2019/1783, Tier 2 (dal 1/7/2021), tab. I.1: 1575 W, +15 % per l'avvolgimento a 36 kV (tab. I.3a) — [allegato I](https://www.legislation.gov.uk/eur/2014/548/annex/I), [2019/1783](https://www.legislation.gov.uk/eur/2019/1783/annex) |
| VT-2500 Pad: Pk | 1,1 % × carico (27,5 kW a pieno carico) | **20,35 kW** a pieno carico | tab. I.1: 18 500 W, +10 % per 36 kV |
| VT-63000 Grid, 63 MVA 33/400 kV: P0 | 28 kW | 28 kW (verificato) | |
| VT-63000 Grid: Pk | 0,55 % × carico (346 kW a pieno carico) | **230 kW** | il Tier 2 chiede un PEI ≥ 99,745 % a 63 MVA: con P0 = 28 kW, PEI = 1 − 2√(P0·Pk)/Sr = 99,7452 %. Un 63 MVA reale (CEB 220/33 kV) fa P0 ≤ 30, Pk ≤ 225 kW ([gara CEB](https://www.ceb.lk/front_img/tender_pdf/2008131108024_Revised_Table_2_12_POWER_TRANSFORMERS_of_Section_6_Employers_Requirements_Part_D_-_Supplementary_Information.pdf)) |
| Vcc % | — | non modellata: 6 % per 2500 kVA ([Legrand](https://www.legrand.fr/pro/catalogue/transformateur-htabt-aaoak-2500kva-5900kg-primaire-20kv-aluminium-2050x1400x2380mm)), 12–14 % per 40–63 MVA AT/MT | |

**Oltre la taglia** — **corretto**: i kVA non limitavano nulla, un VT-2500 passava 26 MW. Un trasformatore vero non
limita la potenza (passa quello che la linea spinge e il carico chiede): si scalda, e lo stacca la protezione. Ora
(`block/TransformerThermal`) le temperature sono quelle della guida di carico IEC 60076-7:2005, equazioni alle
differenze (§8.2.3) con le caratteristiche termiche della tab. 5 — VT-2500 come trasformatore di distribuzione ONAN
(x 0,8, y 1,6, k11 1, k21 1, k22 2, τo 180 min, τw 4 min, Δθor 55 K, Hgr 23 K), VT-63000 come trasformatore di media
potenza ONAF (x 0,8, y 1,3, k11 0,5, k21 2, k22 2, τo 150 min, τw 7 min, Δθor 52 K, Hgr 26 K) — e R = Pk/P0 dalle
cifre Ecodesign sopra, ambiente 20 °C. La protezione scatta dove la guida non ammette più alcun carico (tab. 4,
carico di emergenza di lunga durata): punto caldo 140 °C (termometro dell'avvolgimento), olio 115 °C (termometro
dell'olio), corrente oltre 1,8 volte la taglia per la distribuzione e 1,5 per la media potenza (relè di massima
corrente, entro il secondo). Scattato, il trasformatore non porta nulla finché una mano non lo riarma, e il riarmo è
rifiutato finché non è rientrato nei limiti del carico ciclico normale (punto caldo 120 °C, olio 105 °C). Cifre (test
`TransformerThermalTest`): alla taglia si assesta a 98 °C (il punto caldo di progetto) e va per sempre; a 1,2 volte
(la C90 da 3 MW sul VT-2500 del sito d'esempio) si assesta a 123 °C e non scatta; a 1,6 volte (la C130 da 4 MW)
scatta dopo circa 3 ore da freddo; 26 MW lo staccano subito.

Un 63 MVA a 400 kV non esiste nei parchi reali (i 400/30–33 kV sono 125–315 MVA, es. Talayuela 2 × 135/160 MVA):
63 MVA è la taglia tipica 132–150/20–30 kV. Il nome e la taglia sono rimasti: vedi domande.

## Quadri MT (`catalog/SwitchgearCatalog`)

* **Classe di tensione** — **corretta** 24 → **36 kV** (AB-36/630, VB-36/630): il collettore è a 33 kV, e un
  avvolgimento a 33 kV su apparecchiature da 24 kV non si può costruire. IEC 60038: 33 kV ↔ Um 36 kV.
* **Potenza nominale** — **corretta** 26 MW → **36 MW**: era √3 × 24 kV × 630 A, cioè calcolata con la tensione
  massima Um invece che con la tensione di esercizio; la potenza di un feeder da 630 A è √3·U_n·I = √3 × 33 kV ×
  630 A = 36,0 MVA ([guida Schneider](https://www.cablejoints.co.uk/upload/Schneider--Medium-Voltage-Equipment-Design-Guide.pdf):
  "rated voltage ... always above the operating voltage"). Il sezionatore rifiuta di aprire sopra il 2 % (ora 720 kW).
* 630 A e potere di interruzione 16–25 kA (IEC 62271-100, serie R10) — la corrente è verificata; il potere di
  interruzione non è modellato (la mod non ha correnti di guasto).

## Tralicci (`catalog/TowerCatalog`)

* Donaumast a doppia terna, 400 kV — verificato: è il palo standard tedesco a 380/400 kV, circa 54 m di altezza,
  fascio quadruplo ([netzausbau.de](https://www.netzausbau.de/N2000/DE/Technik/Freileitungen/freileitungen.html)).
* Campate 160/200/200 blocchi — vedi domande (a 10 m/blocco sono 1,6–2 km contro 300–500 m reali).

## Moduli fotovoltaici (`catalog/PvModuleCatalog`)

Tutti i sei moduli ora hanno i numeri di un modulo reale della stessa tecnologia e dello stesso formato. Il nuovo
test `power/PvModuleDatasheetTest` controlla quello che un datasheet non può sbagliare: Vmp × Imp = Pmax, fill
factor fra 0,70 e 0,86, Voc per cella in serie entro quello che la tecnologia produce, efficienza 18–24 %, fusibile
di stringa 1,5 × Isc ammesso dal modulo.

| modulo | riferimento reale | cosa è cambiato | perché |
|---|---|---|---|
| HP-108-415 (PERC) | JA Solar JAM54S30-415/MR, [datasheet](https://solarproof.com.au/datasheets/panel_datasheet_5e4ad67e6eba4721ee3e0758d0e2041d.pdf) | **corretto** Vmp 31,40→31,61 V, Imp 13,22→13,13 A, Voc 37,60→37,45 V, Isc 13,93→14,02 A, γ −0,34→−0,35 %/K, β −0,27→−0,275 %/K, α 0,048→0,045 %/K, **bifacialità 0,70→0** (la serie S è vetro-backsheet, monofacciale), fusibile max 20→25 A | i valori erano "quasi" quelli del JAM54S30; un modulo con backsheet non raccoglie luce dal retro |
| HJ-132-430 (HJT) | REC Alpha Pure 2 430, [datasheet](https://www.recgroup.com/sites/default/files/2025-02/Print_DS%20REC%20Alpha%20Pure%202%20UL_EN%20US.pdf) | **corretto** dimensioni 1730×1118→1865×1040 mm, massa 21,5→21,7 kg, Vmp 33,0→41,8 V, Imp 13,03→10,29 A, Voc 39,5→49,3 V, Isc 13,85→10,88 A, fusibile 20→25 A | 132 mezze celle = 66 in serie; un'eterogiunzione apre a ~0,75 V per cella, quindi ~49 V. I 39,5 V di prima erano 0,60 V/cella, impossibili per HJT. Le dimensioni 1730×1118 erano quelle dell'Alpha Pure-R, che ha 80 celle, non 132 |
| HB-066-440 (IBC) | Maxeon 6 SPR-MAX6-440, [datasheet](https://public.files.energysage.com/LBSFT30-05zomcAlHBqCZw) | **corretto** Vmp 67,0→40,5 V, Imp 6,57→10,87 A, Voc 80,5→48,2 V, Isc 6,98→11,58 A, γ −0,27→−0,29, β −0,235→−0,239, α 0,050→0,057 %/K, massa 22,5→20,9 kg, **tensione di sistema 1500→1000 V** | 66 celle in serie non possono aprire a 80,5 V (1,22 V/cella); quei numeri sono quelli di un Maxeon 3 a 112 celle (81,4 V). Il Maxeon 6 è certificato 1000 V |
| HT-268-530 (CdTe) | First Solar FS-7530A-TR1 (Series 7), [datasheet](https://firstsolar.com/-/media/First-Solar/Technical-Documents/Series-7/Series-7-TR1-Datasheet.ashx) | **corretto** Vmp 183,0→186,9 V, Imp 2,90→2,84 A, Voc 223,0→226,7 V, Isc 3,19→3,05 A | il resto (2300×1216 mm, 39,7 kg, coefficienti, NOCT 45, 1500 V, fusibile 5 A) era già giusto |
| HN-144-580 (TOPCon) | Jinko JKM580N-72HL4-BDV (F6), [datasheet](https://jinkosolar.com/uploads/JKM560-580N-72HL4-BDV-F6-EN.pdf) | **corretto** Vmp 43,52→42,59 V, Imp 13,33→13,62 A, Voc 51,19→51,47 V, Isc 14,06→14,37 A, spessore 35→30 mm, fusibile 25→30 A | coefficienti, NOCT 45, bifacialità 0,80 e 32 kg erano già giusti |
| HN-132-700 (TOPCon 210) | Trina Vertex N TSM-700NEG21C.20 ([famiglia](https://www-cdn.trinasolar.com/wwwstorage/sites/6/DT-M-0042-APAC-EN-J-Datasheet_Vertex_NEG21C.20_715-740-2025_C-web-1.pdf), bin 700 W da [rivenditore](https://sun.store/en/product/trina-tsm-neg21c.20-700-675-700-1500v-silver-frame-ts4-3907)) | **corretto** Vmp 39,6→40,5 V, Imp 17,68→17,29 A, Voc 46,7→48,6 V, Isc 18,75→18,32 A, β −0,25→−0,24, α 0,045→0,040 %/K, fusibile 30→35 A | dimensioni, massa, NOCT 43 e bifacialità 0,80 erano già giusti |

Conseguenza sulle file (`catalog/PvCatalog`): **FT-430** passa da 2 stringhe da 22 a **4 stringhe da 11** moduli
(stessi 44 moduli, stessa copertura). Con il Voc vero (49,3 V, −0,24 %/K) 22 moduli a −10 °C fanno 1176 V, oltre i
1000 V del modulo, e il costruttore di `PvArraySpec` rifiutava il prodotto; il massimo è 18. Le altre file restano
come sono e passano tutte il controllo a freddo (FT-415 903 V/1000, TR-580 1008/1500, TR-530 1493/1500, HX-700
685/1500, AE-440 679/1000).

**Modello di temperatura** (`PvModuleSpec.cellTemperature`) — verificato. È il modello di Faiman (2008),
T = Ta + G / (U0 + U1·v), con U1 = 6,84 W·s/(m³·K) e U0 ricavato dal NOCT: con NOCT 45 °C viene 25,2 W/(m²·K),
il valore di default di Faiman (25,0) ([pvlib](https://pvlib-python.readthedocs.io/en/stable/reference/generated/pvlib.temperature.faiman.html)).
NOCT è definito a 800 W/m², 20 °C, 1 m/s, circuito aperto: è quello che il codice dice. (L'IEC 61215:2021 ha
tolto il NMOT; i datasheet REC stampano "NMOT 44 °C", che va letto allo stesso modo con 1–3 °C di differenza.)

**Bassa irradianza** — verificata: −3,4 % di efficienza relativa a 200 W/m² per il PERC, −2 % per HJT, −1,2 % per
CdTe, dentro i 2–4 % che i datasheet c-Si dichiarano a 200 W/m² (IEC 61853-1).

**Sporcamento** (`weather/Shading`) — verificato: 0,30 %/giorno in clima secco, 0,05 % umido, tetto 15 %, lavaggio
con la pioggia. Sono i tassi misurati in campo (0,05–0,3 %/giorno; Kimber et al. 2006, Micheli & Muller 2017).

**Neve** — verificato come ordine di grandezza: dimezza la luce ogni 14 mm, scivola via sopra i 30° di
inclinazione con il modulo sopra 0 °C (il modello di Marion fa scivolare la neve con velocità ∝ sin(tilt)).

## Inverter (`catalog/InverterCatalog`, `api/power/InverterSpec`)

| inverter | riferimento reale | cosa è cambiato |
|---|---|---|
| VX-10K | Huawei SUN2000-10KTL-M1, [spec](https://solar.huawei.com/en/products/sun2000-3-4-5-6-8-10ktl-m1/specs/), [datasheet EU](https://apljzccbdzncctnpnjdz.supabase.co/storage/v1/object/public/datasheets/dc4e7c3a-4293-45d8-a12a-e9c8de9506bf/1788514736000_datasheet.pdf) | **corretto**: ingressi di stringa 4→2 (uno per MPPT), corrente per MPPT 26→13,5 A (versione "high current"; quella base ha 11 A, che non porterebbe le due stringhe da 13,13 A dell'FT-415), raffreddamento ventola→naturale, autoconsumo notturno 1→5,5 W, rendimento europeo 98,1 %. **Verificati**: 10 kW / 11 kVA, 15 kWp DC, MPPT 140–980 V, max 1100 V, avvio 200 V, picco 98,6 %, 400 V, cos φ 0,8, derating oltre 45 °C, 60 °C max |
| VX-110K | Sungrow SG110CX, [datasheet](https://uk.sungrowpower.com/upload/file/20230302/EN%20DS%20SG110CX%20V112%20Premium%20Datasheet.pdf) | **corretto**: tensione AC 800→**400 V** (800 V è dei 1500 V, il SG110CX è 3/N/PE 400 V), potenza apparente 121→110 kVA ("110 kVA @ 45 °C"), avvio 200→250 V, europeo 98,5 %. **Verificati**: 9 MPPT × 2, 26 A/MPPT, MPPT 200–1000 V, max 1100 V, picco 98,7 %, < 2 W di notte. La potenza DC massima (165 kW, rapporto 1,5) non è sul datasheet: resta come rapporto DC/AC di progetto |
| VX-350K | Sungrow SG350HX, [datasheet](https://solarproof.com.au/datasheets/inverter_datasheet_Sungrow_Power_Supply_Co_Ltd_SG350HX_20251218063540.pdf) | **corretto**: potenza apparente 387→352 kVA (352 kVA @ 30 °C), inizio derating 50→30 °C (320 kVA @ 40, 295 @ 50), notte 3→6 W, europeo 98,8 %. **Verificati**: 16 MPPT × 30 A (opzione), 32 ingressi, MPPT 500–1500 V, max 1500, avvio 550, picco 99,0 %, 800 V |
| VC-2500K | SMA Sunny Central 2500-EV, [datasheet SC2200-3000-EV](https://apps.planningportal.nsw.gov.au/prweb/PRRestService/DocMgmt/v1/PublicDocuments/DATA-WORKATTACH-FILE%20PEC-DPE-EP-WORK%20PPSWES-47!20201021T043313.342%20GMT) | **corretto**: potenza apparente 2750→2500 kVA, corrente DC 2500→**3200 A** (2500 A non portavano i 3125 kW DC che la macchina accetta), MPPT 875–1325→850–1425 V, avvio 875→928 V, picco 98,9→98,6 %, europeo 98,3 %, standby 100→370 W, tensione AC 690→550 V, derating da 50→35 °C, massima 62→60 °C |

**Curva di rendimento** — **corretta**. Era a due termini (perdite = p0 + p2·L²) fissati dal solo picco: il
rendimento europeo veniva sempre 0,2–0,3 punti sotto quello di qualsiasi datasheet, e al 5 % di carico un 110 kW
stava al 93,8 % invece del ~96 %. Ora è il modello a tre termini di Schmidt e Sauer (p0 + p1·L + p2·L²: perdite a
vuoto, di commutazione, di conduzione), con i tre coefficienti ricavati da picco, posizione del picco e rendimento
europeo stampati sul datasheet (nuovo campo `ratedEuropeanEfficiency`). Pesi europei EN 50530 e CEC verificati.
Il VX-10K ha il picco al 60 % del carico: è l'unica posizione che rende compatibili il suo 98,6 % di picco con il
98,1 % europeo (la curva a due termini con picco a metà carico non scende sotto il 98,2).

**Potenza attiva e cos φ** — **corretto**: la potenza attiva ora è limitata a S_nom × cos φ (a cos φ 0,8 resta
0,8 della potenza apparente); prima l'inverter dava piena potenza attiva qualunque cos φ.

**Derating termico** — **corretto**: la retta finiva al 50 % della potenza alla temperatura massima (−3,3 %/K);
le macchine reali perdono 0,7–1,8 %/K. Ora finisce al 75 %, che passa per i punti dei datasheet (SG110CX 100,8 kW
a 50 °C contro 100 dichiarati; SG350HX 322,7 a 40 °C e 293,3 a 50 contro 320 e 295).

**Tensione DC massima** — **corretto**: una stringa il cui Voc a −10 °C supera la tensione massima d'ingresso
dell'inverter ora viene rifiutata (prima l'inverter la accettava, e l'unico limite era la finestra MPPT).

## Quadri di campo e fusibili (`catalog/CombinerCatalog`, `api/power/CombinerSpec`)

* **Fusibile di stringa** — **corretto** 1,4 → **1,5 × Isc STC**, e non oltre il fusibile massimo del modulo:
  IEC 62548, "In > 1,5 × I_SC_MOD_STC; In < 2,4 × I_SC_MOD_STC; In ≤ I_MOD_MAX_OCPR" (citato da
  [ETI](https://eti-de.de/images/product_db/idents/en-GB/002626238_TD.pdf)). Il 1,4 non ha fonte (1,1 × 1,25 = 1,375
  è la regola BS 7671 712 su I_SC_MAX).
* **CB-6** — **corretto** portafusibili 20 → **30 A**: un gPV 10×38 a 1000 V arriva a 30 A (Bussmann, Littelfuse;
  Mersen 32 A). Con 20 A e la regola 1,5 il CB-6 avrebbe rifiutato le stringhe dell'FT-415 (1,5 × 14,02 = 21 A).
* **CB-16** — verificato: 1500 V, 10×85 fino a 30 A, uscita 400 A, SPD tipo II, monitoraggio, IP65 come un
  [FIMER SB 1615](https://www.fimer.com/sites/default/files/910.400.239GB%20REV00%20Combiner%20boxes%20zone%20monitoring%201500V%20datasheet.pdf).
* **CB-32** — 630 A di uscita **non verificato**: il [Weidmüller a 32 stringhe](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/5731/281_8000089220.pdf)
  ha un sezionatore da 500 A e fusibili da 20 A. Lasciato: vedi le domande.
* Autoconsumi (0,5 / 2 / 3 W) — non pubblicati dai costruttori (il monitoraggio Weidmüller è "self-powered"); lasciati.

## Cavi DC (`catalog/CableCatalog`, `api/power/DcCableSpec`)

| | prima | ora | fonte |
|---|---|---|---|
| SC-6, resistenza | 4,32 Ω/km (a 90 °C) | **3,39 Ω/km a 20 °C** | IEC 60228 classe 5 stagnato, [Bayka](https://www.bayka.de/en/service/technical-information/conductor-resistance) |
| SC-6, portata in aria | 70 A | 70 A (verificato) | EN 50618 a 60 °C ambiente, [RR Kabel](https://www.rrkabel.com/products/solar-cable-en-50618/), [Lapp/TÜV 2 PfG 1169](https://lappaustralia.com.au/downloads/solar/CurrentRating_PV_Cables.pdf) |
| DT-240, resistenza | 0,0961 Ω/km (a 90 °C, rame classe 2 nudo) | **0,0817 Ω/km a 20 °C** | IEC 60228 classe 5 stagnato |
| DT-240, portata in aria | 570 A | **775 A** | EN 50618, stessa tabella |
| fattori di interramento 0,86 / 0,82 | — | non verificati | EN 50618 dà solo aria e superficie; per l'interrato rimanda a IEC 60364-5-52 metodo D |

**Formula** — **corretta**: la resistenza ora è R20·(1 + 0,00393·(T − 20)) con la temperatura del rame
T = T_aria + 60 K·(I/Iz)² (alla portata nominale il rame è a 120 °C, il limite dell'isolante, 60 K sopra i 60 °C
delle tabelle). Prima si usava sempre la resistenza a 90 °C: una stringa da 13 A in un cavo da 70 A sta invece a
2 K sopra l'aria, e le perdite DC erano sovrastimate del 20 % circa. Esempio (test `DcCableLossTest`): 22 × HP-108
(695 V, 13,13 A) su 32 blocchi (anello di 640 m) a 25 °C: rame a 27,1 °C, 2,230 Ω, 29,3 V di caduta, **4,21 %**.

**Perdita nel tronco del quadro** — **corretta**: era calcolata alla corrente di progetto (tutte le stringhe a
pieno sole) qualunque fosse la luce; ora alla corrente vera nel punto di massima potenza.

## Stazione meteo (`catalog/SensorCatalog`)

`responseTimeS` è il tempo al 95 % (tre costanti di tempo).

| strumento | riferimento | cosa è cambiato |
|---|---|---|
| SP-11 piranometro | Kipp & Zonen CMP11, [brochure](https://psl.noaa.gov/data/obs/instruments/KippZonen_Brochure_Pyranometers.pdf) | verificato: ISO 9060 classe A (ISO 9060:2018 chiede < 10 s al 95 %), 285–2800 nm, 95 % in 5 s, fino a 4000 W/m² |
| SA-11 albedometro | Kipp & Zonen CMA11, [Campbell](https://www.campbellsci.ca/cma11) | verificato: due CMP11 schiena contro schiena, < 5 s |
| SD-11 diffusa | CMP11 sotto anello d'ombra | verificato |
| RC-1 cella di riferimento | Mencke & Tegtmeyer Si-V-10TC, [Ammonit](https://www.ammonit.com/products/solar-sensors/silicon-irradiance-sensors/product-details/silicon-irradiance-sensor-mt-0-10v/) | **corretto** risposta 0,05→0,1 s (99 % in 0,15 s). Incertezza 3 %: il requisito di taratura IEC 61724-1. La banda 350–1150 nm è quella del silicio (nessun costruttore la stampa) |
| MT-1000 temperatura modulo | Campbell 110PV, [brochure](https://s.campbellsci.com/documents/eu/product-brochures/b_110pv.pdf); Pt classe A IEC 60751 ±(0,15 + 0,002·\|t\|) °C | **corretto** risposta 20→75 s (costante di tempo 25 s su una superficie) |
| AT-7 temperatura aria | Vaisala HMP155 in schermo, [datasheet](https://spaces.awi.de/download/attachments/183009295/HMP155_datasheets.pdf?api=v2) | **corretto** risposta 30→60 s (63 % in 20 s a 3 m/s) |
| SN-50 neve | Campbell SR50A, [scheda](https://campbellsci.co.uk/sr50a) | verificato: 0,5–10 m, ±1 cm o 0,4 %, < 1 s |
| WS-3 anemometro | Thies First Class Advanced II, [Ammonit](https://ammonit.com/en/products/wind-sensors/anemometers/product-details/anemometer-thies-first-class-advanced-ii) | **corretto** classe "IEC 61400-12 class 1" (non esiste) → **IEC 61400-12-1 class 1.8A**; risposta 2→1 s (distanza costante < 3 m). Verificati 0–75 m/s, 1 % della lettura |
| WV-3 banderuola | Thies First Class wind direction 4.3151, [Thies](https://www.thiesclima.com/en/Products/Wind-measuring-technology-First-class/?art=242) | **corretto** classe → "ASTM D5096, ±1°" (le banderuole non hanno classe IEC 61400-12-1), incertezza 3→1°, risposta 2→1 s |

## Tracker (`catalog/TrackerCatalog`, `api/power/TrackerSpec`)

* **Horizon R** (monoassiale orizzontale) — verificato: ±60° come un Nextracker NX Horizon
  ([scheda](https://files-backend.assets.thrillshare.com/documents/asset/uploaded_file/3739/Toh/b350d4be-6a1d-4818-84f4-5ca60eb9ab6e/Tracker_-_Nextracker_NX_Horizon__Typical_.pdf?disposition=inline)),
  backtracking, motore autoalimentato per fila. Messa in sicurezza a 20 m/s in piano: dentro l'intervallo di
  mercato (12–16 m/s a 30–45° per i tracker "convenzionali", 22 m/s a 0° per l'Arctech SkyLine II,
  [pv magazine](https://pv-magazine-usa.com/2022/05/26/trackers-vs-the-elements-part-two-working-with-wind/)).
  Velocità di rotazione (4,4°/min), angolo per la neve e potenza del motore: **non pubblicati** da nessun
  costruttore, lasciati.
* **Zenith AE** (biassiale) — verificato: sole fino a 10° sull'orizzonte come il campo zenitale 0–80° di un
  Mechatron M18 ([TaiyangNews](https://taiyangnews.info/technology/mechatron-highlights-dual-axis-tracker-portfolio-with-hydraulic-drive-design)).
  250 W di motore stanno fra i 15 W del DEGERtraker 5000NT e i 0,55 hp (410 W) del Mechatron.
* **Backtracking** — formula verificata: correzione = arccos(sin(elevazione)/GCR) quando sin(elevazione) < GCR,
  per un asse orizzontale con il sole nel piano perpendicolare all'asse (Lorenzo et al. 2011; Anderson & Mikofski,
  NREL 2020).

## Turbine eoliche (`catalog/TurbineCatalog`, `api/power/TurbineSpec`, `wind/TurbineDynamics`)

La curva di potenza della mod (cubica a Cp di picco costante, ginocchio arrotondato alla nominale) è stata
confrontata con le curve pubblicate a 1,225 kg/m³: coincide entro pochi punti percentuali su tutto il campo.

| modello | riferimento | curva mod vs reale (kW) | cosa è cambiato |
|---|---|---|---|
| SW-10 | Bergey Excel 10, [certificato SWCC](https://www.bergey.com/wp-content/uploads/excel-10-swcc-certification-declaration.pdf), [scheda](https://www.renewableenergyhub.co.uk/uploads/products/10619/product_brochures/Excel_10_Spec_Sheet.pdf) | 11 m/s: 9 vs 8,9 | **corretto** cut-in 3→2,5 m/s, giri 60–250 → 60 / 250 (nominale) / 400 rpm, **trasmissione diretta** (rapporto 7,2→1: l'Excel 10 ha un alternatore a magneti permanenti sull'albero del rotore). Verificato: 7 m di rotore, niente cut-out (si protegge con il furling) |
| C52-0.85 | Vestas V52-850, [specifica generale 946506](https://dmvb.omgeving.vlaanderen.be/api/v1/dossier/PR0568/document/gdo:c57263cd-a2bf-4181-8540-ea5b95c38192) | 8 m/s: 292 vs 304; 12: 762 vs 759 | verificato tutto: 52 m, 850 kW, IA, 4/25 m/s, 14,0–31,4 rpm, mozzo 40–74 m |
| C80-2.0 | Vestas V80-2.0, [brochure](https://www.ledsjovind.se/tolvmanstegen/Vestas%20V90-2MW.pdf), [Yokohama](https://en.city.yokohama.lg.jp/kurashi/machizukuri-kankyo/ondanka/etc/hama_wing/spec.html) | 10 m/s: 1339 vs 1289; 12: 1824 vs 1788 | verificato: 80 m, 2 MW, 4/25 m/s, 9–19 rpm, mozzo 60–100 m |
| C90-3.0 | Vestas V90-3.0, [brochure](https://www.maine.gov/dacf/lupc/projects/windpower/transcanada/Volume3/Volume3_Section2/Appendix%202-I.pdf) | 10 m/s: 1739 vs 1710; 12: 2562 vs 2544 | **corretto** giri minimi 9,9→**8,6 rpm** (8,6–18,4, ±30 % attorno al sincronismo). Verificato il resto |
| C112-3.0 | Vestas V112-3.0, [specifica generale 0004-7993](https://va.mite.gov.it/File/Documento/57415) | 10 m/s: 2449 vs 2584; 12: 2903 vs 2998 | verificato: 112 m, 3 MW, 3/25 m/s, 6,2–17,7 rpm, mozzo 84–119 m |
| C130-4.0 | classe 4 MW / 130 m: Siemens SWT-4.0-130 ([thewindpower](https://www.thewindpower.net/scripts/fpdf181/turbine.php?id=957)), Nordex N131/3900 ([Nordex](https://www.nordex-online.com/en/?p=5569)) | nessuna curva pubblicata per esattamente 4,0 MW | **corretto** giri minimi 5,5→**7,9 rpm** (N131/3900; il 5,5 non aveva una macchina dietro). Verificati 14 rpm massimi (SWT-4.0-130), cut-in 3, cut-out 25 m/s, mozzo 90–130 m |

* **Cp di picco 0,44–0,47** — verificato: il Cp elettrico che si ricava dalle curve Vestas è 0,445–0,456
  (V80, V90, V52, V112); il rotore di riferimento NREL 5 MW arriva a 0,482 con TSR 7,55
  ([Jonkman 2009](https://www.nrel.gov/docs/fy09osti/38060.pdf)).
* **Velocità nominale del rotore** — **corretto** (segnalato dalla revisione): la mod usava la velocità *massima*
  del catalogo come nominale, teneva il rotore al suo TSR fino al limite di sovravelocità e ne ricavava il
  moltiplicatore; la C130 girava con le punte a 95 m/s. Ora `TurbineSpec` ha anche la velocità nominale, dalle
  macchine reali: C52 26 rpm (V52), C80 16,7 (V80), C90 16,1 (V90-3.0), C112 12,8 (V112-3.0), C130 12,6
  (N131/3900), SW-10 250 (dove il TSR 8 incontra i nove decimi della sua curva). Il rotore è tenuto lì e il pitch fa
  il resto; il massimo resta come limite di sovravelocità. **Velocità di punta** alla nominale: 70,8 / 70,0 / 75,9 /
  75,1 / 85,8 m/s, come le macchine terrestri reali (V90 76, N131 86). **TSR** di lavoro 8: verificato (NREL 5 MW
  7,55; ottimo dei tripala moderni 7–9).
* **Velocità del generatore** — **corretto** 1800 → **1680 rpm alla velocità nominale del rotore**: un generatore
  doppiamente alimentato a 4 poli su rete a 50 Hz è sincrono a 1500 rpm e a piena potenza gira ~12 % sopra (V80
  16,7 rpm × 100,5 = 1678; V90 16,1 × 104,5 = 1682); al limite arriva a ~1900. I moltiplicatori che ne risultano:
  C52 1:64,6 (reale 1:62), C80 1:100,6 (1:100,5), C90 1:104,3 (1:104,5), C112 1:131 (il V112 reale ha un generatore
  a magneti permanenti e 1:113,2), C130 1:133 (SWT-4.0-130 1:119).
* **Coppia** — **corretto** (revisione): appena tolto il freno la turbina erogava la piena potenza con il rotore a
  0,7 rpm, cioè 19 volte la coppia nominale. La potenza è coppia × velocità: ora l'uscita è limitata anche dalla
  coppia nominale del generatore alla velocità che il rotore ha davvero raggiunto (`TurbineSpec.torqueCeilingKw`),
  e sale mentre il rotore accelera. In marcia stazionaria il limite non tocca mai la curva (test per ogni modello e
  ogni vento).
* **Disallineamento d'imbardata** — **corretto** (formula): `alignment()` è cos³γ, la frazione di *potenza* che
  resta a un rotore fuori vento di γ; il blocco la moltiplicava per la *velocità* del vento, che la curva poi eleva
  al cubo: cos⁹γ. Nella banda morta di 7,5° la turbina perdeva il 7,4 % invece del 2,5 %. Ora il rotore lavora con
  la componente del vento normale al disco, v·cos γ (`TurbineDynamics.effectiveWind`).
* **Cut-out e ripartenza** — verificato: 25 m/s di media su 10 minuti, ripartenza a 22 (V52 20, V112 23).
  **Corretto** (revisione): lo scatto per raffica (raffica di 3 s ≥ 1,2 × cut-out = 30 m/s) non aveva una banda di
  rilascio sua: con turbolenza oltre ~0,12 la raffica stava sui 30 m/s mentre la media era sotto i 22 di
  ripartenza, e la turbina si fermava e ripartiva a ogni tick. Ora per ripartire servono anche la raffica sotto
  1,2 × 22 = 26,4 m/s.
* **Velocità d'imbardata 5°/s** — **non corretto, è una domanda**: le macchine reali girano a < 0,5°/s (V52).
* Pitch 0→25° fra nominale e inizio controllo tempesta, 60° al cut-out, 90° in bandiera: verificato come ordine
  di grandezza (le schede non pubblicano le velocità di pitch).

## Atmosfera, vento e sole (`weather/`)

Tre correzioni (segnalate dalla revisione del vento), il resto verificato:

* **Livello del mare** — **corretto**: `Level.getSeaLevel()` in Minecraft 1.20.1 è la costante 63 (verificato sul
  bytecode di `net.minecraft.world.level.Level`); in un mondo piatto il terreno è a y −60, cioè 1230 m sotto il mare:
  1170 hPa e 1,48 kg/m³ di aria, un quinto di potenza in più dalle turbine. Ora si usa il livello del mare del
  generatore del terreno (`ChunkGenerator.getSeaLevel()`: 63 normale, −63 piatto, 32 Nether), il terreno sotto il
  mare (fondale, grotta) vale come la superficie, e l'aria è presa all'altezza dello strumento (terreno + palo): un
  mozzo a 130 m sta in aria più rarefatta dell'1,4 % di un sensore a 10 m, come la densità di riferimento della
  curva di potenza presuppone.
* **Deriva delle nubi** — **corretto**: il campo di cumuli era spostato del vento istantaneo *moltiplicato per tutto
  il tempo di gioco*; dopo tre ore un cambio di vento lo spostava di chilometri in un tick e la copertura saltava fino
  a 0,53 (lo leggevano i pannelli e la stazione meteo). Un cumulo è portato dal flusso guida in quota, lo stesso
  flusso costante (11 m/s) con cui viaggiano già i sistemi di pressione: lo spostamento è velocità × tempo, mezzo
  metro a tick.
* **Emisfero** — **corretto**: il vento geostrofico aveva i segni dell'emisfero sud (basse pressioni a destra) mentre
  il parametro di Coriolis è quello di 45° N, e la deviazione per attrito, che gira a sinistra, mandava l'aria in
  superficie verso l'alta pressione (1903 punti su 2000). Convenzione documentata: il mondo sta a **45° nord**; con x
  a est e z a sud, u = (1/ρf)·∂p/∂z, w = −(1/ρf)·∂p/∂x (legge di Buys Ballot). I test controllano la bassa a sinistra
  in quota in 2000 punti, e il vento in superficie che attraversa le isobare del campo intero (sistemi locali più il
  gradiente a grande scala del vento prevalente) verso la bassa in tutti.

| cosa | formula / valore nella mod | fonte |
|---|---|---|
| densità dell'aria | gas ideale, R = 287,05 J/(kg·K); 1,225 kg/m³ a 15 °C e 1013,25 hPa | ISA (ISO 2533) |
| pressione alla quota | ipsometrica, p = p0·exp(−z·g/(R·T)), gradiente 6,5 K/km | ISA |
| vento geostrofico | gradiente di pressione / (ρ·f), f = 1·10⁻⁴ s⁻¹ (45°) | Holton, *An Introduction to Dynamic Meteorology* |
| profilo verticale | legge di potenza da 200 m con α = 1/ln(200/z0), corretta per la stabilità | è la legge logaritmica neutra riscritta: a 100 m differisce dal log di < 0,5 %, a 10 m dell'8 % |
| rugosità z0 | mare 0,0002; sabbia/neve 0,005; terreno nudo 0,012; macchia 0,05; roccia 0,06; bosco 0,55; giungla 1,0 m; pianura ≈ 0,02 | classi Davenport–Wieringa (Wieringa 1992; 0,005 = "smooth", 0,03 = "open", 0,5 = "very rough", 1,0 = "closed") |
| intensità di turbolenza | I = 1/ln(z/z0) in aria neutra | EN 1991-1-4 §4.4 (Iv = kI/(c0·ln(z/z0)), kI = 1) |
| raffica di 3 s | v·(1 + 3·I) | fattore di picco 3–3,5 (EN 1991-1-4 usa 3,5 sul quadrato: 1 + 7·Iv) |
| deviazione per attrito | 8–38° verso la bassa pressione | spirale di Ekman: 15–45° sopra terra |
| albedo | acqua 0,07, neve 0,80, sabbia 0,35, terreno nudo 0,30, roccia 0,20, erba 0,22, bosco 0,14, giungla 0,12 | tabelle di albedo standard (Oke, *Boundary Layer Climates*) |
| massa d'aria | Kasten & Young 1989, con correzione di pressione | Kasten & Young, *Applied Optics* 28 (1989) |
| diretta in cielo sereno | 1361·0,7^(AM^0,678) | Meinel & Meinel 1976, costante solare 1361 W/m² (Kopp & Lean 2011) |
| diffusa in cielo sereno | Liu & Jordan: Td = 0,271 − 0,294·Tb | Liu & Jordan 1960 |
| nuvole | G/G_sereno = 1 − 0,75·N^3,4 | Kasten & Czeplak 1980 |
| piano inclinato | diretta × cos θ × IAM; diffusa Hay-Davies-Klucher-Reindl; riflessa ρ·G·(1 − cos β)/2 | Reindl et al. 1990; Duffie & Beckman cap. 2 |
| perdita angolare del vetro | Martin & Ruiz, ar = 0,16 | Martin & Ruiz 2001 |
| ombreggiamento fra file | quota d'ombra = 1 − 1/(GCR·(cos β + sin β/tan h)) | geometria 2D, sole perpendicolare alle file |
| posizione del sole | sorge a est, zenit a mezzogiorno, tramonta a ovest | è il cielo di Minecraft (vedi domande) |

Il solo punto debole (non un errore) è il profilo a 10 m, dove la legge di potenza sovrastima il log dell'8 %; le
turbine stanno fra 40 e 130 m, dove la differenza è sotto l'1 %.

## Costo delle ricette (`tools/gen_crafting.py`, "what a recipe costs")

### Prima (corretto)

Il costo di una macchina non aveva relazione con la sua taglia. Con le materie prime risolte fino al vanilla
(Fe-eq = lingotti di ferro, revisione M1/M2): il VC-2500K da 2,5 MW costava quanto il VX-110K (58 Fe-eq) e meno
del VX-350K (71); 1 MW di FT-415 costava ~1460 Fe-eq e 945 rame, 1 MW di C130-4.0 ~32 Fe-eq: il fotovoltaico 45 volte
l'eolico per MW, nel verso sbagliato; il C52 e il C80 costavano lo stesso metallo; un traliccio da 400 kV 7–8 Fe-eq,
un trasformatore da 63 MVA 2,6 volte quello da 2,5 MVA. E le parti piccole erano pesanti: un VX-10K da 21 kg
costava 24 lingotti di ferro e 38 di rame.

### Ora: la formula

Ogni ricetta costa, in lingotti di ferro, lingotti di rame, blocchi di vetro e quarzo, quello che la macchina vera
pesa in acciaio, metallo conduttore, vetro e silicio:

    unità(materiale) = tonnellate reali di quel materiale / TONNES_PER_UNIT,     TONNES_PER_UNIT = 2 t

* **acciaio** = acciaio, ghisa e l'alluminio strutturale (telai dei moduli), che il gioco non ha; **metallo
  conduttore** = rame e l'alluminio delle linee; **vetro** = vetro e la fibra di vetro delle pale; **silicio**.
  Carbone, redstone, oro, sabbia, pietra, slime e favo sono la chimica e l'elettronica: costano quel che costano e qui
  non pesano.
* `recipe_units()` risolve ogni ricetta fino al vanilla, con le rese (una piastra è 1/12 di lingotto, una sbarra
  1/24); `real_tonnes()` è la distinta dei materiali di ogni macchina; `cost_problems()` tiene ogni macchina al suo
  costo, e `gen_crafting.py` esce con 1 senza scrivere nulla se una sola è fuori:
  * sopra le **3 unità (6 t)**: totale, acciaio e rame entro **1,5×** in più o in meno della distinta;
  * sotto: non più di **3 unità oltre la distinta** - un inverter pesa 21 kg, ma la sua ricetta ha comunque un
    armadio e una scheda;
  * ogni famiglia (inverter, turbine, trasformatori, quadri di campo, tralicci) costa di più quando cresce.
* `python3 tools/gen_crafting.py --costs` stampa la tabella distinta/ricetta.
* Il vetro delle pale è nel totale ma non è tenuto da solo: due pale (`turbine_blade`, `long_blade`) per cinque
  rotori da 52 a 130 m non possono seguirlo una a una (domanda 3 della revisione dei dati).

### Le distinte e le fonti

| famiglia | come | fonte |
|---|---|---|
| moduli FV (6 file) | massa e numero di moduli dal catalogo; c-Si 76 % vetro, 8 % alluminio, 5 % silicio, 1 % rame; CdTe 97 % vetro | IRENA/IEA-PVPS, *End-of-Life Management: Solar Photovoltaic Panels* (2016), [pdf](https://iea-pvps.org/wp-content/uploads/2020/01/IRENA_IEAPVPS_End-of-Life_Solar_PV_Panels_2016.pdf) |
| tavolo zavorrato (FT) | 97 kg di sottostruttura per 5,46 kWp: 17,8 kg/kWp | relazione di progetto, [Camden](https://camdocs.camden.gov.uk/CMWebDrawer/Record/10891584/file/document) |
| struttura fissa (TR) | 5200 short ton di acciaio per 71 MWdc: 66 t/MW | Brantley, [Solar Power World 2018](https://www.solarpowerworldonline.com/2018/06/5200-tons-of-steel-went-into-71-mw-solar-flexrack-mounting-shipment-to-north-carolina-project/) |
| inseguitore monoassiale (HX) | 1070 kg contro 962 della fissa per gli stessi 26 moduli: +11 % | [EPJ Photovoltaics 2024, tab. 1](https://www.epj-pv.org/articles/epjpv/full_html/2024/01/pv230059/T1.html) |
| biassiale (AE) | una testa DEGERtraker 5000NT, 650 kg senza palo | scheda DEGER 5000NT |
| turbine C52–C130 | rotore, navicella e torre del modello NREL (pala "advanced", trasmissione a tre stadi), torre tarata sui 160 t del V90-3.0 a 80 m; il modello dà al V90-3.0 una navicella di 64 t e un rotore di 42 t, Vestas ne pubblica 70 e 41 | Fingersh, Hand, Laxson, *Wind Turbine Design Cost and Scaling Model*, NREL/TP-500-40566 (2006), [pdf](https://docs.nlr.gov/docs/fy07osti/40566.pdf); brochure Vestas V90-3.0 (link nella sezione turbine) |
| rame delle turbine | 2,5 t/MW (JRC: 3 t/MW per il DD-PMSG) | USGS, Wilburn, SIR 2011-5036, come tabulato da [MIT CBA](https://calischs.pages.cba.mit.edu/energy/studies/wind-turbine.html); Carrara et al., JRC EUR 30095 (2020) |
| SW-10 e blocco di torre | Bergey Excel 10: 545 kg in cima, pale 180 lb, torre a traliccio strallata da 100 ft 4000 lb: 0,59 t ogni 10 m | [scheda Excel 10](https://www.renewableenergyhub.co.uk/uploads/products/10619/product_brochures/Excel_10_Spec_Sheet.pdf), [manuale della torre GL](https://www.bergey.com/wp-content/uploads/excel-10-guyed-lattice-tower-installation-manual.pdf), tab. 5 |
| inverter | 21,2 kg (SUN2000-10KTL-M1 high current), 89 kg (SG110CX), 116 kg (SG350HX), < 3400 kg (SC 2200–3000-EV) | schede dei costruttori (link nella sezione inverter) |
| trasformatori | 2500 kVA: 5900 kg; 63 MVA: 89,8 t, di cui 15,3 t olio e 58,5 t parte attiva; l'olio non conta, gli avvolgimenti sono un terzo della parte attiva (stima) | [Legrand](https://www.legrand.fr/pro/catalogue/transformateur-htabt-aaoak-2500kva-5900kg-primaire-20kv-aluminium-2050x1400x2380mm); SGB DOTR 63000/123, [resale.de](https://www.resale.de/sgb-dotr-63000-123-transformer-transformatoren/nr-166202035) |
| tralicci | 600 t di acciaio per otto nuovi sostegni a 380 kV: 75 t in media; 50 / 75 / 100 t per sospensione, amarro, capolinea (ripartizione stimata) | TenneT, Dörpen–West Niederrhein, [europten](https://www.europten.com/en/whats-new/project-detail/380-kv-ltg-von-doerpen-nach-west-niederrhein-los-2b-in-planung/) |
| conduttori (una bobina = una fase) | NFA2X 4×70: 0,757 kg/m di alluminio; Aster 228: 0,627 kg/m; Curlew: 1,978 kg/m × 4 | sezione conduttori sopra |
| cavi DC | due conduttori di rame da 6 o 240 mm², 10 m a blocco | |
| quadri di campo, palo meteo, controllore, sezionatore, interruttore, palo, cabina, power box | stime, tutte sotto la soglia | |

### Come vengono le ricette

`python3 tools/gen_crafting.py --costs`, in unità da 2 t (totale distinta → ricetta):

| | distinta | ricetta | | distinta | ricetta |
|---|---|---|---|---|---|
| FT-415 | 0,59 | 1,29 | SW-10 (+3 blocchi di torre) | 1,2 | 1,8 |
| TR-580 | 0,61 | 1,00 | C52-0.85 | 33 | 36 |
| HX-700 | 0,56 | 1,42 | C80-2.0 | 100 | 128 |
| AE-440 | 0,45 | 1,62 | C90-3.0 | 145 | 140 |
| VX-10K | 0,01 | 0,81 | C112-3.0 | 230 | 201 |
| VX-110K | 0,04 | 1,44 | C130-4.0 | 336 | 273 |
| VX-350K | 0,06 | 2,46 | VT-2500 | 2,5 | 3,0 |
| VC-2500K | 1,70 | 3,88 | VT-63000 | 37 | 47 |
| traliccio S / A / E | 25 / 37,5 / 50 | 20 / 29 / 46 | bobina AT | 0,63 | 1,69 |

Per MW, torre compresa: FT-415 ~140 t-eq, inseguitori 190–570 (blocchi piccoli, sotto la soglia), C52–C130 87–137
(reale 82–169). Il fotovoltaico e l'eolico costano ora lo stesso ordine di grandezza per MW (nella realtà il
fotovoltaico pesa 0,4–0,9 volte l'eolico); il VC-2500K costa 2,7 volte il VX-110K e 1,6 volte il VX-350K.

Cosa è cambiato nell'albero per arrivarci: le scorte escono a dozzine (piastra, sbarra, scheda); una pala per classe
di peso; un `generator_set` è circa un megawatt (le turbine ne prendono uno o due, da lì il loro rame); una nuova
parte, `tower_section` (otto blocchi di ferro e i cavi), porta la torre delle turbine C nella loro ricetta, perché un
blocco `turbine_tower` regge qualunque macchina ed è tarato sulla torre del SW-10; il SW-10 ha pale di resina e un
alternatore (`motor_core`), niente moltiplicatore né imbardata; tutte le turbine C hanno il moltiplicatore, come
`TurbineSpec.gearboxRatio()` e le macchine di riferimento; il TR-580 ha un laminato, non il "quarto laminato del
modulo grande" (B1); gli array portano i laminati secondo il vetro reale (due ai tavoli da 44 moduli e al film
sottile, uno agli altri); il trasformatore da 63 MVA è ferro e rame a blocchi; il conduttore AT è rame a lingotti
(1,3 t a bobina).

## Unità di gioco

1 kW = 50 FE/tick = 125 J/tick (Mekanism) — **coerenti fra loro**: Mekanism converte 1 FE = 2,5 J, e 50 × 2,5 = 125
(`compat/energy/EnergyBridge.JOULES_PER_KW`, `block/FePool.FE_PER_KW`). Non hanno un equivalente fisico e non sono
state toccate: un kW reale in un tick di 0,05 s sono 50 J SI, quindi la convenzione equivale a 1 FE = 1 J reale e
1 J Mekanism = 0,4 J reali. Nessuna incoerenza trovata nel codice.

## Lasciato com'è, di proposito

* **Carichi uguali**: i power box non dichiarano quanta potenza vogliono, quindi ognuno assorbe come gli altri e la
  potenza di un generatore si divide fra loro in parti uguali (vedi domande).
* **Maglie**: la ripartizione a una diramazione segue i carichi raggiungibili da ciascun ramo, non la resistenza dei
  cammini alternativi; è esatta su linee e alberi (quasi tutte le reti dei giocatori), approssimata ma simmetrica e
  conservativa sulle maglie. Kirchhoff esatto richiederebbe di risolvere un sistema lineare a ogni tick.
* **La reattanza delle linee** non è modellata (solo R): per la BT conta poco (X ≈ 0,1 Ω/km contro R = 0,443), per
  una linea AT la reattanza (≈ 0,25 Ω/km per fase) è ciò che limita davvero la potenza trasmissibile, ma sulle
  distanze della mod non arriva mai a farlo.
* **La resistenza dei conduttori AC è presa a 20 °C**: la rete non ha meteo. A pieno carico un conduttore è a
  70–90 °C e R è il 20–28 % più alta; ai carichi tipici di gioco la differenza è di pochi punti percentuali di
  una perdita già piccola. I cavi DC invece la temperatura ce l'hanno (aria + riscaldamento del carico).
* **Il cielo di Minecraft**: il sole sorge a est, passa allo zenit e tramonta a ovest ogni giorno, come
  all'equatore all'equinozio. La fisica del piano inclinato è giusta per *quel* sole; la mod non ha latitudine.

## Da aggiornare nelle guide

Questi passi delle guide descrivono numeri o regole che ora sono cambiati (non li ho toccati: le guide sono di un
altro agente).

* **La rete in generale** (`README.md` r. 24–28, `docs/getting-started.md` §Losses r. ~128–140, `docs/GUIDA.md`,
  `docs/items.md`, `docs/IMPIANTO.md` r. 251–255, `docs/IMPIANTO-SOLARE.md` r. 199–202):
  * la formula `max(0.1, 1 − (length × 0.01) / (1 + 0.35 × (spans − 1)))`, "about 1% per block", "a hop never
    delivers less than a tenth", "the conductor decides what may be strung and how far, not what is lost",
    "parallel spans share the loss" → perdite P²R/U² secondo conduttore e tensione, limite U²/4R, trasformatori
    che perdono P0 + Pk(S/Sr)²;
  * "where a line divides, the power divides equally" → **la potenza va ai power box** e si divide fra loro in parti
    uguali; un ramo senza box non prende niente; **una linea senza un box in fondo legge 0 al wrench** (il generatore
    legge comunque quello che produce); un interruttore aperto legge 0 e manda tutto agli altri rami;
  * tre campate fra le stesse due macchine sono le tre fasi di un circuito e perdono quanto una; servono quattro-sei
    campate (due circuiti) per dimezzare la perdita;
  * `docs/items.md` r. 214–216 "The transformers' losses are shown but not taken" → ora sono prese dalla rete
    e oltre la taglia il trasformatore si scalda e la protezione lo stacca (sezione Trasformatori);
  * le letture lungo le linee degli impianti di esempio (IMPIANTO, IMPIANTO-SOLARE) cambiano: in MT e AT le perdite
    sono quasi nulle, in BT dipendono dalla potenza.
* **Tensione MT** — fatto: README, `docs/getting-started.md`, `docs/items.md` e `docs/GUIDA.md` dicono 33 kV
  (apparecchi da 36 kV, AB-36/630 e VB-36/630, sgancio sopra i 36 MW); nei tooltip lo controlla
  `registry/LanguageFileTest` contro `ConductorSpec.VoltageClass.MEDIUM` e `SwitchgearCatalog`.
* `docs/photovoltaics.md`:
  * r. 162–168: portafusibili CB-6 20 → 30 A; "IEC 62548 wants a string fuse at 1.4 times" → **1,5 volte** Isc,
    e non oltre il fusibile massimo del modulo.
  * r. 191–192: tabella cavi "Resistance at 90 °C 4.32 / 0.0961" → 3,39 / 0,0817 Ω/km **a 20 °C**, corretta per
    la temperatura del rame; DT-240 "570 A" → 775 A.
  * r. 245: FT-430 "44 (22×2)" → 44 (11×4); r. 330 e 359: tabella di compatibilità FT-430 (674 V → 11 × 41,8 =
    460 V a 25 °C, 421 V a 60 °C).
  * r. 278–279, 287: HJ-132-430 Voc 39,5 → 49,3 V; HB-066-440 Voc 80,5 → 48,2 V, −0,27 → −0,29 %/K, 1500 →
    1000 V; il paragrafo "A back-contact module at 80.5 V open circuit…" va riscritto. Tabella moduli: nuovi
    Vmp/Imp/Voc/Isc di tutti e sei (sezione "Moduli fotovoltaici" sopra), HP-108-415 non è più bifacciale.
  * r. 304–307: tabella inverter — VX-10K 2 ingressi, 13,5 A, europeo 98,1 %, 5,5 W; VX-110K 400 V AC, 110 kVA,
    europeo 98,5 %; VX-350K 352 kVA, derating da 30 °C, europeo 98,8 %, 6 W; VC-2500K 3200 A, 850–1425 V,
    98,6 / 98,3 %, 370 W, 550 V. La curva di rendimento è a tre termini; il derating arriva al 75 % a 60 °C
    (non più al 50 %); la potenza attiva è limitata da kVA × cos φ; una stringa con Voc a −10 °C sopra la tensione
    massima dell'inverter viene rifiutata.
  * r. 697: RC-1 "50 ms" → 0,1 s.
* `docs/telemetry.md` r. 156 ("a generator at 1800" → 1680 rpm alla velocità nominale) e tabella r. 228–233:
  aggiungere la velocità nominale del rotore (SW-10 250, C52 26, C80 16,7, C90 16,1, C112 12,8, C130 12,6 rpm) e
  correggere: SW-10 cut-in 2,5 m/s, 60–400 rpm, **trasmissione diretta (1:1, non 7:1)**; C90-3.0 8,6–18,4 rpm;
  C130-4.0 7,9–14,0 rpm; velocità di punta alla nominale (non alla massima); moltiplicatori 64,6 / 100,6 / 104,3 /
  131 / 133. Classi dei sensori: WS-3 "IEC 61400-12-1 class 1.8A", WV-3 "ASTM D5096, ±1°". Il comportamento nuovo
  del cut-out (per ripartire serve anche la raffica sotto 1,2 × la velocità di ripartenza) e della potenza appena
  tolto il freno (sale con la velocità del rotore).
* Meteo, dove le guide ne parlano (`docs/photovoltaics.md`, `docs/telemetry.md`): il mondo sta a 45° nord (le basse
  pressioni hanno il vento che gira in senso antiorario); i mondi piatti hanno aria al livello del mare, non più
  densa del 20 %; le nubi si spostano con il flusso in quota.
* Per l'agente dei modelli 3D (non sono guide, ma dicono 24 kV): `tools/gen_switch_models.py` (docstring "a 24 kV
  air-break disconnector … a 24 kV vacuum circuit breaker"; i 320 mm di distanza fra le fasi che cita sono proprio
  quelli di un 36 kV, IEC 61936-1), e il modulo HJ-132-430 ora misura 1865 × 1040 mm se un generatore ne disegna
  le dimensioni.

## Domande (scelte che la realtà non decide)

1. **Scala delle campate.** Ho calcolato le perdite con 10 m a blocco, come chiesto. Ma a quella scala le campate
   massime sono 5–10 volte quelle reali (BT 400 m contro ~50, MT 900 m contro ~100, AT 1,6–2 km contro 300–500),
   e pali, cabine e quadri sono disegnati a ~2,5 m a blocco. Vuoi campate massime realistiche a 10 m/blocco (BT 5–9,
   MT 10–12, AT 30–50 blocchi), oppure una scala diversa per le linee?
2. **Tensione MT.** Ho reso coerente la classe MT a **33 kV (Um 36 kV)**, perché trasformatori, turbina, inverter e
   le guide dell'impianto dicevano 33 kV; solo conduttore e quadri dicevano 24 kV. In Italia la distribuzione è a
   20 kV (Um 24) e i parchi raccolgono a 30 kV (Um 36); dal 2021 Terna offre anche la connessione diretta a 36 kV.
   Va bene 33/36 kV, o preferisci 30 kV (stesso equipaggiamento, perdite +21 %) o 20 kV?
3. **Sovraccarico.** Ora la portata (A) dei conduttori non ha effetto: conta solo il limite fisico U²/4R. Una linea
   reale oltre la portata si scalda e la protezione la apre. Vuoi che una campata oltre la portata **scatti**
   (0 kW finché il carico non scende), che **limiti** la potenza alla portata, o che resti com'è?
4. **Trasformatore AT da 63 MVA a 400 kV.** Non esiste in un parco reale (i 400/30–33 kV sono 125–315 MVA);
   63 MVA è la taglia tipica 132–150/20–30 kV (in Italia 150/20 kV). Opzioni: portarlo a 160 MVA (P0 ≈ 65 kW,
   Pk ≈ 500 kW, che a un impianto piccolo costano molto), o portare la trasmissione a 150 kV (cambiano conduttore e
   tralicci), o lasciarlo.
5. **La cabina elettrica** passa fra MT e BT senza perdite e senza trasformatore; una cabina MT/BT vera ne ha uno
   (es. 630 kVA 20/0,4 kV, Tier 2: P0 540 W, Pk 4,6 kW). Aggiungerlo?
6. **Il tempo.** Il cielo corre a 72× (1 tick = 3,6 s di cielo), la turbina e la rete in tempo reale (1 tick =
   0,05 s). Ne dipendono: la velocità d'imbardata (5°/s nella mod, 0,3–0,5°/s reale: a velocità reale non
   seguirebbe un vento che gira 72 volte più in fretta), l'inerzia del rotore (≈ 1 s nella mod, decine di secondi
   reali), e il contatore **"energia di oggi"** degli inverter, che somma kWh in tempo reale: un giorno di
   Minecraft produce 1/72 dell'energia di un giorno vero. Quale orologio vuoi per l'energia e per la dinamica?
7. **cos φ della rete.** Ho usato cos φ = 1. Vuoi che il cos φ impostato su inverter e turbine entri nelle perdite
   di linea (a 0,9 la perdita cresce del 23 %)? Serve che la rete porti anche la potenza reattiva.
8. **Sezionatore sotto carico.** Apre a mano sotto il 2 % della portata (720 kW): un sezionatore reale interrompe
   solo correnti trascurabili (IEC 62271-102: ≲ 0,5 A). È bilanciamento.
9. **VX-350K** accetta anche il tronco di un quadro di campo; il SG350HX reale prende solo stringhe. E il
   **VC-2500K** conta 288 "ingressi" come numero di stringhe, mentre lo SMA ha 24 ingressi DC con fusibile (uno
   per quadro). Tenere le regole di gioco?
10. **CB-32**: uscita da 630 A non trovata su un prodotto reale (il Weidmüller a 32 vie ha 500 A e fusibili da
    20 A). Lasciare 630 o scendere a 500 (con l'HN-132-700 ci starebbero 28 stringhe, non 32)?
11. **Interramento dei cavi DC**: i fattori 0,86 / 0,82 non hanno fonte (EN 50618 non tabella l'interrato).
    Tenerli o ricavarli da IEC 60364-5-52 metodo D?
12. **Domanda dei power box.** Ora la potenza va ai box e si divide fra loro in parti uguali, perché un box non dice
    quanto vuole. Il modello fisico completo è che ogni box chieda quanto i suoi consumatori e i blocchi vicini
    accettano (più lo spazio nel buffer), e che quello che nessuno chiede resti al generatore (curtailment). Lo vuoi?
    Tocca `PowerBoxBlockEntity`; il bug del buffer (A4 della revisione) è corretto a parte (`KioskEnergy`).
13. **FT-430** ora è 4 stringhe da 11 (prima 2 da 22, che superavano i 1000 V a freddo): usa il doppio degli
    ingressi di stringa di un inverter. Va bene così, o preferisci 2 × 18 = 36 moduli?
14. **Quante tonnellate vale un lingotto.** Ho scelto **2 t** (`TONNES_PER_UNIT`). A 1 t il C130-4.0 (678 t con la
    torre) non entra in una griglia 3×3 nemmeno con la `tower_section`; a 4 t i blocchi fotovoltaici (0,9–1,3 t)
    finiscono tutti sotto la soglia e il loro costo non dipende più da niente. A 2 t il C130 costa 273 unità (30
    blocchi di ferro), un FT-415 1,3. Va bene, o preferisci un'altra scala?
15. **La soglia e la banda.** Sotto 3 unità (6 t) una macchina può costare fino a 3 unità più della sua distinta
    (l'elettronica pesa grammi, una ricetta no); sopra, totale, acciaio e rame entro 1,5 volte. Più stretto vuol dire
    più parti intermedie.
16. **La torre delle turbine C sta nella loro ricetta** (`tower_section`, nuova parte), perché un blocco
    `turbine_tower` regge qualunque turbina ed è tarato sui 0,59 t ogni 10 m della torre del SW-10. L'alternativa è
    un blocco di torre per classe di macchina (nuovi blocchi e modelli).
17. **Stime nelle distinte:** la ripartizione 50/75/100 t dei tralicci attorno ai 75 t medi; gli avvolgimenti dei
    trasformatori come un terzo della parte attiva; le masse dei quadri di campo, del palo meteo, del controllore, dei
    sezionatori, del palo e della cabina (tutte sotto la soglia). Il SW-10 non usa più `turbine_blade` (le sue pale
    pesano 27 kg l'una): resta aperta la domanda della revisione su una pala per classe di rotore.

