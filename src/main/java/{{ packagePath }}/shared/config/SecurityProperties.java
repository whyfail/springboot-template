package {{ package }}.shared.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security-related runtime configuration. All values are provided by application.yml defaults and
 * may be overridden per profile or through environment variables; secrets never carry defaults in
 * production profiles.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull Duration sessionTtl,
        @NotNull Duration rememberedSessionTtl,
        @Min(1) @Max(100) int maxSessionsPerUser,
        @Min(4) @Max(15) int bcryptStrength,
        @Min(1) @Max(100) int loginAttemptsPerMinute,
        @Min(1) @Max(10000) int loginAttemptsPerHour,
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @NotBlank String problemTypeBaseUrl,
        boolean swaggerUiEnabled) {

    public SecurityProperties {
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
        if (rememberedSessionTtl.compareTo(sessionTtl) < 0) {
            throw new IllegalArgumentException("rememberedSessionTtl must not be shorter than sessionTtl");
        }
    }
}
