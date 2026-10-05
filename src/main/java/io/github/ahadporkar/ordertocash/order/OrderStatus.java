package io.github.ahadporkar.ordertocash.order;

/** Lifecycle of an order as the business sees it. The process instance holds the details. */
public enum OrderStatus {
  RECEIVED,
  AWAITING_PAYMENT,
  SHIPPED,
  REJECTED,
  DECLINED,
  CANCELLED;

  public boolean isFinal() {
    return this == SHIPPED || this == REJECTED || this == DECLINED || this == CANCELLED;
  }
}
