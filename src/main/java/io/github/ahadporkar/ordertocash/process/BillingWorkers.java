package io.github.ahadporkar.ordertocash.process;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.github.ahadporkar.ordertocash.billing.BillingService;
import io.github.ahadporkar.ordertocash.billing.Invoice;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BillingWorkers {

  private static final String DEFAULT_PAYMENT_TERM = "P14D";

  private final BillingService billing;

  public BillingWorkers(BillingService billing) {
    this.billing = billing;
  }

  /** Uses the payment term that the {@code order-approval} decision put into {@code approval}. */
  @JobWorker(type = JobTypes.CREATE_INVOICE)
  public Map<String, Object> createInvoice(
      @Variable(name = ProcessConstants.ORDER_ID) String orderId,
      @Variable(name = ProcessConstants.APPROVAL) Map<String, Object> approval) {
    String paymentTerm = approval == null
        ? DEFAULT_PAYMENT_TERM
        : String.valueOf(approval.getOrDefault("paymentTerm", DEFAULT_PAYMENT_TERM));
    Invoice invoice = billing.createInvoice(UUID.fromString(orderId), paymentTerm);
    return Map.of(ProcessConstants.INVOICE_ID, invoice.id().toString());
  }

  /** Compensation handler for "Create invoice". */
  @JobWorker(type = JobTypes.CANCEL_INVOICE)
  public void cancelInvoice(@Variable(name = ProcessConstants.ORDER_ID) String orderId) {
    billing.cancelInvoice(UUID.fromString(orderId));
  }
}
