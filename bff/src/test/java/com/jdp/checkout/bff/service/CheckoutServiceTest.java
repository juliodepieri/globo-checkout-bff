package com.jdp.checkout.bff.service;

import com.jdp.checkout.bff.service.CheckoutService.CheckoutCommand;
import com.jdp.checkout.bff.repository.CartRepository;
import com.jdp.checkout.bff.messaging.PaymentEventPublisher;
import com.jdp.checkout.bff.client.PaymentGateway;
import com.jdp.checkout.bff.repository.PaymentRepository;
import com.jdp.checkout.bff.messaging.PaymentEvent;
import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.PaymentDeclinedException;
import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.model.CartStatus;
import com.jdp.checkout.bff.model.ChargeResult;
import com.jdp.checkout.bff.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock CartRepository carts;
    @Mock PaymentRepository payments;
    @Mock PaymentGateway gateway;
    @Mock PaymentEventPublisher events;

    CheckoutService service;
    Cart cart;

    private void paymentsAreSaved() {
        when(payments.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @BeforeEach
    void setUp() {
        service = new CheckoutService(carts, payments, gateway, events, CLOCK);
        cart = premiumCart();
    }

    private void cartExists() {
        when(carts.findByIdForUpdate(cart.id())).thenReturn(Optional.of(cart));
    }

    @Test
    void approvedCardPaymentReturnsLastDigitsAndPublishesEvent() {
        cartExists();
        paymentsAreSaved();
        when(gateway.charge(any())).thenReturn(ChargeResult.approvedCard("4242"));

        var result = service.checkout(new CheckoutCommand(cart.id(), "Cartão de Crédito", APPROVED_CARD));

        assertThat(result.cart().status()).isEqualTo(CartStatus.APPROVED);
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.payment().lastDigits()).isEqualTo("4242");
        assertThat(result.payment().amount()).isEqualByComparingTo("39.90");
        assertThat(result.payment().processedAt()).isEqualTo(NOW);
        var event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(PaymentEvent.APPROVED);
    }

    @Test
    void approvedPixPaymentReturnsPixCodeInsteadOfLastDigits() {
        cartExists();
        paymentsAreSaved();
        when(gateway.charge(any())).thenReturn(ChargeResult.approvedPix("000201PIX"));

        var result = service.checkout(new CheckoutCommand(cart.id(), "Pix", null));

        assertThat(result.payment().pixCode()).isEqualTo("000201PIX");
        assertThat(result.payment().lastDigits()).isNull();
    }

    @Test
    void declinedPaymentIsRecordedAndCartCanBeRetried() {
        cartExists();
        paymentsAreSaved();
        when(gateway.charge(any())).thenReturn(ChargeResult.declined("0002", "CARD_DECLINED_BY_ISSUER"));

        assertThatThrownBy(() -> service.checkout(new CheckoutCommand(cart.id(), "Cartão de Crédito", DECLINED_CARD)))
                .isInstanceOf(PaymentDeclinedException.class);

        assertThat(cart.status()).isEqualTo(CartStatus.DECLINED);
        var event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(PaymentEvent.DECLINED);
    }

    @Test
    void unknownCartIsRejected() {
        var unknown = UUID.randomUUID();
        when(carts.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertErrorCode(() -> service.checkout(new CheckoutCommand(unknown, "Pix", null)), ErrorCode.CART_NOT_FOUND);
        verifyNoInteractions(gateway);
    }

    @Test
    void alreadyApprovedCartIsNotChargedAgain() {
        cart.approve(NOW);
        cartExists();

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Pix", null)), ErrorCode.CART_ALREADY_PAID);
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void paymentMethodMustBeOneOfferedForTheCart() {
        cartExists();

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Boleto", null)),
                ErrorCode.PAYMENT_METHOD_NOT_ALLOWED);
        verifyNoInteractions(gateway);
    }

    @Test
    void pixMustNotReceiveCardData() {
        cartExists();

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Pix", APPROVED_CARD)),
                ErrorCode.INVALID_PAYMENT_DATA);
        verifyNoInteractions(gateway);
    }

    @Test
    void cardMethodRequiresCardData() {
        cartExists();

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Cartão de Débito", null)),
                ErrorCode.INVALID_PAYMENT_DATA);
        verifyNoInteractions(gateway);
    }

    @Test
    void expiredCardIsRejectedBeforeCharging() {
        cartExists();
        var expired = new CardData("Ana Souza", "4111111111114242", "09/26", "123");

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Cartão de Crédito", expired)),
                ErrorCode.INVALID_PAYMENT_DATA);
        verifyNoInteractions(gateway);
    }

    @Test
    void cardIsStillAcceptedOnItsLastDayAnywhereInTheWorld() {
        // 01:00 UTC on Nov 1st = 22:00 on Oct 31st in São Paulo: a 10/26 card is still valid there.
        checkoutAt("2026-11-01T01:00:00Z");
        cartExists();
        paymentsAreSaved();
        when(gateway.charge(any())).thenReturn(ChargeResult.approvedCard("4242"));
        var expiresThisMonth = new CardData("Ana Souza", "4111111111114242", "10/26", "123");

        var result = service.checkout(new CheckoutCommand(cart.id(), "Cartão de Crédito", expiresThisMonth));

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void cardIsExpiredOnceItsMonthIsOverEverywhere() {
        // 12:00 UTC on Nov 1st = midnight in UTC-12, the last zone to leave October.
        checkoutAt("2026-11-01T12:00:00Z");
        cartExists();
        var expiredEverywhere = new CardData("Ana Souza", "4111111111114242", "10/26", "123");

        assertErrorCode(() -> service.checkout(new CheckoutCommand(cart.id(), "Cartão de Crédito", expiredEverywhere)),
                ErrorCode.INVALID_PAYMENT_DATA);
        verifyNoInteractions(gateway);
    }

    private void checkoutAt(String instant) {
        service = new CheckoutService(carts, payments, gateway, events, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }
}
