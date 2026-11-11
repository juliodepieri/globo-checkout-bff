package com.jdp.checkout.bff.exception;

import java.util.UUID;

/**
 * Root of the expected failures. Carries the error code (mapped by {@link FrontendErrorResolver}) and, when known,
 * the cart. Sealed so {@link GlobalExceptionHandler} can tell the categories apart with an exhaustive switch:
 * a {@link BusinessException} is a broken rule or bad input, an {@link ExternalServiceException} is a dependency down.
 */
public sealed abstract class CheckoutException extends RuntimeException
        permits BusinessException, ExternalServiceException {

    private final ErrorCode errorCode;
    private final UUID cartId;

    protected CheckoutException(ErrorCode errorCode, UUID cartId, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.cartId = cartId;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public UUID cartId() {
        return cartId;
    }
}
