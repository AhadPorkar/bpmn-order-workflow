package io.github.ahadporkar.ordertocash.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ahadporkar.ordertocash.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class, properties = "app.messaging.mode=direct")
class OrderControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private OrderService orders;

  @MockitoBean
  private PaymentGateway payments;

  @Test
  void placingAnOrderReturns201WithLocation() throws Exception {
    Order order = order(OrderStatus.RECEIVED);
    when(orders.placeOrder(any())).thenReturn(order);

    mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
            {"customerId": "c-1", "customerSegment": "STANDARD",
             "lines": [{"sku": "SKU-KEYBOARD", "quantity": 2}]}
            """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/orders/" + order.id()))
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.totalAmount").value(178.0));
  }

  @Test
  void anOrderWithoutLinesIsRejected() throws Exception {
    mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
            {"customerId": "c-1", "customerSegment": "STANDARD", "lines": []}
            """))
        .andExpect(status().isBadRequest());

    verify(orders, never()).placeOrder(any());
  }

  @Test
  void anUnknownOrderIs404() throws Exception {
    UUID id = UUID.randomUUID();
    when(orders.find(id)).thenThrow(new OrderNotFoundException(id));

    mvc.perform(get("/api/orders/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Order " + id + " not found"));
  }

  @Test
  void aPaymentIsHandedToTheGateway() throws Exception {
    Order order = order(OrderStatus.AWAITING_PAYMENT);
    when(orders.find(order.id())).thenReturn(order);

    mvc.perform(post("/api/orders/{id}/payments", order.id()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 178.00, \"paymentId\": \"pay-1\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.paymentId").value("pay-1"));

    verify(payments).submit(argThat(p -> p.paymentId().equals("pay-1") && p.orderId().equals(order.id())));
  }

  @Test
  void aShippedOrderTakesNoPayment() throws Exception {
    Order order = order(OrderStatus.SHIPPED);
    when(orders.find(order.id())).thenReturn(order);

    mvc.perform(post("/api/orders/{id}/payments", order.id()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 178.00}"))
        .andExpect(status().isConflict());

    verify(payments, never()).submit(any());
  }

  private static Order order(OrderStatus status) {
    OffsetDateTime now = OffsetDateTime.now();
    return new Order(UUID.randomUUID(), "c-1", CustomerSegment.STANDARD, status, new BigDecimal("178.00"), "EUR",
        2251799813685249L, null, null, now, now,
        List.of(new OrderLine(1, "SKU-KEYBOARD", 2, new BigDecimal("89.00"))));
  }
}
