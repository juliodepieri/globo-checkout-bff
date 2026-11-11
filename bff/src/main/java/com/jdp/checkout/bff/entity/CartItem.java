package com.jdp.checkout.bff.entity;

import com.jdp.checkout.bff.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product snapshot inside a cart (row of {@code cart_item}). Owned by {@link Cart}: created with it and
 * never changed afterwards. {@code cart_id} and {@code sort_order} are managed by the cart's collection mapping.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private String currency;

    protected CartItem() {
    }

    static CartItem of(Product p) {
        var item = new CartItem();
        item.id = UUID.randomUUID();
        item.slug = p.slug();
        item.name = p.name();
        item.description = p.description();
        item.price = p.price();
        item.currency = p.currency();
        return item;
    }

    public UUID id() {
        return id;
    }

    Product toProduct() {
        return new Product(slug, name, description, price, currency);
    }
}
