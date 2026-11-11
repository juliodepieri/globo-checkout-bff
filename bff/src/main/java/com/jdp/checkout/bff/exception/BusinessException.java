package com.jdp.checkout.bff.exception;

import java.util.UUID;

/** A business rule was broken or the input is invalid. Expected, so it is logged at info. */
public sealed class BusinessException extends CheckoutException permits PaymentDeclinedException {

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, null, message);
    }

    public BusinessException(ErrorCode errorCode, UUID cartId, String message) {
        this(errorCode, cartId, message, null);
    }

    public BusinessException(ErrorCode errorCode, UUID cartId, String message, Throwable cause) {
        super(errorCode, cartId, message, cause);
    }
}
