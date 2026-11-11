package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * What a {@link PaymentGateway} charges. Invariant enforced on construction: {@code card} is present
 * exactly when {@code method} requires one, so no gateway implementation has to null-check it.
 */
public record ChargeRequest(UUID cartId, PaymentMethod method, BigDecimal amount, String currency, CardData card) {

    public ChargeRequest {
        Objects.requireNonNull(cartId, "cartId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (method.requiresCard() && card == null) {
            throw new IllegalArgumentException("Card data is required for " + method.type());
        }
        if (!method.requiresCard() && card != null) {
            throw new IllegalArgumentException("Card data must not be sent for " + method.type());
        }
    }
}
