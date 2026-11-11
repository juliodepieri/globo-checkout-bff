package com.jdp.checkout.bff.entity;

import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.model.CartStatus;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart created by the pre-checkout. Holds a snapshot of the products (price at that moment)
 * and of the payment methods offered, so the checkout validates against what the user saw.
 * After creation only the status changes.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Assigned UUID id: a null version is how Spring Data's save() knows the cart is new (persist, no SELECT).
    // Checkout already serializes with SELECT ... FOR UPDATE; the version guards any future writer that does not lock.
    @Version
    private Long version;

    // EAGER: the snapshot is small and always used with the cart (and the cart leaves the transaction
    // to be rendered). Loaded with secondary selects, so the FOR UPDATE lock query stays a single-table select.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    @OrderColumn(name = "sort_order")
    private List<CartItem> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cart_payment_method", joinColumns = @JoinColumn(name = "cart_id"))
    @OrderColumn(name = "sort_order")
    private List<CartPaymentMethod> paymentMethods = new ArrayList<>();

    protected Cart() {
    }

    public static Cart open(List<Product> products, List<PaymentMethod> paymentMethods, Instant now) {
        if (products.isEmpty()) {
            throw new IllegalArgumentException("A cart needs at least one product");
        }
        var cart = new Cart();
        cart.id = UUID.randomUUID();
        cart.status = CartStatus.OPEN;
        cart.items = new ArrayList<>(products.stream().map(CartItem::of).toList());
        cart.paymentMethods = new ArrayList<>(paymentMethods.stream().map(CartPaymentMethod::of).toList());
        cart.totalAmount = products.stream().map(Product::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.currency = products.getFirst().currency();
        cart.createdAt = now;
        cart.updatedAt = now;
        return cart;
    }

    public void ensurePayable() {
        if (status == CartStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CART_ALREADY_PAID, id, "Cart " + id + " is already paid");
        }
    }

    /** The method must be one of the methods offered by the pre-checkout of this very cart. */
    public PaymentMethod requireOfferedPaymentMethod(String name) {
        var wanted = name == null ? "" : name.trim();
        return paymentMethods().stream()
                .filter(m -> m.name().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_ALLOWED, id,
                        "Payment method '" + name + "' was not offered for cart " + id));
    }

    public void approve(Instant now) {
        changeStatus(CartStatus.APPROVED, now);
    }

    public void decline(Instant now) {
        changeStatus(CartStatus.DECLINED, now);
    }

    private void changeStatus(CartStatus newStatus, Instant now) {
        ensurePayable();
        this.status = newStatus;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public CartStatus status() { return status; }
    public BigDecimal total() { return totalAmount; }
    public String currency() { return currency; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public List<Product> products() {
        return items.stream().map(CartItem::toProduct).toList();
    }

    public List<PaymentMethod> paymentMethods() {
        return paymentMethods.stream().map(CartPaymentMethod::toPaymentMethod).toList();
    }
}
