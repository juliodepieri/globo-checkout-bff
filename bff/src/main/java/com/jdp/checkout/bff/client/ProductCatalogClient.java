package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.config.CacheNames;
import com.jdp.checkout.bff.exception.ExternalServiceException;
import com.jdp.checkout.bff.model.Product;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

@Component
public class ProductCatalogClient {

    private final RestClient restClient;

    public ProductCatalogClient(@Qualifier("catalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "'all'")
    public List<Product> findAll() {
        try {
            ProductResponse[] body = restClient.get().uri("/catalog/products").retrieve().body(ProductResponse[].class);
            return body == null ? List.of() : Arrays.stream(body)
                    .filter(ProductResponse::active)
                    .map(ProductCatalogClient::toModel)
                    .toList();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Products service unavailable", e);
        }
    }

    private static Product toModel(ProductResponse p) {
        return new Product(p.sku(), p.title(), p.summary(), p.price(), p.currency());
    }
}
