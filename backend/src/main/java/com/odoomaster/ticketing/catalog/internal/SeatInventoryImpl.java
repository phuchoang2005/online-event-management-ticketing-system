package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.shared.AppException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Catalog-owned implementation of {@link SeatInventory}: the concurrency crux
 * of the system now
 * lives here rather than in {@code sales}' {@code OrderService}.
 *
 * <p>
 * Holds the {@code AVAILABLE → LOCKED → SOLD} state machine, the
 * {@value #LOCK_TTL_MINUTES}-minute
 * lock TTL, and the event-cache eviction that must accompany any seat mutation.
 * Mutating methods are
 * {@code @Transactional}, so when called from within the ordering transaction
 * they join it (the sale
 * stays atomic) and their cache evictions — registered against the
 * transaction-aware cache manager —
 * fire on commit.
 */
@Service
public class SeatInventoryImpl implements SeatInventory {

  /**
   * How long, in minutes, a created order holds its seats before the sweeper may
   * release them.
   */
  private static final int LOCK_TTL_MINUTES = 10;

  private final EventSeatRepository seats;
  private final CacheManager cacheManager;

  public SeatInventoryImpl(EventSeatRepository seats, CacheManager cacheManager) {
    this.seats = seats;
    this.cacheManager = cacheManager;
  }

  @Override
  @Transactional
  public List<SeatDetail> lockSeats(Long eventId, Long userId, List<Long> seatIds) {
    List<EventSeat> picked = seats.findByIdIn(seatIds);
    if (picked.size() != seatIds.size()) {
      throw new AppException("SEAT_NOT_FOUND", "One or more seats not found.", HttpStatus.NOT_FOUND);
    }

    Instant now = Instant.now();
    Instant lockUntil = now.plus(LOCK_TTL_MINUTES, ChronoUnit.MINUTES);
    for (EventSeat s : picked) {
      if (!Objects.equals(s.getEventId(), eventId)) {
        throw new AppException("SEAT_NOT_IN_EVENT", "Seat " + s.getId() + " is not in this event.",
            HttpStatus.BAD_REQUEST);
      }
      boolean lockExpired = s.getLockedUntil() != null && s.getLockedUntil().isBefore(now);
      if (s.getStatus() != SeatStatus.AVAILABLE && !(s.getStatus() == SeatStatus.LOCKED && lockExpired)) {
        throw new AppException("SEAT_TAKEN",
            "Seat " + s.getRowLabel() + "-" + s.getSeatNumber() + " is no longer available.", HttpStatus.CONFLICT);
      }
      s.setStatus(SeatStatus.LOCKED);
      s.setLockedBy(userId);
      s.setLockedUntil(lockUntil);
    }
    seats.saveAll(picked);
    evictEventCaches(eventId);
    return picked.stream().map(SeatInventoryImpl::toDetail).toList();
  }

  @Override
  @Transactional
  public List<SeatDetail> markSold(Long eventId, List<Long> seatIds) {
    List<EventSeat> picked = seats.findByIdIn(seatIds);

    Instant now = Instant.now();
    for (EventSeat s : picked) {
      if (s.getStatus() == SeatStatus.SOLD) {
        throw new AppException("SEAT_TAKEN", "Seat already sold.", HttpStatus.CONFLICT);
      }
      if (s.getLockedUntil() != null && s.getLockedUntil().isBefore(now) && s.getStatus() != SeatStatus.AVAILABLE) {
        throw new AppException("LOCK_EXPIRED", "Seat lock expired; please re-select seats.", HttpStatus.CONFLICT);
      }
      s.setStatus(SeatStatus.SOLD);
      s.setLockedBy(null);
      s.setLockedUntil(null);
    }
    seats.saveAll(picked);
    evictEventCaches(eventId);
    return picked.stream().map(SeatInventoryImpl::toDetail).toList();
  }

  @Override
  @Transactional
  public void releaseLocks(Long eventId, List<Long> seatIds) {
    List<EventSeat> picked = seats.findByIdIn(seatIds);
    for (EventSeat s : picked) {
      if (s.getStatus() == SeatStatus.LOCKED) {
        s.setStatus(SeatStatus.AVAILABLE);
        s.setLockedBy(null);
        s.setLockedUntil(null);
      }
    }
    seats.saveAll(picked);
    evictEventCaches(eventId);
  }

  @Override
  @Transactional
  public void releaseSold(Long eventId, List<Long> seatIds) {
    List<EventSeat> picked = seats.findByIdIn(seatIds);
    for (EventSeat s : picked) {
      if (s.getStatus() == SeatStatus.SOLD) {
        s.setStatus(SeatStatus.AVAILABLE);
        s.setLockedBy(null);
        s.setLockedUntil(null);
      }
    }
    seats.saveAll(picked);
    evictEventCaches(eventId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SeatDetail> findSeats(List<Long> seatIds) {
    return seats.findByIdIn(seatIds).stream().map(SeatInventoryImpl::toDetail).toList();
  }

  /**
   * Evict the event's seat-map and detail caches (by key) and clear the events
   * list. Colocated with
   * the seat mutations so availability is never read stale — the responsibility
   * that used to live in
   * {@code OrderService}'s {@code @CacheEvict}.
   */
  private void evictEventCaches(Long eventId) {
    evict(CacheConfig.EVENT_SEATS, eventId);
    evict(CacheConfig.EVENT_DETAIL, eventId);
    Cache list = cacheManager.getCache(CacheConfig.EVENTS_LIST);
    if (list != null)
      list.clear();
  }

  private void evict(String cacheName, Object key) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null)
      cache.evict(key);
  }

  private static SeatDetail toDetail(EventSeat s) {
    return new SeatDetail(s.getId(), s.getTicketTypeId(), s.getRowLabel(),
        s.getSeatNumber(), s.getSection(), s.getPrice(), s.getStatus().name());
  }
}
