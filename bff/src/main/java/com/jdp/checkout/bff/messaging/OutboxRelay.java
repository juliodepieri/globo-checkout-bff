package com.jdp.checkout.bff.messaging;

import com.jdp.checkout.bff.config.MessagingProperties;
import com.jdp.checkout.bff.entity.OutboxEvent;
import com.jdp.checkout.bff.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/** Polls the outbox and publishes to RabbitMQ (at-least-once: consumers must be idempotent by messageId). */
@Component
@ConditionalOnProperty(name = "checkout.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final Clock clock;

    OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                MessagingProperties properties, Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${checkout.outbox.relay-interval:1s}")
    @Transactional
    public void relay() {
        for (OutboxEvent event : repository.lockNextBatch(BATCH_SIZE)) {
            // If the broker is down this throws, the transaction rolls back and the batch is retried.
            rabbitTemplate.send(properties.exchange(), event.eventType(), toMessage(event));
            event.markPublished(clock.instant());
            log.info("Published event id={} type={} cartId={}", event.id(), event.eventType(), event.aggregateId());
        }
    }

    private static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setHeader("eventType", event.eventType())
                .build();
    }
}
