package com.odoomaster.ticketing.notification.internal;

import java.time.Instant;

/** Builders for notification aggregates. See {@code CatalogFixtures} for why this lives here. */
public final class NotificationFixtures {

    private NotificationFixtures() {
    }

    /** An in-app notification for user 5, read at {@code readAt} (or unread when null). */
    public static Notification notification(Long id, String type, Instant readAt) {
        return notification(id, 5L, type, NotificationChannel.IN_APP, readAt);
    }

    public static Notification notification(Long id, Long userId, String type,
                                            NotificationChannel channel, Instant readAt) {
        return new Notification(id, userId, type, type + " title", "content", channel,
                NotificationStatus.SENT, "/tickets", Instant.now(), readAt, Instant.now());
    }
}
