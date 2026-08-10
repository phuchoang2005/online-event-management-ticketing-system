package com.odoomaster.ticketing.shared;

import org.springframework.modulith.NamedInterface;

/**
 * Domain event published once an order is paid and its tickets are issued.
 *
 * <p>It decouples the order/payment flow from notification delivery (the
 * <strong>Observer</strong> pattern, realised with Spring's {@code ApplicationEventPublisher}).
 * {@code OrderService} publishes this event; {@code NotificationEventListener} observes it and
 * creates the in-app notification, so neither knows about the other.
 *
 * <p>Part of the {@code shared::contracts} named interface.
 *
 * @param userId recipient of the notification
 * @param orderId the paid order
 * @param eventTitle title of the event the tickets belong to (may be {@code null})
 * @param ticketCount number of tickets issued
 * @param paymentMethod the provider/method used to pay
 */
@NamedInterface("contracts")
public record TicketsIssuedEvent(
        Long userId,
        Long orderId,
        String eventTitle,
        int ticketCount,
        String paymentMethod) {
}
