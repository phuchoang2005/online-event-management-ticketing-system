package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.catalog.internal.Event;
import com.odoomaster.ticketing.catalog.internal.EventStatus;
import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.catalog.internal.EventRepository;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import com.odoomaster.ticketing.catalog.internal.EventCatalogImpl;
import com.odoomaster.ticketing.shared.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import com.odoomaster.ticketing.catalog.internal.CatalogFixtures;

/**
 * Reliability tests for {@link EventCatalogImpl} — the "event on sale" guard that moved out of
 * {@code OrderService} in Sprint 2, plus the {@link EventSummary} projection consumers now read.
 */
@ExtendWith(MockitoExtension.class)
class EventCatalogReliabilityTest {

    @Mock EventRepository events;
    @Mock EventSeatRepository seats;

    EventCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new EventCatalogImpl(events, seats);
    }

    @Test
    void requireOnSale_givenPublishedEvent_returnsSummary() {
        when(events.findById(1L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        EventSummary summary = catalog.requireOnSale(1L);

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.title()).isEqualTo("Concert");
        assertThat(summary.location()).isEqualTo("Main Hall");
        assertThat(summary.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void requireOnSale_givenMissingEvent_throwsNotFound() {
        when(events.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.requireOnSale(1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Event not found")
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "COMPLETED"})
    void requireOnSale_givenEventNotPublished_rejectsSale(EventStatus status) {
        when(events.findById(1L)).thenReturn(Optional.of(event(status)));

        assertThatThrownBy(() -> catalog.requireOnSale(1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not currently on sale")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void find_givenExistingEvent_returnsSummary() {
        when(events.findById(1L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        assertThat(catalog.find(1L)).get()
                .extracting(EventSummary::title).isEqualTo("Concert");
    }

    @Test
    void find_givenMissingEvent_returnsEmpty() {
        when(events.findById(9L)).thenReturn(Optional.empty());

        assertThat(catalog.find(9L)).isEmpty();
    }

    @Test
    void listForReporting_mapsEventsToStatsProjection() {
        when(events.findAllForAdmin()).thenReturn(List.of(event(EventStatus.PUBLISHED)));

        assertThat(catalog.listForReporting()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo(1L);
            assertThat(s.title()).isEqualTo("Concert");
            assertThat(s.status()).isEqualTo("PUBLISHED");
            assertThat(s.categoryNames()).isEmpty();
        });
    }

    @Test
    void countAggregates_delegateToRepositories() {
        when(events.count()).thenReturn(60L);
        when(events.countByStatus(EventStatus.PUBLISHED)).thenReturn(58L);
        when(seats.countAll()).thenReturn(3659L);
        when(seats.countAllSold()).thenReturn(42L);

        assertThat(catalog.countEvents()).isEqualTo(60L);
        assertThat(catalog.countEventsByStatus("PUBLISHED")).isEqualTo(58L);
        assertThat(catalog.countAllSeats()).isEqualTo(3659L);
        assertThat(catalog.countSoldSeats()).isEqualTo(42L);
    }

    private static Event event(EventStatus status) {
        return CatalogFixtures.event(1L, status);
    }
}
