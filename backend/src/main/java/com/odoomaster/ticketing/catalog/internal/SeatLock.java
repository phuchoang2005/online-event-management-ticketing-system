package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.Objects;

/**
 * One buyer's temporary hold on a seat: who holds it, and until when.
 *
 * <p>A value object, not an entity — it has no identity of its own and is replaced wholesale rather
 * than mutated. Its reason to exist is that {@code lockedBy} and {@code lockedUntil} are meaningless
 * apart: a seat locked by nobody until midnight, or locked by user 7 until never, are both states
 * the two loose columns could represent and neither is a real thing. Binding them into one object
 * makes those states unconstructible.
 *
 * <p><strong>Schema-neutral.</strong> The field-level {@code @Column} names map straight onto the
 * existing {@code event_seats.locked_by} / {@code locked_until} columns, so no
 * {@code @AttributeOverride} and no migration are needed.
 *
 * <p><strong>Hibernate caveat.</strong> An embeddable whose columns are all NULL is read back as a
 * {@code null} field, not an all-null instance. {@link EventSeat} therefore never exposes the field
 * directly — it null-checks internally and answers {@code lockedBy()} / {@code lockedUntil()} /
 * {@code isLockExpiredAt(...)}.
 *
 * <p>JPA cannot map a {@code record} as an {@code @Embeddable}, hence the class with a private
 * no-arg constructor for Hibernate and a static factory for everyone else.
 */
@Embeddable
public class SeatLock {

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** For Hibernate only. */
    protected SeatLock() {
    }

    private SeatLock(Long lockedBy, Instant lockedUntil) {
        this.lockedBy = lockedBy;
        this.lockedUntil = lockedUntil;
    }

    /**
     * Create a hold for {@code userId} expiring at {@code expiresAt}.
     *
     * @throws IllegalArgumentException if either half is missing — the invariant this type exists for
     */
    static SeatLock heldBy(Long userId, Instant expiresAt) {
        if (userId == null) throw new IllegalArgumentException("A seat lock needs a holder.");
        if (expiresAt == null) throw new IllegalArgumentException("A seat lock needs an expiry.");
        return new SeatLock(userId, expiresAt);
    }

    Long lockedBy() {
        return lockedBy;
    }

    Instant lockedUntil() {
        return lockedUntil;
    }

    /** Whether this hold has lapsed at {@code now}, making the seat re-lockable by another buyer. */
    boolean hasExpiredAt(Instant now) {
        return lockedUntil.isBefore(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeatLock other)) return false;
        return Objects.equals(lockedBy, other.lockedBy) && Objects.equals(lockedUntil, other.lockedUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lockedBy, lockedUntil);
    }

    @Override
    public String toString() {
        return "SeatLock[by=" + lockedBy + ", until=" + lockedUntil + "]";
    }
}
