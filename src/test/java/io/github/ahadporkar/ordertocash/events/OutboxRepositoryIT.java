package io.github.ahadporkar.ordertocash.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ahadporkar.ordertocash.support.Containers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxRepository.class)
@Testcontainers
class OutboxRepositoryIT {

  @Container
  static final PostgreSQLContainer POSTGRES = Containers.postgres();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private OutboxRepository outbox;

  @Autowired
  private JdbcClient jdbc;

  @Test
  void theSameEventIsStoredOnce() {
    UUID orderId = UUID.randomUUID();

    assertThat(outbox.insert(orderId + ":OrderShipped", orderId, "OrderShipped", "{}")).isTrue();
    assertThat(outbox.insert(orderId + ":OrderShipped", orderId, "OrderShipped", "{}")).isFalse();

    assertThat(outbox.eventTypesFor(orderId)).containsExactly("OrderShipped");
  }

  @Test
  void publishedEventsAreNotRelayedAgain() {
    UUID orderId = UUID.randomUUID();
    outbox.insert(orderId + ":OrderReceived", orderId, "OrderReceived", "{\"n\":1}");
    outbox.insert(orderId + ":OrderInvoiced", orderId, "OrderInvoiced", "{\"n\":2}");

    List<OutboxEntry> batch = outbox.lockUnpublished(100).stream()
        .filter(e -> e.aggregateId().equals(orderId))
        .toList();
    assertThat(batch).extracting(OutboxEntry::eventType).containsExactly("OrderReceived", "OrderInvoiced");

    batch.forEach(e -> outbox.markPublished(e.id()));

    assertThat(outbox.lockUnpublished(100)).noneMatch(e -> e.aggregateId().equals(orderId));
  }

  @Test
  void eventsOfAnOrderAreHeldBackUntilItsProcessHasStarted() {
    UUID orderId = UUID.randomUUID();
    jdbc.sql("""
            INSERT INTO orders (id, customer_id, customer_segment, status, total_amount, currency, created_at, updated_at)
            VALUES (:id, 'customer-1', 'STANDARD', 'RECEIVED', 39.00, 'EUR', now(), now())
            """)
        .param("id", orderId)
        .update();
    outbox.insert(orderId + ":OrderReceived", orderId, "OrderReceived", "{}");

    assertThat(outbox.lockUnpublished(100)).noneMatch(e -> e.aggregateId().equals(orderId));

    jdbc.sql("UPDATE orders SET process_instance_key = 2251799813685249 WHERE id = :id").param("id", orderId).update();

    assertThat(outbox.lockUnpublished(100)).anyMatch(e -> e.aggregateId().equals(orderId));
  }

  @Test
  void unpublishedEventsOfARemovedOrderAreDeleted() {
    UUID orderId = UUID.randomUUID();
    outbox.insert(orderId + ":OrderReceived", orderId, "OrderReceived", "{}");

    assertThat(outbox.deleteUnpublished(orderId)).isEqualTo(1);
    assertThat(outbox.eventTypesFor(orderId)).isEmpty();
  }
}
