package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import com.loomai.demo.dealership.security.ReceiverBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:receiver-binding-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "dealership.runtime.enabled=false", "dealership.provider-simulator.enabled=false"
})
@AutoConfigureMockMvc
class ReceiverBindingHttpTest {
    private static final String KEY = "synthetic-new-receiver-credential-123456789";
    private static final String OLD_KEY = "synthetic-old-receiver-credential-123456789";
    private static final String GLOBAL_KEY = "synthetic-old-global-internal-key-123456789";
    private static final String STOCK = "PROVIDER-ONLY-99";
    private static final String ACTION = "/api/internal/actions/execute";
    private static final String AUTHZ = "/api/internal/authz/check";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DealershipDemoProperties properties;
    @Autowired ReceiverBindingService bindings;
    @Autowired LeadService leads;
    @Autowired PiiEncryptionService encryption;
    @MockBean ProviderStockClient provider;
    private String requestKey;

    @BeforeEach
    void newIndependentReceiver() {
        jdbc.update("DELETE FROM dealership_lead_request");
        requestKey = "request-" + UUID.randomUUID();
        properties.getInternal().setApiKey(GLOBAL_KEY);
        properties.getRuntime().getPrivateAccess().setCustomerId("old-customer");
        properties.getRuntime().getPrivateAccess().setTenantId("old-tenant");
        properties.getRuntime().getPrivateAccess().setDeploymentId("old-deployment");
        properties.getRuntime().getPrivateAccess().setSigningKey("preserved-old-runtime-signing-key");
        properties.getPrivacy().setEncryptionKeyBase64(Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        properties.getPrivacy().setLeadRetentionEnabled(false);
        properties.getPrivacy().setLeadRetention(Duration.ofDays(30));
        configure("new", KEY);
        when(provider.configuredAdvertiserId()).thenReturn("advertiser-1");
        when(provider.requireAvailableStock(STOCK)).thenReturn(new ProviderStockClient.Stock(
            STOCK, "advertiser-1", "2025 Meridian E7", Instant.now(), Instant.now()));
    }

    @Test
    void explicitReceiverScopeAuthorizesAndExecutesWithoutRepurposingRuntime() throws Exception {
        var auth = auth("new");
        mvc.perform(post(AUTHZ).header(ReceiverBindingService.API_KEY_HEADER, KEY).contentType("application/json")
            .content(mapper.writeValueAsString(authorization(auth)))).andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(true));
        var first = execute(KEY, auth, 200);
        assertThat(execute(KEY, auth, 200)).isEqualTo(first);
        var row = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
        assertThat(row).containsEntry("receiver_binding_id", "binding-new").containsEntry("receiver_customer_id", "new-customer")
            .containsEntry("receiver_tenant_id", "new-tenant").containsEntry("receiver_deployment_id", "new-deployment")
            .containsEntry("receiver_advertiser_id", "advertiser-1").containsEntry("receiver_request_key", requestKey)
            .containsEntry("receiver_binding_fingerprint", bindings.requireActive().fingerprint());
        assertThat(row.get("idempotency_key")).isEqualTo(bindings.requireActive().storageKey(requestKey));
        assertThat(properties.getRuntime().getPrivateAccess().getCustomerId()).isEqualTo("old-customer");
        assertThat(properties.getRuntime().getPrivateAccess().getTenantId()).isEqualTo("old-tenant");
        assertThat(properties.getRuntime().getPrivateAccess().getDeploymentId()).isEqualTo("old-deployment");
        assertThat(properties.getRuntime().getPrivateAccess().getSigningKey()).isEqualTo("preserved-old-runtime-signing-key");
        assertThat(properties.getInternal().getApiKey()).isEqualTo(GLOBAL_KEY);
        assertThat(first.toString()).doesNotContain(KEY, GLOBAL_KEY, "Buyer", "buyer@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "subjectId", "sessionId"})
    void authzAndExecutionRejectTheSameForgedOrMissingScope(String field) throws Exception {
        var auth = auth("new");
        auth.put(field, field.equals("subjectId") || field.equals("sessionId") ? "" : "foreign");
        mvc.perform(post(AUTHZ).header(ReceiverBindingService.API_KEY_HEADER, KEY).contentType("application/json")
            .content(mapper.writeValueAsString(authorization(auth)))).andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));
        execute(KEY, auth, 403);
        assertThat(count()).isZero();
        verify(provider, never()).requireAvailableStock(anyString());
    }

    @Test
    void oldGlobalCredentialCannotAuthorizeOrExecuteEvenWhenItClaimsTheNewScope() throws Exception {
        for (String path : new String[]{AUTHZ, ACTION}) {
            mvc.perform(post(path).header(ReceiverBindingService.API_KEY_HEADER, GLOBAL_KEY).contentType("application/json")
                .content(mapper.writeValueAsString(path.equals(AUTHZ) ? authorization(auth("new")) : action(auth("new")))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("INTERNAL_AUTH_REQUIRED"));
        }
        assertThat(count()).isZero();
        verify(provider, never()).requireAvailableStock(anyString());
    }

    @Test
    void disabledBindingBlocksUntilDeliberateConfigurationWithoutAnOldAuthorityFallback() throws Exception {
        properties.getReceiver().setEnabled(false);
        execute(KEY, auth("new"), 503);
        mvc.perform(post(AUTHZ).header(ReceiverBindingService.API_KEY_HEADER, GLOBAL_KEY).contentType("application/json")
            .content(mapper.writeValueAsString(authorization(auth("old"))))).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("RECEIVER_BINDING_UNAVAILABLE"));
        assertThat(count()).isZero();
        verify(provider, never()).requireAvailableStock(anyString());
    }

    @Test
    void sequentialCutoverSeparatesIdenticalKeysAndPayloadsAndRejectsTheOldCredential() throws Exception {
        configure("old", OLD_KEY);
        var oldReceipt = execute(OLD_KEY, auth("old"), 200);
        var oldRow = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
        properties.getReceiver().setEnabled(false);
        execute(OLD_KEY, auth("old"), 503);
        configure("new", KEY);
        execute(OLD_KEY, auth("new"), 401);
        execute(KEY, auth("old"), 403);
        var newReceipt = execute(KEY, auth("new"), 200);
        assertThat(newReceipt.path("data").path("receiptCode")).isNotEqualTo(oldReceipt.path("data").path("receiptCode"));
        assertThat(execute(KEY, auth("new"), 200)).isEqualTo(newReceipt);
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", oldRow.get("id"))).isEqualTo(oldRow);
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT receiver_binding_fingerprint) FROM dealership_lead_request", Long.class)).isEqualTo(2);
    }

    @Test
    void legacyReceiptNeverReplaysAsTheNewCustomerAndIsLabeledOperatorOnly() throws Exception {
        String id = insertLegacy(requestKey);
        var original = jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", id);
        var result = execute(KEY, auth("new"), 200);
        assertThat(result.path("data").path("receiptCode").asText()).isNotEqualTo("LEGACY-RECEIPT");
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", id)).isEqualTo(original);
        mvc.perform(get("/api/staff/leads/{id}", id).with(user("fixture-operator").roles("STAFF")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.lead.scopeStatus").value("LEGACY_OPERATOR_ONLY"))
            .andExpect(jsonPath("$.item.lead.receiverScope").doesNotExist());
        mvc.perform(get("/api/staff/leads/{id}", id).header(ReceiverBindingService.API_KEY_HEADER, KEY)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/staff/leads/{id}/status", id).with(user("fixture-operator").roles("STAFF")).with(csrf())
            .contentType("application/json").content("{\"status\":\"CANCELLED\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", id)).isEqualTo(original);
    }

    @Test
    void evenALegacyKeyCollisionCannotReturnAnUnscopedReceipt() throws Exception {
        String id = insertLegacy(bindings.requireActive().storageKey(requestKey));
        var original = jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", id);
        var failure = execute(KEY, auth("new"), 503);
        assertThat(failure.toString()).doesNotContain("LEGACY-RECEIPT", "legacy@example.test");
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", id)).isEqualTo(original);
    }

    @Test
    void retentionIsExplicitAndNeverDeletesLegacyRowsEvenWhenEnabled() throws Exception {
        String legacy = insertLegacy("old-key");
        execute(KEY, auth("new"), 200);
        jdbc.update("UPDATE dealership_lead_request SET created_at=?", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(60))));
        var original = jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", legacy);
        leads.purgeExpired();
        assertThat(count()).isEqualTo(2);
        properties.getPrivacy().setLeadRetentionEnabled(true);
        leads.purgeExpired();
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request WHERE id=?", legacy)).isEqualTo(original);
    }

    @Test
    void operatorCanStillUpdateAScopedReceiptWithoutExposingItToReceiverCredentials() throws Exception {
        execute(KEY, auth("new"), 200);
        String id = jdbc.queryForObject("SELECT id FROM dealership_lead_request", String.class);
        mvc.perform(patch("/api/staff/leads/{id}/status", id).with(user("fixture-operator").roles("STAFF")).with(csrf())
            .contentType("application/json").content("{\"status\":\"CONTACTED\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.item.scopeStatus").value("SCOPED_RECEIVER_BINDING"))
            .andExpect(jsonPath("$.item.receiverScope.customerId").value("new-customer"));
    }

    private void configure(String owner, String key) {
        var receiver = properties.getReceiver();
        receiver.setEnabled(true); receiver.setBindingId("binding-" + owner); receiver.setCustomerId(owner + "-customer");
        receiver.setTenantId(owner + "-tenant"); receiver.setDeploymentId(owner + "-deployment");
        receiver.setAdvertiserId("advertiser-1"); receiver.setApiKey(key);
    }

    private Map<String, Object> auth(String owner) {
        return new LinkedHashMap<>(Map.of("subjectId", "subject", "sessionId", "same-session",
            "customerId", owner + "-customer", "tenantId", owner + "-tenant", "deploymentId", owner + "-deployment"));
    }

    private Map<String, Object> authorization(Map<String, Object> auth) {
        return Map.of("resourceId", STOCK, "operationType", "READ", "authContext", auth);
    }

    private Map<String, Object> action(Map<String, Object> auth) {
        return Map.of("actionId", "dealership_request_callback", "idempotencyKey", requestKey,
            "params", Map.of("confirmationAccepted", true, "vehicleReference", STOCK, "name", "Buyer", "email", "buyer@example.test", "consent", true),
            "trace", Map.of("authContext", auth));
    }

    private JsonNode execute(String key, Map<String, Object> auth, int expected) throws Exception {
        var result = mvc.perform(post(ACTION).header(ReceiverBindingService.API_KEY_HEADER, key).contentType("application/json")
            .content(mapper.writeValueAsString(action(auth)))).andExpect(status().is(expected)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String insertLegacy(String key) {
        String id = "legacy-" + UUID.randomUUID();
        jdbc.update("""
            INSERT INTO dealership_lead_request
              (id,idempotency_key,action_type,vehicle_id,encrypted_contact,status,consent_recorded,source_session_id,receipt_code,created_at,updated_at)
            VALUES (?,?,?,?,?,'NEW',true,'same-session','LEGACY-RECEIPT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, id, key, "dealership_request_callback", STOCK, encryption.encrypt(Map.of("name", "Legacy Buyer", "email", "legacy@example.test")));
        return id;
    }

    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class); }
}
