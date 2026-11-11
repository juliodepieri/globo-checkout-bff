package com.jdp.checkout.bff.entity;

import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.PaymentMethodType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Payment method offered for a cart (row of {@code cart_payment_method}). */
@Embeddable
public class CartPaymentMethod {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodType type;
    @Column(nullable = false)
    private String name;

    protected CartPaymentMethod() {
    }

    static CartPaymentMethod of(PaymentMethod m) {
        var method = new CartPaymentMethod();
        method.type = m.type();
        method.name = m.name();
        return method;
    }

    PaymentMethod toPaymentMethod() {
        return new PaymentMethod(type, name);
    }
}
