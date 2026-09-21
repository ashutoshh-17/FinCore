package com.bankflow.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry. Records every mutating action for compliance and debugging.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String details;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static AuditLog of(String actor, String action, String entityType, UUID entityId,
                               String detailsJson, String correlationId) {
        AuditLog log = new AuditLog();
        log.actor         = actor;
        log.action        = action;
        log.entityType    = entityType;
        log.entityId      = entityId;
        log.details       = detailsJson;
        log.correlationId = correlationId;
        return log;
    }
}
