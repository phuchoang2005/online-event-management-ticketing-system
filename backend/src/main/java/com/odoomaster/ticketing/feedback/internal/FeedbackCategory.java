package com.odoomaster.ticketing.feedback.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Subject area a {@link Feedback} item is filed under.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code feedbacks.category} VARCHAR(32). Replaces
 * {@code FeedbackService.VALID_CATEGORIES}.
 */
public enum FeedbackCategory {

    /** Default when the submitter picks nothing. */
    GENERAL,

    /** About a specific event's content or organisation. */
    EVENT,

    /** About checkout, refunds, or a payment provider. */
    PAYMENT,

    /** A defect report. */
    BUG_REPORT,

    /** A feature request or improvement idea. */
    SUGGESTION;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<FeedbackCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
