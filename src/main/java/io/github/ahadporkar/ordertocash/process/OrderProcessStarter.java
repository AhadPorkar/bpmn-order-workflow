package io.github.ahadporkar.ordertocash.process;

import io.camunda.client.CamundaClient;
import io.github.ahadporkar.ordertocash.order.Order;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Starts one {@code order-to-cash} process instance per order. */
@Component
public class OrderProcessStarter {

  private final CamundaClient camunda;

  public OrderProcessStarter(CamundaClient camunda) {
    this.camunda = camunda;
  }

  /** Returns the process instance key. Only business keys travel as variables, never the lines. */
  public long start(Order order) {
    return camunda.newCreateInstanceCommand()
        .bpmnProcessId(ProcessConstants.PROCESS_ID)
        .latestVersion()
        .variables(Map.of(
            ProcessConstants.ORDER_ID, order.id().toString(),
            ProcessConstants.CUSTOMER_ID, order.customerId(),
            ProcessConstants.CUSTOMER_SEGMENT, order.customerSegment().name(),
            ProcessConstants.TOTAL_AMOUNT, order.totalAmount(),
            ProcessConstants.CURRENCY, order.currency()))
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
