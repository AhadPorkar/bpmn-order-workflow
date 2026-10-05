#!/usr/bin/env bash
# Prints the order events that the outbox relay publishes to Kafka.
set -euo pipefail
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-events --from-beginning \
  --property print.key=true --property key.separator=" | "
