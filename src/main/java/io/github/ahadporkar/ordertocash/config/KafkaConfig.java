package io.github.ahadporkar.ordertocash.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Topics and error handling. Only active when messaging runs over Kafka. */
@Configuration
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaConfig {

  @Bean
  NewTopic paymentsTopic(AppProperties properties) {
    return TopicBuilder.name(properties.kafka().paymentsTopic()).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic paymentsDeadLetterTopic(AppProperties properties) {
    return TopicBuilder.name(properties.kafka().paymentsTopic() + "-dlt").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic orderEventsTopic(AppProperties properties) {
    return TopicBuilder.name(properties.kafka().eventsTopic()).partitions(3).replicas(1).build();
  }

  /**
   * Three retries one second apart, then the record goes to {@code payments-dlt}. A record that is
   * not valid JSON goes there immediately: retrying cannot fix it.
   */
  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, AppProperties properties) {
    String deadLetterTopic = properties.kafka().paymentsTopic() + "-dlt";
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
        (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.addNotRetryableExceptions(JsonProcessingException.class);
    return handler;
  }
}
