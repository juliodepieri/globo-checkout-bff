package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.model.ChargeResult;

/**
 * Acquirer / PSP. Kept as an interface because it is the one integration that will be swapped:
 * today {@link SimulatedPaymentGateway}, in production a real PSP client.
 *
 * <p>Contract for every implementation:
 * <ul>
 *   <li>the request is already valid: {@link ChargeRequest} guarantees {@code card()} is non-null exactly
 *       when {@code method().requiresCard()} is true;</li>
 *   <li>a refusal is a {@link ChargeResult#declined declined} result, never an exception;</li>
 *   <li>{@code lastDigits} is filled for card methods and {@code pixCode} for Pix.</li>
 * </ul>
 */
public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request);
}
