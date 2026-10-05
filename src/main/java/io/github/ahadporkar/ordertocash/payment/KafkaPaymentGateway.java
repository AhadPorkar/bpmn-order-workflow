package io.github.ahadporkar.ordertocash.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahadporkar.ordertocash.config.AppProperties;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Stands in for an external payment provider: the demo endpoint publishes the confirmation to the
 * {@code payments} topic, exactly as a provider integration would.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaPaymentGateway implements PaymentGateway {

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final String topic;

  public KafkaPaymentGateway(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, AppProperties properties) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.topic = properties.kafka().paymentsTopic();
  }

  @Override
  public void submit(PaymentEvent payment) {
    try {
      kafka.send(topic, payment.orderId().toString(), objectMapper.writeValueAsString(payment))
          .get(10, TimeUnit.SECONDS);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize " + payment, e);
    } catch (Exception e) {
      throw new IllegalStateException("Could not publish payment " + payment.paymentId(), e);
    }
  }
}
