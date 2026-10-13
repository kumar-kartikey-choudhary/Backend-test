package com.pratikdairy.parent.exception;

import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place that turns exceptions into {@link ApiError} responses for every servlet-based
 * service. Services opt in with {@code @Import(GlobalExceptionHandler.class)} on their
 * application class (same pattern as {@code JwtAuthFilter} / {@code AuditConfig}).
 *
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own errors (bad JSON, missing
 * parameter, wrong HTTP method, ...) keep their correct status instead of falling into the
 * catch-all 500 below.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ------------------------------------------------------------ our exceptions

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBase(BaseException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("{} {}: {} ({})", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex.getErrorCode());
        }
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    // ---------------------------------------------------------------- security
    // Method security (@PreAuthorize) and the services' ownership checks throw these from inside
    // the controller. Without explicit handlers the catch-all below would turn them into 500s.

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("{} {}: access denied - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.", request.getRequestURI(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("{} {}: authentication failed - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication failed.", request.getRequestURI(), null);
    }

    // ------------------------------------------------------------ framework safety nets

    /** JPA lookups that bypass our own not-found checks (e.g. getReferenceById). */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found.", request.getRequestURI(), null);
    }

    /** Unique / foreign-key violations the service did not pre-check. Never echo SQL to the client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("{} {}: data integrity violation - {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data.", request.getRequestURI(), null);
    }

    /** A Feign call to another microservice failed; surface it as a gateway error, not our own 500. */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeign(FeignException ex, HttpServletRequest request) {
        log.error("{} {}: downstream call failed with status {}: {}", request.getMethod(),
                request.getRequestURI(), ex.status(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR",
                "A dependent service failed to respond correctly.", request.getRequestURI(), null);
    }

    /** RestTemplate / RestClient calls (e.g. the payment gateway) that were rejected or timed out. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleRestClient(RestClientException ex, HttpServletRequest request) {
        log.error("{} {}: external HTTP call failed: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR",
                "An external service failed to respond correctly.", request.getRequestURI(), null);
    }

    /** Anything unexpected. Logged in full, but the client only sees a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("{} {}: unexpected error", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again shortly.", request.getRequestURI(), null);
    }

    // --------------------------------------------------- Spring MVC's own exceptions

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed.", request, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        // The parser's own message can quote internal class/field names, so keep it generic.
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or unreadable request body.", request, null);
    }

    /** Everything else ResponseEntityExceptionHandler covers: keep Spring's status, use our body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status != null ? status.getReasonPhrase() : "Request failed";
        String code = status != null ? status.name() : "REQUEST_FAILED";
        String message = statusCode.is4xxClientError() ? ex.getMessage() : reason;
        return body(statusCode, code, message, request, null);
    }

    // ----------------------------------------------------------------- helpers

    private ResponseEntity<Object> body(HttpStatusCode status, String code, String message,
                                        WebRequest request, Map<String, String> fieldErrors) {
        String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
        return new ResponseEntity<>(apiError(status, code, message, path, fieldErrors), status);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, String path,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(apiError(status, code, message, path, fieldErrors));
    }

    private ApiError apiError(HttpStatusCode status, String code, String message, String path,
                              Map<String, String> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return new ApiError(LocalDateTime.now(), status.value(),
                resolved != null ? resolved.getReasonPhrase() : "Error", code, message, path, fieldErrors);
    }
}
