# ADR 0005: Payments arrive via Kafka and are correlated with a message id and a time to live

**English** · [Deutsch](../../de/adr/0005-payments-via-kafka-with-message-id-and-ttl.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

Payment confirmations come from a payment provider, not from the shop. They arrive asynchronously, may be delivered
more than once (Kafka is at least once, and providers resend webhooks), and can, in rare cases, arrive before the
process instance waits for them, for example while the invoice is still being created.

## Decision

- Confirmations are consumed from the Kafka topic `payments`, keyed by order id.
- `PaymentCorrelator` publishes the BPMN message `PaymentReceived` with:
  - **correlation key** = order id, so the message finds the right instance;
  - **message id** = payment id, so the engine rejects a second message with the same id while the first one is still
    buffered;
  - **time to live** = one hour (`app.payments.message-time-to-live`), so an early payment waits in the engine.
- A rejected duplicate is logged and treated as success. The listener does not fail, and the record is not retried.
- A record that is not valid JSON goes to `payments-dlt` at once. Other failures are retried three times, one second
  apart, and then go there too.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| A REST call from the provider straight to the engine | Couples the provider to the engine and loses Kafka's buffering when the service or engine is down. |
| A deduplication table in the service | Duplicates what the engine already does with message ids, and needs its own cleanup. |
| No time to live | An early payment would be lost, and the order would be cancelled after the term although it was paid. |

## Consequences

Positive:

- Redelivered payments are harmless within the time to live. After it, a late duplicate finds no waiting instance,
  because the original payment has already moved the instance on, and the message simply expires.
- The order of events per order is kept, because the record key is the order id.

Negative:

- The amount is not reconciled with the invoice; partial payments and over-payments are out of scope and would need a
  loop around *Wait for payment*.
- A payment for an order that has already been cancelled is not refunded automatically: it finds no waiting instance
  and expires. The demo endpoint rejects it with `409`; a real system needs a refund path.

## Verification

- `OrderToCashProcessTest.duplicatePaymentIsIgnored` runs the production `PaymentCorrelator` against the engine: the
  first publish succeeds, the second is rejected, and the order ships once.
- `OrderToCashProcessTest.earlyPaymentIsBuffered` publishes the payment before the instance exists.
- `OrderToCashIT.anOrderIsShippedAfterItsPaymentArrivesThroughKafka` covers the path through the real topic.
