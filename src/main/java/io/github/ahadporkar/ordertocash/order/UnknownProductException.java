package io.github.ahadporkar.ordertocash.order;

import java.io.Serial;
import java.util.Collection;

public class UnknownProductException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public UnknownProductException(Collection<String> skus) {
    super("Unknown products: " + String.join(", ", skus));
  }
}
