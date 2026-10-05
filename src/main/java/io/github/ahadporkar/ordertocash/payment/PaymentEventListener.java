package io.github.ahadporkar.ordertocash.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahadporkar.ordertocash.process.PaymentCorrelator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment confirmations and correlates them with waiting process instances. Kafka may
 * redeliver a record; the message id in {@link PaymentCorrelator} turns that into a no-op.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class PaymentEventListener {

  private final PaymentCorrelator correlator;
  private final ObjectMapper objectMapper;

  public PaymentEventListener(PaymentCorrelator correlator, ObjectMapper objectMapper) {
    this.correlator = correlator;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "${app.kafka.payments-topic:payments}", groupId = "order-to-cash")
  public void onPayment(String message) throws JsonProcessingException {
    correlator.correlate(objectMapper.readValue(message, PaymentEvent.class));
  }
}
