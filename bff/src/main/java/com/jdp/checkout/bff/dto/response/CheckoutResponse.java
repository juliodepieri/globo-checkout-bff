package com.jdp.checkout.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Data for the success screen. Card payments carry payment.lastDigits; Pix carries payment.pixCode.")
public record CheckoutResponse(UUID cartId, CheckoutStatus status, Instant paidAt, PaymentView payment,
                               List<PaidProductView> products) {

    /** Card payments carry {@code lastDigits}; Pix carries {@code pixCode} instead. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentView(String method, String lastDigits, String pixCode, BigDecimal amount, String currency) {
    }

    public record PaidProductView(String slug, String name, BigDecimal price) {
    }
}
