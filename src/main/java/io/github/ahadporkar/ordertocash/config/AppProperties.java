package io.github.ahadporkar.ordertocash.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under the {@code app} prefix.
 *
 * @param messaging how payments and domain events travel
 * @param kafka topic names
 * @param payments message correlation settings
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(Messaging messaging, Kafka kafka, Payments payments) {

  public AppProperties {
    messaging = messaging == null ? new Messaging(MessagingMode.KAFKA) : messaging;
    kafka = kafka == null ? new Kafka("payments", "order-events", 1000) : kafka;
    payments = payments == null ? new Payments(Duration.ofHours(1)) : payments;
  }

  /** {@code kafka}: payments and events go through Kafka. {@code direct}: in-process, for demos. */
  public enum MessagingMode {
    KAFKA,
    DIRECT
  }

  public record Messaging(MessagingMode mode) {}

  /**
   * @param paymentsTopic inbound payment confirmations from the payment provider
   * @param eventsTopic outbound order events, published from the outbox
   * @param outboxPollIntervalMs how often the outbox relay looks for unpublished events
   */
  public record Kafka(@NotBlank String paymentsTopic, @NotBlank String eventsTopic, long outboxPollIntervalMs) {}

  /**
   * @param messageTimeToLive how long a payment message waits in the engine if the order is not yet
   *     waiting for it (for example, a fast payment that arrives while the invoice is still being
   *     created)
   */
  public record Payments(Duration messageTimeToLive) {}
}
