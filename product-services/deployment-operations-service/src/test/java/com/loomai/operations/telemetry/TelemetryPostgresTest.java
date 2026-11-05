package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.loomai.operations.OperationsTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY,
    "TELEMETRY_ENABLED=true", "TELEMETRY_READ_KEY=" + TelemetryFixtures.READ_KEY,
    "TELEMETRY_SOURCES_JSON=" + TelemetryFixtures.SOURCES
})
@AutoConfigureMockMvc
@Import(OperationsTestConfiguration.class)
class TelemetryPostgresTest extends AbstractTelemetryTest {
    @LocalServerPort int port;
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void actualHttpServerKeepsTelemetryCapabilitiesAndNormalizedRoutesIsolated() throws Exception {
        String base = "http://localhost:" + port;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var ingest = client.send(HttpRequest.newBuilder(URI.create(base + PREFIX + "/samples"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + INGEST_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(sample(mapper, OperationsTestConfiguration.NOW)))).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(ingest.statusCode()).isEqualTo(204);
            var read = client.send(HttpRequest.newBuilder(URI.create(base + PREFIX + "/servers/stage-host"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + READ_KEY).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(read.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(mapper.readTree(read.body()).get("status").asText()).isEqualTo("FRESH");
            for (String path : new String[] {PREFIX + "/servers", "/internal/v1/other/../telemetry/servers", "/internal/v1/telemetry%2Fservers"}) {
                var denied = client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + OperationsTestConfiguration.API_KEY).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(denied.statusCode()).isIn(400, 401, 404);
            }
            var scoped = client.send(HttpRequest.newBuilder(URI.create(base + PREFIX + "/servers"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + INGEST_KEY).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(scoped.statusCode()).isEqualTo(403);
        }
    }
}
