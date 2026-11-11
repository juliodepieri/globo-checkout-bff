package com.jdp.checkout.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@Schema(description = "Payment of a cart created by the pre-checkout. "
        + "paymentMethod.name must be one of the methods offered for this cart; card is required for card methods "
        + "and must be absent for Pix. Card data is never stored, logged or published.")
public record CheckoutRequest(@NotNull UUID cartId, @NotNull @Valid PaymentMethodInput paymentMethod) {

    /** {@code card} is optional here: whether it is required depends on the method (business rule). */
    public record PaymentMethodInput(@NotBlank String name, @Valid CardInput card) {
    }

    public record CardInput(
            @NotBlank String holderName,
            @NotBlank @Pattern(regexp = "\\d{13,19}", message = "must have 13 to 19 digits") String number,
            @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "must be MM/YY") String expiry,
            @NotBlank @Pattern(regexp = "\\d{3,4}", message = "must have 3 or 4 digits") String cvv) {

        @Override
        public String toString() {
            return "CardInput[***]";
        }
    }
}
