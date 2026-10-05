# ADR 0001: Orchestrate the order with BPMN on Camunda 8

**English** · [Deutsch](../../de/adr/0001-orchestrate-with-bpmn-on-camunda-8.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

An order passes through stock, approval, invoicing, payment and shipping. Several of these steps wait: for a person
to approve, for a payment that may take weeks, for a deadline. Some paths have to undo earlier steps. The business
wants to know, for any order, where it is and why, and wants to change rules such as approval limits without a
release.

Two styles fit a flow like this:

- **Choreography:** each service reacts to events (`OrderPlaced`, `StockReserved`, …) and emits new ones. No component
  knows the whole flow.
- **Orchestration:** one component owns the flow and tells the others what to do.

## Decision

The order-to-cash flow is modeled in BPMN and executed by Camunda 8 (Zeebe). There is one process instance per order.
The Spring Boot service implements the steps as job workers and keeps its own data in PostgreSQL. Process variables
carry business keys only (`orderId`, segment, total); business data stays in the service's database.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Choreography over Kafka | The flow would exist only as the sum of event handlers. Timeouts, reminders and undo logic would be spread over several consumers, each with its own state. "Where is order 4711?" would need a separate tracking view. |
| A state machine in the service (status column plus scheduler) | Works for the happy path. Waiting for weeks, human tasks, timers per order and compensation would all be built by hand, and the model would live only in code. |
| Camunda 7 (embedded engine) | Mature, but in maintenance; new work targets Camunda 8. The external engine of Camunda 8 also scales independently of the service. |

## Consequences

Positive:

- The model in [order-to-cash.bpmn](../../../src/main/resources/bpmn/order-to-cash.bpmn) is the documentation. Sales
  and developers discuss the same diagram, and Operate shows each order on it.
- Waiting, timers, human tasks and compensation are features of the engine, not code in the service.
- Workers stay small: each one maps job variables to one service call.

Negative:

- The engine is infrastructure the service depends on. Placing an order fails with `503` when it is unreachable
  ([ADR 0007](0007-commit-the-order-before-starting-the-process.md)).
- Jobs are delivered at least once, so every worker must be idempotent
  ([ADR 0002](0002-idempotent-job-workers.md)).
- Model changes need care: running instances stay on their version, and some changes require an explicit migration.
- Developers need to know BPMN well enough to read and test a model.

## Verification

- [`OrderToCashProcessTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/process/OrderToCashProcessTest.java)
  runs every path of the model on a real engine.
- [`OrderToCashIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/OrderToCashIT.java) runs the model with
  the real workers.
