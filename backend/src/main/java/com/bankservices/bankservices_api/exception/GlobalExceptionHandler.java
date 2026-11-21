
package com.bankservices.bankservices_api.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private Map<String, Object> createError(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        Map<String, Object> error = new HashMap<>();

        error.put("timestamp", LocalDateTime.now());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        error.put("path", request.getRequestURI());

        return error;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createError(HttpStatus.NOT_FOUND,
                        ex.getMessage(), request));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(
            InsufficientBalanceException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            IllegalStateException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(createError(HttpStatus.CONFLICT,
                        ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> validationErrors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        validationErrors.toString(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(createError(HttpStatus.CONFLICT,
                        "The operation conflicts with existing database data.",
                        request));
    }

    // Too many requests (raised by the rate-limit filter, which already set the
    // Retry-After and RateLimit-* headers). Generic message, no internal details.
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(createError(HttpStatus.TOO_MANY_REQUESTS,
                        RateLimitExceededException.MESSAGE, request));
    }

    // Valid credentials, but the account is pending activation or disabled
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotActive(
            AccountNotActiveException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(createError(HttpStatus.FORBIDDEN,
                        ex.getMessage(), request));
    }

    // Handle authorization failures
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(createError(
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource.",
                        request
                ));
    }

    // =====================================================================
    // Malformed requests: generic messages, never parser/type internals.
    // =====================================================================

    // Unreadable JSON body (syntax error, unknown enum value, wrong type...).
    // The parser message names Java classes and is therefore not returned.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        "Malformed request body.", request));
    }

    // Path/query value of the wrong type, e.g. /api/comptes/1%20OR%201=1
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        "Invalid value for parameter '" + ex.getName() + "'.", request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        "Missing parameter '" + ex.getParameterName() + "'.", request));
    }

    // Constraint violations on @RequestParam / @PathVariable (method validation).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleParameterValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {

        Map<String, String> validationErrors = new HashMap<>();

        ex.getParameterValidationResults().forEach(result ->
                validationErrors.put(
                        result.getMethodParameter().getParameterName(),
                        result.getResolvableErrors().isEmpty()
                                ? "is invalid"
                                : result.getResolvableErrors().getFirst().getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createError(HttpStatus.BAD_REQUEST,
                        validationErrors.toString(), request));
    }

    // =====================================================================
    // Database and unexpected errors: never SQL, credentials or stack traces.
    // =====================================================================

    // Lock wait timeout / deadlock while another operation holds the same rows.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleLockFailure(
            PessimisticLockingFailureException ex,
            HttpServletRequest request) {

        logSafely("Database lock conflict", ex, request);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(createError(HttpStatus.CONFLICT,
                        "The operation could not be completed because the data is being updated. Please try again.",
                        request));
    }

    // Any other database error (connection, SQL grammar, timeouts...).
    // DataIntegrityViolationException keeps its own, more specific handler above.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseError(
            DataAccessException ex,
            HttpServletRequest request) {

        logSafely("Database error", ex, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createError(HttpStatus.INTERNAL_SERVER_ERROR,
                        "A server error occurred. Please try again later.", request));
    }

    // Last resort. Framework exceptions that carry an HTTP status (404, 405, 415...)
    // keep that status; everything else is a generic 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());

            if (status != null && status.is4xxClientError()) {
                return ResponseEntity.status(status)
                        .headers(errorResponse.getHeaders())
                        .body(createError(status, status.getReasonPhrase() + ".", request));
            }
        }

        logSafely("Unexpected error", ex, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createError(HttpStatus.INTERNAL_SERVER_ERROR,
                        "A server error occurred. Please try again later.", request));
    }

    /**
     * Logs a server-side failure without request data: method, path (no query
     * string), exception type and, for database errors, the SQLState / vendor code.
     * Exception messages can contain SQL or submitted values, so the full stack
     * trace is only written at DEBUG level.
     */
    private static void logSafely(String what, Exception ex, HttpServletRequest request) {
        SQLException sqlException = findSqlException(ex);

        logger.error("{} on {} {}: {}{}",
                what,
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                sqlException == null
                        ? ""
                        : " (SQLState " + sqlException.getSQLState() + ", code " + sqlException.getErrorCode() + ")");

        logger.debug("{} details", what, ex);
    }

    private static SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }
}
