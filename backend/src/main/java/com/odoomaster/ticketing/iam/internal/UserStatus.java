package com.odoomaster.ticketing.iam.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Account state of a {@link User}.
 *
 * <p>Single-valued today — registration writes {@code ACTIVE} and nothing deactivates an account
 * yet. Modelled as an enum so the eventual {@code SUSPENDED}/{@code DELETED} lands in a vocabulary
 * rather than as another bare string literal.
 *
 * <p>Persisted as {@code EnumType.STRING} into {@code users.status} VARCHAR(20).
 */
public enum UserStatus {

    /** Able to sign in and buy. */
    ACTIVE;

    /**
     * Parse a persisted or caller-supplied value, tolerating case and surrounding whitespace.
     *
     * @return the matching constant, or {@link Optional#empty()} for null, blank, or unknown input —
     *         never throws, so an unrecognised value degrades to "no match" rather than a 500
     */
    public static Optional<UserStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAMember) {
            return Optional.empty();
        }
    }
}
