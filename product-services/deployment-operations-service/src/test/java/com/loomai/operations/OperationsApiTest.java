package com.loomai.operations;

import static com.loomai.operations.AbstractOperationsServiceTest.request;
import static com.loomai.operations.OperationsTestConfiguration.API_KEY;
import static com.loomai.operations.OperationsTestConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.operations.OperationsTestConfiguration.MutableClock;
import com.loomai.operations.service.DeploymentOperationsService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + API_KEY,
    "DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON=" + OperationsTestConfiguration.POLICY,
    "spring.datasource.url=jdbc:h2:mem:operations-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc
@Import(OperationsTestConfiguration.class)
class OperationsApiTest {

    private static final String ASSESS = "/internal/v1/deployment-operations/assess";
    private static final String OUTCOMES = "/internal/v1/deployment-operations/outcomes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @SpyBean DeploymentOperationsService service;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM deployment_operations_outcome");
        jdbc.update("DELETE FROM deployment_operations_allocation");
        jdbc.update("DELETE FROM deployment_operations_assessment");
        clock.set(NOW);
    }

    @Test
    void authenticatedAssessAndOutcomeUseFrozenWireContract() throws Exception {
        String body = mapper.writeValueAsString(request("cmd-api", "dep-api"));
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("1"))
            .andExpect(jsonPath("$.commandId").value("cmd-api"))
            .andExpect(jsonPath("$.requestFingerprint").value("f".repeat(64)))
            .andExpect(jsonPath("$.assessmentId").isString())
            .andExpect(jsonPath("$.decision").value("ALLOW"))
            .andExpect(jsonPath("$.policyRevision").value("test-policy-v1"))
            .andExpect(jsonPath("$.observedAt").value("2026-10-24T14:00:00Z"))
            .andExpect(jsonPath("$.expiresAt").value("2026-10-24T14:05:00Z"))
            .andExpect(jsonPath("$.length()").value(9));
        String outcome = """
            {"schemaVersion":"1","commandId":"cmd-api","requestFingerprint":"%s","status":"ACTIVE","observedAt":"2026-10-24T14:00:01Z"}
            """.formatted("f".repeat(64));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(OUTCOMES).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON).content(outcome))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_operations_outcome", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer wrong-key", "Basic aW52YWxpZA==", "Bearer ", "Bearer " + API_KEY + " "})
    void requiresServiceAuthenticationBeforeReadingOrValidatingBody(String authorization) throws Exception {
        mvc.perform(post(ASSESS).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("not-json"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(API_KEY))));
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY, "Bearer " + API_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request("cmd-1", "dep-1"))))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthIsPublicButDoesNotExposePolicyDatabaseOrSecrets(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void actuatorConfigurationAndMetricsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + API_KEY)).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + API_KEY)).andExpect(status().isNotFound());
    }

    @Test
    void noBrowserCorsAccessIsEnabled() throws Exception {
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).header("Origin", "https://customer.example")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request("cmd-1", "dep-1"))))
            .andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsLargeEncodedOrNonJsonBodies() throws Exception {
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(" ".repeat(16385))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.TEXT_PLAIN)
            .content("{}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).header("Content-Encoding", "gzip")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"schemaVersion", "commandId", "customerId", "tenantId", "deploymentId", "targetProfileId", "releaseId", "versionId", "versionConfigHash", "operation", "mode", "requestFingerprint", "resourceDemand", "requestedAt"})
    void allAssessmentFieldsAreRequired(String missing) throws Exception {
        ObjectNode node = mapper.valueToTree(request("cmd-1", "dep-1"));
        node.remove(missing);
        assertInvalid(mapper.writeValueAsString(node));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpuMillis", "memoryMiB", "diskMiB", "databases", "indexes", "dependencies", "replicas", "concurrentApplies"})
    void rejectsNegativeOrMissingDemandFields(String field) throws Exception {
        ObjectNode node = mapper.valueToTree(request("cmd-1", "dep-1"));
        ObjectNode demand = (ObjectNode) node.get("resourceDemand");
        demand.put(field, -1);
        assertInvalid(mapper.writeValueAsString(node));
        demand.remove(field);
        assertInvalid(mapper.writeValueAsString(node));
    }

    @Test
    void rejectsUnknownDuplicateTrailingMalformedAndCoercedValues() throws Exception {
        String valid = mapper.writeValueAsString(request("cmd-1", "dep-1"));
        assertInvalid(valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"1\",\"unexpected\":true"));
        assertInvalid(valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"1\",\"schemaVersion\":\"1\""));
        assertInvalid(valid + "{}");
        assertInvalid("{");
        assertInvalid(valid.replace("\"cpuMillis\":1000", "\"cpuMillis\":1000.5"));
        assertInvalid(valid.replace("\"cpuMillis\":1000", "\"cpuMillis\":\"1000\""));
        assertInvalid(valid.replace("\"cpuMillis\":1000", "\"cpuMillis\":1e100"));
        assertInvalid(valid.replace("\"cpuMillis\":1000", "\"cpuMillis\":1000000000001"));
        assertInvalid(valid.replace("\"mode\":\"ENFORCE\"", "\"mode\":\"DISABLED\""));
        assertInvalid(valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":1"));
        assertInvalid(valid.replace("\"requestedAt\":\"2026-10-24T14:00:00Z\"", "\"requestedAt\":1792850400"));
        assertInvalid(valid.replace("\"requestedAt\":\"2026-10-24T14:00:00Z\"", "\"requestedAt\":\"2026-10-24\""));
    }

    @Test
    void boundsScopeAndFingerprintAndAcceptsThePlatformsPrefixedConfigHash() throws Exception {
        String valid = mapper.writeValueAsString(request("cmd-1", "dep-1"));
        assertInvalid(valid.replace("cmd-1", "x".repeat(129)));
        assertInvalid(valid.replace("customer-1", "customer with whitespace"));
        assertInvalid(valid.replace("f".repeat(64), "sha256:" + "f".repeat(64)));
        assertInvalid(valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"2\""));
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(valid.replace("a".repeat(64), "sha256:" + "a".repeat(64)))).andExpect(status().isOk());
    }

    @Test
    void acceptsZeroDiskInTheWireContractForNonpersistentApp() throws Exception {
        ObjectNode node = mapper.valueToTree(request("cmd-no-disk", "dep-1"));
        ((ObjectNode) node.get("resourceDemand")).put("diskMiB", 0L);
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(node))).andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("ALLOW"));
    }

    @Test
    void commandConflictIs409AndDoesNotEchoRequestSecretsOrInternals() throws Exception {
        String valid = mapper.writeValueAsString(request("cmd-1", "dep-1"));
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isOk());
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(valid.replace("f".repeat(64), "b".repeat(64))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMAND_PAYLOAD_CONFLICT"));
    }

    @Test
    void retryAfterTheFreshnessWindowReturnsTheSameResponseInsteadOfRenewal() throws Exception {
        String valid = mapper.writeValueAsString(request("cmd-1", "dep-1"));
        String first = mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(valid)).andReturn().getResponse().getContentAsString();
        clock.set(NOW.plus(Duration.ofDays(1)));
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isOk()).andExpect(content().json(first, true));
    }

    @Test
    void storageFailureIs503NotAFalseAllowAndDoesNotExposeDetails() throws Exception {
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("private-database-host-and-password"))
            .when(service).assess(org.mockito.ArgumentMatchers.any());
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request("cmd-1", "dep-1"))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("OPERATIONS_STORAGE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Operations storage is unavailable."));
    }

    private void assertInvalid(String body) throws Exception {
        mvc.perform(post(ASSESS).header("Authorization", "Bearer " + API_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
            .andExpect(jsonPath("$.message").value("Request JSON or fields are invalid."));
    }
}
