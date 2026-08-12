package com.odoomaster.ticketing.shared;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Maps a {@link DomainException} error code to the HTTP status the API answers with.
 *
 * <p>Exists so aggregates can throw a code without importing {@code HttpStatus}. Deliberately
 * <strong>not</strong> a {@code @NamedInterface} type: it is an implementation detail of how
 * {@code shared} renders errors, and no other module has any reason to reach it.
 *
 * <p>The table below was extracted from the existing {@code new AppException(code, …, status)} call
 * sites, so a code thrown as a {@code DomainException} gets exactly the status it would have got as
 * an {@code AppException}. Codes absent from the table default to {@link HttpStatus#CONFLICT} —
 * which is what every aggregate-level rule in this system already returns (a seat already taken, a
 * lock expired, an order in the wrong state, a ticket already used). That default is why a new
 * aggregate rule needs no registration here to behave correctly.
 */
final class ErrorCatalog {

    /** The status for a code with no explicit entry: a business rule refused the operation. */
    static final HttpStatus DEFAULT_STATUS = HttpStatus.CONFLICT;

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            // ── 400: the request itself is malformed ────────────────────────────────
            Map.entry("VALIDATION_FAILED", HttpStatus.BAD_REQUEST),
            Map.entry("DUPLICATE_SEATS", HttpStatus.BAD_REQUEST),
            Map.entry("SEAT_NOT_IN_EVENT", HttpStatus.BAD_REQUEST),

            // ── 401 / 403: who you are, or are not ──────────────────────────────────
            Map.entry("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED),
            Map.entry("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED),
            Map.entry("FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN),

            // ── 404: the referenced thing does not exist ────────────────────────────
            Map.entry("EVENT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("SEAT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("SECTION_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("VENUE_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("USER_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("FEEDBACK_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND),

            // ── 500: the deployment is misconfigured, not the caller's fault ────────
            Map.entry("ROLE_NOT_SEEDED", HttpStatus.INTERNAL_SERVER_ERROR));

    private ErrorCatalog() {
    }

    /**
     * @param code the error code carried by the exception
     * @return the mapped status, or {@link #DEFAULT_STATUS} when the code is unregistered or null
     */
    static HttpStatus statusFor(String code) {
        if (code == null) return DEFAULT_STATUS;
        return STATUS_BY_CODE.getOrDefault(code, DEFAULT_STATUS);
    }
}
