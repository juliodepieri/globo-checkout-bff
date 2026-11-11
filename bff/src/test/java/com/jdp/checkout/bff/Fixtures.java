package com.jdp.checkout.bff;

import com.jdp.checkout.bff.model.CardData;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.PaymentMethodType;
import com.jdp.checkout.bff.model.Product;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-14T16:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static final Product BASICO = new Product("basico", "Plano Básico", "Assinatura mensal com catálogo essencial", new BigDecimal("19.90"), "BRL");
    public static final Product PREMIUM = new Product("premium", "Plano Premium", "Assinatura mensal com catálogo completo", new BigDecimal("39.90"), "BRL");
    public static final Product FAMILIA = new Product("familia", "Plano Família", "Assinatura mensal para até 5 perfis", new BigDecimal("59.90"), "BRL");
    public static final List<Product> CATALOG = List.of(BASICO, PREMIUM, FAMILIA);

    public static final PaymentMethod CREDIT = new PaymentMethod(PaymentMethodType.CREDIT_CARD, "Cartão de Crédito");
    public static final PaymentMethod DEBIT = new PaymentMethod(PaymentMethodType.DEBIT_CARD, "Cartão de Débito");
    public static final PaymentMethod PIX = new PaymentMethod(PaymentMethodType.PIX, "Pix");
    public static final List<PaymentMethod> METHODS = List.of(CREDIT, DEBIT, PIX);

    public static final CardData APPROVED_CARD = new CardData("Ana Souza", "4111111111114242", "12/28", "123");
    public static final CardData DECLINED_CARD = new CardData("Ana Souza", "4111111111110002", "12/28", "123");

    private Fixtures() {
    }

    public static Cart premiumCart() {
        return Cart.open(List.of(PREMIUM), METHODS, NOW);
    }
}
