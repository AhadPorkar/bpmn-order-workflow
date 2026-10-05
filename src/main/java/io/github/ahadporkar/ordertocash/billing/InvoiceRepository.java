package io.github.ahadporkar.ordertocash.billing;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRepository {

  private final JdbcClient jdbc;

  public InvoiceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Invoice invoice) {
    jdbc.sql("""
            INSERT INTO invoices (id, order_id, amount, currency, payment_term, status, created_at)
            VALUES (:id, :orderId, :amount, :currency, :term, :status, :now)
            """)
        .param("id", invoice.id())
        .param("orderId", invoice.orderId())
        .param("amount", invoice.amount())
        .param("currency", invoice.currency())
        .param("term", invoice.paymentTerm())
        .param("status", invoice.status().name())
        .param("now", OffsetDateTime.now())
        .update();
  }

  public Optional<Invoice> findByOrderId(UUID orderId) {
    return jdbc.sql("SELECT * FROM invoices WHERE order_id = :orderId")
        .param("orderId", orderId)
        .query((rs, n) -> new Invoice(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("payment_term"),
            Invoice.Status.valueOf(rs.getString("status"))))
        .optional();
  }

  /** Returns true if the invoice was ISSUED and is now CANCELLED. */
  public boolean cancel(UUID orderId) {
    return jdbc.sql("""
            UPDATE invoices SET status = 'CANCELLED', cancelled_at = :now
            WHERE order_id = :orderId AND status = 'ISSUED'
            """)
        .param("now", OffsetDateTime.now())
        .param("orderId", orderId)
        .update() == 1;
  }
}
