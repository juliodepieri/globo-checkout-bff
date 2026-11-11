package com.jdp.checkout.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("checkout.messaging")
public record MessagingProperties(String exchange, String paymentEventsQueue, String deadLetterQueue) {
}
