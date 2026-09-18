package {{ package }}.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs first for every request: accepts a syntactically valid {@code X-Request-ID} or generates a
 * new UUID, always echoes it on the response, and seeds MDC with the request id plus the service
 * and environment markers used by structured logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String REQUEST_ID_ATTRIBUTE = "app.requestId";
    public static final String SERVICE_MDC_KEY = "service";
    public static final String ENVIRONMENT_MDC_KEY = "environment";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{7,63}$");
    private static final List<String> REMOVABLE_MDC_KEYS = List.of(
            REQUEST_ID_MDC_KEY, SERVICE_MDC_KEY, ENVIRONMENT_MDC_KEY);

    private final String environment;
    private final String service;

    public RequestIdFilter(Environment environment) {
        this.environment = String.join(
                ",", environment.getActiveProfiles().length > 0 ? List.of(environment.getActiveProfiles()) : List.of("default"));
        this.service = environment.getProperty("spring.application.name", "springboot-template");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = incoming != null && VALID_REQUEST_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(SERVICE_MDC_KEY, service);
        MDC.put(ENVIRONMENT_MDC_KEY, environment);
        try {
            filterChain.doFilter(request, response);
        } finally {
            REMOVABLE_MDC_KEYS.forEach(MDC::remove);
        }
    }
}
