package com.odoomaster.ticketing.sales.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Outcome of a single {@link PaymentRetry} attempt against the gateway.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code payment_retries.status} VARCHAR(20).
 */
public enum PaymentRetryStatus {

    /** The retried charge went through. */
    SUCCEEDED,

    /** The retried charge was declined; {@code errorCode} carries the gateway reason. */
    FAILED;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<PaymentRetryStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
