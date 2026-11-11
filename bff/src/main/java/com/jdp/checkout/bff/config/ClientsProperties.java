package com.jdp.checkout.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("checkout.clients")
public record ClientsProperties(Endpoint catalog, Endpoint paymentMethods, Duration connectTimeout, Duration readTimeout) {

    public record Endpoint(String baseUrl) {
    }
}
