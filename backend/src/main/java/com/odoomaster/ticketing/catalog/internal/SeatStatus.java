package com.odoomaster.ticketing.catalog.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of a single {@link EventSeat} in the {@code AVAILABLE → LOCKED → SOLD} state machine.
 *
 * <p>Module-internal by design (ADR-0013 rule 1): the published {@code catalog::inventory} facet
 * exposes {@code SeatDetail.status} as a {@code String}, so this vocabulary can evolve without a
 * cross-module break. Persisted as {@code EnumType.STRING} into {@code event_seats.status}
 * VARCHAR(16) — the constant names are the on-disk values and must not be renamed.
 */
public enum SeatStatus {

    /** Free to be held by any buyer. */
    AVAILABLE,

    /** Held by one buyer until {@code locked_until}; re-lockable once that instant passes. */
    LOCKED,

    /** Paid for. Only {@code releaseSold} (ticket cancellation) returns it to {@link #AVAILABLE}. */
    SOLD;

    /**
     * Parse a persisted or caller-supplied status, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<SeatStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
