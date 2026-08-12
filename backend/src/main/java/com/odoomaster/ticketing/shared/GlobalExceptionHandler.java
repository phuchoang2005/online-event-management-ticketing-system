package com.odoomaster.ticketing.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates exceptions into the uniform {@link ApiErrorEnvelope} response across all controllers.
 *
 * <p>Handles {@link AppException}s (using their pinned code + status), aggregate-thrown
 * {@link DomainException}s (code resolved to a status via {@link ErrorCatalog}), bean-validation failures
 * (field details), authentication/access-denied errors, and any uncaught exception (logged and
 * returned as {@code INTERNAL_ERROR}). Each response carries the current {@code traceId} from the
 * MDC for correlation.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorEnvelope> handleApp(AppException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    /**
     * Handles a {@link DomainException} thrown by an aggregate, which carries a code but no HTTP
     * status. Spring dispatches to the most specific handler, so an {@link AppException} — a
     * subclass — still lands on {@link #handleApp} above and keeps its pinned status; only the
     * HTTP-free ones reach here and get their status from {@link ErrorCatalog}.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(DomainException ex) {
        return build(ErrorCatalog.statusFor(ex.getCode()), ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorEnvelope.FieldDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorEnvelope.FieldDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String firstMsg = details.isEmpty() ? "Dữ liệu không hợp lệ." : details.get(0).reason();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", firstMsg, details);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAccess(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error.", List.of());
    }

    private ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, String code, String message, List<ApiErrorEnvelope.FieldDetail> details) {
        String traceId = MDC.get("traceId");
        return ResponseEntity.status(status)
                .body(new ApiErrorEnvelope(new ApiErrorEnvelope.ErrorBody(code, message, details, traceId)));
    }
}
