package io.github.ahadporkar.ordertocash.process;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.github.ahadporkar.ordertocash.order.OrderOutcome;
import io.github.ahadporkar.ordertocash.order.OrderService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shipping, cancellation and customer notifications. */
@Component
public class FulfilmentWorkers {

  private final OrderService orders;

  public FulfilmentWorkers(OrderService orders) {
    this.orders = orders;
  }

  @JobWorker(type = JobTypes.SHIP_ORDER)
  public void shipOrder(
      @Variable(name = ProcessConstants.ORDER_ID) String orderId,
      @Variable(name = ProcessConstants.PAYMENT_ID) String paymentId) {
    orders.ship(UUID.fromString(orderId), paymentId);
  }

  /** Runs after compensation has released the stock and cancelled the invoice. */
  @JobWorker(type = JobTypes.CANCEL_ORDER)
  public void cancelOrder(@Variable(name = ProcessConstants.ORDER_ID) String orderId) {
    orders.cancel(UUID.fromString(orderId));
  }

  /**
   * One worker for every "Inform customer" task. The task's input mapping sets {@code outcome}; the
   * worker records it and emits the matching order event, which a notification service consumes.
   */
  @JobWorker(type = JobTypes.NOTIFY_CUSTOMER)
  public void notifyCustomer(
      @Variable(name = ProcessConstants.ORDER_ID) String orderId,
      @Variable(name = ProcessConstants.OUTCOME) String outcome) {
    orders.recordOutcome(UUID.fromString(orderId), OrderOutcome.valueOf(outcome));
  }
}
