package com.jdp.checkout.bff.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * checkout.events (topic) --payment.#--> checkout.payment-events --(rejected)--> checkout.payment-events.dlq
 */
@Configuration
class RabbitConfig {

    @Bean
    TopicExchange checkoutEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.exchange(), true, false);
    }

    @Bean
    Queue paymentEventsQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.paymentEventsQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.deadLetterQueue())
                .build();
    }

    @Bean
    Queue paymentEventsDeadLetterQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.deadLetterQueue()).build();
    }

    @Bean
    Binding paymentEventsBinding(Queue paymentEventsQueue, TopicExchange checkoutEventsExchange) {
        return BindingBuilder.bind(paymentEventsQueue).to(checkoutEventsExchange).with("payment.#");
    }
}
