package io.github.ahadporkar.ordertocash.inventory;

import java.util.UUID;

/** Stock held for one order line. ACTIVE until it is shipped or released. */
public record Reservation(UUID orderId, String sku, int quantity, Status status) {

  public enum Status {
    ACTIVE,
    RELEASED,
    SHIPPED
  }
}
