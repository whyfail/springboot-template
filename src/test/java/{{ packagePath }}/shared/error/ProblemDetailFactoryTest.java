package {{ package }}.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import {{ package }}.shared.config.SecurityProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailFactoryTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-09-18T10:00:00Z");

    private final SecurityProperties properties = new SecurityProperties(
            Duration.ofHours(8),
            Duration.ofDays(30),
            5,
            12,
            5,
            20,
            List.of("http://localhost:5173"),
            "https://docs.example.com/problems",
            false);

    private final ProblemDetailFactory factory =
            new ProblemDetailFactory(properties, Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

    @Test
    void buildsCompleteProblemBody() {
        ProblemDetail problem = factory.create(
                ErrorCode.USERNAME_ALREADY_EXISTS,
                "username taken",
                null,
                List.of(new FieldErrorDto("username", "exists")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getType()).isEqualTo(URI.create("https://docs.example.com/problems/username-already-exists"));
        assertThat(problem.getDetail()).isEqualTo("username taken");
        assertThat(problem.getProperties()).containsEntry("code", "USERNAME_ALREADY_EXISTS");
        assertThat(problem.getProperties()).containsEntry("msg", "用户名已存在");
        assertThat(problem.getProperties()).containsEntry("timestamp", FIXED_TIME.toString());
        assertThat(problem.getProperties().get("errors")).isEqualTo(List.of(new FieldErrorDto("username", "exists")));
    }

    @Test
    void msgOverrideWinsOverDefaultMessage() {
        ProblemDetail problem = factory.create(ErrorCode.AUTH_INVALID_CREDENTIALS, null, "账号或密码错误", List.of());

        assertThat(problem.getDetail()).isEqualTo("账号或密码错误");
        assertThat(problem.getProperties()).containsEntry("msg", "账号或密码错误");
        assertThat(problem.getProperties()).containsEntry("code", "AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void requestIdOmittedWhenMdcEmpty() {
        ProblemDetail problem = factory.create(ErrorCode.INTERNAL_ERROR);

        assertThat(problem.getProperties()).doesNotContainKey("requestId");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
