package com.odoomaster.ticketing.catalog.internal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Builders for catalog aggregates in whatever state a test needs, including states that application
 * code can only reach through a transition.
 *
 * <p><strong>Why it lives in this package.</strong> {@link EventSeat} has no setters since ADR-0013,
 * and its all-args constructor is package-private — the only way to fake a persisted {@code id} or a
 * {@code SOLD} seat that was never bought. Java grants package-private access across source roots,
 * so a test class declared in {@code com.odoomaster.ticketing.catalog.internal} can use it while
 * application code in other modules still cannot. {@code ModularityTests} analyses only
 * {@code src/main}, so this file is invisible to boundary verification and widens nothing.
 *
 * <p><strong>Caveat.</strong> {@code @AllArgsConstructor} is positional, so reordering
 * {@code EventSeat}'s fields breaks this file. That is a compile error, which is the acceptable
 * form of this cost.
 */
public final class CatalogFixtures {

    private CatalogFixtures() {
    }

    /** A free seat at {@code price}, ready to be locked. */
    public static EventSeat availableSeat(Long id, Long eventId, BigDecimal price) {
        return seat(id, eventId, "MAIN", "A", String.valueOf(id), price, SeatStatus.AVAILABLE, null);
    }

    /** A seat held by {@code userId} until {@code lockedUntil} — expired or live, as the test needs. */
    public static EventSeat lockedSeat(Long id, Long eventId, Long userId, Instant lockedUntil) {
        return seat(id, eventId, "MAIN", "A", String.valueOf(id), BigDecimal.TEN,
                SeatStatus.LOCKED, SeatLock.heldBy(userId, lockedUntil));
    }

    /** A sold seat. Unreachable through the aggregate without first locking and paying. */
    public static EventSeat soldSeat(Long id, Long eventId) {
        return seat(id, eventId, "MAIN", "A", String.valueOf(id), BigDecimal.TEN, SeatStatus.SOLD, null);
    }

    /** Full control, for the matrix tests that sweep every status against every lock state. */
    public static EventSeat seat(Long id, Long eventId, String section, String rowLabel,
                                 String seatNumber, BigDecimal price, SeatStatus status,
                                 SeatLock lock) {
        return new EventSeat(id, eventId, id, 3L, rowLabel, seatNumber, section, price, status, lock, 0);
    }

    /** A hold expiring at {@code expiresAt}, for building seats directly. */
    public static SeatLock lock(Long userId, Instant expiresAt) {
        return SeatLock.heldBy(userId, expiresAt);
    }

    /** An event in {@code status}, with a persisted id. */
    public static Event event(Long id, EventStatus status) {
        Event e = new Event(id, "Concert", null, "Main Hall", new java.util.HashSet<>(), null, null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                status, null, Instant.now());
        return e;
    }

    /**
     * Stamp a generated id onto an aggregate, the way Hibernate does on {@code save()}.
     * See {@code SalesFixtures.withId} for why this is reflection rather than a setter.
     */
    public static <T> T withId(T entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign id to " + entity.getClass().getSimpleName(), e);
        }
    }
}
