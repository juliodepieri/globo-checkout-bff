package com.jdp.checkout.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * The ONE error contract. Every failure (business, validation, upstream down, unexpected) uses it.
 * {@code cartId} is always present in the JSON (null before a cart exists) so the shape never varies.
 */
@Schema(description = "The single error contract: every failure of every endpoint has this shape. "
        + "cartId is always present (null when no cart is involved); frontendError texts follow Accept-Language.")
public record ErrorResponse(DisplayType displayType, String errorCode, UUID cartId, FrontendError frontendError) {

    public record FrontendError(String code, String title, String description, Button primaryButton,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Button secondaryButton) {
    }

    /** {@code url}: a frontend route, or {@code RETRY} to resend the same request. */
    public record Button(String label, String url) {
    }
}
