package com.odoomaster.ticketing.sales.payment;

import java.util.Locale;
import java.util.Optional;

/**
 * Outcome of a charge, as reported by a {@link PaymentGateway} and persisted on the payment row.
 *
 * <p>Owned by the gateway package because the gateway decides the outcome; {@code sales.internal}'s
 * {@code Payment} entity persists it as {@code EnumType.STRING} into {@code payments.status}
 * VARCHAR(20) (an intra-module reference, so no facet is involved).
 *
 * <p>Before ADR-0013 only {@link #SUCCEEDED} was ever written, while {@code AnalyticsService}
 * counted {@link #PENDING} and {@link #FAILED} — so the payment funnel reported zeros by
 * construction. The failure path is wired in Sprint 4; the mock gateway still always succeeds, but
 * the funnel is now capable of moving.
 */
public enum PaymentStatus {

    /** Charge accepted by the gateway. */
    SUCCEEDED,

    /** Charge declined. */
    FAILED,

    /** Charge initiated but not yet resolved (asynchronous providers). */
    PENDING;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<PaymentStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
