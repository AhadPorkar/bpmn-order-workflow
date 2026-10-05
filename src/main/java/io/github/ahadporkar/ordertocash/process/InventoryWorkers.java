package io.github.ahadporkar.ordertocash.process;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import io.github.ahadporkar.ordertocash.inventory.InventoryService;
import io.github.ahadporkar.ordertocash.inventory.OutOfStockException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Job workers for the inventory steps of the process. Thin adapters: logic lives in the service. */
@Component
public class InventoryWorkers {

  private final InventoryService inventory;

  public InventoryWorkers(InventoryService inventory) {
    this.inventory = inventory;
  }

  @JobWorker(type = JobTypes.CHECK_INVENTORY)
  public Map<String, Object> checkInventory(@Variable(name = ProcessConstants.ORDER_ID) String orderId) {
    return Map.of(ProcessConstants.IN_STOCK, inventory.isAvailable(UUID.fromString(orderId)));
  }

  /** Stock can run out between the check and the reservation; that is a business error, not a failure. */
  @JobWorker(type = JobTypes.RESERVE_STOCK)
  public void reserveStock(@Variable(name = ProcessConstants.ORDER_ID) String orderId) {
    try {
      inventory.reserve(UUID.fromString(orderId));
    } catch (OutOfStockException e) {
      throw CamundaError.bpmnError(ProcessConstants.ERROR_OUT_OF_STOCK, e.getMessage());
    }
  }

  /** Compensation handler for "Reserve stock". */
  @JobWorker(type = JobTypes.RELEASE_STOCK)
  public void releaseStock(@Variable(name = ProcessConstants.ORDER_ID) String orderId) {
    inventory.release(UUID.fromString(orderId));
  }
}
