package io.github.ahadporkar.ordertocash.inventory;

import io.github.ahadporkar.ordertocash.order.OrderLine;
import io.github.ahadporkar.ordertocash.order.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock checks and reservations.
 *
 * <p>Every operation is idempotent, because Camunda delivers jobs at least once: a worker that
 * crashes after committing but before completing its job will see the same job again.
 */
@Service
public class InventoryService {

  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

  private final InventoryRepository inventory;
  private final OrderRepository orders;

  public InventoryService(InventoryRepository inventory, OrderRepository orders) {
    this.inventory = inventory;
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public List<Product> catalog() {
    return inventory.findAll();
  }

  /** True if every line of the order can be served from available stock right now. */
  @Transactional(readOnly = true)
  public boolean isAvailable(UUID orderId) {
    List<OrderLine> lines = requireLines(orderId);
    Map<String, Product> products = inventory.findBySkus(lines.stream().map(OrderLine::sku).toList())
        .stream()
        .collect(Collectors.toMap(Product::sku, Function.identity()));
    return lines.stream().allMatch(line -> {
      Product product = products.get(line.sku());
      return product != null && product.available() >= line.quantity();
    });
  }

  /**
   * Reserves stock for every line, all or nothing. Calling it again for the same order is a no-op.
   *
   * @throws OutOfStockException if any line cannot be reserved; nothing is reserved in that case
   */
  @Transactional
  public void reserve(UUID orderId) {
    if (!inventory.findReservations(orderId).isEmpty()) {
      log.info("Stock for order {} is already reserved", orderId);
      return;
    }
    for (OrderLine line : requireLines(orderId)) {
      if (!inventory.takeStock(line.sku(), line.quantity())) {
        throw new OutOfStockException(line.sku(), line.quantity());
      }
      inventory.insertReservation(orderId, line.sku(), line.quantity());
    }
    log.info("Reserved stock for order {}", orderId);
  }

  /** Compensation: gives reserved stock back. Already released or shipped lines are skipped. */
  @Transactional
  public void release(UUID orderId) {
    for (Reservation reservation : inventory.findReservations(orderId)) {
      if (inventory.closeReservation(orderId, reservation.sku(), Reservation.Status.RELEASED)) {
        inventory.returnStock(reservation.sku(), reservation.quantity());
      }
    }
    log.info("Released stock for order {}", orderId);
  }

  /** Shipping: reserved stock leaves the warehouse. */
  @Transactional
  public void consume(UUID orderId) {
    for (Reservation reservation : inventory.findReservations(orderId)) {
      if (inventory.closeReservation(orderId, reservation.sku(), Reservation.Status.SHIPPED)) {
        inventory.consumeStock(reservation.sku(), reservation.quantity());
      }
    }
  }

  private List<OrderLine> requireLines(UUID orderId) {
    List<OrderLine> lines = orders.findLines(orderId);
    if (lines.isEmpty()) {
      throw new IllegalStateException("Order " + orderId + " has no lines or does not exist");
    }
    return lines;
  }
}
