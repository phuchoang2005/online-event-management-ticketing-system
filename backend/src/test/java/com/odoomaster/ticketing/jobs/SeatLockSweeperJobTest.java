package com.odoomaster.ticketing.jobs;
import com.odoomaster.ticketing.catalog.internal.SeatLockSweeperJob;

import com.odoomaster.ticketing.catalog.internal.CacheConfig;
import com.odoomaster.ticketing.catalog.internal.CatalogFixtures;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatLockSweeperJobTest {

    @Mock EventSeatRepository seatRepo;
    @Mock CacheManager cacheManager;
    @Mock Cache seatsCache;

    /**
     * Built explicitly rather than via {@code @InjectMocks}: the job now takes the application's
     * {@link Clock} so {@link EventSeat#releaseExpiredLock} can re-check expiry against the same
     * instant the query used, and a mocked {@code Clock} would hand the aggregate a null instant.
     */
    SeatLockSweeperJob job;

    @BeforeEach
    void setUp() {
        job = new SeatLockSweeperJob(seatRepo, cacheManager, Clock.systemUTC());
    }

    @Test
    void noExpiredLocks_doesNothing() {
        when(seatRepo.findExpiredLocks(any())).thenReturn(List.of());

        job.releaseExpiredLocks();

        verify(seatRepo, never()).saveAll(any());
        verify(cacheManager, never()).getCache(any());
    }

    @Test
    void expiredLocks_resetToAvailable() {
        EventSeat s1 = lockedSeat(1L, 10L);
        EventSeat s2 = lockedSeat(2L, 10L);
        when(seatRepo.findExpiredLocks(any())).thenReturn(List.of(s1, s2));
        when(cacheManager.getCache(CacheConfig.EVENT_SEATS)).thenReturn(seatsCache);

        job.releaseExpiredLocks();

        assertThat(s1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(s1.lockedBy()).isNull();
        assertThat(s1.lockedUntil()).isNull();
        assertThat(s2.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(seatRepo).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(s1, s2);
    }

    @Test
    void expiredLocks_evictsCachePerEvent() {
        EventSeat s1 = lockedSeat(1L, 10L);
        EventSeat s2 = lockedSeat(2L, 20L);
        EventSeat s3 = lockedSeat(3L, 10L);
        when(seatRepo.findExpiredLocks(any())).thenReturn(List.of(s1, s2, s3));
        when(cacheManager.getCache(CacheConfig.EVENT_SEATS)).thenReturn(seatsCache);

        job.releaseExpiredLocks();

        verify(seatsCache).evict(10L);
        verify(seatsCache).evict(20L);
        verifyNoMoreInteractions(seatsCache);
    }

    @Test
    void nullSeatsCacheDoesNotThrow() {
        EventSeat s = lockedSeat(1L, 5L);
        when(seatRepo.findExpiredLocks(any())).thenReturn(List.of(s));
        when(cacheManager.getCache(CacheConfig.EVENT_SEATS)).thenReturn(null);

        job.releaseExpiredLocks();

        verify(seatRepo).saveAll(any());
    }

    private static EventSeat lockedSeat(Long id, Long eventId) {
        return CatalogFixtures.lockedSeat(id, eventId, 99L, Instant.now().minusSeconds(60));
    }
}
