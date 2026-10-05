package io.github.ahadporkar.ordertocash.order;

import io.github.ahadporkar.ordertocash.events.OrderEventRecorder;
import io.github.ahadporkar.ordertocash.inventory.InventoryRepository;
import io.github.ahadporkar.ordertocash.inventory.InventoryService;
import io.github.ahadporkar.ordertocash.inventory.Product;
import io.github.ahadporkar.ordertocash.process.OrderProcessStarter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Order lifecycle: placing an order and the state changes the process asks for. */
@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private static final String CURRENCY = "EUR";

  private final OrderRepository orders;
  private final InventoryRepository products;
  private final InventoryService inventory;
  private final OrderEventRecorder events;
  private final OrderProcessStarter processStarter;
  private final TransactionTemplate tx;

  public OrderService(OrderRepository orders, InventoryRepository products, InventoryService inventory,
      OrderEventRecorder events, OrderProcessStarter processStarter, TransactionTemplate tx) {
    this.orders = orders;
    this.products = products;
    this.inventory = inventory;
    this.events = events;
    this.processStarter = processStarter;
    this.tx = tx;
  }

  /**
   * Stores the order, then starts its process instance.
   *
   * <p>The order and its {@code OrderReceived} event are committed <em>before</em> the instance
   * starts, so the first job worker always finds the order and {@code OrderReceived} is always the
   * order's first event. The outbox relay holds the event back until the order has its process
   * instance. If the engine cannot be reached, the order and its event are removed again and the
   * caller gets an error: no order ever exists without a process instance.
   */
  public Order placeOrder(CreateOrderRequest request) {
    Order order = tx.execute(status -> {
      Order newOrder = priceOrder(request);
      orders.insert(newOrder);
      events.record("OrderReceived", newOrder.id(), OrderStatus.RECEIVED.name(), Map.of(
          "customerId", newOrder.customerId(),
          "totalAmount", newOrder.totalAmount(),
          "currency", newOrder.currency()));
      return newOrder;
    });
    long processInstanceKey;
    try {
      processInstanceKey = processStarter.start(order);
    } catch (RuntimeException e) {
      tx.executeWithoutResult(status -> {
        events.discard(order.id());
        orders.delete(order.id());
      });
      throw new OrchestrationUnavailableException(e);
    }
    tx.executeWithoutResult(status -> orders.setProcessInstanceKey(order.id(), processInstanceKey));
    log.info("Order {} started as process instance {}", order.id(), processInstanceKey);
    return find(order.id());
  }

  @Transactional(readOnly = true)
  public Order find(UUID orderId) {
    return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public void ship(UUID orderId, String paymentId) {
    inventory.consume(orderId);
    orders.setPayment(orderId, paymentId);
    if (orders.updateStatus(orderId, OrderStatus.SHIPPED)) {
      events.record("OrderShipped", orderId, OrderStatus.SHIPPED.name(),
          paymentId == null ? Map.of() : Map.of("paymentId", paymentId));
    }
  }

  @Transactional
  public void cancel(UUID orderId) {
    if (orders.updateStatus(orderId, OrderStatus.CANCELLED)) {
      events.record("OrderCancelled", orderId, OrderStatus.CANCELLED.name(), Map.of("reason", "PAYMENT_DEADLINE_MISSED"));
    }
  }

  @Transactional
  public void recordOutcome(UUID orderId, OrderOutcome outcome) {
    outcome.newStatus().ifPresent(status -> orders.updateStatus(orderId, status));
    events.record(outcome.eventType(), orderId, outcome.newStatus().map(Enum::name).orElse(null), Map.of());
  }

  private Order priceOrder(CreateOrderRequest request) {
    // Merge repeated SKUs: one reservation per SKU and order.
    Map<String, Integer> quantities = new LinkedHashMap<>();
    request.lines().forEach(line -> quantities.merge(line.sku(), line.quantity(), Integer::sum));

    Map<String, Product> catalog = products.findBySkus(quantities.keySet()).stream()
        .collect(Collectors.toMap(Product::sku, Function.identity()));
    Set<String> unknown = new TreeSet<>(quantities.keySet());
    unknown.removeAll(catalog.keySet());
    if (!unknown.isEmpty()) {
      throw new UnknownProductException(unknown);
    }

    List<OrderLine> lines = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    int lineNo = 1;
    for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
      OrderLine line = new OrderLine(lineNo++, entry.getKey(), entry.getValue(), catalog.get(entry.getKey()).unitPrice());
      lines.add(line);
      total = total.add(line.lineTotal());
    }
    OffsetDateTime now = OffsetDateTime.now();
    return new Order(UUID.randomUUID(), request.customerId(), request.customerSegment(), OrderStatus.RECEIVED,
        total, CURRENCY, null, null, null, now, now, lines);
  }
}
