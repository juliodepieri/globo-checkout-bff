package com.jdp.checkout.bff.service;

import com.jdp.checkout.bff.client.PaymentMethodsClient;
import com.jdp.checkout.bff.client.ProductCatalogClient;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.ExternalServiceException;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.Product;
import com.jdp.checkout.bff.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the cart for the payment screen from the external products and payment-methods services. */
@Service
public class PreCheckoutService {

    private final ProductCatalogClient productCatalog;
    private final PaymentMethodsClient paymentMethods;
    private final CartRepository carts;
    private final Clock clock;

    public PreCheckoutService(ProductCatalogClient productCatalog, PaymentMethodsClient paymentMethods,
                              CartRepository carts, Clock clock) {
        this.productCatalog = productCatalog;
        this.paymentMethods = paymentMethods;
        this.carts = carts;
        this.clock = clock;
    }

    @Transactional
    public Cart preCheckout(List<String> productSlugs) {
        // Subscriptions: the same plan twice makes no sense, so slugs are de-duplicated (order kept).
        var requested = productSlugs.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Product> catalog = productCatalog.findAll().stream()
                .collect(Collectors.toMap(Product::slug, Function.identity()));

        var missing = requested.stream().filter(slug -> !catalog.containsKey(slug)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Unknown products: " + missing);
        }

        List<PaymentMethod> methods = paymentMethods.findAvailable();
        if (methods.isEmpty()) {
            throw new ExternalServiceException("No payment method available");
        }

        var products = requested.stream().map(catalog::get).toList();
        return carts.save(Cart.open(products, methods, clock.instant()));
    }
}
