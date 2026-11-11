package com.jdp.checkout.bff.messaging;

import org.springframework.stereotype.Component;
import com.jdp.checkout.bff.repository.OutboxEventRepository;
import com.jdp.checkout.bff.entity.OutboxEvent;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional outbox: the event is written in the same DB transaction as the payment.
 * {@link OutboxRelay} ships it to RabbitMQ afterwards, so no event is lost if the broker is down
 * and no event is sent for a transaction that rolled back.
 */
@Component
public class PaymentEventPublisher {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    PaymentEventPublisher(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public void publish(PaymentEvent event) {
        repository.save(new OutboxEvent(event.eventId(), event.cartId(), event.type(),
                jsonMapper.writeValueAsString(event), event.occurredAt()));
    }
}
