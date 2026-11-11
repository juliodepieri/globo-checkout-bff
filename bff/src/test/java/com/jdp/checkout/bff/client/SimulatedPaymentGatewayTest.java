package com.jdp.checkout.bff.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
    private final BigDecimal amount = new BigDecimal("39.90");

    @Test
    void cardEndingIn0002IsDeclined() {
        var result = gateway.charge(new ChargeRequest(UUID.randomUUID(), CREDIT, amount, "BRL", DECLINED_CARD));

        assertThat(result.approved()).isFalse();
        assertThat(result.lastDigits()).isEqualTo("0002");
    }

    @Test
    void otherCardsAreApproved() {
        var result = gateway.charge(new ChargeRequest(UUID.randomUUID(), DEBIT, amount, "BRL", APPROVED_CARD));

        assertThat(result.approved()).isTrue();
        assertThat(result.lastDigits()).isEqualTo("4242");
        assertThat(result.pixCode()).isNull();
    }

    @Test
    void pixIsApprovedWithSimulatedCode() {
        var result = gateway.charge(new ChargeRequest(UUID.randomUUID(), PIX, amount, "BRL", null));

        assertThat(result.approved()).isTrue();
        assertThat(result.lastDigits()).isNull();
        assertThat(result.pixCode()).startsWith("000201").contains("BR.GOV.BCB.PIX").contains("39.90");
    }
}
