# ADR 0007: Die Bestellung wird gespeichert, bevor ihre Prozessinstanz startet

[English](../../en/adr/0007-commit-the-order-before-starting-the-process.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Das Anlegen einer Bestellung berührt zwei Systeme: die Bestellzeilen in PostgreSQL und eine neue Prozessinstanz in
Camunda. Sie können keine Transaktion teilen, deshalb entscheidet die Reihenfolge der beiden Schreibvorgänge darüber,
was schiefgehen kann:

- **Erst starten, dann einfügen:** Der erste Worker (*Check inventory*) kann laufen, bevor die Bestellung committet
  ist, und findet nichts. Scheitert das Einfügen danach, läuft eine Instanz für eine Bestellung, die es nicht gibt.
- **Einfügen, starten, dann committen, alles in einer Datenbanktransaktion:** dieselbe Race Condition, dazu eine lange
  Transaktion, die auf einen Netzwerkaufruf wartet.
- **Einfügen und committen, dann starten:** Der Worker findet die Bestellung immer. Scheitert der Start, existiert die
  Bestellung ohne Instanz.

## Entscheidung

1. Transaktion 1 speichert die bepreiste Bestellung **und ihr Ereignis `OrderReceived`** und committet.
2. Die Prozessinstanz wird gestartet.
3. Scheitert der Start, löscht Transaktion 2 die Bestellung und ihr unveröffentlichtes Ereignis, und die API antwortet
   mit `503`.
4. Gelingt er, speichert Transaktion 3 den Process Instance Key an der Bestellung.

Das Outbox-Relay veröffentlicht die Ereignisse einer Bestellung erst, wenn sie einen Process Instance Key hat
([ADR 0006](0006-transactional-outbox-for-order-events.md)). Zusammen ergeben diese Regeln zwei Garantien: Keine
Bestellung wird angekündigt, wenn ihr Prozess nicht läuft, und `OrderReceived` ist immer das erste Ereignis der
Bestellung, auch wenn die Worker schneller sind als Schritt 4.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Die Instanz aus der Outbox starten (das Relay ruft Camunda auf) | Vollständig asynchron und robust gegen eine nicht erreichbare Engine, aber der Client bekommt keinen Process Instance Key und keinen sofortigen Fehler; die API müsste `202` antworten und der Client abfragen. Sinnvoll bei hohem Volumen, mehr als dieser Service braucht. |
| `OrderReceived` erst nach dem Start schreiben | Einfach, aber ein schneller erster Worker kann `OrderInvoiced` zuerst schreiben, sodass Konsumenten Ereignisse in falscher Reihenfolge sehen könnten. |
| Den Start innerhalb des Requests wiederholen | Verlängert den Request und hilft nur bei kurzen Ausfällen. Der Client kann bei `503` selbst wiederholen. |

## Konsequenzen

Positiv:

- Worker sehen nie eine Bestellung, die nicht committet ist.
- Der Client bekommt eine sofortige, ehrliche Antwort: `201` mit einer Bestellung, deren Prozess läuft, oder `503`.

Negativ:

- Startet der Prozess, aber Transaktion 3 scheitert (etwa weil die Datenbank genau dann ausfällt), läuft die Instanz,
  während die Bestellung keinen Process Instance Key hat; ihre Ereignisse bleiben unveröffentlicht, bis der Key gesetzt
  ist. Dieses Zeitfenster ist klein und in der Outbox sichtbar, ein Produktivsystem braucht aber einen Alarm für
  unveröffentlichte Ereignisse, die älter als wenige Minuten sind.
- Scheitert auch das kompensierende Löschen in Schritt 3, bleibt eine Bestellung ohne Instanz im Status `RECEIVED`;
  im Produktivbetrieb ist eine regelmäßige Prüfung auf solche Bestellungen nötig.

## Überprüfung

- [`OrderServiceTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/order/OrderServiceTest.java): Bestellung
  und Ereignis werden vor dem Start gespeichert, der Key danach; scheitert der Start, werden Ereignis und Bestellung
  entfernt und kein Key geschrieben.
- `OutboxRepositoryIT.eventsOfAnOrderAreHeldBackUntilItsProcessHasStarted` prüft die Abfrage des Relays auf
  PostgreSQL.
- `OrderToCashIT` prüft die genaue Ereignisfolge `OrderReceived`, `OrderInvoiced`, `OrderShipped`.
