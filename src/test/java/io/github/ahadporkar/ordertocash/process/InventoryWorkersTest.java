package io.github.ahadporkar.ordertocash.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.exception.BpmnError;
import io.github.ahadporkar.ordertocash.inventory.InventoryService;
import io.github.ahadporkar.ordertocash.inventory.OutOfStockException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryWorkersTest {

  private final InventoryService inventory = mock(InventoryService.class);
  private final InventoryWorkers workers = new InventoryWorkers(inventory);
  private final UUID orderId = UUID.randomUUID();

  @Test
  void checkReportsAvailabilityAsProcessVariable() {
    when(inventory.isAvailable(orderId)).thenReturn(true);

    assertThat(workers.checkInventory(orderId.toString())).containsEntry(ProcessConstants.IN_STOCK, true);
  }

  @Test
  void stockRunningOutBecomesABpmnError() {
    doThrow(new OutOfStockException("SKU-KEYBOARD", 3)).when(inventory).reserve(orderId);

    assertThatThrownBy(() -> workers.reserveStock(orderId.toString()))
        .isInstanceOfSatisfying(BpmnError.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ProcessConstants.ERROR_OUT_OF_STOCK));
  }

  @Test
  void technicalFailuresStayTechnical() {
    doThrow(new IllegalStateException("database unavailable")).when(inventory).reserve(orderId);

    // Not a BPMN error: the job fails, Camunda retries it and raises an incident when retries run out.
    assertThatThrownBy(() -> workers.reserveStock(orderId.toString())).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void releaseDelegatesToTheService() {
    workers.releaseStock(orderId.toString());

    verify(inventory).release(orderId);
  }
}
