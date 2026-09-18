package com.wl.cwa.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton MySQL 8.4 and Redis containers shared by every test context in the JVM. Subclasses are
 * either full {@code @SpringBootTest} or slice tests; both inherit the datasource/Redis property
 * registration through the {@link DynamicPropertySource} method declared here.
 */
public abstract class SharedContainers {

    public static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("cwa");

    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        // Ryuk is disabled on machines where the Docker socket cannot be bind-mounted (e.g. colima
        // on macOS), so the JVM itself is responsible for stopping the shared containers.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
            MYSQL.stop();
        }, "container-test-cleanup"));
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedContainers::mysqlUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.url", SharedContainers::redisUrl);
    }

    public static String mysqlUrl() {
        String base = MYSQL.getJdbcUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    }

    public static String redisUrl() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }
}
