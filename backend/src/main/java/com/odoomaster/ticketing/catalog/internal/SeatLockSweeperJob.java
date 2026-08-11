package com.odoomaster.ticketing.catalog.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduled sweeper that reclaims seats whose 10-minute hold has expired.
 *
 * <p>
 * Every 30 seconds it finds seats past their {@code lockedUntil}, resets them
 * to
 * {@code AVAILABLE} (clearing {@code lockedBy}/{@code lockedUntil}), and evicts
 * the affected
 * events from the seat cache so availability is re-read fresh. This is the
 * safety net that
 * prevents abandoned checkouts from holding inventory indefinitely.
 */
@Component
@Slf4j
public class SeatLockSweeperJob {

  private final EventSeatRepository seats;
  private final CacheManager cacheManager;

  public SeatLockSweeperJob(EventSeatRepository seats, CacheManager cacheManager) {
    this.seats = seats;
    this.cacheManager = cacheManager;
  }

  /**
   * Release all expired seat locks and evict the seat cache for the affected
   * events.
   * Runs every 30 seconds in its own transaction; a no-op when nothing has
   * expired.
   */
  @Scheduled(fixedDelay = 30_000)
  @Transactional
  public void releaseExpiredLocks() {
    List<EventSeat> expired = seats.findExpiredLocks(Instant.now());
    if (expired.isEmpty())
      return;

    Set<Long> affectedEvents = new HashSet<>();
    for (EventSeat s : expired) {
      s.setStatus(SeatStatus.AVAILABLE);
      s.setLockedBy(null);
      s.setLockedUntil(null);
      affectedEvents.add(s.getEventId());
    }
    seats.saveAll(expired);

    Cache seatsCache = cacheManager.getCache(CacheConfig.EVENT_SEATS);
    if (seatsCache != null) {
      affectedEvents.forEach(seatsCache::evict);
    }

    log.info("Sweeper: released {} expired seat locks across {} events", expired.size(), affectedEvents.size());
  }
}
