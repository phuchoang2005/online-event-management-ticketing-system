package com.odoomaster.ticketing.catalog.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Publication lifecycle of an {@link Event}.
 *
 * <p>Module-internal by design (ADR-0013 rule 1): {@code catalog::events} exposes
 * {@code EventSummary.status} as a {@code String}. Persisted as {@code EnumType.STRING} into
 * {@code events.status} VARCHAR(20) — the constant names are the on-disk values.
 *
 * <p>Replaces {@code AdminEventService.ALLOWED_STATUSES}, which was a private {@code Set<String>}
 * living three classes away from the column it governed.
 */
public enum EventStatus {

    /** Being prepared; invisible to the public catalog and not orderable. */
    DRAFT,

    /** Visible and on sale. The only state {@code EventCatalog.requireOnSale} accepts. */
    PUBLISHED,

    /** Called off. Retained for history rather than deleted. */
    CANCELLED,

    /** Finished. Retained for reporting. */
    COMPLETED;

    /** @see SeatStatus#parse(String) */
    public static Optional<EventStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
