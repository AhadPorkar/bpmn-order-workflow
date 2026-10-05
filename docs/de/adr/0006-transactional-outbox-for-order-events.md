# ADR 0006: Bestellereignisse laufen über eine Transactional Outbox

[English](../../en/adr/0006-transactional-outbox-for-order-events.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Andere Systeme (Benachrichtigungen, Analytics, ein Kundenportal) müssen wissen, wann eine Bestellung abgerechnet,
versandt oder storniert wird. Schreibt ein Worker in PostgreSQL und sendet danach an Kafka, kann einer der beiden
Schritte scheitern: Die Bestellung ist versandt, aber niemand erfährt davon, oder ein Ereignis kündigt etwas an, das
zurückgerollt wurde.

## Entscheidung

Ereignisse werden **in derselben Transaktion** wie die Zustandsänderung, die sie beschreiben, in die Tabelle
`outbox_events` geschrieben (`OrderEventRecorder`, Propagation `MANDATORY`, damit er nicht außerhalb einer Transaktion
aufgerufen werden kann). Ein zeitgesteuertes Relay (`OutboxRelay`) liest unveröffentlichte Zeilen in ID-Reihenfolge
mit `FOR UPDATE SKIP LOCKED`, sendet sie mit der Bestell-ID als Schlüssel an `order-events`, wartet auf die Bestätigung
des Brokers und markiert sie als veröffentlicht.

Das Relay hält die Ereignisse einer Bestellung zurück, bis die Bestellung ihre Prozessinstanz hat
([ADR 0007](0007-commit-the-order-before-starting-the-process.md)).

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Innerhalb der Datenbanktransaktion an Kafka senden | Nicht atomar: Das Senden kann gelingen und der Commit scheitern oder umgekehrt. |
| Kafka-Transaktionen | Decken nur Kafka ab, nicht PostgreSQL. |
| Change Data Capture (Debezium) | Die robuste Wahl bei großem Volumen, bringt aber Kafka Connect und einen Replication Slot in den Betrieb. Für einen Service genügt ein abfragendes Relay; das Tabellenlayout erlaubt einen späteren Wechsel zu CDC. |
| Den BPMN-Prozess Ereignisse erzeugen lassen (ein „Ereignis veröffentlichen“-Task je Schritt) | Verteilt technisches Messaging über das fachliche Modell und braucht trotzdem dieselbe Atomarität. |

## Konsequenzen

Positiv:

- Eine Zustandsänderung und ihr Ereignis werden zusammen committet oder gar nicht.
- Ist Kafka nicht verfügbar, sammeln sich die Ereignisse und werden veröffentlicht, sobald es wieder da ist.
- `SKIP LOCKED` erlaubt mehreren Service-Instanzen, parallel weiterzuleiten, ohne dieselbe Zeile doppelt zu senden.

Negativ:

- Die Zustellung erfolgt mindestens einmal: Stürzt das Relay zwischen Senden und Update ab, wird das Ereignis erneut
  gesendet. Konsumenten deduplizieren nach Bestell-ID und Ereignistyp.
- Ereignisse verzögern sich um bis zu ein Abfrageintervall (standardmäßig eine Sekunde).
- Die Tabelle wächst; veröffentlichte Zeilen brauchen im Produktivbetrieb einen Aufbewahrungs-Job.

## Überprüfung

- [`OutboxRepositoryIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/events/OutboxRepositoryIT.java):
  Ein Ereignis wird einmal gespeichert, veröffentlichte Ereignisse werden nicht erneut weitergeleitet, Ereignisse einer
  Bestellung ohne Prozessinstanz werden zurückgehalten, und die Ereignisse einer entfernten Bestellung werden gelöscht.
- `OrderToCashIT` wartet, bis die Outbox leer ist, das Relay also alles an den echten Broker veröffentlicht hat.
