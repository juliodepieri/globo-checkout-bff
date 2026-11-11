package com.jdp.checkout.mock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class PaymentMethodsController {

    record PaymentMethodDto(String code, String displayName, boolean enabled) {
    }

    private static final List<PaymentMethodDto> METHODS = List.of(
            new PaymentMethodDto("CREDIT_CARD", "Cartão de Crédito", true),
            new PaymentMethodDto("DEBIT_CARD", "Cartão de Débito", true),
            new PaymentMethodDto("PIX", "Pix", true));

    @GetMapping("/payment-methods")
    List<PaymentMethodDto> paymentMethods() {
        return METHODS;
    }
}
