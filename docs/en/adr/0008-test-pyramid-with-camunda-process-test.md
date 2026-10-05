# ADR 0008: A test pyramid built on Camunda Process Test and Testcontainers

**English** · [Deutsch](../../de/adr/0008-test-pyramid-with-camunda-process-test.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

The behaviour of this service is split between a model (BPMN, DMN) and code (workers, services, SQL). Some paths take
two weeks of real time. Some guarantees depend on PostgreSQL features (`ON CONFLICT`, `SKIP LOCKED`, partial indexes)
that an in-memory database does not have or emulates differently. A single kind of test cannot cover all of this
quickly.

## Decision

Four levels, each with its own job:

| Level | Tool | Tests what | Does not test |
| --- | --- | --- | --- |
| Process and decision | Camunda Process Test (real engine in a container), workers mocked, clock controlled | gateways, timers, messages, compensation, DMN rules | Java code, SQL |
| Unit | JUnit, Mockito, `@WebMvcTest` | worker mapping, order of steps in `OrderService`, HTTP contract | engine, database |
| Integration | Testcontainers PostgreSQL, Flyway, `@JdbcTest` | idempotency rules, outbox queries | engine |
| End to end | `@SpringBootTest`, Camunda Process Test Spring, PostgreSQL and Kafka containers | the real workers against the real model, Kafka in and out | load |

Timers are tested by moving the engine clock (`increaseTime`), never by waiting. All tests run on every push in CI.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Only end-to-end tests | Slow, and a failure does not say whether the model or the code is wrong. |
| H2 for repository tests | Faster, but it does not support `ON CONFLICT` and treats locking differently, so the most important queries would go untested. |
| Testing the model by reading it | Gateway conditions and timer expressions are code; they need tests like any other code. |

## Consequences

Positive:

- Each path of the model is a named, readable test; together they document the process.
- A two-week dunning path runs in seconds.
- The SQL that the idempotency rules depend on runs on the same database engine as in production.

Negative:

- The test suite needs Docker, locally and in CI.
- Process tests with mocked workers can pass while a real worker returns the wrong variable name; the end-to-end test
  exists to catch that.

## Verification

[`docs/en/testing.md`](../testing.md) lists every test class and the path or rule it covers. CI runs
`./mvnw verify` on every push and pull request.
