package com.odoomaster.ticketing.ticketing.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Outcome recorded on a {@link CheckIn} row.
 *
 * <p>Single-valued today: a scan that fails validation throws rather than persisting a row, so only
 * successful check-ins reach the table. Modelled as an enum anyway so the column has a vocabulary
 * and a future {@code DENIED}/{@code MANUAL} outcome has somewhere to go.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code check_ins.status} VARCHAR(20).
 */
public enum CheckInStatus {

    /** Scan accepted; the ticket transitioned to {@code USED}. */
    OK;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<CheckInStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
