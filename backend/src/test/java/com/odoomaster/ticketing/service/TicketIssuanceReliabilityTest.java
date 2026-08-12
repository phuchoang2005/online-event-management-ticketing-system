package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.ticketing.internal.Ticket;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;
import com.odoomaster.ticketing.ticketing.TicketIssuance;
import com.odoomaster.ticketing.ticketing.TicketIssuance.TicketLine;
import com.odoomaster.ticketing.ticketing.TicketIssuance.TicketOrder;
import com.odoomaster.ticketing.ticketing.internal.TicketRepository;
import com.odoomaster.ticketing.ticketing.internal.TicketIssuanceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reliability tests for {@link TicketIssuanceImpl} — the QR-ticket issuance that moved out of
 * {@code OrderService} in Sprint 2. Carries the ticket-shape and QR-uniqueness-under-concurrency
 * guarantees that previously lived in {@code OrderServiceReliabilityTest}.
 */
@ExtendWith(MockitoExtension.class)
class TicketIssuanceReliabilityTest {

    @Mock TicketRepository tickets;

    TicketIssuance issuance;

    @BeforeEach
    void setUp() {
        issuance = new TicketIssuanceImpl(tickets);
    }

    @Test
    void issueForOrder_createsOneValidTicketPerLineWithQrShape() {
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int issued = issuance.issueForOrder(new TicketOrder(5L, 1L, List.of(new TicketLine(30L, 10L))));

        assertThat(issued).isEqualTo(1);
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(captor.capture());
        Ticket t = captor.getValue();
        assertThat(t.getOrderItemId()).isEqualTo(30L);
        assertThat(t.getUserId()).isEqualTo(5L);
        assertThat(t.getEventId()).isEqualTo(1L);
        assertThat(t.getEventSeatId()).isEqualTo(10L);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.VALID);
        assertThat(t.getQrCode()).hasSize(32).matches("[0-9A-F]+");
    }

    @Test
    void issueForOrder_issuesOneTicketPerLine_forMultiSeatOrder() {
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int issued = issuance.issueForOrder(new TicketOrder(5L, 1L, List.of(
                new TicketLine(1L, 10L), new TicketLine(2L, 11L), new TicketLine(3L, 12L))));

        assertThat(issued).isEqualTo(3);
        verify(tickets, times(3)).save(any());
    }

    @Test
    void issueForOrder_concurrentOrders_generateUniqueQrCodes() throws Exception {
        int count = 40;
        Set<String> qrCodes = ConcurrentHashMap.newKeySet();
        when(tickets.save(any())).thenAnswer(inv -> {
            Ticket ticket = inv.getArgument(0);
            qrCodes.add(ticket.getQrCode());
            return ticket;
        });

        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            long userId = i;
            tasks.add(() -> {
                start.await(2, TimeUnit.SECONDS);
                issuance.issueForOrder(new TicketOrder(userId, 1L,
                        List.of(new TicketLine(userId * 10, userId * 100))));
                return null;
            });
        }

        var futures = tasks.stream().map(pool::submit).toList();
        start.countDown();
        for (var future : futures) future.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(qrCodes).hasSize(count);
        assertThat(qrCodes).allMatch(qr -> qr.length() == 32 && qr.matches("[0-9A-F]+"));
    }
}
