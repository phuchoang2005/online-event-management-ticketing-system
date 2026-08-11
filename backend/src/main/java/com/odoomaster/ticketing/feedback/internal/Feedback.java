package com.odoomaster.ticketing.feedback.internal;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a feedback.
 */
@Entity
@Table(name = "feedbacks",
        indexes = {
            @Index(name = "idx_feedbacks_user", columnList = "user_id"),
            @Index(name = "idx_feedbacks_status", columnList = "status"),
            @Index(name = "idx_feedbacks_created", columnList = "created_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id")
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackCategory category;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column
    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @PrePersist
    void prePersist() {
        if (status == null) status = FeedbackStatus.NEW;
        if (createdAt == null) createdAt = Instant.now();
    }
}
