package com.odoomaster.ticketing.catalog.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * How long a buyer may hold seats before the sweeper reclaims them.
 *
 * <p>Gives the ten-minute checkout window a name and exactly one owner. Before ADR-0013 the number
 * lived as {@code SeatInventoryImpl.LOCK_TTL_MINUTES = 10} while {@code SeatLockSweeperJob} — the
 * component that actually enforces it — only mentioned it in prose, so the two could drift without
 * anything failing.
 *
 * <p>Not persisted; it is a rule, not a column.
 *
 * @param ttl how long a hold survives from the moment it is taken
 */
public record LockPolicy(Duration ttl) {

    /**
     * The production window: ten minutes of exclusive hold. Long enough to complete a payment,
     * short enough that an abandoned checkout does not strand inventory through a Golden Hour.
     */
    public static final LockPolicy DEFAULT = new LockPolicy(Duration.ofMinutes(10));

    public LockPolicy {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Lock TTL must be a positive duration, was: " + ttl);
        }
    }

    /** The instant a hold taken at {@code now} lapses. */
    public Instant expiryFrom(Instant now) {
        return now.plus(ttl);
    }
}
