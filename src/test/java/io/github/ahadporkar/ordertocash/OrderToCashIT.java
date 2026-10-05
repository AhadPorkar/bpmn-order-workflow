package io.github.ahadporkar.ordertocash;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.github.ahadporkar.ordertocash.billing.Invoice;
import io.github.ahadporkar.ordertocash.billing.InvoiceRepository;
import io.github.ahadporkar.ordertocash.events.OutboxRepository;
import io.github.ahadporkar.ordertocash.inventory.InventoryRepository;
import io.github.ahadporkar.ordertocash.order.OrderResponse;
import io.github.ahadporkar.ordertocash.order.OrderStatus;
import io.github.ahadporkar.ordertocash.support.Containers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole service: REST API, real job workers, PostgreSQL, Kafka and a Camunda engine, all in
 * containers. The engine clock is controlled by the test, so a 14-day payment deadline takes
 * milliseconds.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.kafka.outbox-poll-interval-ms=200", "app.messaging.mode=kafka"})
@CamundaSpringProcessTest
class OrderToCashIT {

  // Started when the class loads, before Spring builds the application context. A JUnit
  // @Container field would start too late: the Camunda Process Test listener creates the
  // context in beforeTestClass, before the Testcontainers extension starts its containers.
  // Testcontainers' Ryuk removes both containers when the test JVM exits.
  static final PostgreSQLContainer POSTGRES = Containers.postgres();
  static final KafkaContainer KAFKA = Containers.kafka();

  static {
    POSTGRES.start();
    KAFKA.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private CamundaProcessTestContext processTestContext;

  @Autowired
  private InventoryRepository inventory;

  @Autowired
  private InvoiceRepository invoices;

  @Autowired
  private OutboxRepository outbox;

  @Test
  void anOrderIsShippedAfterItsPaymentArrivesThroughKafka() {
    OrderResponse order = placeOrder("STANDARD", "SKU-KEYBOARD", 2);
    awaitStatus(order.id(), OrderStatus.AWAITING_PAYMENT);

    ResponseEntity<Map> payment = rest.postForEntity("/api/orders/{id}/payments",
        Map.of("amount", order.totalAmount(), "paymentId", "pay-" + order.id()), Map.class, order.id());
    assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    awaitStatus(order.id(), OrderStatus.SHIPPED);
    assertThatProcessInstance(ProcessInstanceSelectors.byKey(order.processInstanceKey()))
        .isCompleted()
        .hasCompletedElements("task_ship_order", "end_completed");
    assertThat(outbox.eventTypesFor(order.id()))
        .containsExactly("OrderReceived", "OrderInvoiced", "OrderShipped");
    await().atMost(Duration.ofSeconds(30)).until(() -> outbox.countUnpublished() == 0);
  }

  @Test
  void anUnpaidOrderIsCancelledAndItsStockAndInvoiceAreRolledBack() {
    int monitorsBefore = available("SKU-MONITOR");
    OrderResponse order = placeOrder("STANDARD", "SKU-MONITOR", 2);
    awaitStatus(order.id(), OrderStatus.AWAITING_PAYMENT);
    assertThat(available("SKU-MONITOR")).isEqualTo(monitorsBefore - 2);

    processTestContext.increaseTime(Duration.ofDays(15)); // past the 14-day term for STANDARD

    awaitStatus(order.id(), OrderStatus.CANCELLED);
    assertThat(available("SKU-MONITOR")).isEqualTo(monitorsBefore);
    assertThat(invoices.findByOrderId(order.id())).get()
        .extracting(Invoice::status)
        .isEqualTo(Invoice.Status.CANCELLED);
    assertThatProcessInstance(ProcessInstanceSelectors.byKey(order.processInstanceKey()))
        .isCompleted()
        .hasCompletedElements("task_release_stock", "task_cancel_invoice", "end_cancelled");
  }

  @Test
  void aSoldOutProductIsRejected() {
    OrderResponse order = placeOrder("STANDARD", "SKU-SOLDOUT", 1);

    awaitStatus(order.id(), OrderStatus.REJECTED);
    assertThat(outbox.eventTypesFor(order.id())).contains("OrderRejected");
  }

  private OrderResponse placeOrder(String segment, String sku, int quantity) {
    ResponseEntity<OrderResponse> response = rest.postForEntity("/api/orders", Map.of(
        "customerId", "customer-" + UUID.randomUUID(),
        "customerSegment", segment,
        "lines", List.of(Map.of("sku", sku, "quantity", quantity))), OrderResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private void awaitStatus(UUID orderId, OrderStatus expected) {
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        assertThat(rest.getForObject("/api/orders/{id}", OrderResponse.class, orderId).status()).isEqualTo(expected));
  }

  private int available(String sku) {
    return inventory.findBySkus(List.of(sku)).get(0).available();
  }
}
