package io.github.ahadporkar.ordertocash.order;

import java.util.Optional;

/**
 * Outcomes the process reports through the "Inform customer" tasks. Each one is recorded as an
 * order event; some also change the order status.
 */
public enum OrderOutcome {
  REJECTED_OUT_OF_STOCK(OrderStatus.REJECTED, "OrderRejected"),
  DECLINED(OrderStatus.DECLINED, "OrderDeclined"),
  PAYMENT_REMINDER(null, "PaymentReminderDue");

  private final OrderStatus newStatus;
  private final String eventType;

  OrderOutcome(OrderStatus newStatus, String eventType) {
    this.newStatus = newStatus;
    this.eventType = eventType;
  }

  public Optional<OrderStatus> newStatus() {
    return Optional.ofNullable(newStatus);
  }

  public String eventType() {
    return eventType;
  }
}
