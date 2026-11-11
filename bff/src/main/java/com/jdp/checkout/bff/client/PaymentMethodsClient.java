package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.config.CacheNames;
import com.jdp.checkout.bff.exception.ExternalServiceException;
import com.jdp.checkout.bff.model.PaymentMethod;
import com.jdp.checkout.bff.model.PaymentMethodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentMethodsClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodsClient.class);

    private final RestClient restClient;

    public PaymentMethodsClient(@Qualifier("paymentMethodsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = CacheNames.PAYMENT_METHODS, key = "'available'")
    public List<PaymentMethod> findAvailable() {
        try {
            PaymentMethodResponse[] body = restClient.get().uri("/payment-methods").retrieve().body(PaymentMethodResponse[].class);
            return body == null ? List.of() : Arrays.stream(body)
                    .filter(PaymentMethodResponse::enabled)
                    .map(PaymentMethodsClient::toModel)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Payment methods service unavailable", e);
        }
    }

    private static Optional<PaymentMethod> toModel(PaymentMethodResponse m) {
        try {
            return Optional.of(new PaymentMethod(PaymentMethodType.valueOf(m.code()), m.displayName()));
        } catch (IllegalArgumentException e) {
            // A new method the BFF does not support yet must not break the checkout.
            log.warn("Ignoring unsupported payment method code={}", m.code());
            return Optional.empty();
        }
    }
}
