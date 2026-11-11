package com.jdp.checkout.bff.mapper;

import com.jdp.checkout.bff.dto.request.CheckoutRequest;
import com.jdp.checkout.bff.dto.response.CheckoutResponse;
import com.jdp.checkout.bff.dto.response.CheckoutStatus;
import com.jdp.checkout.bff.dto.response.PreCheckoutResponse;
import com.jdp.checkout.bff.service.CheckoutService.CheckoutCommand;
import com.jdp.checkout.bff.service.CheckoutService.CheckoutResult;
import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.entity.Cart;

/** Translates between the frontend contract (DTOs) and the service layer. */
public final class CheckoutMapper {

    private CheckoutMapper() {
    }

    public static PreCheckoutResponse toResponse(Cart cart) {
        return new PreCheckoutResponse(
                cart.id(),
                cart.products().stream()
                        .map(p -> new PreCheckoutResponse.ProductView(p.slug(), p.name(), p.description(), p.price(), p.currency()))
                        .toList(),
                cart.paymentMethods().stream().map(m -> new PreCheckoutResponse.PaymentMethodView(m.name())).toList());
    }

    public static CheckoutCommand toCommand(CheckoutRequest request) {
        var card = request.paymentMethod().card();
        return new CheckoutCommand(request.cartId(), request.paymentMethod().name(),
                card == null ? null : new CardData(card.holderName(), card.number(), card.expiry(), card.cvv()));
    }

    public static CheckoutResponse toResponse(CheckoutResult result) {
        var payment = result.payment();
        return new CheckoutResponse(
                result.cart().id(),
                CheckoutStatus.APPROVED,
                payment.processedAt(),
                new CheckoutResponse.PaymentView(payment.method().name(), payment.lastDigits(), payment.pixCode(),
                        payment.amount(), payment.currency()),
                result.cart().products().stream()
                        .map(p -> new CheckoutResponse.PaidProductView(p.slug(), p.name(), p.price()))
                        .toList());
    }
}
