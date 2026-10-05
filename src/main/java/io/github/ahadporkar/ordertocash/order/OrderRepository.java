package io.github.ahadporkar.ordertocash.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Orders and order lines. Plain SQL through {@link JdbcClient}; no ORM. */
@Repository
public class OrderRepository {

  private final JdbcClient jdbc;

  public OrderRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Order order) {
    jdbc.sql("""
            INSERT INTO orders (id, customer_id, customer_segment, status, total_amount, currency,
                                created_at, updated_at)
            VALUES (:id, :customerId, :segment, :status, :total, :currency, :createdAt, :updatedAt)
            """)
        .param("id", order.id())
        .param("customerId", order.customerId())
        .param("segment", order.customerSegment().name())
        .param("status", order.status().name())
        .param("total", order.totalAmount())
        .param("currency", order.currency())
        .param("createdAt", order.createdAt())
        .param("updatedAt", order.updatedAt())
        .update();
    for (OrderLine line : order.lines()) {
      jdbc.sql("""
              INSERT INTO order_lines (order_id, line_no, sku, quantity, unit_price)
              VALUES (:orderId, :lineNo, :sku, :quantity, :unitPrice)
              """)
          .param("orderId", order.id())
          .param("lineNo", line.lineNo())
          .param("sku", line.sku())
          .param("quantity", line.quantity())
          .param("unitPrice", line.unitPrice())
          .update();
    }
  }

  public Optional<Order> findById(UUID id) {
    return jdbc.sql("SELECT * FROM orders WHERE id = :id")
        .param("id", id)
        .query(OrderRepository::mapOrder)
        .optional()
        .map(order -> order.withLines(findLines(id)));
  }

  public List<OrderLine> findLines(UUID orderId) {
    return jdbc.sql("""
            SELECT line_no, sku, quantity, unit_price FROM order_lines
            WHERE order_id = :orderId ORDER BY line_no
            """)
        .param("orderId", orderId)
        .query((rs, rowNum) -> new OrderLine(
            rs.getInt("line_no"), rs.getString("sku"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
        .list();
  }

  public void setProcessInstanceKey(UUID id, long processInstanceKey) {
    jdbc.sql("UPDATE orders SET process_instance_key = :key, updated_at = :now WHERE id = :id")
        .param("key", processInstanceKey)
        .param("now", OffsetDateTime.now())
        .param("id", id)
        .update();
  }

  /** Changes the status unless the order is already in it. Returns true if a row changed. */
  public boolean updateStatus(UUID id, OrderStatus status) {
    return jdbc.sql("UPDATE orders SET status = :status, updated_at = :now WHERE id = :id AND status <> :status")
        .param("status", status.name())
        .param("now", OffsetDateTime.now())
        .param("id", id)
        .update() == 1;
  }

  public void setInvoice(UUID id, UUID invoiceId) {
    jdbc.sql("UPDATE orders SET invoice_id = :invoiceId, updated_at = :now WHERE id = :id")
        .param("invoiceId", invoiceId)
        .param("now", OffsetDateTime.now())
        .param("id", id)
        .update();
  }

  public void setPayment(UUID id, String paymentId) {
    jdbc.sql("UPDATE orders SET payment_id = :paymentId, updated_at = :now WHERE id = :id")
        .param("paymentId", paymentId)
        .param("now", OffsetDateTime.now())
        .param("id", id)
        .update();
  }

  /** Removes an order that never got a process instance (see {@code OrderService#placeOrder}). */
  public void delete(UUID id) {
    jdbc.sql("DELETE FROM order_lines WHERE order_id = :id").param("id", id).update();
    jdbc.sql("DELETE FROM orders WHERE id = :id").param("id", id).update();
  }

  private static Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
    return new Order(
        rs.getObject("id", UUID.class),
        rs.getString("customer_id"),
        CustomerSegment.valueOf(rs.getString("customer_segment")),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getBigDecimal("total_amount"),
        rs.getString("currency"),
        rs.getObject("process_instance_key", Long.class),
        rs.getObject("invoice_id", UUID.class),
        rs.getString("payment_id"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        List.of());
  }
}
