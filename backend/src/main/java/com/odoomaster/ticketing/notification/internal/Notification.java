package com.odoomaster.ticketing.notification.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a notification.
 */
@Entity
@Table(name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_user", columnList = "user_id"),
            @Index(name = "idx_notifications_user_read", columnList = "user_id, read_at"),
            @Index(name = "idx_notifications_type", columnList = "type")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** A delivered in-app (or queued e-mail/SMS) notification for one user. */
    public static Notification send(Long userId, String type, String title, String content,
                                    NotificationChannel channel, String linkUrl, Instant now) {
        Notification n = new Notification();
        n.userId = Objects.requireNonNull(userId, "userId");
        n.type = Objects.requireNonNull(type, "type");
        n.title = Objects.requireNonNull(title, "title");
        n.content = content;
        n.channel = channel == null ? NotificationChannel.IN_APP : channel;
        n.status = NotificationStatus.SENT;
        n.linkUrl = linkUrl;
        n.createdAt = Objects.requireNonNull(now, "now");
        n.sentAt = now;
        return n;
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    /** Mark as read. Idempotent — the first read timestamp is the one that counts. */
    public void markReadAt(Instant now) {
        if (readAt == null) {
            this.readAt = Objects.requireNonNull(now, "now");
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (status == null) status = NotificationStatus.SENT;
        if (sentAt == null && status == NotificationStatus.SENT) sentAt = now;
        if (channel == null) channel = NotificationChannel.IN_APP;
    }
}
