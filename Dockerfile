# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/bpmn-order-workflow-*.jar extract --layers --launcher --destination /extracted

# ---- run -------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/AhadPorkar/bpmn-order-workflow" \
      org.opencontainers.image.description="Order-to-cash service on Camunda 8" \
      org.opencontainers.image.licenses="MIT"

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Layers ordered from least to most frequently changed, so rebuilds reuse the dependency layer.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER app
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD wget -qO- http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
