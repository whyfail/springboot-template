package {{ package }}.auth.infrastructure.security;

import {{ package }}.shared.api.RequestLogFilter;
import {{ package }}.shared.error.ErrorCode;
import {{ package }}.shared.error.ProblemDetailFactory;
import {{ package }}.shared.security.AuthenticatedUser;
import {{ package }}.shared.security.Hashes;
import {{ package }}.shared.security.SessionData;
import {{ package }}.shared.security.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opaque-token authentication: reads the Bearer token, hashes it and loads the Redis session. A
 * missing or invalid token leaves the request unauthenticated (the authorization layer answers
 * 401); a Redis outage fails CLOSED with a 503 Problem Details body - it must never degrade to
 * anonymous access.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionStore sessionStore;
    private final ProblemDetailFactory problemFactory;
    private final JsonMapper problemMapper;

    public BearerTokenAuthenticationFilter(SessionStore sessionStore, ProblemDetailFactory problemFactory, JsonMapper problemMapper) {
        this.sessionStore = sessionStore;
        this.problemFactory = problemFactory;
        this.problemMapper = problemMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String digest = Hashes.sha256Hex(token);
        Optional<SessionData> session;
        try {
            session = sessionStore.load(digest);
        } catch (DataAccessException e) {
            log.error("Session store unavailable while authenticating request: {}", e.getMessage());
            failClosed(response);
            return;
        }
        if (session.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        SessionData data = session.get();
        if (data.expiresAt().isBefore(Instant.now())) {
            sessionStore.revoke(digest);
            filterChain.doFilter(request, response);
            return;
        }
        AuthenticatedUser principal = new AuthenticatedUser(
                data.userId(),
                data.publicId(),
                data.username(),
                data.displayName(),
                data.avatarUrl(),
                data.roles(),
                data.permissions(),
                digest,
                data.issuedAt(),
                data.expiresAt());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(RequestLogFilter.PRINCIPAL_ID_ATTRIBUTE, principal.publicId().toString());
        filterChain.doFilter(request, response);
    }

    private void failClosed(HttpServletResponse response) throws IOException {
        ProblemDetail problem = problemFactory.create(ErrorCode.SESSION_STORE_UNAVAILABLE);
        response.setStatus(ErrorCode.SESSION_STORE_UNAVAILABLE.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        problemMapper.writeValue(response.getOutputStream(), problem);
    }
}
