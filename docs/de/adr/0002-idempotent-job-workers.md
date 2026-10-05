# ADR 0002: Jeder Job Worker ist idempotent

[English](../../en/adr/0002-idempotent-job-workers.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Zeebe stellt jeden Job **mindestens einmal** zu. Ein Worker, der seine Datenbanktransaktion committet und dann
abstürzt, die Verbindung verliert oder den Job-Timeout überschreitet, bevor er den Job abschließt, bekommt denselben
Job erneut, womöglich auf einer anderen Instanz des Services. Es gibt keine Transaktion, die Datenbank und Engine
umfasst.

In diesem Service könnte ein wiederholter Job Bestand doppelt reservieren, eine zweite Rechnung stellen, doppelt
versenden oder ein Ereignis doppelt veröffentlichen.

## Entscheidung

Jede Operation, die ein Worker aufruft, ist idempotent, und die Datenbank erzwingt das:

| Operation | Wie eine Wiederholung wirkungslos wird |
| --- | --- |
| Bestand reservieren | Kehrt sofort zurück, wenn Reservierungen für die Bestellung existieren; Primärschlüssel `(order_id, sku)` als Sicherheitsnetz |
| Bestand freigeben / versenden | Jede Reservierung wechselt per bedingtem Update von `ACTIVE` nach `RELEASED` oder `SHIPPED`; der Bestand ändert sich nur, wenn dieses Update eine Zeile getroffen hat |
| Rechnung erstellen | Gibt die vorhandene Rechnung zurück; `UNIQUE (order_id)` auf `invoices` |
| Rechnung stornieren | Bedingtes Update auf `status = 'ISSUED'` |
| Bestellstatus ändern | `UPDATE … WHERE status <> :status`; das Ereignis wird nur geschrieben, wenn sich eine Zeile geändert hat |
| Ereignis schreiben | `event_key = orderId:type` mit `ON CONFLICT DO NOTHING` |

Worker enthalten keine eigene Logik. Sie bilden Variablen auf einen Service-Aufruf ab und eine fachliche Ausnahme auf
einen BPMN-Fehler. Jeder Service-Aufruf läuft in einer Transaktion, sodass ein Job entweder ganz oder gar nicht wirkt.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Eine Tabelle „verarbeitete Jobs“ mit dem Job-Key als Schlüssel | Generisch, aber ein nach Timeout wiederholter Job kann mit demselben Key ankommen, während der erste Versuch noch läuft; das kostet eine Tabelle und eine Sperre pro Job, und die fachliche Operation muss trotzdem sicher sein. |
| Sich auf die Engine verlassen (kurze Timeouts, keine Wiederholungen) | Verkleinert das Zeitfenster, schließt es aber nicht; ein Absturz nach dem Commit führt immer zu einer erneuten Zustellung. |

## Konsequenzen

Positiv:

- Worker lassen sich jederzeit horizontal skalieren und neu starten.
- Wiederholungen nach technischen Fehlern sind sicher, die Standardeinstellung von drei Versuchen pro Job kann bleiben.

Negativ:

- Jede neue Operation braucht eine ausdrückliche Idempotenzregel und einen Test dafür.
- Idempotenz gilt je Bestellung. Eine nachträgliche Änderung einer Reservierung (etwa eine andere Menge) bräuchte eine
  neue Regel, weil die aktuelle einen zweiten Aufruf bewusst ignoriert.

## Überprüfung

- [`InventoryServiceIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/inventory/InventoryServiceIT.java):
  Zweimal reservieren entnimmt einmal, zweimal freigeben bucht einmal zurück, eine Reservierung gilt ganz oder gar
  nicht.
- [`OutboxRepositoryIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/events/OutboxRepositoryIT.java):
  Dasselbe Ereignis wird einmal gespeichert.
