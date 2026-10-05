package io.github.ahadporkar.ordertocash.billing;

import java.math.BigDecimal;
import java.util.UUID;

/** An invoice for one order. {@code paymentTerm} is an ISO 8601 duration from the DMN decision. */
public record Invoice(UUID id, UUID orderId, BigDecimal amount, String currency, String paymentTerm, Status status) {

  public enum Status {
    ISSUED,
    CANCELLED
  }
}
