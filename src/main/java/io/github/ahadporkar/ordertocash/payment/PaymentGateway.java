package io.github.ahadporkar.ordertocash.payment;

/** Where confirmed payments go. Kafka in normal operation, in-process in {@code direct} mode. */
public interface PaymentGateway {

  void submit(PaymentEvent payment);
}
