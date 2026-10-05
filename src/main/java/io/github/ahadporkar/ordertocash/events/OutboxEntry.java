package io.github.ahadporkar.ordertocash.events;

import java.util.UUID;

/** A row of the transactional outbox. */
public record OutboxEntry(long id, UUID aggregateId, String eventType, String payload) {}
