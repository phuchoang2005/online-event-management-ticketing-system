package com.odoomaster.ticketing.shared;

import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

/**
 * A {@link DomainException} that pins the HTTP {@link HttpStatus} to respond with.
 *
 * <p>Thrown by the <strong>service layer</strong>, which sits at the HTTP boundary and legitimately
 * knows that "event not published" is a {@code 409} while "event not found" is a {@code 404}.
 * {@code GlobalExceptionHandler} maps it to the standard {@code ApiErrorEnvelope} so the frontend
 * receives a consistent {@code { error: { code, message, … } }} body.
 *
 * <p>Aggregates throw the plain {@link DomainException} instead — see its javadoc and ADR-0013 §4.
 * Making this a subclass keeps every existing call site and {@code catch} clause working unchanged.
 *
 * <p>Part of the {@code shared::errors} named interface.
 */
@NamedInterface("errors")
public class AppException extends DomainException {
    private final HttpStatus status;

    /**
     * @param code stable, machine-readable error code (e.g. {@code SEAT_TAKEN})
     * @param message human-readable message
     * @param status the HTTP status to respond with
     */
    public AppException(String code, String message, HttpStatus status) {
        super(code, message);
        this.status = status;
    }

    /** @return the HTTP status to respond with */
    public HttpStatus getStatus() { return status; }
}
