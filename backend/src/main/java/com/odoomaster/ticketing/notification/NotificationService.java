package com.odoomaster.ticketing.notification;

import com.odoomaster.ticketing.notification.internal.Notification;
import com.odoomaster.ticketing.notification.internal.NotificationChannel;
import com.odoomaster.ticketing.notification.internal.NotificationStatus;
import com.odoomaster.ticketing.notification.NotificationDtos.*;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.notification.internal.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages user notifications: the in-app inbox, unread counts, read-state, and creation.
 *
 * <p>Creation is invoked by {@code NotificationEventListener} in response to domain events (e.g.
 * {@code TicketsIssuedEvent}) as well as directly by seeders, decoupling notification delivery
 * from the flows that trigger it.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public InboxResponse inbox(Long userId, String typeFilter) {
        List<Notification> all = notifications.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Notification n : all) counts.merge(n.getType(), 1L, Long::sum);

        List<NotificationView> items = all.stream()
                .filter(n -> typeFilter == null || typeFilter.isBlank() || typeFilter.equalsIgnoreCase(n.getType()))
                .map(this::view)
                .toList();

        long unread = all.stream().filter(n -> n.getReadAt() == null).count();
        return new InboxResponse(items, unread, counts);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationView markRead(Long userId, Long notificationId) {
        Notification n = notifications.findById(notificationId)
                .orElseThrow(() -> new AppException("NOTIFICATION_NOT_FOUND", "Notification not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(n.getUserId(), userId)) {
            throw new AppException("FORBIDDEN", "Notification does not belong to current user.", HttpStatus.FORBIDDEN);
        }
        if (n.isUnread()) {
            n.markReadAt(Instant.now());
            notifications.save(n);
        }
        return view(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    /**
     * Create and persist a notification for a user.
     *
     * @param userId recipient user id
     * @param type notification type (e.g. {@code TICKETS_ISSUED})
     * @param title short title
     * @param content body text
     * @param channel delivery channel ({@code IN_APP} if {@code null})
     * @param linkUrl optional deep-link the notification points to
     * @return the saved notification
     */
    @Transactional
    public Notification create(Long userId, String type, String title, String content, String channel, String linkUrl) {
        return notifications.save(Notification.send(userId, type, title, content,
                NotificationChannel.parse(channel).orElse(NotificationChannel.IN_APP),
                linkUrl, Instant.now()));
    }

    private NotificationView view(Notification n) {
        return new NotificationView(
                n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getChannel().name(),
                n.getStatus().name(), n.getLinkUrl(), n.getSentAt(), n.getReadAt(), n.getCreatedAt());
    }
}
