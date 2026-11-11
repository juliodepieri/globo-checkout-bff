package com.jdp.checkout.bff.model;

import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodTest {

    private final UUID cartId = UUID.randomUUID();
    private final YearMonth now = YearMonth.of(2026, 10);

    @Test
    void cardMethodAcceptsValidCard() {
        assertThatCode(() -> CREDIT.ensureValidPaymentData(cartId, APPROVED_CARD, now)).doesNotThrowAnyException();
    }

    @Test
    void pixAcceptsNoCard() {
        assertThatCode(() -> PIX.ensureValidPaymentData(cartId, null, now)).doesNotThrowAnyException();
    }

    @Test
    void cardMethodRequiresCard() {
        assertInvalid(() -> DEBIT.ensureValidPaymentData(cartId, null, now));
    }

    @Test
    void pixRejectsCard() {
        assertInvalid(() -> PIX.ensureValidPaymentData(cartId, APPROVED_CARD, now));
    }

    @Test
    void expiredCardIsRejected() {
        var expired = new CardData("Ana Souza", "4111111111114242", "09/26", "123");
        assertInvalid(() -> CREDIT.ensureValidPaymentData(cartId, expired, now));
    }

    private void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_PAYMENT_DATA);
    }
}
