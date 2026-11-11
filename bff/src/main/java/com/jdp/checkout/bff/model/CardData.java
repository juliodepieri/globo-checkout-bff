package com.jdp.checkout.bff.model;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Sensitive card data. Lives only in memory during the request: it is never persisted,
 * never published in events, and {@link #toString()} masks it so it cannot leak into logs.
 */
public record CardData(String holderName, String number, String expiry, String cvv) {

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("MM/yy");

    public String lastDigits() {
        return number.substring(number.length() - 4);
    }

    public boolean isExpiredAt(YearMonth reference) {
        try {
            return YearMonth.parse(expiry, EXPIRY).isBefore(reference);
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    @Override
    public String toString() {
        return "CardData[holderName=***, number=****" + lastDigits() + ", expiry=**/**, cvv=***]";
    }
}
