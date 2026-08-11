package com.odoomaster.ticketing.notification.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Delivery state of a {@link Notification}.
 *
 * <p>Single-valued today — the in-app channel writes the row already delivered, so
 * {@code sentAt} is stamped on persist. Modelled as an enum so a future {@code QUEUED}/{@code FAILED}
 * for e-mail or push has somewhere to go without another string vocabulary appearing.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code notifications.status} VARCHAR(20).
 */
public enum NotificationStatus {

    /** Delivered to the recipient's inbox. */
    SENT;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<NotificationStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
