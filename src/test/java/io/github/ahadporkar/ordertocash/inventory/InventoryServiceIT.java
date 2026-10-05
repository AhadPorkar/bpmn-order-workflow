package io.github.ahadporkar.ordertocash.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ahadporkar.ordertocash.order.CustomerSegment;
import io.github.ahadporkar.ordertocash.order.Order;
import io.github.ahadporkar.ordertocash.order.OrderLine;
import io.github.ahadporkar.ordertocash.order.OrderRepository;
import io.github.ahadporkar.ordertocash.order.OrderStatus;
import io.github.ahadporkar.ordertocash.support.Containers;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Stock movements against a real PostgreSQL, with the Flyway schema and the demo catalog. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InventoryService.class, InventoryRepository.class, OrderRepository.class})
@Testcontainers
class InventoryServiceIT {

  @Container
  static final PostgreSQLContainer POSTGRES = Containers.postgres();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private InventoryService inventory;

  @Autowired
  private InventoryRepository products;

  @Autowired
  private OrderRepository orders;

  @Test
  void reservingTwiceTakesStockOnce() {
    int keyboards = available("SKU-KEYBOARD");
    UUID orderId = placeOrder(line(1, "SKU-KEYBOARD", 2), line(2, "SKU-MOUSE", 1));

    inventory.reserve(orderId);
    inventory.reserve(orderId); // a redelivered job

    assertThat(available("SKU-KEYBOARD")).isEqualTo(keyboards - 2);
    assertThat(products.findReservations(orderId)).hasSize(2)
        .allSatisfy(r -> assertThat(r.status()).isEqualTo(Reservation.Status.ACTIVE));
  }

  /** Runs without the test transaction, so the service's own rollback is what we observe. */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aReservationIsAllOrNothing() {
    int keyboards = available("SKU-KEYBOARD");
    UUID orderId = placeOrder(line(1, "SKU-KEYBOARD", 1), line(2, "SKU-SOLDOUT", 1));

    assertThatThrownBy(() -> inventory.reserve(orderId)).isInstanceOf(OutOfStockException.class);

    assertThat(available("SKU-KEYBOARD")).isEqualTo(keyboards);
    assertThat(products.findReservations(orderId)).isEmpty();
  }

  @Test
  void releasingGivesStockBackOnlyOnce() {
    int monitors = available("SKU-MONITOR");
    UUID orderId = placeOrder(line(1, "SKU-MONITOR", 3));
    inventory.reserve(orderId);

    inventory.release(orderId);
    inventory.release(orderId); // compensation handlers can run twice too

    assertThat(available("SKU-MONITOR")).isEqualTo(monitors);
    assertThat(products.findReservations(orderId)).singleElement()
        .satisfies(r -> assertThat(r.status()).isEqualTo(Reservation.Status.RELEASED));
  }

  @Test
  void shippingConsumesTheReservation() {
    int laptops = available("SKU-LAPTOP");
    UUID orderId = placeOrder(line(1, "SKU-LAPTOP", 1));
    inventory.reserve(orderId);

    inventory.consume(orderId);

    Product laptop = product("SKU-LAPTOP");
    assertThat(laptop.available()).isEqualTo(laptops - 1);
    assertThat(laptop.reserved()).isZero();
  }

  @Test
  void availabilityCheckSeesSoldOutProducts() {
    assertThat(inventory.isAvailable(placeOrder(line(1, "SKU-MOUSE", 1)))).isTrue();
    assertThat(inventory.isAvailable(placeOrder(line(1, "SKU-SOLDOUT", 1)))).isFalse();
  }

  private UUID placeOrder(OrderLine... lines) {
    OffsetDateTime now = OffsetDateTime.now();
    BigDecimal total = List.of(lines).stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    Order order = new Order(UUID.randomUUID(), "customer-it", CustomerSegment.STANDARD, OrderStatus.RECEIVED,
        total, "EUR", null, null, null, now, now, List.of(lines));
    orders.insert(order);
    return order.id();
  }

  private static OrderLine line(int lineNo, String sku, int quantity) {
    return new OrderLine(lineNo, sku, quantity, new BigDecimal("10.00"));
  }

  private int available(String sku) {
    return product(sku).available();
  }

  private Product product(String sku) {
    return products.findBySkus(List.of(sku)).get(0);
  }
}
