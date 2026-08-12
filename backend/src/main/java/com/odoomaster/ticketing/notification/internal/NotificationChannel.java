package com.odoomaster.ticketing.notification.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Transport a {@link Notification} was delivered over.
 *
 * <p>Only the in-app inbox is delivered today (ADR-0004 keeps the notifications table as the queue,
 * with no broker), but {@code NotificationService.create} accepts a caller-supplied channel and
 * persists it verbatim, so {@link #EMAIL} and {@link #SMS} are part of the written vocabulary and
 * are modelled here. This is the distinction ADR-0013 §1 draws: a value some caller <em>writes</em>
 * belongs in the enum; a value only ever <em>queried</em> (the analytics fictions on
 * {@code OrderStatus}) does not.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code notifications.channel} VARCHAR(20).
 */
public enum NotificationChannel {

    /** The in-app inbox backed by the {@code notifications} table. The only channel delivered today. */
    IN_APP,

    /** Queued for e-mail delivery. Accepted and persisted; no dispatcher exists yet. */
    EMAIL,

    /** Queued for SMS delivery. Accepted and persisted; no dispatcher exists yet. */
    SMS;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<NotificationChannel> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
