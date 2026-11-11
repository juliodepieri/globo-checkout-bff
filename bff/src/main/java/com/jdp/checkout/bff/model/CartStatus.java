package com.jdp.checkout.bff.model;

public enum CartStatus {
    /** Created by the pre-checkout, never charged. */
    OPEN,
    /** Last attempt was declined; the cart can be paid again. */
    DECLINED,
    /** Final state: paid. */
    APPROVED
}
