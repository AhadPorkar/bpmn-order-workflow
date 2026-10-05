package io.github.ahadporkar.ordertocash.process;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ClientStatusException;
import io.github.ahadporkar.ordertocash.config.AppProperties;
import io.github.ahadporkar.ordertocash.payment.PaymentEvent;
import io.grpc.Status;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a payment confirmation into the BPMN message {@code PaymentReceived}.
 *
 * <ul>
 *   <li>The correlation key is the order id, so the message finds the right instance.
 *   <li>The message id is the payment id. The engine rejects a second message with the same id,
 *       which makes redelivered payments harmless.
 *   <li>A time to live keeps an early payment buffered until the instance reaches the receive task.
 * </ul>
 */
@Component
public class PaymentCorrelator {

  private static final Logger log = LoggerFactory.getLogger(PaymentCorrelator.class);
  private static final int HTTP_CONFLICT = 409;

  private final CamundaClient camunda;
  private final Duration timeToLive;

  public PaymentCorrelator(CamundaClient camunda, AppProperties properties) {
    this.camunda = camunda;
    this.timeToLive = properties.payments().messageTimeToLive();
  }

  /** Returns false if the same payment had already been published. */
  public boolean correlate(PaymentEvent payment) {
    try {
      camunda.newPublishMessageCommand()
          .messageName(ProcessConstants.MESSAGE_PAYMENT_RECEIVED)
          .correlationKey(payment.orderId().toString())
          .messageId(payment.paymentId())
          .timeToLive(timeToLive)
          .variables(Map.of(
              ProcessConstants.PAYMENT_ID, payment.paymentId(),
              ProcessConstants.PAID_AMOUNT, payment.amount()))
          .send()
          .join();
      log.info("Published payment {} for order {}", payment.paymentId(), payment.orderId());
      return true;
    } catch (ClientHttpException e) {
      if (e.code() == HTTP_CONFLICT) {
        log.info("Payment {} was already published; ignoring the duplicate", payment.paymentId());
        return false;
      }
      throw e;
    } catch (ClientStatusException e) {
      if (e.getStatusCode() == Status.Code.ALREADY_EXISTS) {
        log.info("Payment {} was already published; ignoring the duplicate", payment.paymentId());
        return false;
      }
      throw e;
    }
  }
}
