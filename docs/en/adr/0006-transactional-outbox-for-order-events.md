# ADR 0006: Order events go through a transactional outbox

**English** · [Deutsch](../../de/adr/0006-transactional-outbox-for-order-events.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

Other systems (notifications, analytics, a customer portal) need to know when an order is invoiced, shipped or
cancelled. If a worker writes to PostgreSQL and then sends to Kafka, one of the two can fail: the order is shipped but
nobody hears about it, or an event announces something that was rolled back.

## Decision

Events are written to the table `outbox_events` **in the same transaction** as the state change they describe
(`OrderEventRecorder`, propagation `MANDATORY`, so it cannot be called outside a transaction). A scheduled relay
(`OutboxRelay`) reads unpublished rows in id order with `FOR UPDATE SKIP LOCKED`, sends them to `order-events` with the
order id as key, waits for the broker's acknowledgement, and marks them published.

The relay holds an order's events back until the order has its process instance
([ADR 0007](0007-commit-the-order-before-starting-the-process.md)).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Send to Kafka inside the database transaction | Not atomic: the send can succeed and the commit fail, or the reverse. |
| Kafka transactions | Cover Kafka only, not PostgreSQL. |
| Change data capture (Debezium) | The robust choice at scale, but it adds Kafka Connect and a replication slot to operate. For one service, a polling relay is enough; the table layout allows switching to CDC later. |
| Let the BPMN process emit events (a "publish event" task per step) | Spreads technical messaging over the business model and still needs the same atomicity. |

## Consequences

Positive:

- A state change and its event are committed together or not at all.
- If Kafka is down, events accumulate and are published when it is back.
- `SKIP LOCKED` lets several service instances relay in parallel without sending the same row twice.

Negative:

- Delivery is at least once: if the relay crashes between send and update, the event is sent again. Consumers
  deduplicate by order id and event type.
- Events are delayed by up to one poll interval (one second by default).
- The table grows; published rows need a retention job in production.

## Verification

- [`OutboxRepositoryIT`](../../../src/test/java/io/github/ahadporkar/ordertocash/events/OutboxRepositoryIT.java):
  an event is stored once, published events are not relayed again, events of an order without a process instance are
  held back, and the events of a removed order are deleted.
- `OrderToCashIT` waits until the outbox is empty, which means the relay published everything to the real broker.
