package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.model.ChargeResult;
import com.jdp.checkout.bff.model.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.util.zip.CRC32;

/**
 * Fake acquirer.
 * <ul>
 *   <li>card ending in {@value #DECLINED_SUFFIX} is declined;</li>
 *   <li>any other card is approved;</li>
 *   <li>Pix is always approved and returns a simulated copy and paste code.</li>
 * </ul>
 * Swapping it for a real PSP means writing another {@link PaymentGateway} implementation.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINED_SUFFIX = "0002";

    /** Exhaustive switch: a new {@link PaymentMethodType} does not compile until the gateway can charge it. */
    @Override
    public ChargeResult charge(ChargeRequest request) {
        return switch (request.method().type()) {
            case CREDIT_CARD, DEBIT_CARD -> chargeCard(request.card());
            case PIX -> ChargeResult.approvedPix(pixCode(request));
        };
    }

    private static ChargeResult chargeCard(CardData card) {
        if (card.number().endsWith(DECLINED_SUFFIX)) {
            return ChargeResult.declined(card.lastDigits(), "CARD_DECLINED_BY_ISSUER");
        }
        return ChargeResult.approvedCard(card.lastDigits());
    }

    /** EMV-like BR Code, good enough for a demo (not a valid Pix payload). */
    private static String pixCode(ChargeRequest request) {
        String txId = request.cartId().toString().replace("-", "").substring(0, 25).toUpperCase();
        String amount = request.amount().toPlainString();
        String payload = "00020126580014BR.GOV.BCB.PIX0136" + txId
                + "520400005303986540" + amount.length() + amount
                + "5802BR5913CHECKOUT DEMO6009SAO PAULO6304";
        var crc = new CRC32();
        crc.update(payload.getBytes());
        return payload + String.format("%04X", crc.getValue() & 0xFFFF);
    }
}
