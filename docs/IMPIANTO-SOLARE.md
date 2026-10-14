# Impianto solare da 579 kW — scheda di costruzione

Un impianto fotovoltaico completo: quattro sottocampi, quattro tipi di pannello, due quadri di stringa,
**quattro inverter** di tre taglie, due trasformatori macchina, interruttore, sezionatore, cabina,
sottostazione, linea a 400 kV su tralicci, sottostazione di arrivo, tratto MT posato a terra, pali, kiosk,
palo meteo e quadro di controllo.

Non è un esempio inventato: è generato e **verificato** da
[tools/gen_example_plant.py](../tools/gen_example_plant.py) — che prima di scrivere controlla che nessuna
macchina invada le celle di un'altra, che ogni fila abbia il cavo su una faccia che accetta davvero, che
ogni rete in continua sia un pezzo solo e non ne tocchi un'altra, e che ogni campata stia dentro la portata
del suo conduttore — ed è stato **costruito e letto macchina per macchina** su un server, prima di essere
scritto qui.

> Questo è l'**esempio minimo**: un solo impianto solare, per capire la catena.
> Il sito completo — due parchi solari, un parco eolico e la sottostazione con i servizi ausiliari — è
> [IMPIANTO.md](IMPIANTO.md), ed è quello che sta nel mondo `run/impianto`.

## Costruirlo

```bash
python3 tools/gen_example_plant.py
```

Scrive due cose, perché ci sono due modi di avere un mondo.

**A) Un mondo qualsiasi, anche in singleplayer — il datapack.** `build/plant/datapack/` ha una sola
funzione, a **coordinate relative**: costruisce l'impianto dal blocco su cui stai. Copiala in
`saves/<mondo>/datapacks/`, entra con i trucchi attivi, `/reload`, poi `/function renewables:plant`.
Guardati intorno prima: occupa **da 3 blocchi a ovest a 146 a est** e **da 5 a nord a 28 a sud**.
Nel mondo `run/impianto` il datapack c'è già.

**B) Il server di sviluppo — RCON.** Con il server avviato:

```bash
python3 tools/gen_example_plant.py --at 0 56 8
python3 tools/rcon.py -f build/plant/plant.txt
```

RCON è un protocollo da server: in singleplayer non funziona, lì serve il datapack.

**In entrambi i casi le campate restano da fare.** Una campata è salvata contro due isolatori, non contro
un blocco, e va tirata a mano con la bobina: sono 18, elencate al §4. Ti servono **3 bobine di MT**, **6 di
AT** e **1 di BT**.

---

## 1. Cosa fa, in numeri

| | |
|---|---|
| Moduli in campo | **505,4 kW** in **60 stringhe** |
| Inverter | **582,0 kW** in alternata, su quattro macchine |
| Rapporto DC/AC | **0,87** |
| Produzione a mezzogiorno, cielo sereno | circa **390 kW** (stima: 447,6 kW misurati prima che i sottocampi 2–4 fossero ridotti agli ingressi per inseguitore) |
| Vie fusibilate nei quadri | 48 (un CB-16 pieno e un CB-32 a metà) |
| Blocchi di cavo | 53 |
| Campate da tirare | 18 |
| Comandi di costruzione | 123 |

I quattro sottocampi, ognuno dimensionato sull'inverter che lo raccoglie:

| # | Prodotto | Blocchi | Stringhe | kW | Quadro | Inverter | DC/AC |
|---|---|---|---|---|---|---|---|
| 1 | **TR-580** inclinazione fissa | 32 | 32 | 334,1 | CB-16 + CB-32 | **VX-350K** | 0,95 |
| 2 | **HX-700** ×8 + **AE-440** ×2 | 10 | 10 | 84,2 | — diretto | **VX-110K** | 0,77 |
| 3 | **FT-430** tavolo piatto | 4 | 16 | 75,7 | — diretto | **VX-110K** | 0,69 |
| 4 | **AE-440**, due file | 2 | 2 | 11,4 | — diretto | **VX-10K** | 1,14 |

---

## 2. Le quattro regole che decidono la forma del campo

Nessuna è indovinabile, e ognuna è costata un tentativo sbagliato prima di essere scritta qui.

**1. Il sole sta solo a est o solo a ovest.** Tutta la mattina a est, tutto il pomeriggio a ovest, mai a
sud. Una fila a inclinazione fissa va quindi orientata **a est o a ovest**; a nord o a sud sta a 90° dal
sole tutto il giorno e rende meno di un tavolo piatto. Metà delle file guardano da una parte e metà
dall'altra: è quello che appiattisce la curva della giornata. Una fila **su inseguitore ignora
l'orientamento**: si gira lei.

**2. Una fila prende il cavo su un asse solo.** Una fila **fissa** lo prende sulla faccia che *guarda* —
quindi un blocco di file fisse è una **spina nord-sud con le file ai due lati**, quelle a est della spina
girate a ovest e viceversa. Una fila **su inseguitore** lo prende a nord o a sud qualunque cosa guardi —
quindi un blocco di inseguitori è una **spina est-ovest con una fila sopra e una sotto**. Al contrario il
campo sembra cablato e non porta niente.

**3. Un inverter ha un numero di stringhe, non solo dei kW.** Il VX-350K ne prende 32: tre quadri da sedici
fanno 48 e il terzo lo rifiuta — resta pieno e nessuno lo raccoglie. E oltre alle stringhe c'è la potenza
continua e **la corrente di ciascun inseguitore**: una stringa entra sui morsetti di un inseguitore solo e
deve stare sotto i suoi ampere. Un VX-110K ha nove inseguitori da 26 A: una stringa HX-700 da 17,3 A ne
occupa uno intero, due AE-440 da 10,9 A ne dividono uno. Se superi un limite accetta le file **più vicine**
e lascia fuori le altre, e il pannello dell'inverter dice quale limite. Ogni sottocampo qui è tagliato
sugli ingressi del suo inverter.

**4. Un quadro di stringa vuole un inverter con i morsetti per la dorsale**, e i due inverter di stringa
non li hanno: solo il **VX-350K** e il **VC-2500K**. Un CB-16 davanti a un VX-110K resta pieno e scollegato.
Per questo i sottocampi 2, 3 e 4 vanno **diretti**, senza quadro: è esattamente cosa vuol dire "inverter di
stringa".

E una quinta, sulla tensione: il **TR-530** (film sottile) mette sei moduli in serie che in una mattina a
−10 °C aprono a 1493 V, oltre i 1100 V d'ingresso del VX-10K e del VX-110K: lo rifiutano. Vuole il
cabinet da 1500 V e tre vie per fila, e per questo su questo impianto non c'è. Una stringa che sta fuori
dalla **finestra MPPT** invece non ferma più l'inverter: il suo inseguitore la tiene al bordo della finestra,
dove rende meno (o niente, sopra la sua tensione a vuoto), e il pannello lo dice.

**Cosa ombreggia davvero:** non la distanza fra le file (quella dipende dal prodotto, non da come le
disponi) ma **qualsiasi cosa stia a est o a ovest di una fila e sia più alta dei suoi moduli**, perché è lì
che sta il sole: un blocco, un armadio, un quadro, il palo meteo. Per questo tutti gli armadi di questo
impianto stanno **a nord** del campo che servono.

---

## 3. La mappa

Coordinate del mondo `run/impianto`: lo spawn è (0, 56, 0), X cresce verso **est**, Z verso **sud**.

```
 z=1    [palo meteo]                        (3,56,4)
 z=3                                        [QUADRO DI CONTROLLO] (20,56,3)
 z=6    ══dorsale DT-240══════════════════► [VX-350K] ► [TX-A] ► [INT] ► [SEZ] ► [CABINA] ► [TX SOTTOST.]
 z=7    [CB-16]         [CB-32]                 (17)     (20)    (24)    (27)     (31)         (36)
 z=8..15  ████ spina ████    ████ spina ████
          x=0  x=1   x=2     x=5  x=6   x=7      32 × TR-580, sedici per quadro
 z=19   █████ 4 × HX-700 + 1 × AE-440  (x=0..4)
 z=20   ─────── cavo SC-6 ──────────────► [VX-110K inseguitori] (9,56,20)
 z=21   █████ 4 × HX-700 + 1 × AE-440
 z=24   ██ 2 × FT-430 (east)  |  x=1 spina |  ██ 2 × FT-430 (west)
 z=25   [AE-440] (5,56,25)  [AE-440] (7,56,25)
 z=26   ── cavo SC-6 x=5..7 ──► [VX-10K] (6,56,27)
 z=28   [VX-110K tavoli piani] (1,56,28)          [TX-B] (16,56,28)
```

La linea, tutta sulla riga **z=6**:

```
 x=36          x=44          x=64          x=84          x=104        x=112        x=116..120  x=124  x=138  x=146
[TX SOTTOST.] [TERMINALE] ─ [SOSPENS. 1] ─ [SOSPENS. 2] ─ [TERMINALE] [TX ARRIVO] ══MT posato══ [PALO] [PALO] [KIOSK]
                  └────────── 400 kV, campate da 20 blocchi ────────┘
```

---

## 4. Le 18 campate, in ordine

Bobina in mano, click destro sul primo isolatore, click destro sul secondo.

| # | Conduttore | Da | A | Blocchi |
|---|---|---|---|---|
| 1 | MT | inverter VX-350K (tetto) (17, 56, 6) | TX macchina A (isolatore basso) (20, 56, 6) | 3 |
| 2 | MT | inverter VX-110K inseguitori (tetto) (9, 56, 20) | TX macchina B (isolatore basso) (16, 56, 28) | 11 |
| 3 | MT | inverter VX-110K tavoli piani (tetto) (1, 56, 28) | TX macchina B (isolatore basso) (16, 56, 28) | 15 |
| 4 | MT | inverter VX-10K biassiali (tetto) (6, 56, 27) | TX macchina B (isolatore basso) (16, 56, 28) | 10 |
| 5 | MT | TX macchina A (isolatore alto) (20, 56, 6) | interruttore (lato linea) (24, 56, 6) | 4 |
| 6 | MT | TX macchina B (isolatore alto) (16, 56, 28) | interruttore (lato linea) (24, 56, 6) | 23 |
| 7 | MT | interruttore (lato carico) (24, 56, 6) | sezionatore (lato linea) (27, 56, 6) | 3 |
| 8 | MT | sezionatore (lato carico) (27, 56, 6) | cabina (ingresso) (31, 56, 6) | 4 |
| 9 | MT | cabina (uscita) (31, 56, 6) | TX sottostazione (isolatore basso) (36, 56, 6) | 5 |
| 10 | **AT** | TX sottostazione (isolatore alto) (36, 56, 6) | traliccio terminale partenza (44, 56, 6) | 8 |
| 11 | **AT** | traliccio terminale partenza (44, 56, 6) | traliccio sospensione 1 (64, 56, 6) | 20 |
| 12 | **AT** | traliccio sospensione 1 (64, 56, 6) | traliccio sospensione 2 (84, 56, 6) | 20 |
| 13 | **AT** | traliccio sospensione 2 (84, 56, 6) | traliccio terminale arrivo (104, 56, 6) | 20 |
| 14 | **AT** | traliccio terminale arrivo (104, 56, 6) | TX arrivo (isolatore alto) (112, 56, 6) | 8 |
| 15 | MT | TX arrivo (isolatore basso) (112, 56, 6) | tratto MT posato (primo blocco) (116, 56, 6) | 4 |
| 16 | MT | tratto MT posato (ultimo blocco) (120, 56, 6) | palo 1 (124, 56, 6) | 4 |
| 17 | MT | palo 1 (124, 56, 6) | palo 2 (138, 56, 6) | 14 |
| 18 | **BT** | palo 2 (138, 56, 6) | kiosk (146, 56, 6) | 8 |

Le tre fasi dei tralicci vanno tirate una per una fra gli isolatori corrispondenti: non incrociarle.
Le campate da 20 blocchi hanno un massimo di 160, quindi la linea si può allungare quanto vuoi.

---

## 5. Perché serve una seconda sottostazione, e non un palo

Un **traliccio accetta solo conduttore di trasmissione**. Un **palo lo rifiuta**. Quindi **non esiste
nessun conduttore che si possa tirare fra un traliccio e un palo** — vale anche fra traliccio e cabina.

Per scendere da 400 kV serve quello che serve nella realtà: una **sottostazione di arrivo**. Il traliccio
terminale entra sui **tre isolatori alti** del trasformatore, e dai **tre bassi** esce media tensione. E da
un trasformatore da sottostazione la MT può andare solo verso un traliccio (che la rifiuta) o verso un
**tratto posato a terra** — quindi il tratto MT posato non è un vezzo, è l'unica uscita. Da lì al palo, e
dal palo al kiosk in bassa tensione.

```
traliccio ──AT──► TX arrivo (isolatori alti)
                  TX arrivo (isolatori bassi) ──MT──► tratto posato ──MT──► palo ──BT──► kiosk
```

---

## 6. La messa in servizio, con quello che devi leggere

Chiave inglese in mano, in questo ordine. Se un passo non torna, il guasto è lì e non a valle.
Le letture qui sotto sono quelle **misurate davvero** su questo impianto a mezzogiorno, tranne le tre
segnate come stima: i sottocampi 2, 3 e 4 sono stati ridotti agli ingressi per inseguitore e le loro potenze
sono riscalate sulla targa (il numero di stringhe è invece misurato sul server: 32 / 10 / 16 / 2).

| # | Macchina | Dove | Deve dire |
|---|---|---|---|
| 1 | una fila TR-580 | (0, 56, 8) | `Wired`, e una potenza disponibile intorno a 10 kW |
| 2 | CB-16 | (1, 56, 7) | **16 vie su 16**, stato `chiuso` |
| 3 | CB-32 | (6, 56, 7) | **16 vie su 32** |
| 4 | VX-350K | (17, 56, 6) | **2 quadri**, **32 stringhe**, ~**250 kW** |
| 5 | VX-110K inseguitori | (9, 56, 20) | **10 stringhe**, nessun quadro, circa **68 kW** (stima) |
| 6 | VX-110K tavoli piani | (1, 56, 28) | **16 stringhe**, circa **61 kW** (stima) |
| 7 | VX-10K | (6, 56, 27) | **2 stringhe**, circa **9 kW** (stima) |
| 8 | TX-A e TX-B | (20, 56, 6) e (16, 56, 28) | rapporto, potenza e perdite (click destro a mano vuota) |
| 9 | interruttore | (24, 56, 6) | si apre e si chiude a mano vuota, e a valle va a zero |
| 10 | sezionatore | (27, 56, 6) | **rifiuta** di aprirsi sotto carico. Apri prima l'interruttore |
| 11 | cabina → tralicci → pali | z=6 | la potenza scende un po' a ogni passo: quello che manca sono le perdite |
| 12 | kiosk | (146, 56, 6) | accumula FE |
| 13 | palo meteo | (3, 56, 4) | gli strumenti del piano sono **sulla fila**, non del palo |
| 14 | quadro di controllo | (20, 56, 3) | **4 unità**, **582,0 kW** installati |

Il quadro di controllo, provato su questo impianto: messo a **200 kW** ha dato ai quattro inverter
**120,96 / 37,80 / 37,80 / 3,44 kW** — cioè il 34,4 % della targa di ciascuno. Il riferimento è ripartito
in proporzione, non diviso in parti uguali.

---

## 7. Cosa provare, una volta che gira

* **Isola il CB-32** (click destro a mano vuota): il VX-350K perde 16 stringhe e ~125 kW. Richiudi.
* **Metti un blocco alto a est di una fila** e guarda `beam` scendere al mattino e tornare al pomeriggio.
* **Aspetta il tramonto:** le file girate a ovest tengono mentre quelle a est sono già a zero.
* **Comparatore sul quadro di controllo:** legge quanto è carico l'impianto, da 0 a 15.
* **`following redstone`:** una leva sul quadro e tutto l'impianto la segue.
