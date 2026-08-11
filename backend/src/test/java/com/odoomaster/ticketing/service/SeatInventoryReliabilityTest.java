package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.catalog.internal.CatalogFixtures;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.catalog.SeatInventory.SeatDetail;
import com.odoomaster.ticketing.catalog.internal.CacheConfig;
import com.odoomaster.ticketing.catalog.internal.SeatInventoryImpl;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reliability tests for {@link SeatInventoryImpl} — the seat {@code AVAILABLE → LOCKED → SOLD} state
 * machine, lock TTL, and event-cache eviction that moved out of {@code OrderService} in Sprint 2.
 * These carry the double-booking / contention / lock-expiry guarantees at the layer that now owns
 * them. (True cross-transaction atomicity is enforced by the {@code @Version} optimistic lock at the
 * DB level; here we assert the in-transaction status guards.)
 */
@ExtendWith(MockitoExtension.class)
class SeatInventoryReliabilityTest {

    @Mock EventSeatRepository seats;
    @Mock CacheManager cacheManager;

    SeatInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new SeatInventoryImpl(seats, cacheManager, Clock.systemUTC());
    }

    @Test
    void lockSeats_givenAvailableSeats_locksForBuyerAndReturnsPricedDetails() {
        EventSeat a1 = seat(10L, SeatStatus.AVAILABLE, BigDecimal.valueOf(100_000));
        EventSeat a2 = seat(11L, SeatStatus.AVAILABLE, BigDecimal.valueOf(150_000));
        when(seats.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(a1, a2));

        List<SeatDetail> details = inventory.lockSeats(1L, 5L, List.of(10L, 11L));

        assertThat(a1.getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(a1.lockedBy()).isEqualTo(5L);
        assertThat(a1.lockedUntil()).isAfter(Instant.now());
        assertThat(a2.getStatus()).isEqualTo(SeatStatus.LOCKED);
        verify(seats).saveAll(List.of(a1, a2));
        assertThat(details).extracting(SeatDetail::id).containsExactly(10L, 11L);
        assertThat(details).extracting(SeatDetail::price)
                .containsExactly(BigDecimal.valueOf(100_000), BigDecimal.valueOf(150_000));
    }

    @Test
    void lockSeats_givenExpiredLock_relocksForCurrentBuyer() {
        EventSeat s = seat(10L, 1L, SeatStatus.LOCKED, BigDecimal.TEN, 44L, Instant.now().minusSeconds(1));
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        inventory.lockSeats(1L, 9L, List.of(10L));

        assertThat(s.getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(s.lockedBy()).isEqualTo(9L);
        assertThat(s.lockedUntil()).isAfter(Instant.now());
    }

    @ParameterizedTest
    @EnumSource(value = SeatStatus.class, names = {"SOLD", "LOCKED"})
    void lockSeats_givenUnavailableSeat_rejectsContention(SeatStatus status) {
        // A live hold (or a sold seat) must reject a second buyer.
        EventSeat s = seat(10L, 1L, status, BigDecimal.TEN, 44L, Instant.now().plusSeconds(60));
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> inventory.lockSeats(1L, 5L, List.of(10L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no longer available");
        verify(seats, never()).saveAll(any());
    }

    @Test
    void lockSeats_afterSeatLocked_secondBuyerIsRejected_preventingDoubleBooking() {
        EventSeat s = seat(10L, SeatStatus.AVAILABLE, BigDecimal.TEN);
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        inventory.lockSeats(1L, 5L, List.of(10L)); // first buyer takes the hold
        assertThat(s.getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(s.lockedBy()).isEqualTo(5L);

        // A second buyer contending for the same live-locked seat must be rejected.
        assertThatThrownBy(() -> inventory.lockSeats(1L, 9L, List.of(10L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no longer available");
        assertThat(s.lockedBy()).isEqualTo(5L); // still held by the first buyer
    }

    @Test
    void lockSeats_givenSeatFromDifferentEvent_rejectsCrossEventLock() {
        EventSeat s = seat(10L, 2L, SeatStatus.AVAILABLE, BigDecimal.TEN, null, null);
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> inventory.lockSeats(1L, 5L, List.of(10L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not in this event");
    }

    @Test
    void lockSeats_givenMissingSeat_rejects() {
        when(seats.findByIdIn(List.of(404L))).thenReturn(List.of());

        assertThatThrownBy(() -> inventory.lockSeats(1L, 5L, List.of(404L)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("One or more seats not found");
        verify(seats, never()).saveAll(any());
    }

    @Test
    void lockSeats_evictsEventCaches() {
        EventSeat s = seat(10L, SeatStatus.AVAILABLE, BigDecimal.TEN);
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));
        Cache seatsCache = mock(Cache.class);
        Cache detailCache = mock(Cache.class);
        Cache listCache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.EVENT_SEATS)).thenReturn(seatsCache);
        when(cacheManager.getCache(CacheConfig.EVENT_DETAIL)).thenReturn(detailCache);
        when(cacheManager.getCache(CacheConfig.EVENTS_LIST)).thenReturn(listCache);

        inventory.lockSeats(1L, 5L, List.of(10L));

        verify(seatsCache).evict(1L);
        verify(detailCache).evict(1L);
        verify(listCache).clear();
    }

    @Test
    void markSold_givenLockedSeat_sellsAndClearsLock() {
        EventSeat s = seat(10L, 1L, SeatStatus.LOCKED, BigDecimal.valueOf(120_000), 5L,
                Instant.now().plusSeconds(60));
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        inventory.markSold(1L, List.of(10L));

        assertThat(s.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(s.lockedBy()).isNull();
        assertThat(s.lockedUntil()).isNull();
        verify(seats).saveAll(List.of(s));
    }

    @ParameterizedTest
    @CsvSource({
            "SOLD,Seat already sold",
            "LOCKED,Seat lock expired"
    })
    void markSold_givenSeatUnsellable_rejectsBeforeSelling(SeatStatus status, String message) {
        EventSeat s = seat(10L, 1L, status, BigDecimal.TEN, 5L, Instant.now().minusSeconds(5));
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> inventory.markSold(1L, List.of(10L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(message);
    }

    @Test
    void releaseLocks_releasesLockedSeatsAndLeavesOthersUntouched() {
        EventSeat locked = seat(10L, 1L, SeatStatus.LOCKED, BigDecimal.TEN, 5L, Instant.now().plusSeconds(60));
        EventSeat sold = seat(11L, SeatStatus.SOLD, BigDecimal.TEN);
        when(seats.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(locked, sold));

        inventory.releaseLocks(1L, List.of(10L, 11L));

        assertThat(locked.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(locked.lockedBy()).isNull();
        assertThat(locked.lockedUntil()).isNull();
        assertThat(sold.getStatus()).isEqualTo(SeatStatus.SOLD); // a sold seat is never reclaimed by cancel
        verify(seats).saveAll(List.of(locked, sold));
    }

    @Test
    void releaseSold_reclaimsSoldSeatsAndLeavesOthersUntouched() {
        EventSeat sold = seat(10L, SeatStatus.SOLD, BigDecimal.TEN);
        EventSeat locked = seat(11L, 1L, SeatStatus.LOCKED, BigDecimal.TEN, 5L, Instant.now().plusSeconds(60));
        when(seats.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(sold, locked));

        inventory.releaseSold(1L, List.of(10L, 11L));

        assertThat(sold.getStatus()).isEqualTo(SeatStatus.AVAILABLE); // freed for resale on cancel/refund
        assertThat(sold.lockedBy()).isNull();
        assertThat(sold.lockedUntil()).isNull();
        assertThat(locked.getStatus()).isEqualTo(SeatStatus.LOCKED); // only SOLD seats are reclaimed here
        verify(seats).saveAll(List.of(sold, locked));
    }

    @Test
    void findSeats_returnsDetailsForFoundSeats() {
        EventSeat s = seat(10L, SeatStatus.SOLD, BigDecimal.valueOf(99_000));
        when(seats.findByIdIn(List.of(10L))).thenReturn(List.of(s));

        assertThat(inventory.findSeats(List.of(10L))).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(10L);
            assertThat(d.price()).isEqualByComparingTo("99000");
            assertThat(d.status()).isEqualTo("SOLD");
            assertThat(d.section()).isEqualTo("MAIN");
            assertThat(d.ticketTypeId()).isEqualTo(3L);
        });
    }

    private static EventSeat seat(Long id, SeatStatus status, BigDecimal price) {
        return seat(id, 1L, status, price, null, null);
    }

    /** A seat optionally held by {@code lockedBy} until {@code lockedUntil} — contention/expiry cases. */
    private static EventSeat seat(Long id, Long eventId, SeatStatus status, BigDecimal price,
                                  Long lockedBy, Instant lockedUntil) {
        return CatalogFixtures.seat(id, eventId, "MAIN", "A", String.valueOf(id), price, status,
                lockedUntil == null ? null : CatalogFixtures.lock(lockedBy == null ? 5L : lockedBy, lockedUntil));
    }
}
