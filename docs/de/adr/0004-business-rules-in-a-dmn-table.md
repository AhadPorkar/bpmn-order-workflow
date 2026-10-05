# ADR 0004: Freigaberegeln und Zahlungsziele stehen in einer DMN-Tabelle

[English](../../en/adr/0004-business-rules-in-a-dmn-table.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Ob eine Bestellung freigegeben werden muss, wie lange der Kunde Zeit zum Zahlen hat und wann eine Erinnerung
verschickt wird, hängt vom Kundensegment und von der Bestellsumme ab. Diese Grenzen ändern sich häufiger als der Code:
eine Verkaufsaktion, ein neues Segment, eine andere Zahlungsrichtlinie.

## Entscheidung

Die Regeln sind eine DMN-Entscheidungstabelle,
[`order-approval.dmn`](../../../src/main/resources/dmn/order-approval.dmn), die ein Business Rule Task auswertet.
Eingaben sind `customerSegment` und `totalAmount`; das Ergebnis ist ein Objekt, `approval`, mit `approvalRequired`,
`paymentTerm` und `reminderAfter`. Die Hit Policy ist **FIRST**: Regeln werden von oben nach unten gelesen,
Sonderfälle zuerst, die letzte Regel ist der Standard.

Das Modell nutzt das Ergebnis direkt: Das Gateway liest `approval.approvalRequired`, die beiden Timer an *Wait for
payment* lesen `approval.reminderAfter` und `approval.paymentTerm`. Der Rechnungs-Worker speichert dasselbe
Zahlungsziel.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Bedingungen in Java | Jede Änderung braucht einen Entwickler und ein Release, und die Regeln sind für die Menschen, denen sie gehören, nicht lesbar. |
| Gateway-Bedingungen in BPMN (FEEL-Ausdrücke an jedem Fluss) | Gut für eine Bedingung. Bei drei Ergebnissen und mehreren Segmenten füllt sich das Diagramm mit Ausdrücken, und die Timer brauchen die Dauern trotzdem von irgendwoher. |
| Hit Policy UNIQUE | Erzwingt sich gegenseitig ausschließende Regeln, was eine Standardregel umständlich macht. FIRST ist für „Sonderfälle, dann Standard“ leichter zu lesen, um den Preis, dass die Zeilenreihenfolge bedeutsam wird. |

## Konsequenzen

Positiv:

- Der Vertrieb kann die Tabelle im Modeler lesen, und eine Änderung ist ein neues Deployment einer Datei.
- Timer und Rechnung folgen der Tabelle automatisch.

Negativ:

- Die Zeilenreihenfolge zählt. Eine neue Regel in der falschen Zeile kann von einer früheren verdeckt werden, ohne
  dass ein Fehler auftritt.
- Eine neue Tabellenversion gilt nur für neue Instanzen; laufende Bestellungen behalten ihr Zahlungsziel. Das will das
  Unternehmen meist so, es muss aber erklärt werden.

## Überprüfung

[`OrderApprovalDecisionTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/process/OrderApprovalDecisionTest.java)
wertet die Tabelle auf der Engine auf beiden Seiten jedes Schwellenwerts aus und prüft, welche Regel gegriffen hat.
Eine verdeckte Regel lässt den Test also auch dann fehlschlagen, wenn das Ergebnis zufällig stimmt.
