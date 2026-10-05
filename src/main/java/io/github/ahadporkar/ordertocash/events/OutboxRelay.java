package io.github.ahadporkar.ordertocash.events;

import io.github.ahadporkar.ordertocash.config.AppProperties;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka. Delivery is at least once: if the process dies between the send
 * and the commit, the event is sent again, so consumers deduplicate by event type and order id.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;

  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final String topic;

  public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, AppProperties properties) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.topic = properties.kafka().eventsTopic();
  }

  @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval-ms:1000}")
  @Transactional
  public void relay() throws Exception {
    List<OutboxEntry> batch = outbox.lockUnpublished(BATCH_SIZE);
    for (OutboxEntry entry : batch) {
      kafka.send(topic, entry.aggregateId().toString(), entry.payload()).get(10, TimeUnit.SECONDS);
      outbox.markPublished(entry.id());
    }
    if (!batch.isEmpty()) {
      log.debug("Published {} order events to {}", batch.size(), topic);
    }
  }
}
