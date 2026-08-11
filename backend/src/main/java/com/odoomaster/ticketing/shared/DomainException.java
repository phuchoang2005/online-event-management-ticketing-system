package com.odoomaster.ticketing.shared;

import org.springframework.modulith.NamedInterface;

/**
 * A business rule was violated, identified by a stable machine-readable {@code code}.
 *
 * <p>This is the exception <strong>aggregates</strong> throw. Unlike {@link AppException} it carries
 * no {@code HttpStatus}: an {@code EventSeat} refusing to be locked twice is a domain fact, and the
 * domain has no business knowing that HTTP calls that {@code 409}. {@code GlobalExceptionHandler}
 * supplies the status by looking the code up in {@link ErrorCatalog}, so the API contract
 * {@code { error: { code, message, details, traceId } }} is identical either way.
 *
 * <p>{@link AppException} extends this class rather than replacing it, so the ~40 existing
 * {@code new AppException(code, message, status)} call sites in the service layer are untouched and
 * a single {@code catch}/{@code assertThatThrownBy} on either type still matches both. Use
 * {@code AppException} when a service deliberately pins a status; use {@code DomainException} from
 * inside an aggregate, where HTTP is not a concept.
 *
 * <p>Part of the {@code shared::errors} named interface. See ADR-0013 §4.
 */
@NamedInterface("errors")
public class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code stable, machine-readable error code (e.g. {@code SEAT_TAKEN}). Register it in
     *             {@link ErrorCatalog} if it should map to anything other than {@code 409 CONFLICT}
     * @param message human-readable message, surfaced to the client
     */
    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return the stable error code */
    public String getCode() {
        return code;
    }
}
