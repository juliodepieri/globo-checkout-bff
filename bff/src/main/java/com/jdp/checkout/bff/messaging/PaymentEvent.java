package com.jdp.checkout.bff.messaging;

import com.jdp.checkout.bff.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Integration event published after each payment attempt. Never contains card data. */
public record PaymentEvent(
        UUID eventId,
        String type,
        UUID cartId,
        UUID paymentId,
        String status,
        String method,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {

    public static final String APPROVED = "payment.approved";
    public static final String DECLINED = "payment.declined";

    public static PaymentEvent from(Payment payment) {
        return new PaymentEvent(
                UUID.randomUUID(),
                payment.approved() ? APPROVED : DECLINED,
                payment.cartId(),
                payment.id(),
                payment.status().name(),
                payment.method().type().name(),
                payment.amount(),
                payment.currency(),
                payment.processedAt());
    }
}
