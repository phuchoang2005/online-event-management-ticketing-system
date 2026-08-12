package com.odoomaster.ticketing.ticketing.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of an issued {@link Ticket}.
 *
 * <p>Module-internal by design (ADR-0013 rule 1): {@code ticketing::reporting} exposes
 * {@code countTicketsByStatus(String)}. Persisted as {@code EnumType.STRING} into
 * {@code tickets.status} VARCHAR(20).
 *
 * <p>Replaces {@code TicketService.TICKET_STATUSES}, which was a private {@code Set<String>}.
 */
public enum TicketStatus {

    /** Issued and scannable at the gate. */
    VALID,

    /** Checked in. Terminal — a used ticket can neither be re-scanned nor deleted. */
    USED,

    /** Withdrawn by the holder; the seat was released back to the inventory. */
    CANCELLED;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<TicketStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
