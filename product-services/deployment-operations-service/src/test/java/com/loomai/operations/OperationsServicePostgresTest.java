package com.loomai.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.loomai.operations.model.OperationsContracts.AssessmentResponse;
import com.loomai.operations.model.OperationsContracts.Decision;
import com.loomai.operations.model.OperationsContracts.OutcomeStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY,
    "DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON=" + OperationsTestConfiguration.POLICY
})
@Import(OperationsTestConfiguration.class)
class OperationsServicePostgresTest extends AbstractOperationsServiceTest {

    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void actualHttpServerAuthenticatesAndPersistsTheAssessmentAndOutcomeContract() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(OperationsTestConfiguration.API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = request("http-postgres", "dep-http");
        var response = http.postForEntity("/internal/v1/deployment-operations/assess",
            new HttpEntity<>(mapper.writeValueAsString(request), headers), AssessmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commandId()).isEqualTo("http-postgres");
        assertThat(response.getBody().decision()).isEqualTo(Decision.ALLOW);
        var observation = outcome(request, OutcomeStatus.ACTIVE, OperationsTestConfiguration.NOW.plusSeconds(1));
        var accepted = http.postForEntity("/internal/v1/deployment-operations/outcomes",
            new HttpEntity<>(mapper.writeValueAsString(observation), headers), Void.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repository.assessment("http-postgres").orElseThrow().lastOutcome()).isEqualTo(observation);
    }

    @Test
    void actualHttpServerExposesOnlySafeHealthWithoutCredentials() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var denied = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + "/internal/v1/deployment-operations/assess"))
                .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(401);
            assertThat(denied.body()).doesNotContain(OperationsTestConfiguration.API_KEY);
        }
        var health = http.getForEntity("/actuator/health/readiness", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }
}
