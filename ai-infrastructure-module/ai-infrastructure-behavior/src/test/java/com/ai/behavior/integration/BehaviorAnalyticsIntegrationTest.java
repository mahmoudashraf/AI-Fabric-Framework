package com.ai.behavior.integration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;

@SpringBootTest(classes = TestBehaviorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringJUnitConfig
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public abstract class BehaviorAnalyticsIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (embeddedPostgres != null) {
                try {
                    embeddedPostgres.close();
                } catch (IOException ignored) {
                }
            }
        }));
    }

    @BeforeAll
    static void startEmbeddedPostgres() throws IOException {
        if (embeddedPostgres == null) {
            embeddedPostgres = EmbeddedPostgres.builder().start();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (embeddedPostgres == null) {
            try {
                embeddedPostgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start embedded PostgreSQL", e);
            }
        }
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("ai.behavior.retention.temp-events-ttl-days", () -> "3");
        registry.add("ai.behavior.retention.cleanup-schedule", () -> "0 0 3 * * *");
        registry.add("ai.behavior.processing.worker.delay-seconds", () -> "1");
        registry.add("ai.behavior.processing.worker.max-retries", () -> "3");
        registry.add("ai.behavior.security.rate-limiting.enabled", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("ai.indexing.async-worker.enabled", () -> "false");
        registry.add("ai.indexing.batch-worker.enabled", () -> "false");
        registry.add("ai.indexing.cleanup.enabled", () -> "false");
    }
}
