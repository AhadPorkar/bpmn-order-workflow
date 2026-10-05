package io.github.ahadporkar.ordertocash.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** An order with its lines, as stored in PostgreSQL. */
public record Order(
    UUID id,
    String customerId,
    CustomerSegment customerSegment,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency,
    Long processInstanceKey,
    UUID invoiceId,
    String paymentId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<OrderLine> lines) {

  public Order {
    lines = List.copyOf(lines);
  }

  public Order withLines(List<OrderLine> newLines) {
    return new Order(id, customerId, customerSegment, status, totalAmount, currency,
        processInstanceKey, invoiceId, paymentId, createdAt, updatedAt, newLines);
  }
}
