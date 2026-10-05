package io.github.ahadporkar.ordertocash.billing;

import io.github.ahadporkar.ordertocash.events.OrderEventRecorder;
import io.github.ahadporkar.ordertocash.order.Order;
import io.github.ahadporkar.ordertocash.order.OrderNotFoundException;
import io.github.ahadporkar.ordertocash.order.OrderRepository;
import io.github.ahadporkar.ordertocash.order.OrderStatus;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invoicing. Idempotent per order: one order never gets two invoices. */
@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);

  private final InvoiceRepository invoices;
  private final OrderRepository orders;
  private final OrderEventRecorder events;

  public BillingService(InvoiceRepository invoices, OrderRepository orders, OrderEventRecorder events) {
    this.invoices = invoices;
    this.orders = orders;
    this.events = events;
  }

  @Transactional
  public Invoice createInvoice(UUID orderId, String paymentTerm) {
    return invoices.findByOrderId(orderId).orElseGet(() -> {
      Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
      Invoice invoice = new Invoice(UUID.randomUUID(), orderId, order.totalAmount(), order.currency(),
          paymentTerm, Invoice.Status.ISSUED);
      invoices.insert(invoice);
      orders.setInvoice(orderId, invoice.id());
      orders.updateStatus(orderId, OrderStatus.AWAITING_PAYMENT);
      events.record("OrderInvoiced", orderId, OrderStatus.AWAITING_PAYMENT.name(), Map.of(
          "invoiceId", invoice.id().toString(),
          "amount", invoice.amount(),
          "currency", invoice.currency(),
          "paymentTerm", paymentTerm));
      log.info("Issued invoice {} for order {}", invoice.id(), orderId);
      return invoice;
    });
  }

  /** Compensation: cancels the invoice of an order. Safe to call more than once. */
  @Transactional
  public void cancelInvoice(UUID orderId) {
    if (invoices.cancel(orderId)) {
      events.record("InvoiceCancelled", orderId, null, Map.of());
      log.info("Cancelled invoice of order {}", orderId);
    }
  }
}
