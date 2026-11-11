package com.jdp.checkout.bff.entity;

import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.model.CartStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    @Test
    void totalIsTheSumOfProductPrices() {
        var cart = Cart.open(List.of(PREMIUM, FAMILIA), METHODS, NOW);

        assertThat(cart.total()).isEqualByComparingTo(new BigDecimal("99.80"));
        assertThat(cart.status()).isEqualTo(CartStatus.OPEN);
    }

    @Test
    void resolvesOnlyOfferedPaymentMethods() {
        var cart = Cart.open(List.of(PREMIUM), List.of(PIX), NOW);

        assertThat(cart.requireOfferedPaymentMethod("pix")).isEqualTo(PIX);
        assertThatThrownBy(() -> cart.requireOfferedPaymentMethod("Cartão de Crédito"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_ALLOWED);
    }

    @Test
    void declinedCartCanBePaidAgainButApprovedCannot() {
        var cart = premiumCart();

        cart.decline(NOW);
        cart.approve(NOW);

        assertThat(cart.status()).isEqualTo(CartStatus.APPROVED);
        assertThatThrownBy(() -> cart.approve(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CART_ALREADY_PAID);
    }

    @Test
    void cardToStringNeverExposesSensitiveData() {
        assertThat(APPROVED_CARD.toString())
                .doesNotContain("4111111111114242", "123", "Ana Souza")
                .contains("4242");
    }
}
