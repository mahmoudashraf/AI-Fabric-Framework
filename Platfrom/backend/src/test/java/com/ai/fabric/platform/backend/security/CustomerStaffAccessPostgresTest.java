package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.CustomerStaffAccessRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.CustomerStaffAccessPolicy;
import com.ai.fabric.platform.backend.security.service.CustomerStaffGrantService;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real migrations, customer-row locks, cookie sessions and grant-policy reads; no provider calls. */
@SpringBootTest(properties = {
    "platform.auth.enabled=true", "platform.auth.api-key-enabled=false", "platform.auth.session-enabled=true",
    "platform.auth.session-cookie-name=platform_session", "platform.auth.session-cookie-secure=false",
    "platform.auth.bootstrap-admin-enabled=false", "platform.auth.customer-supabase.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerStaffAccessPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String PASSWORD = "Local-Staff-Grant-Test-Only-123!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.target", () -> "latest");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformTenantRepository tenants;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentAssignmentRepository assignments;
    @Autowired CustomerStaffAccessRepository grants;
    @Autowired CustomerStaffAccessPolicy policy;
    @Autowired CustomerStaffGrantService grantService;
    @Autowired PlatformIdentityService identities;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @SpyBean PlatformAuditService audit;
    private PlatformCustomerEntity customer;
    private PlatformCustomerEntity otherCustomer;
    private PlatformUserEntity owner;
    private PlatformUserEntity otherOwner;
    private PlatformUserEntity operator;
    private PlatformUserEntity globalAdmin;
    private Cookie ownerCookie;

    @BeforeEach void seed() throws Exception {
        reset(audit);
        customer = customer(); otherCustomer = customer();
        String hash = passwords.encode(PASSWORD);
        owner = user("CUSTOMER_ADMIN", customer.getId(), hash); otherOwner = user("CUSTOMER_ADMIN", otherCustomer.getId(), hash);
        operator = user("PLATFORM_OPERATOR", null, hash); globalAdmin = user("PLATFORM_ADMIN", null, hash);
        ownerCookie = login(owner);
    }
    @AfterEach void resetAudit() { reset(audit); }

    @Test void sameOperatorCookieGainsAndLosesActualProductHttpAccessIncludingAnExistingLegacyAssignment() throws Exception {
        String deploymentId = legacyAssignedDraft();
        var operatorCookie = login(operator);
        var accountBefore = jdbc.queryForMap("select * from platform_users where id=?", operator.getId());
        String versions = "/api/deployments/" + deploymentId + "/versions";
        String kits = "/api/staff/customers/" + customer.getId() + "/experience-kits";
        String apps = "/api/platform/customers/" + customer.getId() + "/ai-workspace-installations";
        var ungrantedPaths = List.of(
            "/api/staff/customers/" + otherCustomer.getId() + "/experience-kits",
            "/api/platform/customers/" + otherCustomer.getId() + "/ai-workspace-installations");

        mvc.perform(get(versions).cookie(operatorCookie)).andExpect(status().isOk());
        var entry = grant(ownerCookie, operator.getEmail());
        var visible = body(mvc.perform(get("/api/platform/customers").cookie(operatorCookie)).andExpect(status().isOk()).andReturn());
        assertThat(visible).hasSize(1); assertThat(visible.get(0).path("id").asText()).isEqualTo(customer.getId());
        for (String path : List.of(kits, apps, versions)) {
            var response = body(mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isOk()).andReturn());
            assertThat(response.isArray()).as(path).isTrue(); assertThat(response).as(path).isEmpty();
        }
        for (String path : ungrantedPaths) mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/customer/staff-access/{id}", entry.path("id").asText()).cookie(ownerCookie)).andExpect(status().isOk());
        var afterRevoke = body(mvc.perform(get("/api/platform/customers").cookie(operatorCookie)).andExpect(status().isOk()).andReturn());
        assertThat(afterRevoke.isArray()).isTrue(); assertThat(afterRevoke).isEmpty();
        for (String path : List.of(kits, apps)) mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isForbidden());
        mvc.perform(get(versions).cookie(operatorCookie)).andExpect(status().isNotFound());
        for (String path : ungrantedPaths) mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isForbidden());
        assertThat(assignments.findByUserIdAndDeploymentId(operator.getId(), deploymentId).orElseThrow().getAssignmentRole())
            .isEqualTo("DEPLOYMENT_ADMIN");

        assertThat(grant(ownerCookie, operator.getEmail()).path("id").asText()).isEqualTo(entry.path("id").asText());
        for (String path : List.of(kits, apps, versions)) mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isOk());
        var afterRegrant = body(mvc.perform(get("/api/platform/customers").cookie(operatorCookie)).andExpect(status().isOk()).andReturn());
        assertThat(afterRegrant).hasSize(1); assertThat(afterRegrant.get(0).path("id").asText()).isEqualTo(customer.getId());
        assertThat(jdbc.queryForMap("select * from platform_users where id=?", operator.getId())).isEqualTo(accountBefore);
    }

    @Test void sameOperatorCookieSeesGrantThenAuthoritativeRevocationWithoutReauthentication() throws Exception {
        var cookie = login(operator);
        var principal = identities.authenticateSessionToken(cookie.getValue());
        var accountBefore = jdbc.queryForMap("select * from platform_users where id=?", operator.getId());
        assertThat(policy.decision(customer.getId(), principal)).isEmpty();
        var entry = grant(ownerCookie, operator.getEmail());
        String id = entry.path("id").asText();
        assertThat(entry.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "customerId", "userId", "email", "displayName", "status", "grantedAt", "revokedAt");
        assertThat(policy.decision(customer.getId(), principal)).contains(true);
        assertThat(policy.visibleCustomerIds(principal)).contains(customer.getId());
        var revoked = body(mvc.perform(delete("/api/customer/staff-access/{id}", id).cookie(ownerCookie))
            .andExpect(status().isOk()).andReturn());
        assertThat(revoked.path("status").asText()).isEqualTo("REVOKED");
        assertThat(policy.decision(customer.getId(), principal)).contains(false);
        assertThat(policy.visibleCustomerIds(principal)).doesNotContain(customer.getId());
        var sameSession = identities.authenticateSessionToken(cookie.getValue());
        assertThat(sameSession).isEqualTo(principal);
        assertThat(policy.decision(customer.getId(), sameSession).orElse(true)).isFalse();
        mvc.perform(get("/api/platform/auth/session").cookie(cookie)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from platform_customer_staff_access where id=? and status='REVOKED'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select actor_id from platform_audit_events where action='CUSTOMER_STAFF_ACCESS_REVOKED' and target_id=?", String.class, id))
            .isEqualTo(owner.getEmail());
        var regranted = grant(ownerCookie, operator.getEmail());
        assertThat(regranted.path("id").asText()).isEqualTo(id);
        assertThat(policy.decision(customer.getId(), principal)).contains(true);
        assertThat(policy.visibleCustomerIds(principal)).contains(customer.getId());
        assertThat(identities.authenticateSessionToken(cookie.getValue())).isEqualTo(principal);
        assertThat(jdbc.queryForMap("select * from platform_users where id=?", operator.getId())).isEqualTo(accountBefore);
        assertThat(policy.decision(customer.getId(), identities.authenticateSessionToken(login(globalAdmin).getValue()))).isEmpty();
    }

    @Test void duplicateGrantRepeatedRevokeAndRegrantRetainOneImmutableBinding() throws Exception {
        var first = grant(ownerCookie, operator.getEmail()); var duplicate = grant(ownerCookie, operator.getEmail().toUpperCase());
        String id = first.path("id").asText(); assertThat(duplicate).isEqualTo(first);
        var revoked = body(mvc.perform(delete("/api/customer/staff-access/{id}", id).cookie(ownerCookie)).andExpect(status().isOk()).andReturn());
        var repeated = body(mvc.perform(delete("/api/customer/staff-access/{id}", id).cookie(ownerCookie)).andExpect(status().isOk()).andReturn());
        assertThat(repeated).isEqualTo(revoked);
        var again = grant(ownerCookie, operator.getEmail()); assertThat(again.path("id").asText()).isEqualTo(id);
        assertThat(again.path("status").asText()).isEqualTo("ACTIVE"); assertThat(again.path("revokedAt").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from platform_customer_staff_access where customer_id=? and user_id=?", Integer.class,
            customer.getId(), operator.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_audit_events where action like 'CUSTOMER_STAFF_ACCESS_%' and target_id=?", Integer.class, id)).isEqualTo(3);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var entity = grants.findById(id).orElseThrow(); entity.setCustomerId(otherCustomer.getId()); entity.setUserId(globalAdmin.getId());
            grants.saveAndFlush(entity);
        });
        assertThat(jdbc.queryForObject("select customer_id from platform_customer_staff_access where id=?", String.class, id)).isEqualTo(customer.getId());
        assertThat(jdbc.queryForObject("select user_id from platform_customer_staff_access where id=?", String.class, id)).isEqualTo(operator.getId());
    }

    @Test void customerCanOnlyListOrRevokeOwnEntriesEvenWhenForeignIdIsReal() throws Exception {
        var own = grant(ownerCookie, operator.getEmail());
        var otherCookie = login(otherOwner); var foreign = grant(otherCookie, operator.getEmail());
        var listing = body(mvc.perform(get("/api/customer/staff-access").cookie(ownerCookie)).andExpect(status().isOk()).andReturn());
        assertThat(listing).hasSize(1); assertThat(listing.get(0).path("id").asText()).isEqualTo(own.path("id").asText());
        var rejected = mvc.perform(delete("/api/customer/staff-access/{id}", foreign.path("id").asText()).cookie(ownerCookie))
            .andExpect(status().isNotFound()).andReturn();
        var missing = mvc.perform(delete("/api/customer/staff-access/not-a-real-id").cookie(ownerCookie))
            .andExpect(status().isNotFound()).andReturn();
        assertThat(body(rejected).path("message").asText()).isEqualTo(body(missing).path("message").asText());
        assertThat(grants.findById(foreign.path("id").asText()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test void invalidRecipientRolesAndStatusShareAGenericConflictAndCannotCreateUsers() throws Exception {
        var inactive = user("PLATFORM_OPERATOR", null, passwords.encode(PASSWORD)); inactive.setStatus("DISABLED"); users.saveAndFlush(inactive);
        long before = users.count();
        String expected = null;
        for (String email : List.of(owner.getEmail(), globalAdmin.getEmail(), inactive.getEmail(), "unknown-" + UUID.randomUUID() + "@example.test")) {
            var result = mvc.perform(post("/api/customer/staff-access").cookie(ownerCookie).contentType("application/json")
                .content(json.writeValueAsString(new com.ai.fabric.platform.backend.security.web.CustomerStaffAccessController.Request(email))))
                .andExpect(status().isConflict()).andReturn();
            String message = body(result).path("message").asText();
            if (expected == null) expected = message; else assertThat(message).isEqualTo(expected);
        }
        assertThat(users.count()).isEqualTo(before);
        assertThat(grants.findByCustomerIdOrderByGrantedAtAscIdAsc(customer.getId())).isEmpty();
    }

    @Test void concurrentCustomerGrantsSerializeToOneRowAndOneAuditTransition() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return grant(ownerCookie, operator.getEmail()); });
            var second = pool.submit(() -> { start.await(); return grant(ownerCookie, operator.getEmail()); });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.path("id").asText()).isEqualTo(b.path("id").asText());
            assertThat(jdbc.queryForObject("select count(*) from platform_audit_events where action='CUSTOMER_STAFF_ACCESS_GRANTED' and target_id=?", Integer.class,
                a.path("id").asText())).isEqualTo(1);
        }
    }

    @Test void auditFailureRollsBackTheGrantRatherThanCreatingUnauditedAccess() throws Exception {
        doThrow(new IllegalStateException("fixture audit unavailable")).when(audit)
            .record(eq("CUSTOMER_STAFF_ACCESS_GRANTED"), anyString(), anyString(), anyMap());
        var principal = identities.authenticateSessionToken(ownerCookie.getValue());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))));
        try {
            assertThatThrownBy(() -> grantService.grant(operator.getEmail())).hasMessage("fixture audit unavailable");
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(grants.findByCustomerIdAndUserId(customer.getId(), operator.getId())).isEmpty();
    }

    @Test void fullPostgresMigrationEnforcesUniqueMembershipForeignKeysAndRevocationShape() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='235' and success=true", Integer.class)).isEqualTo(1);
        var entry = grant(ownerCookie, operator.getEmail());
        String insert = "insert into platform_customer_staff_access(id,customer_id,user_id,status,granted_by,granted_at) values (?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP)";
        assertThatThrownBy(() -> jdbc.update(insert, "dup-" + UUID.randomUUID(), customer.getId(), operator.getId(), owner.getEmail()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "bad-customer-" + UUID.randomUUID(), "missing-customer", operator.getId(), owner.getEmail()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "bad-user-" + UUID.randomUUID(), customer.getId(), "missing-user", owner.getEmail()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update platform_customer_staff_access set status='DELETED' where id=?", entry.path("id").asText()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update platform_customer_staff_access set status='REVOKED' where id=?", entry.path("id").asText()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void customerSecretsStayOwnedAndDelegatedOperatorLosesAccessOnTheSameCookie() throws Exception {
        String id = legacyAssignedDraft(), path = "/api/deployments/" + id + "/secrets";
        Cookie operatorCookie = login(operator), otherCookie = login(otherOwner);
        var created = body(mvc.perform(post(path).cookie(ownerCookie).contentType("application/json").content("""
            {"name":"STOCK_API","purpose":"INTEGRATION_CREDENTIAL","value":"test-only-initial-private-value"}
            """)).andExpect(status().isCreated()).andReturn());
        String reference = created.path(0).path("name").asText();
        assertThat(created.toString()).doesNotContain("test-only-initial-private-value", "secretValue");
        mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(otherCookie)).andExpect(status().isNotFound());
        mvc.perform(put(path + "/" + reference).cookie(otherCookie).contentType("application/json")
            .content("{\"value\":\"foreign-attempt\"}")).andExpect(status().isNotFound());
        var grant = grant(ownerCookie, operator.getEmail());
        var visible = body(mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isOk()).andReturn());
        assertThat(visible.path("secrets").size()).isEqualTo(1);
        mvc.perform(put(path + "/" + reference).cookie(operatorCookie).contentType("application/json")
            .content("{\"value\":\"test-only-rotated-private-value\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select secret_value from platform_secrets where name=?", String.class, reference))
            .isEqualTo("test-only-rotated-private-value");
        mvc.perform(delete("/api/customer/staff-access/" + grant.path("id").asText()).cookie(ownerCookie)).andExpect(status().isOk());
        mvc.perform(get(path).cookie(operatorCookie)).andExpect(status().isForbidden());
        mvc.perform(put(path + "/" + reference).cookie(operatorCookie).contentType("application/json")
            .content("{\"value\":\"revoked-attempt\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(path + "/" + reference).cookie(ownerCookie)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from platform_secrets where name=?", Integer.class, reference)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_audit_events where target_id=? and details_json like '%private-value%'", Integer.class, id)).isZero();
    }

    @Test void customerProviderKeyUsesExistingBindingAndCannotBeDeletedWhileSelected() throws Exception {
        String id = legacyAssignedDraft(), path = "/api/deployments/" + id + "/secrets";
        var created = body(mvc.perform(post(path).cookie(ownerCookie).contentType("application/json").content("""
            {"name":"MY_VECTOR","purpose":"QDRANT_API_KEY","value":"test-only-qdrant-value"}
            """)).andExpect(status().isCreated()).andReturn());
        String reference = created.path(0).path("name").asText();
        assertThat(jdbc.queryForObject("select binding_mode from platform_deployment_provider_secret_bindings where deployment_id=? and secret_purpose='QDRANT_API_KEY'", String.class, id))
            .isEqualTo("REQUIRE_OVERRIDE");
        mvc.perform(delete(path + "/" + reference).cookie(ownerCookie)).andExpect(status().isConflict());
        mvc.perform(post(path).cookie(ownerCookie).contentType("application/json").content("""
            {"name":"MY_VECTOR","purpose":"QDRANT_API_KEY","value":"duplicate-attempt"}
            """)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select secret_value from platform_secrets where name=?", String.class, reference))
            .isEqualTo("test-only-qdrant-value");
    }

    @Test void bindingFailureRollsBackCustomerKeysAndTheirAuditTogether() throws Exception {
        String id = legacyAssignedDraft();
        doThrow(new IllegalStateException("test-only binding audit failure")).when(audit)
            .record(eq("DEPLOYMENT_OVERRIDE_BOUND"), eq("DEPLOYMENT"), eq(id), anyMap());
        assertThatThrownBy(() -> mvc.perform(post("/api/deployments/" + id + "/secrets").cookie(ownerCookie)
            .contentType("application/json").content("""
                {"name":"MY_VECTOR","purpose":"QDRANT_API_KEY","value":"test-only-private-value"}
                """)))
            .hasRootCauseMessage("test-only binding audit failure");
        assertThat(jdbc.queryForObject("select count(*) from platform_secrets where deployment_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_deployment_provider_secret_bindings where deployment_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_audit_events where target_id=? and action='CUSTOMER_SECRET_CREATED'", Integer.class, id)).isZero();
    }

    private JsonNode grant(Cookie cookie, String email) throws Exception {
        return body(mvc.perform(post("/api/customer/staff-access").cookie(cookie).contentType("application/json")
            .content(json.writeValueAsString(new com.ai.fabric.platform.backend.security.web.CustomerStaffAccessController.Request(email))))
            .andExpect(status().isOk()).andReturn());
    }
    private String legacyAssignedDraft() {
        var tenant = new PlatformTenantEntity(); tenant.setId("ten-" + UUID.randomUUID()); tenant.setCustomerId(customer.getId());
        tenant.setName("Staff grant fixture"); tenant.setSlug(tenant.getId()); tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(Instant.now()); tenant.setUpdatedAt(Instant.now()); tenants.saveAndFlush(tenant);
        var deployment = new DeploymentEntity(); deployment.setId("dep-" + UUID.randomUUID()); deployment.setName("Staff grant draft");
        deployment.setEnvironmentName("staging"); deployment.setTemplateId("dev-openai-lucene"); deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setStatus("DRAFT"); deployment.setCustomerId(customer.getId()); deployment.setTenantId(tenant.getId());
        deployment.setCreatedAt(Instant.now()); deployment.setUpdatedAt(Instant.now()); deployments.saveAndFlush(deployment);
        var assignment = new DeploymentAssignmentEntity(); assignment.setId("asg-" + UUID.randomUUID());
        assignment.setDeploymentId(deployment.getId()); assignment.setUserId(operator.getId()); assignment.setAssignmentRole("DEPLOYMENT_ADMIN");
        assignment.setCreatedAt(Instant.now()); assignment.setUpdatedAt(Instant.now()); assignments.saveAndFlush(assignment);
        return deployment.getId();
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private Cookie login(PlatformUserEntity user) throws Exception {
        var result = mvc.perform(post("/api/platform/auth/login").contentType("application/json")
            .content(json.writeValueAsString(new com.ai.fabric.platform.backend.security.model.PlatformLoginRequest(user.getEmail(), PASSWORD))))
            .andExpect(status().isOk()).andReturn();
        String header = result.getResponse().getHeader("Set-Cookie");
        return new Cookie("platform_session", header.substring("platform_session=".length(), header.indexOf(';')));
    }
    private PlatformCustomerEntity customer() {
        var value = new PlatformCustomerEntity(); value.setId("cus-" + UUID.randomUUID()); value.setName(value.getId()); value.setSlug(value.getId());
        value.setStatus("ACTIVE"); value.setCreatedAt(Instant.now()); value.setUpdatedAt(Instant.now()); return customers.saveAndFlush(value);
    }
    private PlatformUserEntity user(String role, String customerId, String hash) {
        var user = new PlatformUserEntity(); user.setId("usr-" + UUID.randomUUID()); user.setEmail(user.getId() + "@example.test");
        user.setDisplayName(role); user.setRole(role); user.setStatus("ACTIVE"); user.setCustomerId(customerId); user.setPasswordHash(hash);
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now()); return users.saveAndFlush(user);
    }
}
