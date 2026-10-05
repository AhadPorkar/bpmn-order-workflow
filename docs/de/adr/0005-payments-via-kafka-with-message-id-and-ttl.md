# ADR 0005: Zahlungen kommen über Kafka und werden mit Message-ID und Time to Live korreliert

[English](../../en/adr/0005-payments-via-kafka-with-message-id-and-ttl.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Zahlungsbestätigungen kommen vom Zahlungsdienstleister, nicht vom Shop. Sie treffen asynchron ein, können mehrfach
zugestellt werden (Kafka liefert mindestens einmal, und Dienstleister senden Webhooks erneut) und können in seltenen
Fällen ankommen, bevor die Prozessinstanz auf sie wartet, etwa während die Rechnung noch erstellt wird.

## Entscheidung

- Bestätigungen werden aus dem Kafka-Topic `payments` gelesen, mit der Bestell-ID als Schlüssel.
- `PaymentCorrelator` veröffentlicht die BPMN-Nachricht `PaymentReceived` mit:
  - **Correlation Key** = Bestell-ID, damit die Nachricht die richtige Instanz findet;
  - **Message-ID** = Zahlungs-ID, damit die Engine eine zweite Nachricht mit derselben ID ablehnt, solange die erste
    noch gepuffert ist;
  - **Time to Live** = eine Stunde (`app.payments.message-time-to-live`), damit eine verfrühte Zahlung in der Engine
    wartet.
- Ein abgelehntes Duplikat wird protokolliert und als Erfolg behandelt. Der Listener schlägt nicht fehl, und der
  Record wird nicht wiederholt.
- Ein Record, der kein gültiges JSON ist, geht sofort nach `payments-dlt`. Andere Fehler werden dreimal im Abstand
  von einer Sekunde wiederholt und landen danach ebenfalls dort.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Ein REST-Aufruf des Dienstleisters direkt an die Engine | Koppelt den Dienstleister an die Engine und verliert die Pufferung durch Kafka, wenn Service oder Engine ausfallen. |
| Eine Deduplizierungstabelle im Service | Dupliziert, was die Engine mit Message-IDs bereits leistet, und braucht eine eigene Bereinigung. |
| Keine Time to Live | Eine verfrühte Zahlung ginge verloren, und die Bestellung würde nach Ablauf des Zahlungsziels storniert, obwohl sie bezahlt ist. |

## Konsequenzen

Positiv:

- Erneut zugestellte Zahlungen sind innerhalb der Time to Live harmlos. Danach findet ein spätes Duplikat keine
  wartende Instanz mehr, weil die ursprüngliche Zahlung die Instanz bereits weitergeführt hat, und die Nachricht
  verfällt einfach.
- Die Reihenfolge der Ereignisse je Bestellung bleibt erhalten, weil der Record-Schlüssel die Bestell-ID ist.

Negativ:

- Der Betrag wird nicht mit der Rechnung abgeglichen; Teil- und Überzahlungen liegen außerhalb des Umfangs und
  bräuchten eine Schleife um *Wait for payment*.
- Eine Zahlung für eine bereits stornierte Bestellung wird nicht automatisch erstattet: Sie findet keine wartende
  Instanz und verfällt. Der Demo-Endpunkt lehnt sie mit `409` ab; ein echtes System braucht einen Erstattungsweg.

## Überprüfung

- `OrderToCashProcessTest.duplicatePaymentIsIgnored` führt den produktiven `PaymentCorrelator` gegen die Engine aus:
  Die erste Veröffentlichung gelingt, die zweite wird abgelehnt, die Bestellung wird einmal versandt.
- `OrderToCashProcessTest.earlyPaymentIsBuffered` veröffentlicht die Zahlung, bevor die Instanz existiert.
- `OrderToCashIT.anOrderIsShippedAfterItsPaymentArrivesThroughKafka` deckt den Weg über das echte Topic ab.
