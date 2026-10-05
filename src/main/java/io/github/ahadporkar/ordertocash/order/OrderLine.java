package io.github.ahadporkar.ordertocash.order;

import java.math.BigDecimal;

/** One line of an order. The unit price is copied from the catalog when the order is placed. */
public record OrderLine(int lineNo, String sku, int quantity, BigDecimal unitPrice) {

  public BigDecimal lineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
