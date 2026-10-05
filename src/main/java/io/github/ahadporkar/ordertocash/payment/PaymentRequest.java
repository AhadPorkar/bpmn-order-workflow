package io.github.ahadporkar.ordertocash.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of the demo endpoint that simulates a payment provider. */
public record PaymentRequest(@NotNull @Positive BigDecimal amount, @Size(max = 64) String paymentId) {}
