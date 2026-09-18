package com.example.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for tests that need MySQL but deliberately control Redis properties themselves (e.g. the
 * fail-closed suite). Reuses the shared MySQL container singleton without inheriting its Redis URL.
 */
@ActiveProfiles("test")
@SpringBootTest
public abstract class SharedMySqlOnly {

    static {
        if (!SharedContainers.MYSQL.isRunning()) {
            SharedContainers.MYSQL.start();
        }
    }

    @DynamicPropertySource
    static void mysqlOnly(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedContainers::mysqlUrl);
        registry.add("spring.datasource.username", SharedContainers.MYSQL::getUsername);
        registry.add("spring.datasource.password", SharedContainers.MYSQL::getPassword);
    }
}
