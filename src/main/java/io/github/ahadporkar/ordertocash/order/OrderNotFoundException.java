package io.github.ahadporkar.ordertocash.order;

import java.io.Serial;
import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public OrderNotFoundException(UUID orderId) {
    super("Order " + orderId + " not found");
  }
}
