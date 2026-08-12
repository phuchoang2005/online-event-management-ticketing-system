package com.odoomaster.ticketing.sales.payment;

import java.util.Locale;
import java.util.Optional;

/**
 * Payment provider chosen by the buyer, and the key {@link PaymentGatewayResolver} dispatches on.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code orders.payment_method} and
 * {@code payments.provider}, both VARCHAR(20).
 *
 * <p>Note that {@code OrderDtos.PayRequest.method()} deliberately stays a {@code String} guarded by
 * {@code @Pattern(regexp = "MOMO|VNPAY|MOCK")}. An enum-typed request component would make Jackson
 * throw {@code HttpMessageNotReadableException} on a bad value, which falls through to the catch-all
 * handler as {@code 500 INTERNAL_ERROR} instead of the {@code 400 VALIDATION_FAILED} the API
 * contract promises (ADR-0013 §1).
 */
public enum PaymentMethod {

    /** MoMo e-wallet. */
    MOMO,

    /** VNPAY gateway. */
    VNPAY,

    /** Built-in mock provider used by the demo and the dev profile. */
    MOCK;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<PaymentMethod> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
