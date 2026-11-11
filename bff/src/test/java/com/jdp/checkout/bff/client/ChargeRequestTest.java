package com.jdp.checkout.bff.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeRequestTest {

    private final BigDecimal amount = new BigDecimal("39.90");

    @Test
    void cardMethodWithoutCardCannotBeBuilt() {
        assertThatThrownBy(() -> new ChargeRequest(UUID.randomUUID(), CREDIT, amount, "BRL", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pixWithCardCannotBeBuilt() {
        assertThatThrownBy(() -> new ChargeRequest(UUID.randomUUID(), PIX, amount, "BRL", APPROVED_CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
