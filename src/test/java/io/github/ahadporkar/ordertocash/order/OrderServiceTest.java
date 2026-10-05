package io.github.ahadporkar.ordertocash.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ahadporkar.ordertocash.events.OrderEventRecorder;
import io.github.ahadporkar.ordertocash.inventory.InventoryRepository;
import io.github.ahadporkar.ordertocash.inventory.InventoryService;
import io.github.ahadporkar.ordertocash.inventory.Product;
import io.github.ahadporkar.ordertocash.process.OrderProcessStarter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The order of steps when an order is placed (ADR 0007), with every collaborator mocked. */
class OrderServiceTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final InventoryRepository products = mock(InventoryRepository.class);
  private final OrderEventRecorder events = mock(OrderEventRecorder.class);
  private final OrderProcessStarter processStarter = mock(OrderProcessStarter.class);
  private final OrderService service = new OrderService(orders, products, mock(InventoryService.class), events,
      processStarter, new TransactionTemplate(mock(PlatformTransactionManager.class)));

  private final AtomicReference<Order> stored = new AtomicReference<>();

  @BeforeEach
  void catalogAndStorage() {
    when(products.findBySkus(any())).thenReturn(
        List.of(new Product("SKU-MOUSE", "Wireless mouse", new BigDecimal("39.00"), 800, 0)));
    doAnswer(invocation -> {
      stored.set(invocation.getArgument(0));
      return null;
    }).when(orders).insert(any());
    when(orders.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
  }

  @Test
  void theOrderAndItsFirstEventAreStoredBeforeTheProcessStarts() {
    when(processStarter.start(any())).thenReturn(42L);

    Order placed = service.placeOrder(mouseOrder());

    InOrder steps = inOrder(orders, events, processStarter);
    steps.verify(orders).insert(any());
    steps.verify(events).record(eq("OrderReceived"), eq(placed.id()), eq("RECEIVED"), anyMap());
    steps.verify(processStarter).start(any());
    steps.verify(orders).setProcessInstanceKey(placed.id(), 42L);
  }

  @Test
  void pricesComeFromTheCatalogAndRepeatedSkusAreMerged() {
    when(processStarter.start(any())).thenReturn(42L);

    Order placed = service.placeOrder(mouseOrder());

    assertThat(placed.lines()).hasSize(1);
    assertThat(placed.totalAmount()).isEqualByComparingTo("117.00"); // 3 x 39.00
    assertThat(placed.currency()).isEqualTo("EUR");
  }

  @Test
  void whenTheEngineIsUnreachableTheOrderAndItsEventAreRemoved() {
    when(processStarter.start(any())).thenThrow(new IllegalStateException("connection refused"));

    assertThatThrownBy(() -> service.placeOrder(mouseOrder())).isInstanceOf(OrchestrationUnavailableException.class);

    UUID orderId = stored.get().id();
    InOrder steps = inOrder(orders, events);
    steps.verify(orders).insert(any());
    steps.verify(events).discard(orderId);
    steps.verify(orders).delete(orderId);
    verify(orders, never()).setProcessInstanceKey(any(), anyLong());
  }

  @Test
  void anUnknownProductStoresNothing() {
    CreateOrderRequest request = new CreateOrderRequest("customer-1", CustomerSegment.NEW,
        List.of(new CreateOrderRequest.Line("SKU-UNKNOWN", 1)));

    assertThatThrownBy(() -> service.placeOrder(request)).isInstanceOf(UnknownProductException.class);

    verify(orders, never()).insert(any());
    verify(processStarter, never()).start(any());
  }

  private static CreateOrderRequest mouseOrder() {
    return new CreateOrderRequest("customer-1", CustomerSegment.STANDARD, List.of(
        new CreateOrderRequest.Line("SKU-MOUSE", 2),
        new CreateOrderRequest.Line("SKU-MOUSE", 1)));
  }
}
