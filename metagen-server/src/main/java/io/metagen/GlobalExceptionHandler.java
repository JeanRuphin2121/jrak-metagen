package io.metagen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping.
 *
 * <p>Uses Spring's {@link ProblemDetail} (RFC 7807) for consistent,
 * machine-readable error responses across all endpoints.</p>
 *
 * <h2>Response format</h2>
 * <pre>
 * {
 *   "type":   "about:blank",
 *   "title":  "Not Found",
 *   "status": 404,
 *   "detail": "Entity not found: supplier_x"
 * }
 * </pre>
 *
 * <h2>Validation errors</h2>
 * <pre>
 * {
 *   "status": 400,
 *   "title":  "Validation Failed",
 *   "detail": "Request body has validation errors",
 *   "errors": { "name": "name must be snake_case", "type": "must not be null" }
 * }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Entity or resource not found → 404 */
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Bad input (duplicate name, invalid state, etc.) → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** RBAC violation → 403 */
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleForbidden(SecurityException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Workflow transition not allowed → 422 */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleUnprocessable(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** Bean validation failures (@Valid) → 400 with per-field errors */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid",
                (a, b) -> a   // keep first error per field
            ));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request body has validation errors");
        detail.setTitle("Validation Failed");
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    /** Catch-all → 500 (log full stack trace) */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred — check server logs");
    }
}
