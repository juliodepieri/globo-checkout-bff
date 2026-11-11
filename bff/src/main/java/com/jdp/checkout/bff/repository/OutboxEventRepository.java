package com.jdp.checkout.bff.repository;

import com.jdp.checkout.bff.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several BFF instances run the relay without publishing the same row twice. */
    @Query(value = """
            select * from outbox_event
            where published_at is null
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateId(UUID aggregateId);
}
