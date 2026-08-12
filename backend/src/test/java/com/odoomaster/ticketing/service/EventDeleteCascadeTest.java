package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.sales.internal.Order;
import com.odoomaster.ticketing.sales.internal.OrderStatus;
import com.odoomaster.ticketing.sales.internal.SalesFixtures;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import com.odoomaster.ticketing.sales.internal.OrderItem;
import com.odoomaster.ticketing.sales.internal.OrderItemRepository;
import com.odoomaster.ticketing.sales.internal.OrderRepository;
import com.odoomaster.ticketing.sales.internal.Payment;
import com.odoomaster.ticketing.sales.internal.PaymentRepository;
import com.odoomaster.ticketing.sales.internal.SalesEventCleanupListener;
import com.odoomaster.ticketing.shared.EventDeletedEvent;
import com.odoomaster.ticketing.ticketing.internal.CheckInRepository;
import com.odoomaster.ticketing.ticketing.internal.Ticket;
import com.odoomaster.ticketing.ticketing.internal.TicketRepository;
import com.odoomaster.ticketing.ticketing.internal.TicketingEventCleanupListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.odoomaster.ticketing.ticketing.internal.TicketingFixtures;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;

/**
 * Covers the {@link EventDeletedEvent} cascade that replaced {@code AdminEventService}'s hand-written
 * foreign-row deletes in Sprint 3: each owning module purges its own rows via a synchronous listener.
 */
@ExtendWith(MockitoExtension.class)
class EventDeleteCascadeTest {

    // ── sales ────────────────────────────────────────────────────────────────

    @Mock OrderRepository orders;
    @Mock OrderItemRepository orderItems;
    @Mock PaymentRepository payments;

    @Test
    void salesListener_purgesItemsThenPaymentsThenOrder_perOrder() {
        SalesEventCleanupListener listener = new SalesEventCleanupListener(orders, orderItems, payments);
        Order order = order(100L);
        OrderItem item = SalesFixtures.orderItem(200L, 100L, 10L, java.math.BigDecimal.TEN);
        Payment payment = SalesFixtures.payment(300L, 100L, PaymentStatus.SUCCEEDED, java.math.BigDecimal.TEN);
        when(orders.findByEventId(9L)).thenReturn(List.of(order));
        when(orderItems.findByOrderId(100L)).thenReturn(List.of(item));
        when(payments.findByOrderId(100L)).thenReturn(List.of(payment));

        listener.onEventDeleted(new EventDeletedEvent(9L));

        InOrder ordered = inOrder(orderItems, payments, orders);
        ordered.verify(orderItems).deleteAll(List.of(item));
        ordered.verify(payments).deleteAll(List.of(payment));
        ordered.verify(orders).delete(order);
    }

    @Test
    void salesListener_noOrders_purgesNothing() {
        SalesEventCleanupListener listener = new SalesEventCleanupListener(orders, orderItems, payments);
        when(orders.findByEventId(9L)).thenReturn(List.of());

        listener.onEventDeleted(new EventDeletedEvent(9L));

        verify(orders, never()).delete(any());
        verify(orderItems, never()).deleteAll(any());
        verify(payments, never()).deleteAll(any());
    }

    // ── ticketing ──────────────────────────────────────────────────────────────

    @Mock TicketRepository tickets;
    @Mock CheckInRepository checkIns;

    @Test
    void ticketingListener_purgesCheckInsBeforeTickets_forFkSafety() {
        TicketingEventCleanupListener listener = new TicketingEventCleanupListener(tickets, checkIns);
        Ticket t1 = ticket(7L);
        Ticket t2 = ticket(8L);
        when(tickets.findByEventId(9L)).thenReturn(List.of(t1, t2));

        listener.onEventDeleted(new EventDeletedEvent(9L));

        InOrder ordered = inOrder(checkIns, tickets);
        ordered.verify(checkIns).deleteByTicketIdIn(List.of(7L, 8L));
        ordered.verify(tickets).deleteAll(List.of(t1, t2));
    }

    @Test
    void ticketingListener_noTickets_skipsCheckInPurge() {
        TicketingEventCleanupListener listener = new TicketingEventCleanupListener(tickets, checkIns);
        when(tickets.findByEventId(9L)).thenReturn(List.of());

        listener.onEventDeleted(new EventDeletedEvent(9L));

        verify(checkIns, never()).deleteByTicketIdIn(any());
        verify(tickets, never()).deleteAll(any());
    }

    private static Order order(Long id) {
        return SalesFixtures.order(id, OrderStatus.PENDING, 5L, 9L, java.math.BigDecimal.TEN);
    }

    private static Ticket ticket(Long id) {
        return TicketingFixtures.ticket(id, TicketStatus.VALID, 4L, 9L, 3L, "qr-" + id);
    }
}
