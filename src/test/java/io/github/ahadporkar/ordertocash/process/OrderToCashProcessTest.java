package io.github.ahadporkar.ordertocash.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.github.ahadporkar.ordertocash.config.AppProperties;
import io.github.ahadporkar.ordertocash.payment.PaymentEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the BPMN model and the DMN decision on a real Camunda engine (Testcontainers), with mocked
 * job workers. These tests document the process: each one is a path a real order can take.
 */
@CamundaProcessTest
class OrderToCashProcessTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void deployModels() {
    client.newDeployResourceCommand()
        .addResourceFromClasspath("bpmn/order-to-cash.bpmn")
        .addResourceFromClasspath("dmn/order-approval.dmn")
        .addResourceFromClasspath("forms/approve-order.form")
        .send()
        .join();
  }

  @Test
  @DisplayName("A standard order is approved automatically and shipped after payment")
  void standardOrderIsShippedAfterPayment() {
    mockHappyPathWorkers();
    String orderId = UUID.randomUUID().toString();

    ProcessInstanceEvent instance = startOrder(orderId, "STANDARD", 480);

    assertThat(instance)
        .isWaitingForMessage(ProcessConstants.MESSAGE_PAYMENT_RECEIVED, orderId)
        .hasVariableSatisfies(ProcessConstants.APPROVAL, Map.class, approval -> {
          assertThat(approval.get("approvalRequired")).isEqualTo(false);
          assertThat(approval.get("paymentTerm")).isEqualTo("P14D");
        });

    publishPayment(orderId, "pay-" + orderId);

    assertThat(instance)
        .isCompleted()
        .hasCompletedElementsInOrder(
            "task_check_inventory", "task_decide_approval", "task_reserve_stock",
            "task_create_invoice", "task_wait_for_payment", "task_ship_order", "end_completed")
        .hasNotActivatedElements("task_approve_order", "task_release_stock", "task_cancel_invoice")
        .hasVariable(ProcessConstants.PAYMENT_ID, "pay-" + orderId);
  }

  @Test
  @DisplayName("A large order from a gold customer waits for the sales team")
  void largeGoldOrderNeedsApproval() {
    mockHappyPathWorkers();
    String orderId = UUID.randomUUID().toString();

    ProcessInstanceEvent instance = startOrder(orderId, "GOLD", 62_500);

    assertThatUserTask(UserTaskSelectors.byElementId("task_approve_order"))
        .isCreated()
        .hasCandidateGroup("sales");

    processTestContext.completeUserTask("task_approve_order", Map.of("approved", true));

    assertThat(instance)
        .isWaitingForMessage(ProcessConstants.MESSAGE_PAYMENT_RECEIVED, orderId)
        .hasCompletedElements("task_approve_order", "task_reserve_stock", "task_create_invoice");
  }

  @Test
  @DisplayName("A declined order informs the customer and ends")
  void declinedOrderEndsDeclined() {
    processTestContext.mockJobWorker(JobTypes.CHECK_INVENTORY).thenComplete(Map.of(ProcessConstants.IN_STOCK, true));
    JobWorkerMock notify = processTestContext.mockJobWorker(JobTypes.NOTIFY_CUSTOMER).thenComplete();
    String orderId = UUID.randomUUID().toString();

    ProcessInstanceEvent instance = startOrder(orderId, "NEW", 1_890);
    processTestContext.completeUserTask("task_approve_order",
        Map.of("approved", false, "approvalComment", "no credit history"));

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("task_notify_declined", "end_declined")
        .hasNotActivatedElements("task_reserve_stock");
    assertThat(notify.getActivatedJobs()).hasSize(1);
    assertThat(notify.getActivatedJobs().get(0).getVariablesAsMap())
        .containsEntry(ProcessConstants.OUTCOME, "DECLINED");
  }

  @Test
  @DisplayName("An order for a product without stock is rejected")
  void soldOutOrderIsRejected() {
    processTestContext.mockJobWorker(JobTypes.CHECK_INVENTORY).thenComplete(Map.of(ProcessConstants.IN_STOCK, false));
    JobWorkerMock notify = processTestContext.mockJobWorker(JobTypes.NOTIFY_CUSTOMER).thenComplete();

    ProcessInstanceEvent instance = startOrder(UUID.randomUUID().toString(), "STANDARD", 199);

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("task_notify_out_of_stock", "end_rejected")
        .hasNotActivatedElements("task_decide_approval");
    assertThat(notify.getActivatedJobs().get(0).getVariablesAsMap())
        .containsEntry(ProcessConstants.OUTCOME, "REJECTED_OUT_OF_STOCK");
  }

  @Test
  @DisplayName("Stock that runs out between check and reservation raises a BPMN error")
  void stockRunningOutDuringReservationRejectsTheOrder() {
    processTestContext.mockJobWorker(JobTypes.CHECK_INVENTORY).thenComplete(Map.of(ProcessConstants.IN_STOCK, true));
    processTestContext.mockJobWorker(JobTypes.RESERVE_STOCK).thenThrowBpmnError(ProcessConstants.ERROR_OUT_OF_STOCK);
    processTestContext.mockJobWorker(JobTypes.NOTIFY_CUSTOMER).thenComplete();

    ProcessInstanceEvent instance = startOrder(UUID.randomUUID().toString(), "STANDARD", 89);

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("boundary_out_of_stock", "task_notify_out_of_stock", "end_rejected")
        .hasNotActivatedElements("task_create_invoice");
  }

  @Test
  @DisplayName("An unpaid order gets a reminder, then reservation and invoice are compensated")
  void unpaidOrderIsCompensatedAfterTheDeadline() {
    mockHappyPathWorkers();
    JobWorkerMock release = processTestContext.mockJobWorker(JobTypes.RELEASE_STOCK).thenComplete();
    JobWorkerMock cancelInvoice = processTestContext.mockJobWorker(JobTypes.CANCEL_INVOICE).thenComplete();
    processTestContext.mockJobWorker(JobTypes.CANCEL_ORDER).thenComplete();
    String orderId = UUID.randomUUID().toString();

    ProcessInstanceEvent instance = startOrder(orderId, "STANDARD", 750);
    assertThat(instance).isWaitingForMessage(ProcessConstants.MESSAGE_PAYMENT_RECEIVED, orderId);

    // STANDARD customers: reminder after 10 days, deadline after 14 days (decision table)
    processTestContext.increaseTime(Duration.ofDays(10).plusMinutes(1));
    assertThat(instance)
        .isActive()
        .hasCompletedElements("boundary_payment_reminder", "task_send_reminder", "end_reminder_sent")
        .hasActiveElements("task_wait_for_payment");

    processTestContext.increaseTime(Duration.ofDays(4));
    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("boundary_payment_deadline", "throw_compensate_order",
            "task_release_stock", "task_cancel_invoice", "task_cancel_order", "end_cancelled")
        .hasNotActivatedElements("task_ship_order");
    assertThat(release.getInvocations()).isEqualTo(1);
    assertThat(cancelInvoice.getInvocations()).isEqualTo(1);
  }

  @Test
  @DisplayName("A payment that arrives early is buffered until the order waits for it")
  void earlyPaymentIsBuffered() {
    String orderId = UUID.randomUUID().toString();
    // Workers are registered only after the payment, so the instance is certainly not waiting yet.
    publishPayment(orderId, "pay-early-" + orderId);
    ProcessInstanceEvent instance = startOrder(orderId, "STANDARD", 120);
    mockHappyPathWorkers();

    assertThat(instance)
        .isCompleted()
        .hasCorrelatedMessage(ProcessConstants.MESSAGE_PAYMENT_RECEIVED, orderId)
        .hasCompletedElements("task_ship_order", "end_completed");
  }

  @Test
  @DisplayName("A payment delivered twice is correlated once and the order ships once")
  void duplicatePaymentIsIgnored() {
    JobWorkerMock ship = mockHappyPathWorkers();
    UUID orderId = UUID.randomUUID();
    ProcessInstanceEvent instance = startOrder(orderId.toString(), "STANDARD", 217);
    assertThat(instance).isWaitingForMessage(ProcessConstants.MESSAGE_PAYMENT_RECEIVED, orderId.toString());

    // The production correlator, against the real engine: the payment id is the message id.
    PaymentCorrelator correlator = new PaymentCorrelator(client, new AppProperties(null, null, null));
    PaymentEvent payment = new PaymentEvent("pay-twice-" + orderId, orderId, new BigDecimal("217.00"), OffsetDateTime.now());

    assertThat(correlator.correlate(payment)).isTrue();
    assertThat(correlator.correlate(payment)).isFalse(); // a redelivery, for example after a consumer restart

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("task_ship_order", "end_completed");
    assertThat(ship.getInvocations()).isEqualTo(1);
  }

  /** Mocks every worker of the happy path and returns the shipping worker. */
  private JobWorkerMock mockHappyPathWorkers() {
    processTestContext.mockJobWorker(JobTypes.CHECK_INVENTORY).thenComplete(Map.of(ProcessConstants.IN_STOCK, true));
    processTestContext.mockJobWorker(JobTypes.RESERVE_STOCK).thenComplete();
    processTestContext.mockJobWorker(JobTypes.CREATE_INVOICE)
        .thenComplete(Map.of(ProcessConstants.INVOICE_ID, UUID.randomUUID().toString()));
    processTestContext.mockJobWorker(JobTypes.NOTIFY_CUSTOMER).thenComplete();
    return processTestContext.mockJobWorker(JobTypes.SHIP_ORDER).thenComplete();
  }

  private ProcessInstanceEvent startOrder(String orderId, String segment, double totalAmount) {
    return client.newCreateInstanceCommand()
        .bpmnProcessId(ProcessConstants.PROCESS_ID)
        .latestVersion()
        .variables(Map.of(
            ProcessConstants.ORDER_ID, orderId,
            ProcessConstants.CUSTOMER_ID, "customer-" + orderId,
            ProcessConstants.CUSTOMER_SEGMENT, segment,
            ProcessConstants.TOTAL_AMOUNT, totalAmount,
            ProcessConstants.CURRENCY, "EUR"))
        .send()
        .join();
  }

  private void publishPayment(String orderId, String paymentId) {
    client.newPublishMessageCommand()
        .messageName(ProcessConstants.MESSAGE_PAYMENT_RECEIVED)
        .correlationKey(orderId)
        .messageId(paymentId)
        .timeToLive(Duration.ofHours(1))
        .variables(Map.of(ProcessConstants.PAYMENT_ID, paymentId))
        .send()
        .join();
  }
}
