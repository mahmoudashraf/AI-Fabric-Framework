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
import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        registry.add("dealership.staff.password-hash-base64", () -> Base64.getEncoder().encodeToString(
            new BCryptPasswordEncoder(4).encode("test-password").getBytes(StandardCharsets.UTF_8)
        ));
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
        registry.add("server.servlet.session.cookie.secure", () -> "true");
        registry.add("server.servlet.session.cookie.same-site", () -> "none");
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
            .andExpect(jsonPath("$.items[0].priceGbp").value(31950.00))
            .andExpect(jsonPath("$.items[0].priceFormatted").value("£31,950.00"))
            .andExpect(jsonPath("$.items[0].priceMinor").doesNotExist())
            .andExpect(jsonPath("$.items[0].sourceLabel").value("Demonstration inventory"))
            .andExpect(jsonPath("$.facets.makes").isArray())
            .andExpect(jsonPath("$.appliedFilters.fuelType").value("Electric"))
            .andExpect(jsonPath("$.appliedFilters.maxPriceGbp").value(35000))
            .andExpect(jsonPath("$.appliedFilters.sort").value("recommended"))
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
    void resolvesAnExplicitVehicleReferenceToOneTrustedActiveTarget() throws Exception {
        mvc.perform(get("/api/public/vehicles/resolve")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("reference", "I want to book a test drive for the Aster E1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.vehicleId").value("veh-aster-e1"))
            .andExpect(jsonPath("$.slug").value("aster-e1-motion"))
            .andExpect(jsonPath("$.dealershipId").value("dealer-demo-001"))
            .andExpect(jsonPath("$.lifecycleState").value("ACTIVE"));
    }

    @Test
    void loadsAuthoritativeVehicleDetailsFromOneBuyerFacingReference() throws Exception {
        mvc.perform(get("/api/public/vehicles/by-reference")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("reference", "2025 Aster E1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.vehicle.id").value("veh-aster-e1"))
            .andExpect(jsonPath("$.vehicle.slug").value("aster-e1-motion"))
            .andExpect(jsonPath("$.vehicle.model").value("E1"))
            .andExpect(jsonPath("$.vehicle.lifecycleState").value("ACTIVE"));
    }

    @Test
    void vehicleReferenceResolutionFailsClosedWhenMissingOrAmbiguous() throws Exception {
        mvc.perform(get("/api/public/vehicles/resolve")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("reference", "Aster"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));

        mvc.perform(get("/api/public/vehicles/resolve")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("reference", "Not a real model"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void comparesBuyerFacingReferencesAfterTrustedActiveInventoryResolution() throws Exception {
        mvc.perform(get("/api/public/vehicles/compare")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("references", "Aster E1, Morrow C2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.vehicles.length()").value(2))
            .andExpect(jsonPath("$.vehicles[0].id").value("veh-aster-e1"))
            .andExpect(jsonPath("$.vehicles[0].priceGbp").value(31950.00))
            .andExpect(jsonPath("$.vehicles[1].id").value("veh-morrow-c2"));
    }

    @Test
    void comparisonFailsClosedForAmbiguousOrDuplicateReferences() throws Exception {
        mvc.perform(get("/api/public/vehicles/compare")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("references", "Aster, Morrow C2"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));

        mvc.perform(get("/api/public/vehicles/compare")
                .queryParam("dealershipId", "dealer-demo-001")
                .queryParam("references", "Aster E1, veh-aster-e1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicRuntimeDescriptorContainsOnlyBrowserSafeRoutes() throws Exception {
        String body = mvc.perform(get("/api/public/runtime-descriptor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.integrationMode").value("public-runtime-anonymous"))
            .andExpect(jsonPath("$.chatBaseUrl").value("https://runtime.example.test"))
            .andExpect(jsonPath("$.runtimeRoutes.renewUrl")
                .value("/api/public/chat/session/renew"))
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
    void crossSiteCsrfCookieUsesTheConfiguredSecureSessionPolicy() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/public/security/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrf.getResponse().getHeader("Set-Cookie"))
            .contains("XSRF-TOKEN=")
            .contains("Path=/")
            .contains("Secure");
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("None");
    }

    @Test
    void authenticatedStaffCanUpdateALeadWithTheIssuedCsrfContract() throws Exception {
        String leadId = "lead-csrf-" + UUID.randomUUID();
        jdbc.update("""
            INSERT INTO dealership_lead_request
                (id, idempotency_key, action_type, vehicle_id, encrypted_contact, status,
                 consent_recorded, source_session_id, receipt_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, leadId, "csrf-" + UUID.randomUUID(), "dealership_request_test_drive",
            "veh-aster-e1", "test-encrypted-contact", "NEW", false, "csrf-session",
            "NFM-CSRF" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        MvcResult login = mvc.perform(post("/api/staff/session")
                .contentType("application/json")
                .content("{\"username\":\"staff\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult csrf = mvc.perform(get("/api/public/security/csrf").session(session))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrf.getResponse().getContentAsString());
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mvc.perform(patch("/api/staff/leads/{id}/status", leadId)
                .session(session)
                .cookie(csrfCookie)
                .header(csrfBody.path("headerName").asText(), csrfBody.path("token").asText())
                .contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.status").value("CANCELLED"));
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
    void internalAuthorizationResolvesBuyerFacingTargetsAndFailsClosedOnAmbiguity() throws Exception {
        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "2025 Aster E1", "READ")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted").value(true));

        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "Aster E1", "REQUEST_TEST_DRIVE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted").value(true));

        mvc.perform(post("/api/internal/authz/check")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(authzRequest("dep-dealership-demo", "Aster", "REQUEST_TEST_DRIVE")))
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
                "vehicleReference", "Aster E1",
                "name", "Avery Buyer",
                "email", "avery@example.test",
                "phone", "+44 7700 900123",
                "preferredDate", "Saturday afternoon"
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
        Boolean consentRecorded = jdbc.queryForObject(
            "SELECT consent_recorded FROM dealership_lead_request WHERE idempotency_key = ?",
            Boolean.class,
            idempotencyKey
        );
        assertThat(consentRecorded).isFalse();
    }

    @Test
    void testDriveRequiresNameEmailAndPhone() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "actionId", "dealership_request_test_drive",
            "idempotencyKey", "test-drive-contact-" + UUID.randomUUID(),
            "params", Map.of(
                "confirmationAccepted", true,
                "vehicleReference", "Aster E1",
                "name", "Avery Buyer",
                "email", "avery@example.test"
            ),
            "trace", Map.of(
                "requestId", "request-test-drive-contact",
                "conversationId", "conversation-test-drive-contact",
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

        mvc.perform(post("/api/internal/actions/execute")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("phone is required and must be at most 80 characters."));
    }

    @Test
    void callbackStillRequiresExplicitContactConsent() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "actionId", "dealership_request_callback",
            "idempotencyKey", "callback-test-" + UUID.randomUUID(),
            "params", Map.of(
                "confirmationAccepted", true,
                "vehicleReference", "Aster E1",
                "name", "Avery Buyer",
                "phone", "+44 7700 900123"
            ),
            "trace", Map.of(
                "requestId", "request-callback",
                "conversationId", "conversation-callback",
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

        mvc.perform(post("/api/internal/actions/execute")
                .header("X-DEALERSHIP-INTERNAL-KEY", INTERNAL_KEY)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Contact consent is required."));
    }

    private String authzRequest(String deploymentId, String resourceId) throws Exception {
        return authzRequest(deploymentId, resourceId, "READ");
    }

    private String authzRequest(String deploymentId, String resourceId, String operationType) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "contractVersion", "AUTH_CONTEXT_V1",
            "subjectId", "anonymous-session-1",
            "resourceId", resourceId,
            "operationType", operationType,
            "authContext", Map.of(
                "subjectId", "anonymous-session-1",
                "sessionId", "anonymous-session-1",
                "deploymentId", deploymentId,
                "tenantId", "tenant-dealership-demo"
            )
        ));
    }
}
