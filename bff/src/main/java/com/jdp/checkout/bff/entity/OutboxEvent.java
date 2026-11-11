package com.jdp.checkout.bff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    // Assigned UUID id: a null version is how Spring Data's save() knows the event is new (persist, no SELECT).
    // The relay locks with FOR UPDATE SKIP LOCKED; the version guards any future writer that does not lock.
    @Version
    private Long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public UUID id() { return id; }
    public UUID aggregateId() { return aggregateId; }
    public String eventType() { return eventType; }
    public String payload() { return payload; }
    public Instant publishedAt() { return publishedAt; }
}
