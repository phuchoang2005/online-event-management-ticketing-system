package com.odoomaster.ticketing.feedback.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Triage state of a {@link Feedback} item.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code feedbacks.status} VARCHAR(20). Replaces
 * {@code FeedbackService.VALID_STATUSES}.
 */
public enum FeedbackStatus {

    /** Submitted, not yet triaged. */
    NEW,

    /** Seen by staff. */
    READ,

    /** Closed out. Stamps {@code resolvedAt} on entry; the stamp is never cleared afterwards. */
    RESOLVED;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<FeedbackStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
