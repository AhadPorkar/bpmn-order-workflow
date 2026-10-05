# ADR 0003: BPMN compensation instead of distributed transactions

**English** · [Deutsch](../../de/adr/0003-compensation-instead-of-distributed-transactions.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

When a customer does not pay within the payment term, two completed steps must be undone: the stock reservation and
the invoice. In a larger system these belong to different services (warehouse, billing) with their own databases. The
steps are days or weeks apart, so no database transaction can span them.

## Decision

Undoing is modeled as **BPMN compensation**, the saga pattern expressed in the model:

- *Reserve stock* and *Create invoice* each have a compensation boundary event with a handler: *Release stock* and
  *Cancel invoice*.
- When the interrupting deadline timer on *Wait for payment* fires, an intermediate compensation throw event
  (*Undo reservation and invoice*) triggers the handlers of every completed activity that has one.
- Only then is the order cancelled and the process ends in *Order cancelled*.

The handlers are idempotent ([ADR 0002](0002-idempotent-job-workers.md)) and independent of each other, so they do
not rely on the order in which the engine calls them.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Two-phase commit (XA) across services | Needs every resource to take part, holds locks for the duration, and cannot span weeks. Kafka and most HTTP services do not support it. |
| An error end event and explicit "undo" tasks on a separate path | Works, but the model must then track which steps were completed. Compensation does this automatically: a handler runs only if its activity completed. |
| A cleanup job that scans for overdue orders | Hides the business rule in a scheduler and duplicates the state the engine already has. |

## Consequences

Positive:

- The rollback is visible in the diagram and in Operate, step by step.
- An order rejected at *Reserve stock* (BPMN error) needs no compensation, because the activity did not complete; the
  model expresses this without any condition.

Negative:

- Compensation is a semantic undo, not a rollback. Between reservation and release, the stock was not available to
  others; between issue and cancellation, the invoice existed. Consumers of `InvoiceCancelled` must handle this.
- Each new step with side effects needs a matching handler, or it will not be undone.

## Verification

- `OrderToCashProcessTest.unpaidOrderIsCompensatedAfterTheDeadline` checks that both handlers run exactly once and that
  shipping never starts.
- `OrderToCashIT.anUnpaidOrderIsCancelledAndItsStockAndInvoiceAreRolledBack` checks the database: stock is back and the
  invoice is cancelled.
