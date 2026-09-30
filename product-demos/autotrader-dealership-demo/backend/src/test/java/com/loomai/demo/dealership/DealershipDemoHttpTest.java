package com.loomai.demo.dealership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DealershipDemoHttpTest {

    private static final String INTERNAL_KEY = "integration-test-internal-key";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(
        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dealership-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        registry.add("dealership.internal.api-key", () -> INTERNAL_KEY);
        registry.add("dealership.privacy.encryption-key-base64", () -> ENCRYPTION_KEY);
        registry.add("dealership.staff.username", () -> "staff");
        registry.add("dealership.staff.password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dealership.runtime.enabled", () -> "true");
        registry.add("dealership.runtime.base-url", () -> "https://runtime.example.test");
        registry.add("dealership.runtime.private-access.trusted-api-key", () -> "runtime-secret-that-must-not-leak");
        registry.add("dealership.runtime.private-access.signing-key", () -> "assertion-secret-that-must-not-leak");
        registry.add("dealership.runtime.private-access.audience", () -> "dealership-demo");
        registry.add("dealership.runtime.private-access.deployment-id", () -> "dep-dealership-demo");
        registry.add("dealership.runtime.private-access.customer-id", () -> "customer-dealership-demo");
        registry.add("dealership.runtime.private-access.tenant-id", () -> "tenant-dealership-demo");
        registry.add("info.app.version", () -> "test-version");
        registry.add("info.app.commit", () -> "test-commit");
        registry.add("info.app.build-time", () -> "test-build-time");
        registry.add("DEALERSHIP_SYNC_RECONCILE_INTERVAL_MS", () -> "3600000");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void exposesStructuredInventoryAndFacetsFromTheApplicationDatabase() throws Exception {
        mvc.perform(get("/api/public/vehicles")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("fuelType", "Electric")
                .queryParam("maxPriceGbp", "35000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].dealershipId").value("dealer-demo-001"))
            .andExpect(jsonPath("$.items[0].sourceLabel").value("Demonstration inventory"))
            .andExpect(jsonPath("$.facets.makes").isArray())
            .andExpect(jsonPath("$.dataNotice").value("Fictional demonstration inventory. No live Auto Trader data is used."));
    }

    @Test
    void rejectsNegativePublicPriceFilters() throws Exception {
        mvc.perform(get("/api/public/vehicles")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("maxPriceGbp", "-1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInventoryReadsOutsideTheConfiguredDealershipScope() throws Exception {
        mvc.perform(get("/api/public/vehicles")
                .queryParam("dealershipId", "another-dealership"))
            .andExpect(status().isNotFound());
    }

    @Test
    void publicRuntimeDescriptorContainsOnlyBrowserSafeRoutes() throws Exception {
        String body = mvc.perform(get("/api/public/runtime-descriptor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.integrationMode").value("public-runtime-anonymous"))
            .andExpect(jsonPath("$.chatBaseUrl").value("https://runtime.example.test"))
            .andExpect(jsonPath("$.runtimeRoutes.conversationsUrl")
                .value("/api/chat/me/conversations"))
            .andExpect(jsonPath("$.runtimeRoutes.conversationItemUrlTemplate")
                .value("/api/chat/me/conversations/{conversationId}"))
            .andReturn().getResponse().getContentAsString();

        assertThat(body)
            .doesNotContain("runtime-secret-that-must-not-leak")
            .doesNotContain("assertion-secret-that-must-not-leak")
            .doesNotContain("X-AIFABRIC-RUNTIME-API-KEY");
    }

    @Test
    void publicStatusExposesSafeBuildIdentity() throws Exception {
        mvc.perform(get("/api/public/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("loomai-dealership-demo-backend"))
            .andExpect(jsonPath("$.version").value("test-version"))
            .andExpect(jsonPath("$.commit").value("test-commit"))
            .andExpect(jsonPath("$.buildTime").value("test-build-time"));
    }

    @Test
    void unknownPublicResourceReturnsNotFoundInsteadOfInternalError() throws Exception {
        mvc.perform(get("/api/public/not-a-real-resource"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void invalidStaffCredentialsReturnUnauthorized() throws Exception {
        mvc.perform(post("/api/staff/session")
                .contentType("application/json")
                .content("{\"username\":\"staff\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_STAFF_CREDENTIALS"));
    }

    @Test
    void validStaffCredentialsEstablishAnAuthenticatedSession() throws Exception {
        MvcResult login = mvc.perform(post("/api/staff/session")
                .contentType("application/json")
                .content("{\"username\":\"staff\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("staff"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/staff/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void internalAuthorizationFailsClosedAndRejectsCrossDeploymentContext() throws Exception {
        String request = authzRequest("dep-other", "veh-aster-e1");
        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted").value(false));

        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", "wrong")
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "veh-aster-e1")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_AUTH_REQUIRED"));
    }

    @Test
    void internalAuthorizationAllowsScopedOrchestrationEntry() throws Exception {
        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "rag:intent")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted").value(true));

        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "unknown-resource")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted").value(false));
    }

    @Test
    void confirmedActionPersistsEncryptedPiiAndReplaysIdempotently() throws Exception {
        String idempotencyKey = "lead-test-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
            "actionId", "dealership_request_test_drive",
            "idempotencyKey", idempotencyKey,
            "params", Map.of(
                "confirmationAccepted", true,
                "vehicleId", "veh-aster-e1",
                "name", "Avery Buyer",
                "email", "avery@example.test",
                "preferredDate", "Saturday afternoon",
                "consent", true
            ),
            "trace", Map.of(
                "requestId", "request-1",
                "conversationId", "conversation-1",
                "authContext", Map.of(
                    "subjectId", "anonymous-session-1",
                    "subjectType", "ANONYMOUS_SESSION",
                    "authMode", "PUBLIC_RUNTIME_TOKEN",
                    "callerType", "PUBLIC_BROWSER",
                    "sessionId", "anonymous-session-1",
                    "deploymentId", "dep-dealership-demo",
                    "customerId", "customer-dealership-demo",
                    "tenantId", "tenant-dealership-demo",
                    "issuer", "runtime-public-bootstrap"
                )
            )
        ));

        String first = mvc.perform(post("/api/internal/actions/execute")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("NEW"))
            .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/api/internal/actions/execute")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode firstJson = objectMapper.readTree(first);
        JsonNode secondJson = objectMapper.readTree(second);
        assertThat(secondJson.path("data").path("receiptCode").asText())
            .isEqualTo(firstJson.path("data").path("receiptCode").asText());

        String encrypted = jdbc.queryForObject(
            "SELECT encrypted_contact FROM dealership_lead_request WHERE idempotency_key = ?",
            String.class,
            idempotencyKey
        );
        assertThat(encrypted).startsWith("v1.").doesNotContain("avery@example.test").doesNotContain("Avery Buyer");
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dealership_lead_request WHERE idempotency_key = ?",
            Integer.class,
            idempotencyKey
        );
        assertThat(count).isEqualTo(1);
    }

    private String authzRequest(String deploymentId, String resourceId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "contractVersion", "AUTH_CONTEXT_V1",
            "subjectId", "anonymous-session-1",
            "resourceId", resourceId,
            "operationType", "READ",
            "authContext", Map.of(
                "subjectId", "anonymous-session-1",
                "sessionId", "anonymous-session-1",
                "deploymentId", deploymentId,
                "tenantId", "tenant-dealership-demo"
            )
        ));
    }
}
