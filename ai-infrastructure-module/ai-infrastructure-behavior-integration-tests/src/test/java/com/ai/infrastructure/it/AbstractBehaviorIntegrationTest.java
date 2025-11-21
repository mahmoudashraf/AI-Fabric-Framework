package com.ai.infrastructure.it;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Base class that provisions a shared embedded PostgreSQL database for integration tests.
 * The database is started once per JVM and wiring is supplied through {@link DynamicPropertySource}.
 */
public abstract class AbstractBehaviorIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startEmbeddedPostgres();

    private static EmbeddedPostgres startEmbeddedPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setCleanDataDirectory(true)
                .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // best-effort shutdown hook
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start embedded Postgres for tests", e);
        }
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.liquibase.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.liquibase.user", () -> "postgres");
        registry.add("spring.liquibase.password", () -> "postgres");
        registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-master.yaml");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    protected static EmbeddedPostgres getEmbeddedPostgres() {
        return POSTGRES;
    }
}
