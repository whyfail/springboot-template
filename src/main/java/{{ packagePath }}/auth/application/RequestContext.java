package {{ package }}.auth.application;

import jakarta.servlet.http.HttpServletRequest;

/** Request-scoped metadata captured for audit records; only hashed fingerprints are persisted. */
public record RequestContext(String clientIp, String userAgent) {

    public static RequestContext from(HttpServletRequest request) {
        return new RequestContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
