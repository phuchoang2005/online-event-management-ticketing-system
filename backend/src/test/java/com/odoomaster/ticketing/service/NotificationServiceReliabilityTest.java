package com.odoomaster.ticketing.service;
import com.odoomaster.ticketing.notification.NotificationService;

import com.odoomaster.ticketing.notification.internal.Notification;
import com.odoomaster.ticketing.notification.internal.NotificationChannel;
import com.odoomaster.ticketing.notification.internal.NotificationStatus;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.notification.internal.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceReliabilityTest {

    @Mock NotificationRepository notifications;

    @Test
    void inbox_givenMixedTypes_returnsUnreadCountAndTypeCounts() {
        NotificationService service = new NotificationService(notifications);
        when(notifications.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(
                notification(1L, "TICKETS_ISSUED", null),
                notification(2L, "PAYMENT_FAILED", Instant.now()),
                notification(3L, "TICKETS_ISSUED", null)));

        var inbox = service.inbox(5L, null);

        assertThat(inbox.items()).hasSize(3);
        assertThat(inbox.unreadCount()).isEqualTo(2);
        assertThat(inbox.countsByType()).containsEntry("TICKETS_ISSUED", 2L).containsEntry("PAYMENT_FAILED", 1L);
    }

    @ParameterizedTest
    @CsvSource({
            "TICKETS_ISSUED,2",
            "tickets_issued,2",
            "PAYMENT_FAILED,1",
            "'',3",
            "'   ',3"
    })
    void inbox_givenTypeFilter_filtersCaseInsensitively(String filter, int expected) {
        NotificationService service = new NotificationService(notifications);
        when(notifications.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(
                notification(1L, "TICKETS_ISSUED", null),
                notification(2L, "PAYMENT_FAILED", Instant.now()),
                notification(3L, "TICKETS_ISSUED", null)));

        assertThat(service.inbox(5L, filter).items()).hasSize(expected);
    }

    @Test
    void unreadCount_delegatesToRepository() {
        NotificationService service = new NotificationService(notifications);
        when(notifications.countByUserIdAndReadAtIsNull(5L)).thenReturn(12L);

        assertThat(service.unreadCount(5L).unreadCount()).isEqualTo(12);
    }

    @Test
    void markRead_givenUnreadOwnedNotification_setsReadAtOnce() {
        NotificationService service = new NotificationService(notifications);
        Notification n = notification(1L, "TICKETS_ISSUED", null);
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        var view = service.markRead(5L, 1L);

        assertThat(view.readAt()).isNotNull();
        verify(notifications).save(n);
    }

    @Test
    void markRead_givenAlreadyReadNotification_isIdempotent() {
        NotificationService service = new NotificationService(notifications);
        Notification n = notification(1L, "TICKETS_ISSUED", Instant.now());
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        service.markRead(5L, 1L);

        verify(notifications, never()).save(any());
    }

    @Test
    void markRead_givenAnotherUsersNotification_rejectsAccess() {
        NotificationService service = new NotificationService(notifications);
        Notification n = notification(1L, "TICKETS_ISSUED", null);
        n.setUserId(99L);
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markRead(5L, 1L))
                .isInstanceOf(AppException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void markRead_givenMissingNotification_returnsNotFound() {
        NotificationService service = new NotificationService(notifications);
        when(notifications.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(5L, 1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void markAllRead_returnsUpdatedCount() {
        NotificationService service = new NotificationService(notifications);
        when(notifications.markAllRead(eq(5L), any())).thenReturn(4);

        assertThat(service.markAllRead(5L)).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({
            "TICKETS_ISSUED,Vé đã phát hành,IN_APP,/tickets",
            "PAYMENT_FAILED,Thanh toán lỗi,EMAIL,/orders/7",
            "SECURITY_ALERT,Cảnh báo,SMS,/profile"
    })
    void create_givenNotificationPayload_persistsReliableDefaults(String type, String title, String channel, String link) {
        NotificationService service = new NotificationService(notifications);
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification created = service.create(5L, type, title, "content", channel, link);

        assertThat(created.getUserId()).isEqualTo(5L);
        assertThat(created.getType()).isEqualTo(type);
        assertThat(created.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(created.getChannel()).isEqualTo(NotificationChannel.valueOf(channel));
        assertThat(created.getSentAt()).isNotNull();
    }

    @Test
    void create_givenNullChannel_defaultsToInApp() {
        NotificationService service = new NotificationService(notifications);
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(5L, "TICKETS_ISSUED", "title", "content", null, "/tickets");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
    }

    private static Notification notification(Long id, String type, Instant readAt) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(5L);
        n.setType(type);
        n.setTitle(type + " title");
        n.setContent("content");
        n.setChannel(NotificationChannel.IN_APP);
        n.setStatus(NotificationStatus.SENT);
        n.setLinkUrl("/tickets");
        n.setSentAt(Instant.now());
        n.setCreatedAt(Instant.now());
        n.setReadAt(readAt);
        return n;
    }
}
