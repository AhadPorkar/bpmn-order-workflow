# ADR 0001: Die Bestellung wird mit BPMN auf Camunda 8 orchestriert

[English](../../en/adr/0001-orchestrate-with-bpmn-on-camunda-8.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Eine Bestellung durchläuft Bestand, Freigabe, Rechnung, Zahlung und Versand. Mehrere dieser Schritte warten: auf eine
Person, die freigibt, auf eine Zahlung, die Wochen dauern kann, auf eine Frist. Manche Wege müssen frühere Schritte
rückgängig machen. Das Unternehmen will für jede Bestellung wissen, wo sie steht und warum, und Regeln wie
Freigabegrenzen ohne Release ändern können.

Zwei Stile passen zu einem solchen Ablauf:

- **Choreografie:** Jeder Service reagiert auf Ereignisse (`OrderPlaced`, `StockReserved`, …) und erzeugt neue. Keine
  Komponente kennt den gesamten Ablauf.
- **Orchestrierung:** Eine Komponente besitzt den Ablauf und sagt den anderen, was zu tun ist.

## Entscheidung

Der Order-to-Cash-Ablauf wird in BPMN modelliert und von Camunda 8 (Zeebe) ausgeführt. Je Bestellung gibt es eine
Prozessinstanz. Der Spring-Boot-Service implementiert die Schritte als Job Worker und hält seine eigenen Daten in
PostgreSQL. Prozessvariablen tragen nur fachliche Schlüssel (`orderId`, Segment, Summe); fachliche Daten bleiben in
der Datenbank des Services.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Choreografie über Kafka | Der Ablauf existierte nur als Summe der Event-Handler. Timeouts, Erinnerungen und Rückabwicklung wären über mehrere Konsumenten mit jeweils eigenem Zustand verteilt. „Wo steht Bestellung 4711?“ bräuchte eine eigene Tracking-Sicht. |
| Ein Zustandsautomat im Service (Statusspalte plus Scheduler) | Funktioniert für den Normalfall. Wochenlanges Warten, Benutzeraufgaben, Timer je Bestellung und Kompensation müssten von Hand gebaut werden, und das Modell existierte nur im Code. |
| Camunda 7 (eingebettete Engine) | Ausgereift, aber in Wartung; neue Entwicklung zielt auf Camunda 8. Die externe Engine von Camunda 8 skaliert außerdem unabhängig vom Service. |

## Konsequenzen

Positiv:

- Das Modell in [order-to-cash.bpmn](../../../src/main/resources/bpmn/order-to-cash.bpmn) ist die Dokumentation.
  Vertrieb und Entwicklung besprechen dasselbe Diagramm, und Operate zeigt jede Bestellung darauf.
- Warten, Timer, Benutzeraufgaben und Kompensation sind Funktionen der Engine, kein Code im Service.
- Worker bleiben klein: Jeder bildet Job-Variablen auf einen Service-Aufruf ab.

Negativ:

- Die Engine ist Infrastruktur, von der der Service abhängt. Das Anlegen einer Bestellung scheitert mit `503`, wenn
  sie nicht erreichbar ist ([ADR 0007](0007-commit-the-order-before-starting-the-process.md)).
- Jobs werden mindestens einmal zugestellt, daher muss jeder Worker idempotent sein
  ([ADR 0002](0002-idempotent-job-workers.md)).
- Modelländerungen brauchen Sorgfalt: Laufende Instanzen bleiben auf ihrer Version, und manche Änderungen erfordern
  eine ausdrückliche Migration.
- Entwickler müssen BPMN gut genug kennen, um ein Modell zu lesen und zu testen.

## Überprüfung

- [`OrderToCashProcessTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/process/OrderToCashProcessTest.java)
  führt jeden Weg des Modells auf einer echten Engine aus.
- [`OrderToCashIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/OrderToCashIT.java) führt das Modell mit
  den echten Workern aus.
