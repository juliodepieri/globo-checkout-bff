package com.jdp.checkout.bff.exception;

import com.jdp.checkout.bff.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.UUID;

/**
 * Guarantees the stable error contract: whatever goes wrong, the body is an {@link ErrorResponse}.
 * Extending ResponseEntityExceptionHandler covers every Spring MVC exception (invalid JSON,
 * bean validation, 404 route, 405, 415...) through {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final FrontendErrorResolver resolver;

    GlobalExceptionHandler(FrontendErrorResolver resolver) {
        this.resolver = resolver;
    }

    @ExceptionHandler(CheckoutException.class)
    ResponseEntity<ErrorResponse> handleCheckout(CheckoutException e) {
        switch (e) {
            case ExternalServiceException ex -> log.error("External service failure: {}", ex.getMessage(), ex);
            case BusinessException ex ->
                    log.info("Business failure code={} cartId={}: {}", ex.errorCode(), ex.cartId(), ex.getMessage());
        }
        var error = resolver.resolve(e.errorCode(), LocaleContextHolder.getLocale());
        return ResponseEntity.status(error.httpStatus())
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .body(body(e.errorCode(), e.cartId(), error));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        var error = resolver.resolve(ErrorCode.INTERNAL_ERROR, LocaleContextHolder.getLocale());
        return ResponseEntity.status(error.httpStatus())
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .body(body(ErrorCode.INTERNAL_ERROR, null, error));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object ignoredBody, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        log.info("Invalid request status={}: {}", status.value(), e.getMessage());
        var error = resolver.resolve(ErrorCode.INVALID_REQUEST, LocaleContextHolder.getLocale());
        // Keep the framework status (400/404/405/415) but always the same body.
        return ResponseEntity.status(status).headers(headers)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .body(body(ErrorCode.INVALID_REQUEST, null, error));
    }

    // The frontendError texts follow Accept-Language, hence "Vary: Accept-Language" on every error response.
    private static ErrorResponse body(ErrorCode code, UUID cartId, FrontendErrorResolver.ResolvedError error) {
        return new ErrorResponse(error.displayType(), code.name(), cartId, error.frontendError());
    }
}
