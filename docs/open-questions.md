# Domande aperte

Scelte che la realtà non decide da sola: estetica, scala di gioco, bilanciamento. Per ciascuna è indicato il
comportamento **attuale**, cioè quello che la mod fa oggi finché non si decide altro. Si risolvono in un ramo dedicato.

Le domande che le revisioni avevano posto ma che i rami di correzione hanno già chiuso con dati reali non sono qui:
flusso conservativo, perdite I²R, fasi trifase, velocità nominale del rotore, livello del mare, emisfero, perdite dei
trasformatori, generatore non simulato.

## 1. Scala e misure

| # | Domanda | Oggi |
|---|---|---|
| 1.1 | La scala non è unica: turbina 1 blocco = 10 m, traliccio 4,7 m, palo circa 2,5 m, campate 10–20 m per blocco. Si unifica, o si tiene una scala per tipo di oggetto? | scale diverse |
| 1.2 | Campate massime 5–10 volte quelle reali (BT 400 m contro ~50, MT 900 contro ~100, AT 1,6–2 km contro 300–500). Si accorciano? | 40 / 90 / 160 blocchi |
| 1.3 | Limite di campata dei tralicci (`TowerSpec.maxSpan`): quanti blocchi? | 160 / 200 |
| 1.4 | Conduttori disegnati 5–6 volte più spessi del vero; il fascio AT da vicino sembra un nastro (passo/diametro 1,4 contro circa 14). Si assottigliano e si allarga il passo? | spessi |
| 1.5 | Tralicci: dischi degli isolatori 1,5 volte e aste più spesse del vero, per leggibilità. Si portano alle misure reali? | maggiorati |
| 1.6 | Palo: mensole di 3,4 blocchi e 8 isolatori, molto più grande di un palo MT reale. Si rimpicciolisce? | grande |
| 1.7 | Recipe: quante tonnellate reali vale un lingotto? (a 1 t la C130 non entra nella griglia, a 4 t i pannelli scendono sotto il minimo) | 2 t |
| 1.8 | Costo minimo di una ricetta e tolleranza rispetto alla massa reale | 3 unità, 1,5× |

## 2. Rete elettrica

| # | Domanda | Oggi |
|---|---|---|
| 2.1 | Media tensione: 33 kV, 30 kV (tipico italiano, perdite +21 %) o 20 kV? | 33 kV su apparecchiature a 36 kV |
| 2.2 | Palo e cabina collegano BT e MT senza trasformatore. Serve un trasformatore MT/BT (su palo o in cabina) per passare di classe? | ponte gratuito |
| 2.3 | Un conduttore oltre la sua portata: scatta, limita la potenza o non succede nulla? | nulla |
| 2.4 | Il cos φ impostato su inverter e turbine deve aumentare le perdite di linea? | cos φ = 1 |
| 2.5 | Trasformatore AT da 63 MVA a 400 kV, che nella realtà non esiste (125–315 MVA): 160 MVA, trasmissione a 150 kV, o si lascia? | 63 MVA / 400 kV |
| 2.6 | Il sezionatore si apre a mano sotto il 2 % della portata (720 kW), mentre uno reale interrompe solo correnti trascurabili. E la redstone lo apre anche sotto carico, scavalcando l'interblocco. | 2 %, redstone esente |
| 2.7 | Un interruttore fa passare potenza tra macchine che la tabella `feeds` non collega ("adattatore universale"). Voluto? | passa |
| 2.8 | I power box dichiarano una domanda, con il resto ridotto al generatore? | ripartizione uguale fra i carichi |
| 2.9 | Il campo del kiosk accende qualsiasi blocco `powered`/`lit` nel raggio (leve, pistoni, falò, anche nei claim altrui). Lo si limita a un tag ed escludendo le sorgenti di redstone? | tutto |
| 2.10 | Quando scatta l'interruttore interno del kiosk, si ferma anche l'uscita FE verso i vicini? | no |
| 2.11 | Con `doTileDrops=false` il rimborso delle bobine va perso: deve rispettare la regola o ignorarla? | rimborso perso |
| 2.12 | Portata del giocatore: primo capo entro 32 + campata massima, oppure "almeno un capo entro 32"? | 32 + campata |
| 2.13 | Serve uno strumento per rimuovere una singola campata (oggi sparisce solo rompendo una macchina)? | nessuno |
| 2.14 | Valori scelti dalle correzioni, da confermare: avventura esclusa dai pannelli, massimo 4 comandi per macchina per giocatore per tick, invio della potenza oltre 0,5 % o 10 tick, riserva di 1024 id a ogni riavvio | come scritto |

## 3. Eolico

| # | Domanda | Oggi |
|---|---|---|
| 3.1 | Il trip per raffica resta deterministico (un cut-out più basso con turbolenza alta)? Serve un tempo minimo di attesa dopo un trip? | deterministico, nessuna attesa |
| 3.2 | La potenza va filtrata con l'inerzia del rotore (decine di secondi)? | segue il vento istantaneo |
| 3.3 | Costanti termiche di olio e cuscinetti: 25 s o minuti reali? | 25 s |
| 3.4 | Svolgimento automatico del cavo d'imbardata oltre ±720–1080°? | assente |
| 3.5 | SW-10: banderuola nel modello e nel comportamento, o si corregge il catalogo? | imbardata come le grandi |
| 3.6 | Effetto scia tra turbine? (le ostruzioni sono già modellate) | assente |
| 3.7 | Le foglie ostruiscono il disco del rotore ma non contano come copertura sopra la navicella. Va bene? | così |
| 3.8 | Torre in mare: resta un pozzo d'aria attorno al fusto. Serve una torre waterloggable? | pozzo d'aria |
| 3.9 | Due orologi: il cielo va 72 volte più veloce di turbine e rete (imbardata, inerzia, "energia di oggi" degli inverter). Quale orologio per cosa? Con la regola del tempo reale il meteo segue l'orologio da parete: va bene? | misto |
| 3.10 | Distanza di disegno delle turbine: una C130 alta 19 blocchi appare all'improvviso a 128 blocchi. 192–256 (circa 85 µs per turbina in vista)? | 128 |
| 3.11 | Passo delle pale visibile (pale in bandiera a turbina ferma): richiede una pala per gruppo nel modello. Si fa? | non disegnato |
| 3.12 | Torri delle turbine C incluse nella ricetta della turbina, mentre il blocco `turbine_tower` vale come la torre della SW-10. Va bene, o un blocco torre per classe? | incluse |
| 3.13 | Una pala per classe di rotore? (la C condivide due tipi di pala) Le C sono tutte con moltiplicatore e solo la SW-10 a presa diretta: va bene? | come detto |

## 4. Fotovoltaico

| # | Domanda | Oggi |
|---|---|---|
| 4.1 | Latitudine e stagioni: il sole è sempre all'equinozio all'equatore, e questo toglie senso a inclinazione ottimale e asse polare. Si aggiunge una latitudine o una stagione? | equinozio, equatore |
| 4.2 | Corrente d'ingresso oltre il limite: un inverter reale limita (clipping), quello della mod rifiuta. Il VX-10K rifiuta il TR-580 per 0,12 A. Rifiutare o limitare? | rifiuta |
| 4.3 | Stringhe che arrivano da un quadro: consumano i morsetti di stringa dell'inverter (32 sul VX-350K, 288 sul centrale). Il limite giusto è il numero di stringhe o la corrente? E il VX-350K deve accettare un trunk da quadro, che il modello reale non accetta? | conta le stringhe; trunk accettato |
| 4.4 | Isteresi della modalità diffusa del tracker: ±25 %, larga e simmetrica. Va bene? | ±25 % |
| 4.5 | Temperatura di cella: NOCT open-rack per tutti i montaggi, mentre un tavolo zavorrato gira 10–20 °C più caldo. Si differenzia? | uguale |
| 4.6 | Basi dei tempi: nuvole in secondi reali, sensori in secondi di cielo. Il ritardo del piranometro dura 1–2 tick ed è invisibile. | misto |
| 4.7 | Controllore d'impianto ad anello aperto (ripartisce per targa), mentre un PPC reale chiude l'anello sul punto di consegna. | anello aperto |
| 4.8 | FT-430: 4 stringhe da 11 o 2 da 18? | 4 × 11 |
| 4.9 | Cifre senza fonte: uscita 630 A del CB-32 (un quadro reale da 32 stringhe ha 500 A), fattori di posa 0,86 / 0,82 dei cavi DC. | come scritto |
| 4.10 | La neve sulle file sta nel blocco sopra: su rack e inseguitori appare sospesa sopra i moduli. | sospesa |
| 4.11 | Sotto inseguitori e biassiali c'è un'ombra fissa sull'erba, che non cambia con l'ora. Si tiene o si toglie? | fissa |
| 4.12 | Fila fissa: il cavo di stringa corre a terra sotto la struttura invece che fissato alla struttura. | a terra |

## 5. Modelli e grafica

| # | Domanda | Oggi |
|---|---|---|
| 5.1 | Traliccio d'amarro con doppie catene: 30.819 facce, 6–7 MB di GPU ciascuno. Si passa a catene singole (circa la metà)? | doppie |
| 5.2 | Ingombri cambiati (base del terminale 3 blocchi, sospensione ~2, palo con meno celle, terminale senza tiranti): vanno bene per il gameplay? | come scritto |
| 5.3 | Quadro d'impianto: armadio chiuso, o variante con l'interno visibile? | chiuso |
| 5.4 | SW-10: l'isolatore è collegato alla torre con un cunicolo in calcestruzzo. Estetica ok, o il renderer avvicina l'isolatore? | cunicolo |
| 5.5 | Luce delle macchine alte presa alla base: una lanterna ai piedi illumina il palo fino in cima. Va bene, o una luce per altezza? | alla base |
| 5.6 | Vertex buffer condivisi per (modello, gruppo, luce) invece che per (posizione, gruppo)? Molto più veloce con molte macchine, ma cambia `models.md` §6.8. | per posizione |
| 5.7 | Sprite delle turbine a 32×32, mentre tutti gli altri item sono a 16×16. Voluto? | 32×32 |

## 6. Gioco e interazione

| # | Domanda | Oggi |
|---|---|---|
| 6.1 | Quarzo del Nether obbligatorio per ogni generatore (niente energia prima del Nether)? Il silicio reale viene dalla sabbia silicea. | obbligatorio |
| 6.2 | Le macchine si posano in aria: serve un appoggio sotto? | nessun appoggio |
| 6.3 | Imbardata del palo fino a ±180° con celle fisse. Si limita a ±45°, o si ruotano le celle? | ±180°, celle fisse |
| 6.4 | Mano vuota per i pannelli: basta che la mano secondaria non regga uno strumento della mod, o devono essere vuote entrambe? | basta la secondaria |
| 6.5 | Un clic al piede della torre vale come clic sulla turbina per tutto (bobina, chiave, anteprima) o solo per la bobina? | per tutto |
| 6.6 | Un clic con la bobina su un tratto a terra senza puntare il morsetto: allunga il tratto o dice "Aim at an insulator."? | messaggio |
| 6.7 | Slider del quadro d'impianto a capacità 0: disattivato, o frazione convertita dal server? | disattivato |
| 6.8 | Pannelli più alti della finestra di default: scorrimento, o ridisegno per stare in 240 px? | scorrimento |

## 7. Progetto

| # | Domanda | Oggi |
|---|---|---|
| 7.1 | Licenza (codice e asset): MIT, LGPL, GPL, All Rights Reserved, o altra? | "To be decided" |
| 7.2 | Traduzione italiana (`it_it.json`)? Le guide sono in italiano | solo inglese |
| 7.3 | Visibilità del repository | privato |
