package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.catalog.AdminEventService;
import com.odoomaster.ticketing.catalog.SeatCatalogService;
import com.odoomaster.ticketing.catalog.internal.Event;
import com.odoomaster.ticketing.catalog.internal.EventCategoryRepository;
import com.odoomaster.ticketing.catalog.internal.EventRepository;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import com.odoomaster.ticketing.catalog.internal.EventStatus;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.catalog.internal.TicketTypeRepository;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.shared.EventDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the two {@code AdminEventService} behaviours that read a persisted status — the
 * PUBLISHED-delete refusal and the per-section sold/available tally.
 *
 * <p>Both were written as {@code "PUBLISHED".equals(e.getStatus())}. When ADR-0013 retyped the
 * column to an enum those comparisons still <em>compiled</em> — {@code String.equals(Object)}
 * accepts anything — but silently evaluated to {@code false} forever, which would have let an
 * operator delete a live on-sale event and made every section report {@code 0 sold / 0 available}.
 * Neither had any test coverage, so the whole 727-test suite stayed green through the break; it was
 * caught by a literal grep. These tests exist so that cannot happen again.
 */
@ExtendWith(MockitoExtension.class)
class AdminEventServiceReliabilityTest {

    @Mock private EventRepository events;
    @Mock private EventCategoryRepository categories;
    @Mock private EventSeatRepository seats;
    @Mock private TicketTypeRepository ticketTypes;
    @Mock private SeatCatalogService catalog;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminEventService service() {
        return new AdminEventService(events, categories, seats, ticketTypes, catalog, eventPublisher);
    }

    @Test
    void delete_givenPublishedEvent_refusesAndDoesNotCascade() {
        when(events.findById(1L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        assertThatThrownBy(() -> service().delete(1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot delete a PUBLISHED event")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        // The cascade must not fire: downstream modules purge orders and tickets on this event.
        verify(eventPublisher, never()).publishEvent(any(EventDeletedEvent.class));
        verify(events, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "COMPLETED"})
    void delete_givenNonPublishedEvent_cascadesThenRemoves(EventStatus status) {
        Event e = event(status);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        when(seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(1L)).thenReturn(List.of());

        service().delete(1L);

        verify(eventPublisher).publishEvent(any(EventDeletedEvent.class));
        verify(events).delete(e);
    }

    @Test
    void detail_talliesSoldAndAvailableSeatsPerSection() {
        Event e = event(EventStatus.DRAFT);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        when(seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(1L)).thenReturn(List.of(
                seat("MAIN", "A", "1", SeatStatus.SOLD),
                seat("MAIN", "A", "2", SeatStatus.SOLD),
                seat("MAIN", "B", "1", SeatStatus.AVAILABLE),
                seat("MAIN", "B", "2", SeatStatus.LOCKED)));
        when(seats.sumSoldPriceForEvent(1L)).thenReturn(new BigDecimal("200000"));

        var detail = service().detail(1L);

        assertThat(detail.totalSeats()).isEqualTo(4);
        assertThat(detail.soldSeats()).isEqualTo(2);
        // A LOCKED seat is held, not available — it counts toward neither tally.
        assertThat(detail.availableSeats()).isEqualTo(1);
        assertThat(detail.sections()).singleElement().satisfies(section -> {
            assertThat(section.name()).isEqualTo("MAIN");
            assertThat(section.rowCount()).isEqualTo(2);
            assertThat(section.seatCount()).isEqualTo(4);
            assertThat(section.soldCount()).isEqualTo(2);
            assertThat(section.availableCount()).isEqualTo(1);
        });
    }

    private static Event event(EventStatus status) {
        Event e = new Event();
        e.setId(1L);
        e.setTitle("Concert");
        e.setLocation("Main Hall");
        e.setStartTime(Instant.now().plusSeconds(3600));
        e.setEndTime(Instant.now().plusSeconds(7200));
        e.setStatus(status);
        return e;
    }

    private static EventSeat seat(String section, String row, String number, SeatStatus status) {
        EventSeat s = new EventSeat();
        s.setId((long) (section + row + number).hashCode());
        s.setEventId(1L);
        s.setSection(section);
        s.setRowLabel(row);
        s.setSeatNumber(number);
        s.setPrice(new BigDecimal("100000"));
        s.setStatus(status);
        s.setVersion(0);
        return s;
    }
}
