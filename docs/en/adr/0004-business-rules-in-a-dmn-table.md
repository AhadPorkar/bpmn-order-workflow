# ADR 0004: Approval rules and payment terms live in a DMN table

**English** · [Deutsch](../../de/adr/0004-business-rules-in-a-dmn-table.md)

- **Status:** Accepted
- **Date:** 2026-10

## Context

Whether an order needs approval, how long the customer has to pay and when to send a reminder depend on the customer
segment and the order total. These limits change more often than the code: a sales campaign, a new segment, a
different payment policy.

## Decision

The rules are a DMN decision table, [`order-approval.dmn`](../../../src/main/resources/dmn/order-approval.dmn),
evaluated by a business rule task. Inputs are `customerSegment` and `totalAmount`; the output is one object,
`approval`, with `approvalRequired`, `paymentTerm` and `reminderAfter`. The hit policy is **FIRST**: rules are read top
down, special cases first, and the last rule is the default.

The model uses the result directly: the gateway reads `approval.approvalRequired`, and the two timers on *Wait for
payment* read `approval.reminderAfter` and `approval.paymentTerm`. The invoice worker stores the same term.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Conditions in Java | Every change needs a developer and a release, and the rules cannot be read by the people who own them. |
| Gateway conditions in BPMN (FEEL expressions on each flow) | Fine for one condition. With three outputs and several segments, the diagram fills with expressions and the timers still need the durations from somewhere. |
| Hit policy UNIQUE | Forces rules to be mutually exclusive, which makes a default rule awkward. FIRST is easier to read for "special cases, then default", at the cost of making row order significant. |

## Consequences

Positive:

- Sales can read the table in the Modeler, and a change is a new deployment of one file.
- Timers and invoice follow the table automatically.

Negative:

- Row order matters. A new rule inserted in the wrong row can be shadowed by an earlier one without any error.
- A new table version applies to new instances only; running orders keep the term they were given, which is usually
  what the business wants but must be explained.

## Verification

[`OrderApprovalDecisionTest`](../../../src/test/java/io/github/ahadporkar/ordertocash/process/OrderApprovalDecisionTest.java)
evaluates the table on the engine on both sides of every threshold and asserts which rule matched, so a shadowed rule
fails the test even when the output happens to be right.
