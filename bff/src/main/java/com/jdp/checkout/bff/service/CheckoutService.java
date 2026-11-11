package com.jdp.checkout.bff.service;

import com.jdp.checkout.bff.client.PaymentGateway;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.entity.Payment;
import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.PaymentDeclinedException;
import com.jdp.checkout.bff.messaging.PaymentEvent;
import com.jdp.checkout.bff.messaging.PaymentEventPublisher;
import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.client.ChargeRequest;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.repository.CartRepository;
import com.jdp.checkout.bff.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/** Pays a cart created by the pre-checkout. */
@Service
public class CheckoutService {

    /**
     * Card expiry is checked in the last time zone on Earth to reach midnight (UTC-12), not in UTC or the server zone.
     * A card is valid until the end of its expiry month, but in which time zone depends on the issuer, and the issuer
     * can be in any country (and a country can span several zones). So the BFF only calls a card expired once its
     * month is over everywhere: it never blocks a valid card, and a card that expired a few hours ago in the
     * customer's zone goes to the issuer, which has the final word and declines it. This check is only fast feedback.
     */
    private static final ZoneOffset LATEST_ZONE = ZoneOffset.ofHours(-12);

    /** {@code card} must be null for Pix. */
    public record CheckoutCommand(UUID cartId, String paymentMethodName, CardData card) {
    }

    public record CheckoutResult(Cart cart, Payment payment) {
    }

    private final CartRepository carts;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentEventPublisher events;
    private final Clock clock;

    public CheckoutService(CartRepository carts, PaymentRepository payments, PaymentGateway gateway,
                           PaymentEventPublisher events, Clock clock) {
        this.carts = carts;
        this.payments = payments;
        this.gateway = gateway;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The cart row is locked (SELECT ... FOR UPDATE) for the whole transaction, so two concurrent
     * checkouts of the same cart are serialized and the second one sees APPROVED.
     * A declined attempt is still committed (payment + event), hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public CheckoutResult checkout(CheckoutCommand command) {
        Cart cart = carts.findByIdForUpdate(command.cartId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND, command.cartId(),
                        "Cart " + command.cartId() + " not found"));

        cart.ensurePayable();
        PaymentMethod method = cart.requireOfferedPaymentMethod(command.paymentMethodName());
        method.ensureValidPaymentData(cart.id(), command.card(), YearMonth.now(clock.withZone(LATEST_ZONE)));

        var result = gateway.charge(new ChargeRequest(cart.id(), method, cart.total(), cart.currency(), command.card()));

        Instant now = clock.instant();
        Payment payment = save(Payment.of(cart, method, result, now));
        if (payment.approved()) {
            cart.approve(now);
        } else {
            cart.decline(now);
        }
        // The cart is a managed entity: its new status is flushed on commit.
        events.publish(PaymentEvent.from(payment));

        if (!payment.approved()) {
            throw new PaymentDeclinedException(cart.id(), payment.declineReason());
        }
        return new CheckoutResult(cart, payment);
    }

    private Payment save(Payment payment) {
        try {
            // Flush now so the partial unique index (one APPROVED payment per cart) is checked here.
            return payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Last line of defense; the row lock on the cart should already prevent this.
            throw new BusinessException(ErrorCode.CART_ALREADY_PAID, payment.cartId(),
                    "Cart already has an approved payment", e);
        }
    }
}
