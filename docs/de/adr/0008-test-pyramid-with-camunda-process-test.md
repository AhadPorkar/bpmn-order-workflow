# ADR 0008: Eine Testpyramide auf Basis von Camunda Process Test und Testcontainers

[English](../../en/adr/0008-test-pyramid-with-camunda-process-test.md) · **Deutsch**

- **Status:** Angenommen
- **Datum:** 2026-10

## Kontext

Das Verhalten dieses Services verteilt sich auf ein Modell (BPMN, DMN) und Code (Worker, Services, SQL). Manche Wege
dauern in Echtzeit zwei Wochen. Manche Garantien hängen von PostgreSQL-Funktionen ab (`ON CONFLICT`, `SKIP LOCKED`,
partielle Indizes), die eine In-Memory-Datenbank nicht hat oder anders nachbildet. Eine einzige Art von Test kann das
nicht schnell abdecken.

## Entscheidung

Vier Ebenen mit jeweils eigener Aufgabe:

| Ebene | Werkzeug | Testet | Testet nicht |
| --- | --- | --- | --- |
| Prozess und Entscheidung | Camunda Process Test (echte Engine im Container), Worker gemockt, Uhr gesteuert | Gateways, Timer, Nachrichten, Kompensation, DMN-Regeln | Java-Code, SQL |
| Unit | JUnit, Mockito, `@WebMvcTest` | Abbildung in den Workern, Schrittfolge in `OrderService`, HTTP-Vertrag | Engine, Datenbank |
| Integration | Testcontainers PostgreSQL, Flyway, `@JdbcTest` | Idempotenzregeln, Outbox-Abfragen | Engine |
| End-to-End | `@SpringBootTest`, Camunda Process Test Spring, PostgreSQL- und Kafka-Container | die echten Worker gegen das echte Modell, Kafka rein und raus | Last |

Timer werden getestet, indem die Uhr der Engine vorgestellt wird (`increaseTime`), nie durch Warten. Alle Tests laufen
bei jedem Push in der CI.

## Betrachtete Alternativen

| Alternative | Warum nicht |
| --- | --- |
| Nur End-to-End-Tests | Langsam, und ein Fehlschlag verrät nicht, ob Modell oder Code falsch ist. |
| H2 für Repository-Tests | Schneller, unterstützt aber kein `ON CONFLICT` und behandelt Sperren anders; die wichtigsten Abfragen blieben ungetestet. |
| Das Modell durch Lesen prüfen | Gateway-Bedingungen und Timer-Ausdrücke sind Code; sie brauchen Tests wie jeder andere Code. |

## Konsequenzen

Positiv:

- Jeder Weg des Modells ist ein benannter, lesbarer Test; zusammen dokumentieren sie den Prozess.
- Ein zweiwöchiger Mahnweg läuft in Sekunden.
- Das SQL, von dem die Idempotenzregeln abhängen, läuft auf derselben Datenbank-Engine wie in Produktion.

Negativ:

- Die Testsuite braucht Docker, lokal und in der CI.
- Prozesstests mit gemockten Workern können grün sein, während ein echter Worker einen falschen Variablennamen
  zurückgibt; dafür gibt es den End-to-End-Test.

## Überprüfung

[`docs/de/testing.md`](../testing.md) listet jede Testklasse und den Weg oder die Regel, die sie abdeckt. Die CI führt
`./mvnw verify` bei jedem Push und Pull Request aus.
