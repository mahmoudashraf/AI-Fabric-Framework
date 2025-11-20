package com.ai.behavior.realapi;

import com.ai.behavior.realapi.support.BehaviorRealApiTestSupport;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(classes = BehaviorRealApiTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("real-api-test")
public abstract class BehaviorRealApiIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;
    private static final Path LUCENE_INDEX = Path.of(
        System.getProperty("java.io.tmpdir"),
        "behavior-realapi-lucene-index"
    );

    static {
        BehaviorRealApiTestSupport.ensureProvidersConfigured();
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
        Files.createDirectories(LUCENE_INDEX);
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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.task.scheduling.enabled", () -> "false");

        registry.add("ai.behavior.test.use-real-ai", () -> "true");
        registry.add("ai.behavior.test.mock-policy", () -> "false");
        registry.add("ai.behavior.processing.worker.delay-seconds", () -> "1");
        registry.add("ai.behavior.processing.worker.max-retries", () -> "3");
        registry.add("ai.behavior.events.batch-size", () -> "50");
        registry.add("ai.behavior.processing.embedding.enabled", () -> "true");
        registry.add("ai.behavior.processing.embedding.schema-ids",
            () -> "intent.search,engagement.view,conversion.purchase");
        registry.add("ai.behavior.security.rate-limiting.enabled", () -> "false");
        registry.add("ai.behavior.retention.cleanup-schedule", () -> "0 0 3 * * *");
        registry.add("ai.vector-db.type", () -> "lucene");
        registry.add("ai.vector-db.lucene.index-path", () -> LUCENE_INDEX.toString());
    }

    protected void assumeRealApiAvailable() {
        Assumptions.assumeTrue(
            BehaviorRealApiTestSupport.hasOpenAIKey(),
            "OPENAI_API_KEY must be configured to run real API behavior tests"
        );
    }
}
