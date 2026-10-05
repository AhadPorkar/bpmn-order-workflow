# ADR 0007: The order is committed before its process instance starts

**English** · [Deutsch](../../de/adr/0007-commit-the-order-before-starting-the-process.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

Placing an order touches two systems: the order rows in PostgreSQL and a new process instance in Camunda. They cannot
share a transaction, so the order of the two writes decides what can go wrong:

- **Start first, then insert:** the first worker (*Check inventory*) can run before the order is committed and find
  nothing. If the insert then fails, an instance runs for an order that does not exist.
- **Insert, start, then commit, all in one database transaction:** the same race, plus a long transaction that waits
  for a network call.
- **Insert and commit, then start:** the worker always finds the order. If the start fails, the order exists without
  an instance.

## Decision

1. Transaction 1 stores the priced order **and its `OrderReceived` event**, and commits.
2. The process instance is started.
3. If the start fails, transaction 2 deletes the order and its unpublished event, and the API answers `503`.
4. If it succeeds, transaction 3 stores the process instance key on the order.

The outbox relay publishes an order's events only once the order has a process instance key
([ADR 0006](0006-transactional-outbox-for-order-events.md)). Together, these rules give two guarantees: no order is
announced unless its process runs, and `OrderReceived` is always the order's first event, even if the workers are
faster than step 4.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Start the instance from the outbox (relay calls Camunda) | Fully asynchronous and robust against an unavailable engine, but the client gets no process instance key and no immediate error; the API would have to answer `202` and the client poll. Reasonable for high volume, more than this service needs. |
| Record `OrderReceived` after the start | Simple, but a fast first worker can record `OrderInvoiced` first, so consumers could see events out of order. |
| Retry the start inside the request | Lengthens the request and only helps with short outages. The client can retry on `503`. |

## Consequences

Positive:

- Workers never see an order that is not committed.
- The client gets an immediate, honest answer: `201` with an order that has a running process, or `503`.

Negative:

- If the process starts but transaction 3 fails (for example, the database goes down at that moment), the instance
  runs while the order has no process instance key; its events stay unpublished until the key is set. This window is
  small and visible in the outbox, but a production system needs an alert for unpublished events older than a few
  minutes.
- If the compensating delete in step 3 fails too, an order without an instance remains in status `RECEIVED`; a
  periodic check for such orders is needed in production.

## Verification

- [`OrderServiceTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/order/OrderServiceTest.java): the order
  and its event are stored before the start, the key afterwards; when the start fails, the event and the order are
  removed and no key is written.
- `OutboxRepositoryIT.eventsOfAnOrderAreHeldBackUntilItsProcessHasStarted` checks the relay query on PostgreSQL.
- `OrderToCashIT` asserts the exact event sequence `OrderReceived`, `OrderInvoiced`, `OrderShipped`.
