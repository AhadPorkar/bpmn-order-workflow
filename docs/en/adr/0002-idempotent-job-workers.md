# ADR 0002: Every job worker is idempotent

**English** · [Deutsch](../../de/adr/0002-idempotent-job-workers.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

Zeebe delivers each job **at least once**. A worker that commits its database transaction and then crashes, loses its
connection or exceeds the job timeout before it completes the job will see the same job again, possibly on another
instance of the service. There is no transaction that spans the database and the engine.

For this service, a repeated job could reserve stock twice, issue a second invoice, ship twice or publish an event
twice.

## Decision

Every operation called by a worker is idempotent, and the database enforces it:

| Operation | How a repetition becomes a no-op |
| --- | --- |
| Reserve stock | Returns early if reservations for the order exist; primary key `(order_id, sku)` as a safety net |
| Release stock / ship | Each reservation moves `ACTIVE → RELEASED` or `ACTIVE → SHIPPED` with a conditional update; stock changes only if that update hit a row |
| Create invoice | Returns the existing invoice; `UNIQUE (order_id)` on `invoices` |
| Cancel invoice | Conditional update on `status = 'ISSUED'` |
| Change order status | `UPDATE … WHERE status <> :status`; the event is recorded only if a row changed |
| Record an event | `event_key = orderId:type` with `ON CONFLICT DO NOTHING` |

Workers contain no logic of their own. They map variables to a service call and a business exception to a BPMN error.
Each service call runs in one transaction, so a job is either fully applied or not at all.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| A "processed jobs" table keyed by job key | Generic, but a retried job after a timeout may arrive with the same key while the first attempt is still running; it adds a table and a lock for every job and still needs the business operation to be safe. |
| Rely on the engine (short timeouts, no retries) | Reduces the window but cannot close it; a crash after commit always leads to redelivery. |

## Consequences

Positive:

- Workers can be scaled out and restarted at any time.
- Retries after technical errors are safe, so the default of three attempts per job can stay.

Negative:

- Every new operation needs an explicit idempotency rule and a test for it.
- Idempotency is per order. Changing a reservation after it was made (for example, a different quantity) would need a
  new rule, because the current one deliberately ignores a second call.

## Verification

- [`InventoryServiceIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/inventory/InventoryServiceIT.java):
  reserving twice takes stock once, releasing twice returns it once, a reservation is all or nothing.
- [`OutboxRepositoryIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/events/OutboxRepositoryIT.java):
  the same event is stored once.
