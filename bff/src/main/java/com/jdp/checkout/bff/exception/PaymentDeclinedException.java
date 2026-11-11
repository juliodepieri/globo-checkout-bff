package com.jdp.checkout.bff.exception;

import java.util.UUID;

/**
 * Dedicated subtype so the checkout transaction can commit the declined attempt
 * ({@code noRollbackFor}) while still answering the frontend with an error.
 */
public final class PaymentDeclinedException extends BusinessException {

    public PaymentDeclinedException(UUID cartId, String reason) {
        super(ErrorCode.PAYMENT_DECLINED, cartId, "Payment declined: " + reason);
    }
}
