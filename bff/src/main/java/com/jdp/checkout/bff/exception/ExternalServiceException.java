package com.jdp.checkout.bff.exception;

/**
 * An external dependency (catalog, payment methods) failed or answered with nothing usable.
 * Always {@link ErrorCode#SERVICE_UNAVAILABLE} and logged at error; the place to hook retries or a circuit breaker.
 */
public final class ExternalServiceException extends CheckoutException {

    public ExternalServiceException(String message) {
        this(message, null);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, null, message, cause);
    }
}
