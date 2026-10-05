package io.github.ahadporkar.ordertocash.events;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A fact about an order, published to the {@code order-events} topic.
 *
 * @param type for example {@code OrderShipped}
 * @param orderId the aggregate id; also the Kafka record key, so events of one order stay ordered
 * @param status the order status after the event, or null if it did not change
 * @param details event-specific data
 */
public record OrderEvent(String type, UUID orderId, String status, OffsetDateTime occurredAt, Map<String, Object> details) {}
