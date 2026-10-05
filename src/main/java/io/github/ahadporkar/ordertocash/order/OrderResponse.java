package io.github.ahadporkar.ordertocash.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
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
    List<Line> lines) {

  public record Line(String sku, int quantity, BigDecimal unitPrice) {}

  public static OrderResponse from(Order order) {
    return new OrderResponse(order.id(), order.customerId(), order.customerSegment(), order.status(),
        order.totalAmount(), order.currency(), order.processInstanceKey(), order.invoiceId(),
        order.paymentId(), order.createdAt(), order.updatedAt(),
        order.lines().stream().map(l -> new Line(l.sku(), l.quantity(), l.unitPrice())).toList());
  }
}
