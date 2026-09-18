package com.example.app.shared.error;

import com.example.app.shared.api.RequestIdFilter;
import com.example.app.shared.config.SecurityProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Builds Problem Details bodies carrying the template extensions {@code code}, {@code msg} and
 * {@code requestId} plus a {@code timestamp}, as required by the OpenAPI contract.
 */
@Component
public class ProblemDetailFactory {

    private final SecurityProperties properties;
    private final Clock clock;

    public ProblemDetailFactory(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ProblemDetail create(ErrorCode errorCode, String detail, String msgOverride, List<FieldErrorDto> fieldErrors) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(errorCode.status(), detail != null ? detail : errorCode.defaultMsg());
        problem.setType(URI.create(properties.problemTypeBaseUrl() + "/" + slug(errorCode)));
        problem.setTitle(errorCode.title());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("msg", msgOverride != null ? msgOverride : errorCode.defaultMsg());
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        problem.setProperty("timestamp", Instant.now(clock).toString());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("errors", fieldErrors);
        }
        String instance = currentInstance();
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        return problem;
    }

    public ProblemDetail create(ErrorCode errorCode) {
        return create(errorCode, null, null, List.of());
    }

    private String slug(ErrorCode errorCode) {
        return errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String currentInstance() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String uri = attributes.getRequest().getRequestURI();
            return uri != null && !uri.isEmpty() ? uri : null;
        }
        return null;
    }
}
