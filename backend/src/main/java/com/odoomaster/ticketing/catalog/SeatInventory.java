package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.shared.AppException;

import org.springframework.modulith.NamedInterface;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published catalog API that owns seat inventory and its concurrency-critical state machine.
 *
 * <p>Seats move {@code AVAILABLE → LOCKED → SOLD} (or back to {@code AVAILABLE} on release/expiry).
 * The lock TTL, the seat status transitions, and eviction of the event caches all live behind this
 * API, so callers such as {@code sales}' {@code OrderService} drive inventory without touching the
 * {@code EventSeat} entity or its repository. Every mutating method runs in the caller's transaction
 * (so the ordering flow stays a single atomic unit) and evicts the affected event's caches on commit.
 *
 * <p>Exposed as the {@code catalog::inventory} named interface, kept separate from
 * {@code catalog::events} so a module that only reads event metadata cannot reach the
 * concurrency-critical seat state machine.
 */
@NamedInterface("inventory")
public interface SeatInventory {

    /**
     * Hold the given seats for a buyer for the standard lock TTL.
     *
     * <p>Validates every seat exists, belongs to {@code eventId}, and is {@code AVAILABLE} (or holds
     * an already-expired {@code LOCKED} lock), then transitions each to {@code LOCKED} with
     * {@code lockedBy}/{@code lockedUntil} set. Evicts the event caches.
     *
     * @param eventId the event the seats must belong to
     * @param userId the buyer taking the hold
     * @param seatIds the seats to lock
     * @return the priced details of the locked seats, in request order
     * @throws AppException {@code SEAT_NOT_FOUND}, {@code SEAT_NOT_IN_EVENT}, or {@code SEAT_TAKEN}
     */
    List<SeatDetail> lockSeats(Long eventId, Long userId, List<Long> seatIds);

    /**
     * Transition the given seats to {@code SOLD}, clearing their lock. Re-checks that no seat is
     * already {@code SOLD} and that no non-available seat's lock has expired. Evicts the event caches.
     *
     * @param eventId the event whose caches to evict
     * @param seatIds the seats to sell
     * @return the details of the sold seats
     * @throws AppException {@code SEAT_TAKEN} if already sold, {@code LOCK_EXPIRED} if the hold lapsed
     */
    List<SeatDetail> markSold(Long eventId, List<Long> seatIds);

    /**
     * Release any {@code LOCKED} seats among the given ids back to {@code AVAILABLE} (idempotent for
     * seats in any other status). Evicts the event caches.
     *
     * @param eventId the event whose caches to evict
     * @param seatIds the seats to release
     */
    void releaseLocks(Long eventId, List<Long> seatIds);

    /**
     * Release any {@code SOLD} seats among the given ids back to {@code AVAILABLE} (idempotent for
     * seats in any other status), clearing their lock fields. Used when a ticket is cancelled/refunded
     * so the freed seat can be resold. Evicts the event caches.
     *
     * @param eventId the event whose caches to evict
     * @param seatIds the seats to release
     */
    void releaseSold(Long eventId, List<Long> seatIds);

    /**
     * Look up seat details for rendering (e.g. order line items). Missing ids are simply absent from
     * the result.
     *
     * @param seatIds the seats to read
     * @return their details, in repository order
     */
    List<SeatDetail> findSeats(List<Long> seatIds);

    /**
     * Immutable projection of an {@code EventSeat} exposed across module boundaries.
     */
    @NamedInterface("inventory")
    record SeatDetail(Long id, Long ticketTypeId, String rowLabel, String seatNumber,
                      String section, BigDecimal price, String status) {}
}
