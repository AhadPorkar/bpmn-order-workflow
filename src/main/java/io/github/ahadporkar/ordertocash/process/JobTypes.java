package io.github.ahadporkar.ordertocash.process;

/** Job types of the service tasks in {@code order-to-cash.bpmn}. */
public final class JobTypes {

  public static final String CHECK_INVENTORY = "inventory.check";
  public static final String RESERVE_STOCK = "inventory.reserve";
  public static final String RELEASE_STOCK = "inventory.release";
  public static final String CREATE_INVOICE = "invoice.create";
  public static final String CANCEL_INVOICE = "invoice.cancel";
  public static final String SHIP_ORDER = "shipping.ship";
  public static final String CANCEL_ORDER = "order.cancel";
  public static final String NOTIFY_CUSTOMER = "customer.notify";

  private JobTypes() {}
}
