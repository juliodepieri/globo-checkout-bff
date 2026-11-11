package com.jdp.checkout.bff.exception;

/**
 * Every failure the frontend may need to render. {@link FrontendErrorResolver} maps each code to an
 * HTTP status and a {@code frontendError}; services throw codes and know nothing about HTTP.
 */
public enum ErrorCode {
    INVALID_REQUEST,
    PRODUCT_NOT_FOUND,
    CART_NOT_FOUND,
    PAYMENT_METHOD_NOT_ALLOWED,
    INVALID_PAYMENT_DATA,
    PAYMENT_DECLINED,
    CART_ALREADY_PAID,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR
}
