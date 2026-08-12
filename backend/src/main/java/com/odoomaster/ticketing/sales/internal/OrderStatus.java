package com.odoomaster.ticketing.sales.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of an {@link Order}.
 *
 * <p>Module-internal by design (ADR-0013 rule 1): {@code sales::reporting} exposes
 * {@code countOrdersByStatus(String)}, which is what lets {@code analytics} keep asking about
 * statuses this vocabulary does not model. Persisted as {@code EnumType.STRING} into
 * {@code orders.status} VARCHAR(20).
 *
 * <p><strong>Deliberately absent:</strong> {@code EXPIRED} and {@code REFUND_PENDING}. Both are
 * queried by {@code AnalyticsService} but have never been written by any code path, so they are
 * analytics fictions rather than states. {@link #parse} returns empty for them and the reporting
 * facet answers {@code 0} — exactly today's behaviour. Their zero rows mean "not modelled", not
 * "broken". {@link #REFUNDED} is modelled but likewise never written yet.
 */
public enum OrderStatus {

    /** Created, seats held, awaiting payment. The only payable state. */
    PENDING,

    /** Paid; seats are SOLD and tickets issued. Terminal for the buyer. */
    PAID,

    /** Abandoned before payment; the held seats were released. */
    CANCELLED,

    /** Money returned after payment. Modelled for completeness; no code path writes it yet. */
    REFUNDED;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<OrderStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
