# Architekturentscheidungen (ADRs)

[English](../../en/adr/README.md) · **Deutsch**

Jeder Eintrag beschreibt eine Entscheidung: die Ausgangslage, was entschieden wurde, was sonst in Frage kam, was es
kostet und wo im Code oder in den Tests sich die Entscheidung überprüfen lässt.

| ADR | Entscheidung | Status |
| --- | --- | --- |
| [0001](0001-orchestrate-with-bpmn-on-camunda-8.md) | Die Bestellung wird mit BPMN auf Camunda 8 orchestriert statt per Event-Choreografie | Angenommen |
| [0002](0002-idempotent-job-workers.md) | Jeder Job Worker ist idempotent, weil Jobs mindestens einmal zugestellt werden | Angenommen |
| [0003](0003-compensation-instead-of-distributed-transactions.md) | BPMN-Kompensation statt verteilter Transaktionen | Angenommen |
| [0004](0004-business-rules-in-a-dmn-table.md) | Freigaberegeln und Zahlungsziele stehen in einer DMN-Tabelle | Angenommen |
| [0005](0005-payments-via-kafka-with-message-id-and-ttl.md) | Zahlungen kommen über Kafka und werden mit Message-ID und Time to Live korreliert | Angenommen |
| [0006](0006-transactional-outbox-for-order-events.md) | Bestellereignisse laufen über eine Transactional Outbox | Angenommen |
| [0007](0007-commit-the-order-before-starting-the-process.md) | Die Bestellung wird gespeichert, bevor ihre Prozessinstanz startet | Angenommen |
| [0008](0008-test-pyramid-with-camunda-process-test.md) | Eine Testpyramide auf Basis von Camunda Process Test und Testcontainers | Angenommen |

Das Format folgt dem ursprünglichen Vorschlag von Michael Nygard, mit einer Ergänzung: einem Abschnitt
*Überprüfung*, der die Tests nennt, die fehlschlagen würden, wenn die Entscheidung stillschweigend rückgängig gemacht
würde.
