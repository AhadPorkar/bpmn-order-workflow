package io.github.ahadporkar.ordertocash.inventory;

import java.io.Serial;

/** Raised when stock disappeared between the availability check and the reservation. */
public class OutOfStockException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public OutOfStockException(String sku, int requested) {
    super("Not enough stock for " + sku + " (requested " + requested + ")");
  }
}
