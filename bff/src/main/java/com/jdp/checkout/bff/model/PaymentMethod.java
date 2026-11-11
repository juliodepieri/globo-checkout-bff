package com.jdp.checkout.bff.model;

import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.UUID;

/** {@code name} is the label shown (and sent back) by the frontend, e.g. "Cartão de Crédito". */
public record PaymentMethod(PaymentMethodType type, String name) implements Serializable {

    /** Exhaustive switch: a new {@link PaymentMethodType} does not compile until it says what data it needs. */
    public boolean requiresCard() {
        return switch (type) {
            case CREDIT_CARD, DEBIT_CARD -> true;
            case PIX -> false;
        };
    }

    /**
     * Checks that the payment data sent by the frontend fits this method: card methods need a valid,
     * non-expired card; the others must not receive one.
     *
     * @throws BusinessException {@link ErrorCode#INVALID_PAYMENT_DATA} when it does not
     */
    public void ensureValidPaymentData(UUID cartId, CardData card, YearMonth reference) {
        if (requiresCard() && card == null) {
            throw invalid(cartId, "Card data is required for " + name);
        }
        if (!requiresCard() && card != null) {
            throw invalid(cartId, "Card data must not be sent for " + name);
        }
        if (card != null && card.isExpiredAt(reference)) {
            throw invalid(cartId, "Card is expired");
        }
    }

    private static BusinessException invalid(UUID cartId, String message) {
        return new BusinessException(ErrorCode.INVALID_PAYMENT_DATA, cartId, message);
    }
}
