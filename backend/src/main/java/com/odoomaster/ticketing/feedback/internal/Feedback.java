package com.odoomaster.ticketing.feedback.internal;

import jakarta.persistence.*;
import java.util.Objects;
import com.odoomaster.ticketing.shared.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

    /** A newly submitted, untriaged feedback item. */
    public static Feedback submit(Long userId, Long eventId, FeedbackCategory category,
                                  String subject, String body, Integer rating, Instant now) {
        if (subject == null || subject.isBlank()) {
            throw new DomainException("VALIDATION_FAILED", "Subject is required.");
        }
        if (body == null || body.isBlank()) {
            throw new DomainException("VALIDATION_FAILED", "Body is required.");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new DomainException("VALIDATION_FAILED", "Rating must be 1–5.");
        }
        Feedback fb = new Feedback();
        fb.userId = Objects.requireNonNull(userId, "userId");
        fb.eventId = eventId;
        fb.category = Objects.requireNonNull(category, "category");
        fb.subject = subject.trim();
        fb.body = body.trim();
        fb.rating = rating;
        fb.status = FeedbackStatus.NEW;
        fb.createdAt = Objects.requireNonNull(now, "now");
        return fb;
    }

    /**
     * Move the item through triage.
     *
     * <p>{@code resolvedAt} is stamped on entry to {@link FeedbackStatus#RESOLVED} and never cleared
     * afterwards — re-opening a resolved item keeps the record that it was once closed.
     */
    public void moveTo(FeedbackStatus newStatus, Instant now) {
        this.status = Objects.requireNonNull(newStatus, "status");
        if (newStatus == FeedbackStatus.RESOLVED && resolvedAt == null) {
            this.resolvedAt = Objects.requireNonNull(now, "now");
        }
    }

    /** Attach a staff note. Blank input is ignored rather than wiping an existing note. */
    public void attachAdminNote(String note) {
        if (note != null && !note.isBlank()) {
            this.adminNote = note.trim();
        }
    }

    @PrePersist
    void prePersist() {
        if (status == null) status = FeedbackStatus.NEW;
        if (createdAt == null) createdAt = Instant.now();
    }
}
