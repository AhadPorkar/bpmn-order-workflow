package io.github.ahadporkar.ordertocash.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Products and stock reservations. Every stock movement is a single conditional UPDATE, so two
 * concurrent reservations can never take the same unit.
 */
@Repository
public class InventoryRepository {

  private final JdbcClient jdbc;

  public InventoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<Product> findAll() {
    return jdbc.sql("SELECT * FROM products ORDER BY sku").query(InventoryRepository::mapProduct).list();
  }

  public List<Product> findBySkus(Collection<String> skus) {
    if (skus.isEmpty()) {
      return List.of();
    }
    return jdbc.sql("SELECT * FROM products WHERE sku IN (:skus)")
        .param("skus", skus)
        .query(InventoryRepository::mapProduct)
        .list();
  }

  /** Moves stock from available to reserved. Returns false if not enough is available. */
  public boolean takeStock(String sku, int quantity) {
    return jdbc.sql("""
            UPDATE products SET available = available - :qty, reserved = reserved + :qty
            WHERE sku = :sku AND available >= :qty
            """)
        .param("qty", quantity)
        .param("sku", sku)
        .update() == 1;
  }

  public void returnStock(String sku, int quantity) {
    jdbc.sql("UPDATE products SET available = available + :qty, reserved = reserved - :qty WHERE sku = :sku")
        .param("qty", quantity)
        .param("sku", sku)
        .update();
  }

  public void consumeStock(String sku, int quantity) {
    jdbc.sql("UPDATE products SET reserved = reserved - :qty WHERE sku = :sku")
        .param("qty", quantity)
        .param("sku", sku)
        .update();
  }

  public void insertReservation(UUID orderId, String sku, int quantity) {
    jdbc.sql("""
            INSERT INTO stock_reservations (order_id, sku, quantity, status, created_at)
            VALUES (:orderId, :sku, :qty, 'ACTIVE', :now)
            """)
        .param("orderId", orderId)
        .param("sku", sku)
        .param("qty", quantity)
        .param("now", OffsetDateTime.now())
        .update();
  }

  public List<Reservation> findReservations(UUID orderId) {
    return jdbc.sql("SELECT order_id, sku, quantity, status FROM stock_reservations WHERE order_id = :orderId")
        .param("orderId", orderId)
        .query((rs, n) -> new Reservation(
            rs.getObject("order_id", UUID.class),
            rs.getString("sku"),
            rs.getInt("quantity"),
            Reservation.Status.valueOf(rs.getString("status"))))
        .list();
  }

  /** Moves an ACTIVE reservation to a final status. Returns false if it was not ACTIVE any more. */
  public boolean closeReservation(UUID orderId, String sku, Reservation.Status newStatus) {
    return jdbc.sql("""
            UPDATE stock_reservations SET status = :status
            WHERE order_id = :orderId AND sku = :sku AND status = 'ACTIVE'
            """)
        .param("status", newStatus.name())
        .param("orderId", orderId)
        .param("sku", sku)
        .update() == 1;
  }

  private static Product mapProduct(ResultSet rs, int rowNum) throws SQLException {
    return new Product(
        rs.getString("sku"),
        rs.getString("name"),
        rs.getBigDecimal("unit_price"),
        rs.getInt("available"),
        rs.getInt("reserved"));
  }
}
