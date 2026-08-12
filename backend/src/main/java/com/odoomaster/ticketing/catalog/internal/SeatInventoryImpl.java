package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.shared.AppException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Catalog-owned implementation of {@link SeatInventory}.
 *
 * <p>Since ADR-0013 this class is deliberately thin: load the seats, let each {@link EventSeat}
 * decide whether the transition is legal, save, evict. The {@code AVAILABLE → LOCKED → SOLD} rules
 * and the lock TTL live in the aggregate and {@link LockPolicy}, so the sweeper and the admin
 * screens go through the same checks rather than around them.
 *
 * <p>What this class still owns, and must keep owning:
 * <ul>
 *   <li><strong>{@code @Transactional} on every mutator.</strong> Called from inside
 *       {@code OrderService.pay()} these join that transaction, so the seat check and the sale
 *       commit atomically. Narrowing this to {@code REQUIRES_NEW} would split the sale into
 *       separate transactions and reintroduce double-booking.</li>
 *   <li><strong>Cache eviction after every mutation</strong>, registered against the
 *       transaction-aware cache manager so it fires on commit and availability is never read
 *       stale.</li>
 *   <li><strong>The {@link Clock}.</strong> The aggregate never reads the time itself; this is
 *       where "now" enters the domain.</li>
 * </ul>
 */
@Service
public class SeatInventoryImpl implements SeatInventory {

  private final EventSeatRepository seats;
  private final CacheManager cacheManager;
  private final Clock clock;

  public SeatInventoryImpl(EventSeatRepository seats, CacheManager cacheManager, Clock clock) {
    this.seats = seats;
    this.cacheManager = cacheManager;
    this.clock = clock;
  }

  @Override
  @Transactional
  public List<SeatDetail> lockSeats(Long eventId, Long userId, List<Long> seatIds) {
    List<EventSeat> picked = seats.findByIdIn(seatIds);
    if (picked.size() != seatIds.size()) {
      throw new AppException("SEAT_NOT_FOUND", "One or more seats not found.", HttpStatus.NOT_FOUND);
    }
    Instant now = clock.instant();
    return apply(eventId, picked, seat -> {
      seat.requireBelongsTo(eventId);
      seat.lockFor(userId, now, LockPolicy.DEFAULT);
    });
  }

  @Override
  @Transactional
  public List<SeatDetail> markSold(Long eventId, List<Long> seatIds) {
    Instant now = clock.instant();
    return apply(eventId, seats.findByIdIn(seatIds), seat -> seat.markSold(now));
  }

  @Override
  @Transactional
  public void releaseLocks(Long eventId, List<Long> seatIds) {
    apply(eventId, seats.findByIdIn(seatIds), EventSeat::releaseHold);
  }

  @Override
  @Transactional
  public void releaseSold(Long eventId, List<Long> seatIds) {
    apply(eventId, seats.findByIdIn(seatIds), EventSeat::releaseSale);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SeatDetail> findSeats(List<Long> seatIds) {
    return seats.findByIdIn(seatIds).stream().map(SeatInventoryImpl::toDetail).toList();
  }

  /**
   * Run one aggregate transition over every picked seat, then persist and invalidate together.
   *
   * <p>All-or-nothing by construction: a transition that throws aborts before {@code saveAll}, and
   * the surrounding transaction rolls back whatever the caller had already done.
   */
  private List<SeatDetail> apply(Long eventId, List<EventSeat> picked, Consumer<EventSeat> transition) {
    picked.forEach(transition);
    seats.saveAll(picked);
    evictEventCaches(eventId);
    return picked.stream().map(SeatInventoryImpl::toDetail).toList();
  }

  /**
   * Evict the event's seat-map and detail caches and clear the events list. Colocated with the seat
   * mutations so availability is never served stale.
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

  /** Map the aggregate to the published projection; {@code status} crosses the boundary as a String. */
  private static SeatDetail toDetail(EventSeat s) {
    return new SeatDetail(s.getId(), s.getTicketTypeId(), s.getRowLabel(),
        s.getSeatNumber(), s.getSection(), s.getPrice(), s.getStatus().name());
  }
}
