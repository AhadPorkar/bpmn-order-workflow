package io.github.ahadporkar.ordertocash.order;

import java.io.Serial;

public class OrchestrationUnavailableException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public OrchestrationUnavailableException(Throwable cause) {
    super("The order could not be started because the process engine is not reachable", cause);
  }
}
