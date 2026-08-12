package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.catalog.SeatInventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduled sweeper that reclaims seats whose 10-minute hold has expired.
 *
 * <p>Every 30 seconds it finds seats past their {@code lockedUntil} and asks each one to
 * {@link EventSeat#releaseExpiredLock release itself}, then evicts the affected events from the
 * seat cache so availability is re-read fresh. This is the safety net that prevents abandoned
 * checkouts from holding inventory indefinitely.
 *
 * <p>Since ADR-0013 it goes through the aggregate rather than writing {@code setStatus} directly.
 * It stays in {@code catalog.internal} and calls {@link EventSeat} rather than gaining a method on
 * the published {@link SeatInventory} facet: the invariant that matters is "only the aggregate
 * mutates seat state", not "only the facet does", and widening the facet for a scheduled job would
 * hand every consumer a way to mass-release locks.
 *
 * <p>The hold duration itself belongs to {@link LockPolicy}, not to this class.
 */
@Component
@Slf4j
public class SeatLockSweeperJob {

  private final EventSeatRepository seats;
  private final CacheManager cacheManager;
  private final Clock clock;

  public SeatLockSweeperJob(EventSeatRepository seats, CacheManager cacheManager, Clock clock) {
    this.seats = seats;
    this.cacheManager = cacheManager;
    this.clock = clock;
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
    List<EventSeat> expired = seats.findExpiredLocks(clock.instant());
    if (expired.isEmpty())
      return;

    Instant now = clock.instant();
    Set<Long> affectedEvents = new HashSet<>();
    for (EventSeat s : expired) {
      // The aggregate decides — and re-checks expiry against the same instant, so a seat whose
      // lock was renewed between the query and here is left alone rather than yanked from its buyer.
      if (s.releaseExpiredLock(now)) {
        affectedEvents.add(s.getEventId());
      }
    }
    if (affectedEvents.isEmpty())
      return;
    seats.saveAll(expired);

    Cache seatsCache = cacheManager.getCache(CacheConfig.EVENT_SEATS);
    if (seatsCache != null) {
      affectedEvents.forEach(seatsCache::evict);
    }

    log.info("Sweeper: released {} expired seat locks across {} events", expired.size(), affectedEvents.size());
  }
}
