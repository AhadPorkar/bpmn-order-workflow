package io.github.ahadporkar.ordertocash.process;

/** Names shared between the Java code and the BPMN/DMN models. Change both together. */
public final class ProcessConstants {

  public static final String PROCESS_ID = "order-to-cash";
  public static final String DECISION_ID = "order-approval";
  public static final String MESSAGE_PAYMENT_RECEIVED = "PaymentReceived";
  public static final String ERROR_OUT_OF_STOCK = "OUT_OF_STOCK";

  // Process variables
  public static final String ORDER_ID = "orderId";
  public static final String CUSTOMER_ID = "customerId";
  public static final String CUSTOMER_SEGMENT = "customerSegment";
  public static final String TOTAL_AMOUNT = "totalAmount";
  public static final String CURRENCY = "currency";
  public static final String IN_STOCK = "inStock";
  public static final String APPROVAL = "approval";
  public static final String INVOICE_ID = "invoiceId";
  public static final String PAYMENT_ID = "paymentId";
  public static final String PAID_AMOUNT = "paidAmount";
  public static final String OUTCOME = "outcome";

  private ProcessConstants() {}
}
