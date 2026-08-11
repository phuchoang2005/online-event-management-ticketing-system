package com.odoomaster.ticketing.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ADR-0013 §4 error contract: aggregates may throw an HTTP-free {@link DomainException},
 * and the response the client sees is indistinguishable from the {@link AppException} the service
 * layer throws today.
 *
 * <p>Lives in {@code com.odoomaster.ticketing.shared} so it can see the package-private
 * {@code ErrorCatalog}. Test sources are outside the module model that {@code ModularityTests}
 * analyses, so this does not widen any published surface.
 */
class DomainErrorHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── the subtype relationship that keeps ~40 existing call sites working ──────────────

    @Test
    void appExceptionIsADomainException_soExistingCatchesAndAssertionsStillMatch() {
        AppException ex = new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.getCode()).isEqualTo("EVENT_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Event not found.");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── ErrorCatalog resolution ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "VALIDATION_FAILED,BAD_REQUEST",
            "SEAT_NOT_IN_EVENT,BAD_REQUEST",
            "INVALID_CREDENTIALS,UNAUTHORIZED",
            "FORBIDDEN,FORBIDDEN",
            "ACCOUNT_INACTIVE,FORBIDDEN",
            "EVENT_NOT_FOUND,NOT_FOUND",
            "ORDER_NOT_FOUND,NOT_FOUND",
            "TICKET_NOT_FOUND,NOT_FOUND",
            "ROLE_NOT_SEEDED,INTERNAL_SERVER_ERROR"
    })
    void registeredCodesResolveToTheStatusTheAppExceptionCallSitesUse(String code, HttpStatus expected) {
        assertThat(ErrorCatalog.statusFor(code)).isEqualTo(expected);
    }

    /**
     * Every aggregate-level rule in this system answers {@code 409}, so an unregistered code —
     * which is what a brand-new aggregate invariant will be — is already correct without anyone
     * remembering to add a table entry.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SEAT_TAKEN", "LOCK_EXPIRED", "ORDER_STATE_INVALID", "ORDER_ALREADY_PAID",
            "TICKET_ALREADY_USED", "SEAT_SOLD_IMMUTABLE", "A_RULE_INVENTED_TOMORROW"})
    void unregisteredCodesDefaultToConflict(String code) {
        assertThat(ErrorCatalog.statusFor(code)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nullCodeDefaultsToConflictRatherThanThrowing() {
        assertThat(ErrorCatalog.statusFor(null)).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── the rendered response ───────────────────────────────────────────────────────────

    @Test
    void domainException_rendersTheStandardEnvelopeWithTheCatalogStatus() {
        ResponseEntity<ApiErrorEnvelope> response =
                handler.handleDomain(new DomainException("SEAT_TAKEN", "Seat A-1 is no longer available."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorEnvelope.ErrorBody body = response.getBody().error();
        assertThat(body.code()).isEqualTo("SEAT_TAKEN");
        assertThat(body.message()).isEqualTo("Seat A-1 is no longer available.");
        assertThat(body.details()).isEmpty();
    }

    /**
     * An {@code AppException} must keep its pinned status even though it is now a
     * {@code DomainException} — Spring dispatches to the most specific handler, and the two
     * handlers must not disagree. {@code EVENT_NOT_PUBLISHED} is the sharp case: it is absent from
     * the catalog, so routing it through the wrong handler would still yield 409 by default and
     * hide the bug. {@code EVENT_NOT_FOUND} (catalog: 404) proves the dispatch explicitly.
     */
    @Test
    void appException_keepsItsPinnedStatusRatherThanTheCatalogDefault() {
        ResponseEntity<ApiErrorEnvelope> response =
                handler.handleApp(new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().code()).isEqualTo("EVENT_NOT_FOUND");
    }
}
