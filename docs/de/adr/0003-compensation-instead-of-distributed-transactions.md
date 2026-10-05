# ADR 0003: BPMN-Kompensation statt verteilter Transaktionen

[English](../../en/adr/0003-compensation-instead-of-distributed-transactions.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Zahlt ein Kunde nicht innerhalb des Zahlungsziels, müssen zwei abgeschlossene Schritte rückgängig gemacht werden: die
Bestandsreservierung und die Rechnung. In einem größeren System gehören sie zu verschiedenen Services (Lager,
Buchhaltung) mit eigenen Datenbanken. Zwischen den Schritten liegen Tage oder Wochen; keine Datenbanktransaktion kann
das umspannen.

## Entscheidung

Die Rückabwicklung wird als **BPMN-Kompensation** modelliert, also als Saga-Muster im Modell:

- *Reserve stock* und *Create invoice* haben je ein Compensation Boundary Event mit einem Handler: *Release stock* und
  *Cancel invoice*.
- Wenn der unterbrechende Frist-Timer an *Wait for payment* auslöst, stößt ein Compensation Intermediate Throw Event
  (*Undo reservation and invoice*) die Handler aller abgeschlossenen Aktivitäten an, die einen haben.
- Erst danach wird die Bestellung storniert, und der Prozess endet in *Order cancelled*.

Die Handler sind idempotent ([ADR 0002](0002-idempotent-job-workers.md)) und voneinander unabhängig; sie verlassen
sich nicht auf die Reihenfolge, in der die Engine sie aufruft.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Two-Phase-Commit (XA) über Services hinweg | Erfordert, dass jede Ressource teilnimmt, hält Sperren für die ganze Dauer und kann keine Wochen umspannen. Kafka und die meisten HTTP-Services unterstützen es nicht. |
| Ein Error End Event und ausdrückliche Rückabwicklungs-Tasks auf einem eigenen Pfad | Funktioniert, aber das Modell müsste dann mitführen, welche Schritte abgeschlossen sind. Kompensation erledigt das automatisch: Ein Handler läuft nur, wenn seine Aktivität abgeschlossen wurde. |
| Ein Aufräum-Job, der nach überfälligen Bestellungen sucht | Versteckt die Geschäftsregel in einem Scheduler und dupliziert den Zustand, den die Engine bereits hat. |

## Konsequenzen

Positiv:

- Die Rückabwicklung ist im Diagramm und in Operate Schritt für Schritt sichtbar.
- Eine an *Reserve stock* abgelehnte Bestellung (BPMN-Fehler) braucht keine Kompensation, weil die Aktivität nicht
  abgeschlossen wurde; das Modell drückt das ohne jede Bedingung aus.

Negativ:

- Kompensation ist eine fachliche Rücknahme, kein Rollback. Zwischen Reservierung und Freigabe stand der Bestand
  anderen nicht zur Verfügung; zwischen Ausstellung und Stornierung existierte die Rechnung. Konsumenten von
  `InvoiceCancelled` müssen damit umgehen.
- Jeder neue Schritt mit Seiteneffekten braucht einen passenden Handler, sonst wird er nicht zurückgenommen.

## Überprüfung

- `OrderToCashProcessTest.unpaidOrderIsCompensatedAfterTheDeadline` prüft, dass beide Handler genau einmal laufen und
  der Versand nie beginnt.
- `OrderToCashIT.anUnpaidOrderIsCancelledAndItsStockAndInvoiceAreRolledBack` prüft die Datenbank: Der Bestand ist
  zurück, die Rechnung storniert.
