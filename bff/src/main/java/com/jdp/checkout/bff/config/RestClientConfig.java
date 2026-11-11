package com.jdp.checkout.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per external service (each has its own base URL). Both start from Spring Boot's
 * auto-configured builder so outgoing calls get the Boot JSON settings and the {@code http.client.requests}
 * metrics/tracing.
 */
@Configuration
class RestClientConfig {

    @Bean
    RestClient catalogRestClient(RestClient.Builder builder, ClientsProperties props) {
        return build(builder, props.catalog().baseUrl(), props);
    }

    @Bean
    RestClient paymentMethodsRestClient(RestClient.Builder builder, ClientsProperties props) {
        return build(builder, props.paymentMethods().baseUrl(), props);
    }

    /**
     * Short timeouts: a slow upstream must fail fast instead of holding the frontend.
     * The injected builder is shared and mutable, so it is cloned before being configured.
     */
    private static RestClient build(RestClient.Builder builder, String baseUrl, ClientsProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
