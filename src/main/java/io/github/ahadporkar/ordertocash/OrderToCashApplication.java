package io.github.ahadporkar.ordertocash;

import io.camunda.client.annotation.Deployment;
import io.github.ahadporkar.ordertocash.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order-to-cash service.
 *
 * <p>On startup the BPMN process, the DMN decision and the Camunda form are deployed to the
 * orchestration cluster. Deployments are idempotent: an unchanged resource does not create a new
 * version.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
@Deployment(resources = {"classpath*:bpmn/*.bpmn", "classpath*:dmn/*.dmn", "classpath*:forms/*.form"})
public class OrderToCashApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderToCashApplication.class, args);
  }
}
