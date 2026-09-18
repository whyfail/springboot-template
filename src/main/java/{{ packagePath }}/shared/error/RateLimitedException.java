package {{ package }}.shared.error;

import java.time.Duration;
import java.util.List;

/** Login rate-limit rejection carrying the duration clients should wait before retrying. */
public class RateLimitedException extends BusinessException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, "Login rate limit exceeded", null, List.of());
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
