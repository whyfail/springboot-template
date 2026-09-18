package com.example.app.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.app.shared.config.SecurityProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemDetailFactory(
            new SecurityProperties(
                    Duration.ofHours(8),
                    Duration.ofDays(30),
                    5,
                    12,
                    5,
                    20,
                    List.of("http://localhost:5173"),
                    "https://docs.example.com/problems",
                    false),
            Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC)));

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/example");

    @Test
    void businessExceptionMapsToItsErrorCode() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            ResponseEntity<ProblemDetail> response =
                    handler.handleBusiness(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "user missing"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getProperties()).containsEntry("code", "RESOURCE_NOT_FOUND");
            assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/api/v1/example"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void rateLimitedExceptionCarriesRetryAfterHeader() {
        ResponseEntity<ProblemDetail> response =
                handler.handleRateLimited(new RateLimitedException(Duration.ofSeconds(45)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("45");
        assertThat(response.getBody().getProperties()).containsEntry("code", "RATE_LIMITED");
    }

    @Test
    void optimisticLockFailureBecomesVersionConflict() {
        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("UserEntity", 1L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "VERSION_CONFLICT");
    }

    @Test
    void dataIntegrityViolationBecomesConflict() {
        ResponseEntity<ProblemDetail> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("dup"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "DATA_CONFLICT");
    }

    @Test
    void unknownRouteBecomes404() {
        ResponseEntity<ProblemDetail> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/missing", "No static resource"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void unsupportedMethodBecomes405() {
        ResponseEntity<ProblemDetail> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
    }

    @Test
    void unexpectedExceptionNeverLeaksDetails() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("jdbc secret stack"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(response.getBody().getDetail()).doesNotContain("jdbc");
    }
}
