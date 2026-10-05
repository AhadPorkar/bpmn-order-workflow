# Architecture decision records

**English** · [Deutsch](../../de/adr/README.md)

Each record describes one decision: the situation, what was decided, what else was considered, what it costs, and
where in the code or tests the decision can be checked.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-orchestrate-with-bpmn-on-camunda-8.md) | Orchestrate the order with BPMN on Camunda 8 instead of event choreography | Accepted |
| [0002](0002-idempotent-job-workers.md) | Every job worker is idempotent, because jobs are delivered at least once | Accepted |
| [0003](0003-compensation-instead-of-distributed-transactions.md) | BPMN compensation instead of distributed transactions | Accepted |
| [0004](0004-business-rules-in-a-dmn-table.md) | Approval rules and payment terms live in a DMN table | Accepted |
| [0005](0005-payments-via-kafka-with-message-id-and-ttl.md) | Payments arrive via Kafka and are correlated with a message id and a time to live | Accepted |
| [0006](0006-transactional-outbox-for-order-events.md) | Order events go through a transactional outbox | Accepted |
| [0007](0007-commit-the-order-before-starting-the-process.md) | The order is committed before its process instance starts | Accepted |
| [0008](0008-test-pyramid-with-camunda-process-test.md) | A test pyramid built on Camunda Process Test and Testcontainers | Accepted |

The format follows Michael Nygard's original proposal, with one addition: a *Verification* section that names the
tests that would fail if the decision were silently reversed.
