package com.jdp.checkout.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;

/** Constants in English for the code; {@link #label()} is the value the frontend contract requires. */
public enum CheckoutStatus {
    /** Payment approved; the only status of a 200 response (declines are errors). */
    APPROVED("APROVADO");

    private final String label;

    CheckoutStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
