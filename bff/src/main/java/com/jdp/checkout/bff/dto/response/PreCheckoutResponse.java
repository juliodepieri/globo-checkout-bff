package com.jdp.checkout.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Everything the payment screen needs. Prices and payment methods are frozen in the cart.")
public record PreCheckoutResponse(UUID cartId, List<ProductView> products, List<PaymentMethodView> paymentMethods) {

    public record ProductView(String slug, String name, String description, BigDecimal price, String currency) {
    }

    public record PaymentMethodView(String name) {
    }
}
