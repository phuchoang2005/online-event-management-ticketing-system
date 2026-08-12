package com.odoomaster.ticketing.audit.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a auditlog.
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
            @Index(name = "idx_audit_user",    columnList = "user_id"),
            @Index(name = "idx_audit_entity",  columnList = "entity, entity_id"),
            @Index(name = "idx_audit_created", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 64)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(columnDefinition = "json")
    private String metadata;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Record one audited action. Append-only: there are no transitions and nothing to mutate. */
    public static AuditLog of(Long userId, String action, String entity, Long entityId,
                              String metadata, String traceId, Instant now) {
        AuditLog log = new AuditLog();
        log.userId = userId;
        log.action = Objects.requireNonNull(action, "action");
        log.entity = Objects.requireNonNull(entity, "entity");
        log.entityId = entityId;
        log.metadata = metadata;
        log.traceId = traceId;
        log.createdAt = Objects.requireNonNull(now, "now");
        return log;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
