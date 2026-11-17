package io.metagen;

import io.metagen.engine.config.ConfigValidationException;
import io.metagen.engine.crud.FieldValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.NoSuchElementException;

/**
 * One place that maps exceptions to RFC 7807 responses.
 *
 * <p>Status meaning: 400 invalid request or data · 403 not allowed · 404 not found ·
 * 409 conflict with existing data · 422 request understood but a business rule forbids it.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such endpoint");
    }

    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The record has validation errors");
        detail.setTitle("Validation Failed");
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(ConfigValidationException.class)
    public ProblemDetail handleConfigValidation(ConfigValidationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The configuration is invalid — nothing was applied");
        detail.setTitle("Invalid Configuration");
        detail.setProperty("errors", ex.getErrors());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleUnprocessable(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", rootMessage(ex));
        String msg = rootMessage(ex);
        String detail = msg.contains("duplicate key")
            ? "A record with the same unique value already exists"
            : msg.contains("foreign key")
                ? "The operation conflicts with a referenced or referencing record"
                : "The operation violates a data constraint";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
            fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid"));
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request body has validation errors");
        detail.setTitle("Validation Failed");
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleUnreadable(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "The request could not be read: check the JSON body and the path / query parameters");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Missing request parameter '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported Content-Type. Supported: " + ex.getSupportedMediaTypes());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
            "Method " + ex.getMethod() + " is not supported on this endpoint");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred — check server logs");
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? "" : root.getMessage();
    }
}
