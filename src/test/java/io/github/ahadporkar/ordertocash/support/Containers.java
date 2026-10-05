package io.github.ahadporkar.ordertocash.support;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Container images shared by the integration tests. Same versions as docker-compose.yml. */
public final class Containers {

  public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:16-alpine");
  public static final DockerImageName KAFKA = DockerImageName.parse("apache/kafka:3.9.1");

  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(POSTGRES).withDatabaseName("orders").withUsername("orders").withPassword("orders");
  }

  public static KafkaContainer kafka() {
    return new KafkaContainer(KAFKA);
  }

  private Containers() {}
}
