package io.github.ahadporkar.ordertocash.events;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

  private final JdbcClient jdbc;

  public OutboxRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Stores an event. The event key makes the insert idempotent: a job that is executed twice
   * produces one event, not two.
   *
   * @return true if the event was new
   */
  public boolean insert(String eventKey, UUID aggregateId, String eventType, String payload) {
    return jdbc.sql("""
            INSERT INTO outbox_events (event_key, aggregate_id, event_type, payload, created_at)
            VALUES (:key, :aggregateId, :type, :payload, :now)
            ON CONFLICT (event_key) DO NOTHING
            """)
        .param("key", eventKey)
        .param("aggregateId", aggregateId)
        .param("type", eventType)
        .param("payload", payload)
        .param("now", OffsetDateTime.now())
        .update() == 1;
  }

  /**
   * Locks the oldest unpublished events. {@code SKIP LOCKED} lets several application instances
   * relay in parallel without publishing the same event twice.
   *
   * <p>Events of an order are held back until the order has its process instance. An order whose
   * process could not be started is deleted together with its events, so the outside world never
   * hears of it.
   */
  public List<OutboxEntry> lockUnpublished(int limit) {
    return jdbc.sql("""
            SELECT e.id, e.aggregate_id, e.event_type, e.payload
            FROM outbox_events e
            LEFT JOIN orders o ON o.id = e.aggregate_id
            WHERE e.published_at IS NULL
              AND (o.id IS NULL OR o.process_instance_key IS NOT NULL)
            ORDER BY e.id
            LIMIT :limit
            FOR UPDATE OF e SKIP LOCKED
            """)
        .param("limit", limit)
        .query((rs, n) -> new OutboxEntry(
            rs.getLong("id"), rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"), rs.getString("payload")))
        .list();
  }

  /** Removes the events of an aggregate that have not been published yet. */
  public int deleteUnpublished(UUID aggregateId) {
    return jdbc.sql("DELETE FROM outbox_events WHERE aggregate_id = :id AND published_at IS NULL")
        .param("id", aggregateId)
        .update();
  }

  public void markPublished(long id) {
    jdbc.sql("UPDATE outbox_events SET published_at = :now WHERE id = :id")
        .param("now", OffsetDateTime.now())
        .param("id", id)
        .update();
  }

  public long countUnpublished() {
    return jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL").query(Long.class).single();
  }

  public List<String> eventTypesFor(UUID aggregateId) {
    return jdbc.sql("SELECT event_type FROM outbox_events WHERE aggregate_id = :id ORDER BY id")
        .param("id", aggregateId)
        .query(String.class)
        .list();
  }
}
