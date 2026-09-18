package com.wl.cwa.shared.error;

import com.wl.cwa.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates framework and business exceptions into the unified Problem Details contract. 4xx
 * failures log a concise warning, 5xx failures log the full stack server-side while the response
 * body never contains stack traces or SQL.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        logProblem(request, ex.errorCode().status(), ex.getMessage());
        return ResponseEntity.status(ex.errorCode().status()).body(problemFactory.create(ex.errorCode(), ex.getMessage(), ex.msgOverride(), ex.fieldErrors()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        // Reached from @PreAuthorize on authenticated sessions; anonymous callers are rejected by
        // the authorization filter before any controller runs.
        logProblem(request, ErrorCode.AUTH_FORBIDDEN.status(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.AUTH_FORBIDDEN.status())
                .body(problemFactory.create(ErrorCode.AUTH_FORBIDDEN, "Insufficient permissions", null, List.of()));
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex, HttpServletRequest request) {
        logProblem(request, HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        long seconds = Math.max(1, ex.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problemFactory.create(ErrorCode.RATE_LIMITED, ex.getMessage(), null, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDto(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", fieldErrors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDto(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request parameter validation failed", fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request parameter validation failed", fieldErrors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Parameter '" + ex.getName() + "' has an invalid format", List.of(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Missing required parameter '" + ex.getParameterName() + "'", List.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(ErrorCode.VALIDATION_FAILED, "Malformed request body", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No handler for " + request.getMethod() + " " + request.getRequestURI(), List.of(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No handler for " + request.getMethod() + " " + request.getRequestURI(), List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.VERSION_CONFLICT, "The resource was modified concurrently", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.DATA_CONFLICT, "The request conflicts with stored data", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {} requestId={}", request.getMethod(), request.getRequestURI(), request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of(), request);
    }

    private FieldErrorDto toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return new FieldErrorDto(field, violation.getMessage());
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode errorCode, String detail, List<FieldErrorDto> fieldErrors, HttpServletRequest request) {
        logProblem(request, errorCode.status(), detail);
        return ResponseEntity.status(errorCode.status()).body(problemFactory.create(errorCode, detail, null, fieldErrors));
    }

    private void logProblem(HttpServletRequest request, HttpStatus status, String detail) {
        if (status.is5xxServerError()) {
            log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(), detail);
        } else {
            log.debug("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(), detail);
        }
    }
}
