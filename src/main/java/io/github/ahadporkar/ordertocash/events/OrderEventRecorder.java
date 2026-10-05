package io.github.ahadporkar.ordertocash.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes order events to the outbox in the caller's transaction, so that a state change and its
 * event are committed together or not at all.
 */
@Component
public class OrderEventRecorder {

  private final OutboxRepository outbox;
  private final ObjectMapper objectMapper;

  public OrderEventRecorder(OutboxRepository outbox, ObjectMapper objectMapper) {
    this.outbox = outbox;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String type, UUID orderId, String status, Map<String, Object> details) {
    OrderEvent event = new OrderEvent(type, orderId, status, OffsetDateTime.now(), details);
    try {
      outbox.insert(orderId + ":" + type, orderId, type, objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize " + event, e);
    }
  }

  /** Drops the unpublished events of an order that is being removed again. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void discard(UUID orderId) {
    outbox.deleteUnpublished(orderId);
  }
}
