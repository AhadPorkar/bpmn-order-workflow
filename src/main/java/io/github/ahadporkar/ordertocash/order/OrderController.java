package io.github.ahadporkar.ordertocash.order;

import io.github.ahadporkar.ordertocash.payment.PaymentEvent;
import io.github.ahadporkar.ordertocash.payment.PaymentGateway;
import io.github.ahadporkar.ordertocash.payment.PaymentRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orders;
  private final PaymentGateway payments;

  public OrderController(OrderService orders, PaymentGateway payments) {
    this.orders = orders;
    this.payments = payments;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
    Order order = orders.placeOrder(request);
    return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(OrderResponse.from(order));
  }

  @GetMapping("/{orderId}")
  public OrderResponse getOrder(@PathVariable UUID orderId) {
    return OrderResponse.from(orders.find(orderId));
  }

  /**
   * Simulates the payment provider confirming a payment. In production this confirmation arrives
   * on the {@code payments} topic from the provider's own integration.
   */
  @PostMapping("/{orderId}/payments")
  public ResponseEntity<Map<String, Object>> pay(@PathVariable UUID orderId, @Valid @RequestBody PaymentRequest request) {
    Order order = orders.find(orderId);
    if (order.status().isFinal()) {
      throw new IllegalStateException("Order " + orderId + " is " + order.status() + " and takes no payments");
    }
    String paymentId = request.paymentId() == null ? "pay-" + UUID.randomUUID() : request.paymentId();
    payments.submit(new PaymentEvent(paymentId, orderId, request.amount(), OffsetDateTime.now()));
    return ResponseEntity.accepted().body(Map.of("orderId", orderId, "paymentId", paymentId));
  }
}
