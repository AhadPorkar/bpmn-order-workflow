package io.github.ahadporkar.ordertocash.payment;

import io.github.ahadporkar.ordertocash.process.PaymentCorrelator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code app.messaging.mode=direct}: correlates payments without Kafka. For demos and tests. */
@Component
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "direct")
public class DirectPaymentGateway implements PaymentGateway {

  private final PaymentCorrelator correlator;

  public DirectPaymentGateway(PaymentCorrelator correlator) {
    this.correlator = correlator;
  }

  @Override
  public void submit(PaymentEvent payment) {
    correlator.correlate(payment);
  }
}
