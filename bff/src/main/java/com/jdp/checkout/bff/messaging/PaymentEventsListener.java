package com.jdp.checkout.bff.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Async post-payment processing (activate the subscription, send the receipt, feed analytics...).
 * In a real system this consumer would live in another service; here it only demonstrates the flow.
 * Failures go to the dead-letter queue after the listener retries (see application.yml).
 */
@Component
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    @RabbitListener(queues = "${checkout.messaging.payment-events-queue}")
    void onPaymentEvent(Message message) {
        var props = message.getMessageProperties();
        log.info("Consumed {} messageId={} payload={}", props.getHeader("eventType"), props.getMessageId(),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
