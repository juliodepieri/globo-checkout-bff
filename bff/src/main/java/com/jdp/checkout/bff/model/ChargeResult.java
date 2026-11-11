package com.jdp.checkout.bff.model;

public record ChargeResult(boolean approved, String lastDigits, String pixCode, String declineReason) {

    public static ChargeResult approvedCard(String lastDigits) {
        return new ChargeResult(true, lastDigits, null, null);
    }

    public static ChargeResult approvedPix(String pixCode) {
        return new ChargeResult(true, null, pixCode, null);
    }

    public static ChargeResult declined(String lastDigits, String reason) {
        return new ChargeResult(false, lastDigits, null, reason);
    }
}
