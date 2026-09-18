package com.example.app.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Emits one structured HTTP access log line per request. The path is always the matched route
 * template, never the raw URL, so no identifiers leak into logs or metric labels. The principal id
 * is provided by the authentication filter through a request attribute because the security context
 * is already cleared when this filter completes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ID_ATTRIBUTE = "app.principalId";

    private static final Logger accessLog = LoggerFactory.getLogger("http.access");
    private static final String[] REMOVABLE_MDC_KEYS = {"method", "pathTemplate", "status", "durationMs", "principalId", "event"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            Object template = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String principalId = String.valueOf(request.getAttribute(PRINCIPAL_ID_ATTRIBUTE));
            MDC.put("event", "http_access");
            MDC.put("method", request.getMethod());
            MDC.put("pathTemplate", template != null ? template.toString() : request.getRequestURI());
            MDC.put("status", Integer.toString(status));
            MDC.put("durationMs", Long.toString(durationMs));
            if (!"null".equals(principalId)) {
                MDC.put("principalId", principalId);
            }
            try {
                if (status >= 500) {
                    accessLog.error("HTTP {} {} -> {}", request.getMethod(), request.getRequestURI(), status);
                } else if (status >= 400) {
                    accessLog.warn("HTTP {} {} -> {}", request.getMethod(), request.getRequestURI(), status);
                } else {
                    accessLog.info("HTTP {} {} -> {}", request.getMethod(), request.getRequestURI(), status);
                }
            } finally {
                for (String key : REMOVABLE_MDC_KEYS) {
                    MDC.remove(key);
                }
            }
        }
    }
}
