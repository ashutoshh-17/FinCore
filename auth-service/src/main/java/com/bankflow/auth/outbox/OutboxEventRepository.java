package com.bankflow.auth.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Returns up to 100 unpublished events ordered oldest-first for the relay batch. */
    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC LIMIT 100")
    List<OutboxEvent> findUnpublished();
}
