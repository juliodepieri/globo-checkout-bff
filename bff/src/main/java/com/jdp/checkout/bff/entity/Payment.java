package com.jdp.checkout.bff.entity;

import com.jdp.checkout.bff.model.ChargeResult;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.PaymentMethodType;
import com.jdp.checkout.bff.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A payment attempt. Only non-sensitive data: last 4 digits or the Pix code.
 * There is no column for the card number or CVV, by design.
 * Payments are never updated, so there is no version column. Trade-off: the id is an assigned UUID, so
 * Spring Data's save() cannot tell the payment is new and does a merge (one SELECT before the INSERT).
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;
    @Column(name = "cart_id", nullable = false)
    private UUID cartId;
    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false)
    private PaymentMethodType methodType;
    @Column(name = "method_name", nullable = false)
    private String methodName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    @Column(name = "last_digits")
    private String lastDigits;
    @Column(name = "pix_code")
    private String pixCode;
    @Column(name = "decline_reason")
    private String declineReason;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected Payment() {
    }

    public static Payment of(Cart cart, PaymentMethod method, ChargeResult result, Instant now) {
        var p = new Payment();
        p.id = UUID.randomUUID();
        p.cartId = cart.id();
        p.methodType = method.type();
        p.methodName = method.name();
        p.status = result.approved() ? PaymentStatus.APPROVED : PaymentStatus.DECLINED;
        p.amount = cart.total();
        p.currency = cart.currency();
        p.lastDigits = result.lastDigits();
        p.pixCode = result.pixCode();
        p.declineReason = result.declineReason();
        p.processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        return p;
    }

    public boolean approved() {
        return status == PaymentStatus.APPROVED;
    }

    public UUID id() { return id; }
    public UUID cartId() { return cartId; }
    public PaymentMethod method() { return new PaymentMethod(methodType, methodName); }
    public PaymentStatus status() { return status; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String lastDigits() { return lastDigits; }
    public String pixCode() { return pixCode; }
    public String declineReason() { return declineReason; }
    public Instant processedAt() { return processedAt; }
}
