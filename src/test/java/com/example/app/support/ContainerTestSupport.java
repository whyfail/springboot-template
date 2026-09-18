package com.example.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Base for full-application integration tests against the shared MySQL and Redis containers. */
@ActiveProfiles("test")
@SpringBootTest
public abstract class ContainerTestSupport extends SharedContainers {}
