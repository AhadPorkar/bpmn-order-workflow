package io.github.ahadporkar.ordertocash.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A payment confirmation as the payment provider publishes it on the {@code payments} topic. */
public record PaymentEvent(String paymentId, UUID orderId, BigDecimal amount, OffsetDateTime paidAt) {}
