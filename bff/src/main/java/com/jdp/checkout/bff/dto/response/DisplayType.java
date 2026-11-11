package com.jdp.checkout.bff.dto.response;

public enum DisplayType {
    /** Error screen with actions; the flow stops. */
    BLOCK,
    /** Warning that does not end the flow (e.g. fix a field and resubmit). */
    INTERVENTION
}
