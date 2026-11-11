package com.jdp.checkout.bff.client;

/** Contract of the external payment-methods service. */
record PaymentMethodResponse(String code, String displayName, boolean enabled) {
}
