package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.CustomerAccountRecoveryService;
import com.ai.fabric.platform.backend.security.web.CustomerAccountRecoveryController;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Native security chain, real signed JWT/JWKS, current-user HTTP verification and real PostgreSQL locks/rollback.
 * Supabase email delivery and token exchange are an explicit loopback provider fixture, not a hosted proof. */
@SpringBootTest(properties = {
    "platform.auth.enabled=true", "platform.auth.admin-api-key=recovery-test-staff-key", "platform.auth.session-enabled=true",
    "platform.auth.customer-supabase.enabled=true", "platform.auth.customer-supabase.public-key=sb_publishable_recovery_test_public_key",
    "platform.auth.customer-supabase.link-origins[0]=https://console.example.test",
    "platform.auth.customer-supabase.desk-clients[0].client-id=desk-approved",
    "platform.auth.customer-supabase.desk-clients[0].redirect-uris[0]=https://desk.example.test/auth/callback",
    "platform.cors.allowed-origins[0]=https://console.example.test", "platform.cors.allowed-origins[1]=https://site.example.test",
    "platform.cors.allowed-origin-patterns[0]=https://*.apps.example.test", "platform.cors.allow-credentials=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerAccountRecoveryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Proof> PROOFS = new ConcurrentHashMap<>();
    private static final Map<String, Proof> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger VERIFY_CALLS = new AtomicInteger(), USER_CALLS = new AtomicInteger(), REDIRECT_CALLS = new AtomicInteger();
    private static final List<String> CONTRACT_ERRORS = new CopyOnWriteArrayList<>();
    private static HttpServer provider;
    private static ExecutorService providerThreads;
    private static RSAKey key;
    private static String base;

    @DynamicPropertySource static void configuration(DynamicPropertyRegistry p) throws Exception {
        key = new RSAKeyGenerator(2048).keyID("recovery-key").generate();
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerThreads = Executors.newCachedThreadPool(); provider.setExecutor(providerThreads);
        provider.createContext("/auth/v1/.well-known/jwks.json", x -> reply(x, 200, new JWKSet(key.toPublicJWK()).toString()));
        provider.createContext("/auth/v1/verify", CustomerAccountRecoveryPostgresTest::verify);
        provider.createContext("/auth/v1/user", CustomerAccountRecoveryPostgresTest::user);
        provider.createContext("/must-not-follow", x -> { REDIRECT_CALLS.incrementAndGet(); reply(x, 200, "{}"); });
        provider.start(); base = "http://127.0.0.1:" + provider.getAddress().getPort();
        p.add("platform.auth.customer-supabase.project-url", () -> base);
        p.add("platform.auth.customer-supabase.issuer", () -> base + "/auth/v1");
        p.add("platform.auth.customer-supabase.jwks-uri", () -> base + "/auth/v1/.well-known/jwks.json");
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl); p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.flyway.locations", () -> "classpath:db/migration"); p.add("spring.flyway.target", () -> "latest");
    }

    @AfterAll static void stop() {
        if (provider != null) provider.stop(0);
        if (providerThreads != null) providerThreads.shutdownNow();
        assertThat(CONTRACT_ERRORS).isEmpty();
    }

    @Autowired MockMvc mvc;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformCustomerIdentityLinkRepository links;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired CustomerAccountRecoveryService recovery;
    @SpyBean PlatformAuditService audit;

    @Test void explicitEmailProofLinksOnlyExistingOwnerAndPreservesAllLocalAccountData() throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        String beforeUser = row("platform_users", owner.getId()), beforeCustomer = row("platform_customers", owner.getCustomerId());
        long userCount = users.count(), customerCount = customers.count();
        var result = recover(proof, "  " + owner.getEmail().toUpperCase(Locale.ROOT) + "  ");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("accessToken", "refreshToken", "expiresAt", "userId");
        assertThat(body.path("accessToken").asText()).isEqualTo(proof.accessToken);
        assertThat(body.path("refreshToken").asText()).isEqualTo(proof.refreshToken);
        assertThat(body.path("userId").asText()).isEqualTo(proof.subject);
        assertThat(body.path("expiresAt").asLong()).isGreaterThan(Instant.now().getEpochSecond());
        assertThat(row("platform_users", owner.getId())).isEqualTo(beforeUser);
        assertThat(row("platform_customers", owner.getCustomerId())).isEqualTo(beforeCustomer);
        assertThat(users.count()).isEqualTo(userCount); assertThat(customers.count()).isEqualTo(customerCount);
        var link = links.findByPlatformUserId(owner.getId()).orElseThrow();
        assertThat(link.getSubject()).isEqualTo(proof.subject); assertThat(link.getIssuer()).isEqualTo(base + "/auth/v1");
        assertThat(auditCount(owner.getId())).isEqualTo(1);
        String details = jdbc.queryForObject("select details_json from platform_audit_events where action='CUSTOMER_IDENTITY_RECOVERED' and target_id=?", String.class, owner.getId());
        assertThat(JSON.readTree(details).fieldNames()).toIterable().containsExactlyInAnyOrder("identityLinkId", "customerId", "proof");
        assertThat(details).doesNotContain(proof.hash, proof.accessToken, proof.refreshToken, owner.getEmail());
        var session = mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer " + proof.accessToken)).andReturn();
        assertThat(session.getResponse().getStatus()).isEqualTo(200);
        assertThat(JSON.readTree(session.getResponse().getContentAsString()).path("customerId").asText()).isEqualTo(owner.getCustomerId());
    }

    @Test void consumedLinkIsNotRetriedButFreshProofForExactActiveLinkIsIdempotent() throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        assertThat(recover(proof, owner.getEmail()).getResponse().getStatus()).isEqualTo(200);
        int calls = VERIFY_CALLS.get();
        var replay = recover(proof, owner.getEmail()); assertFailure(replay, 401);
        assertThat(VERIFY_CALLS.get()).isEqualTo(calls + 1);
        var fresh = proof(owner.getEmail(), proof.subject, "valid");
        assertThat(recover(fresh, owner.getEmail()).getResponse().getStatus()).isEqualTo(200);
        assertThat(auditCount(owner.getId())).isEqualTo(1);
    }

    @Test void unexpectedOrForwardedLinkCannotRecoverADifferentConfirmedEmail() throws Exception {
        var intended = owner(); var unexpected = owner(); var proof = proof(unexpected.getEmail(), "valid");
        assertFailure(recover(proof, intended.getEmail()), 401);
        assertThat(links.findByPlatformUserId(intended.getId())).isEmpty();
        assertThat(links.findByPlatformUserId(unexpected.getId())).isEmpty();
        assertFailure(recover(proof, unexpected.getEmail()), 401);
    }

    @ParameterizedTest @ValueSource(strings = {"jwt-email", "subject", "unconfirmed", "anonymous", "missing-anonymous", "missing-email", "metadata-only", "revoked-session"})
    void currentProviderIdentityMustAgreeWithSignedJwtAndVerifiedMailbox(String fault) throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), fault);
        assertFailure(recover(proof, owner.getEmail()), 401);
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty(); assertThat(auditCount(owner.getId())).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"delegated", "wrong-issuer", "wrong-audience", "expired", "foreign-signature", "service-role", "missing-session"})
    void recoveryUsesExistingDecoderAndNeverAcceptsDelegatedTokens(String fault) throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), fault); int calls = USER_CALLS.get();
        assertFailure(recover(proof, owner.getEmail()), 401);
        assertThat(USER_CALLS.get()).isEqualTo(calls);
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR", "disabled-user", "disabled-customer", "no-customer", "missing-user"})
    void onlyAnExistingActiveCustomerAdminWithActiveOwningCustomerCanRecover(String fault) throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        switch (fault) {
            case "disabled-user" -> { owner.setStatus("DISABLED"); users.saveAndFlush(owner); }
            case "disabled-customer" -> jdbc.update("update platform_customers set status='DISABLED' where id=?", owner.getCustomerId());
            case "no-customer" -> { owner.setCustomerId(null); users.saveAndFlush(owner); }
            case "missing-user" -> users.deleteById(owner.getId());
            default -> { owner.setRole(fault); users.saveAndFlush(owner); }
        }
        assertFailure(recover(proof, owner.getEmail()), 403);
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty(); assertThat(auditCount(owner.getId())).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"other-subject", "other-issuer", "revoked", "foreign-user"})
    void existingLinksCannotBeReassignedOrReactivated(String fault) throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        var link = new PlatformCustomerIdentityLinkEntity(); link.setId("cil-" + UUID.randomUUID());
        link.setIssuer(fault.equals("other-issuer") ? "https://other.invalid/auth/v1" : base + "/auth/v1");
        link.setSubject(fault.equals("other-subject") ? "other" : proof.subject);
        link.setPlatformUserId(fault.equals("foreign-user") ? owner().getId() : owner.getId());
        link.setCreatedAt(Instant.now()); if (fault.equals("revoked")) link.setRevokedAt(Instant.now());
        links.saveAndFlush(link); String before = row("platform_customer_identity_links", link.getId());
        assertFailure(recover(proof, owner.getEmail()), 409);
        assertThat(row("platform_customer_identity_links", link.getId())).isEqualTo(before);
        assertThat(auditCount(owner.getId())).isZero();
    }

    @Test void recoveryIsPublicButOnlyExactConsoleOriginsCanConsumeProof() throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid"); int calls = VERIFY_CALLS.get();
        for (String origin : Arrays.asList(null, "null", "https://site.example.test", "https://dealer.apps.example.test",
            "https://console.example.test.attacker.test", "https://unknown.example.test")) {
            var request = post("/api/platform/auth/customer-recovery").contentType(MediaType.APPLICATION_JSON)
                .content(body(proof.hash, owner.getEmail()));
            if (origin != null) request.header(HttpHeaders.ORIGIN, origin);
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(VERIFY_CALLS.get()).isEqualTo(calls);
        // An expired cached browser bearer must not replace the submitted mailbox proof or require an existing link.
        assertThat(mvc.perform(post("/api/platform/auth/customer-recovery").header(HttpHeaders.ORIGIN, "https://console.example.test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer expired-cached-session").contentType(MediaType.APPLICATION_JSON)
            .content(body(proof.hash, owner.getEmail()))).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test void boundedInputAndRedactionPreventAccidentalCredentialExposure() throws Exception {
        var owner = owner(); int calls = VERIFY_CALLS.get();
        for (String hash : Arrays.asList(null, "", " ", "x".repeat(1025))) {
            assertThat(mvc.perform(post("/api/platform/auth/customer-recovery").header(HttpHeaders.ORIGIN, "https://console.example.test")
                .contentType(MediaType.APPLICATION_JSON).content(body(hash, owner.getEmail()))).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        for (String email : Arrays.asList(null, "", "not-email", "a".repeat(256) + "@test.invalid")) {
            assertThat(mvc.perform(post("/api/platform/auth/customer-recovery").header(HttpHeaders.ORIGIN, "https://console.example.test")
                .contentType(MediaType.APPLICATION_JSON).content(body("token", email))).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(VERIFY_CALLS.get()).isEqualTo(calls);
        assertThat(new CustomerAccountRecoveryController.RecoveryRequest("secret", "private@test.invalid").toString()).isEqualTo("RecoveryRequest[REDACTED]");
        assertThat(new CustomerAccountRecoveryService.RecoverySession("access", "refresh", 1, "subject").toString()).isEqualTo("RecoverySession[REDACTED]");
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void malformedJsonNeverExposesTheEmailTokenInParserDiagnostics(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        int calls = VERIFY_CALLS.get();
        var result = mvc.perform(post("/api/platform/auth/customer-recovery").header(HttpHeaders.ORIGIN, "https://console.example.test")
            .contentType(MediaType.APPLICATION_JSON).content("{\"tokenHash\":\"parser-token-must-not-appear")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("parser-token-must-not-appear", "JsonEOFException", "JSON parse error");
        assertThat(output.getAll()).doesNotContain("parser-token-must-not-appear");
        assertThat(VERIFY_CALLS.get()).isEqualTo(calls);
    }

    @ParameterizedTest @ValueSource(strings = {"verify-redirect", "user-redirect", "verify-down", "user-down", "malformed", "missing-refresh", "verify-timeout"})
    void providerFailuresAreSanitizedVisibleAndNeverFollowedOrRetried(String fault) throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), fault); int calls = VERIFY_CALLS.get();
        assertFailure(recover(proof, owner.getEmail()), 503);
        assertThat(VERIFY_CALLS.get()).isEqualTo(calls + 1); assertThat(REDIRECT_CALLS.get()).isZero();
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty();
    }

    @Test void auditFailureRollsBackTheLinkAndDoesNotReturnSessionSecrets() throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        doThrow(new IllegalStateException("synthetic-internal-secret")).when(audit)
            .record(eq("CUSTOMER_IDENTITY_RECOVERED"), anyString(), eq(owner.getId()), anyMap());
        assertFailure(recover(proof, owner.getEmail()), 503);
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty(); assertThat(auditCount(owner.getId())).isZero();
        assertFailure(recover(proof, owner.getEmail()), 401);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentProofsSerializeOnLocalUserWithoutDuplicateLinksOrAudit(boolean sameSubject) throws Exception {
        var owner = owner(); var first = proof(owner.getEmail(), "valid");
        var second = proof(owner.getEmail(), sameSubject ? first.subject : UUID.randomUUID().toString(), "valid");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return recover(first, owner.getEmail()).getResponse().getStatus(); });
            var b = pool.submit(() -> { start.await(); return recover(second, owner.getEmail()).getResponse().getStatus(); });
            start.countDown(); List<Integer> statuses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            if (sameSubject) assertThat(statuses).containsExactly(200, 200);
            else assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(links.findByPlatformUserId(owner.getId())).isPresent(); assertThat(auditCount(owner.getId())).isEqualTo(1);
    }

    @Test void aCommittedDisableWhileRecoveryWaitsForTheUserLockCannotBeLost() throws Exception {
        var owner = owner(); var proof = proof(owner.getEmail(), "valid");
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                var user = users.findByEmailForRecovery(owner.getEmail()).orElseThrow();
                user.setStatus("DISABLED"); users.saveAndFlush(user); locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Release timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var recovering = pool.submit(() -> recover(proof, owner.getEmail()));
            try { assertThatThrownBy(() -> recovering.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS); assertFailure(recovering.get(10, TimeUnit.SECONDS), 403);
        }
        assertThat(links.findByPlatformUserId(owner.getId())).isEmpty();
    }

    private PlatformUserEntity owner() {
        Instant now = Instant.now(); String id = UUID.randomUUID().toString();
        var c = new PlatformCustomerEntity(); c.setId("cus-" + id); c.setName("Recovery test"); c.setSlug(id);
        c.setStatus("ACTIVE"); c.setCreatedAt(now); c.setUpdatedAt(now); customers.saveAndFlush(c);
        var u = new PlatformUserEntity(); u.setId("usr-" + id); u.setEmail(id + "@test.invalid"); u.setDisplayName("Existing owner");
        u.setPasswordHash("preserved-legacy-password-hash"); u.setRole("CUSTOMER_ADMIN"); u.setStatus("ACTIVE");
        u.setCustomerId(c.getId()); u.setCreatedAt(now); u.setUpdatedAt(now); return users.saveAndFlush(u);
    }

    private MvcResult recover(Proof proof, String email) throws Exception {
        return mvc.perform(post("/api/platform/auth/customer-recovery").header(HttpHeaders.ORIGIN, "https://console.example.test")
            .contentType(MediaType.APPLICATION_JSON).content(body(proof.hash, email))).andReturn();
    }
    private String body(String hash, String email) throws Exception { return JSON.writeValueAsString(new CustomerAccountRecoveryController.RecoveryRequest(hash, email)); }
    private void assertFailure(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(result.getResponse().getContentAsString()).contains("Request a new email link", "contact your workspace administrator")
            .doesNotContain("accessToken", "refreshToken", "synthetic-internal-secret", "provider-body-secret");
    }
    private String row(String table, String id) { return jdbc.queryForObject("select row_to_json(r)::text from " + table + " r where id=?", String.class, id); }
    private int auditCount(String userId) { return jdbc.queryForObject("select count(*) from platform_audit_events where action='CUSTOMER_IDENTITY_RECOVERED' and target_id=?", Integer.class, userId); }

    private Proof proof(String email, String fault) throws Exception { return proof(email, UUID.randomUUID().toString(), fault); }
    private Proof proof(String email, String subject, String fault) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(fault.equals("wrong-issuer") ? "https://foreign.invalid/auth/v1" : base + "/auth/v1")
            .subject(subject).audience(fault.equals("wrong-audience") ? "foreign" : "authenticated")
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(Instant.now().plusSeconds(fault.equals("expired") ? -180 : 300)))
            .claim("email", fault.equals("jwt-email") ? "foreign@test.invalid" : email)
            .claim("role", fault.equals("service-role") ? "service_role" : "authenticated")
            .claim("session_id", fault.equals("missing-session") ? null : UUID.randomUUID().toString());
        if (fault.equals("delegated")) claims.claim("client_id", "desk-approved");
        RSAKey signing = fault.equals("foreign-signature") ? new RSAKeyGenerator(2048).keyID("foreign").generate() : key;
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signing.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(signing));
        var proof = new Proof(UUID.randomUUID().toString(), subject, email, jwt.serialize(), "refresh-" + UUID.randomUUID(), fault);
        PROOFS.put(proof.hash, proof); SESSIONS.put(proof.accessToken, proof); return proof;
    }

    private static void verify(HttpExchange x) throws IOException {
        VERIFY_CALLS.incrementAndGet();
        if (!x.getRequestMethod().equals("POST") || !"sb_publishable_recovery_test_public_key".equals(x.getRequestHeaders().getFirst("apikey"))
            || x.getRequestHeaders().getFirst("Authorization") != null) CONTRACT_ERRORS.add("verify headers");
        JsonNode request = JSON.readTree(x.getRequestBody());
        if (request.size() != 2 || !request.path("type").asText().equals("email")) CONTRACT_ERRORS.add("verify body");
        Proof p = PROOFS.remove(request.path("token_hash").asText());
        if (p == null) { reply(x, 403, "{\"error\":\"provider-body-secret\"}"); return; }
        if (p.fault.equals("verify-timeout")) {
            try { Thread.sleep(6500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        if (p.fault.equals("verify-redirect")) { x.getResponseHeaders().add("Location", base + "/must-not-follow"); reply(x, 302, "{}"); return; }
        if (p.fault.equals("verify-down")) { reply(x, 503, "provider-body-secret"); return; }
        if (p.fault.equals("malformed")) { reply(x, 200, "not-json provider-body-secret"); return; }
        var body = JSON.createObjectNode().put("access_token", p.accessToken).put("token_type", "bearer").put("expires_in", 300);
        if (!p.fault.equals("missing-refresh")) body.put("refresh_token", p.refreshToken);
        reply(x, 200, body.toString());
    }
    private static void user(HttpExchange x) throws IOException {
        USER_CALLS.incrementAndGet();
        if (!x.getRequestMethod().equals("GET") || !"sb_publishable_recovery_test_public_key".equals(x.getRequestHeaders().getFirst("apikey"))) CONTRACT_ERRORS.add("user headers");
        String auth = x.getRequestHeaders().getFirst("Authorization");
        Proof p = auth != null && auth.startsWith("Bearer ") ? SESSIONS.get(auth.substring(7)) : null;
        if (p == null || p.fault.equals("revoked-session")) { reply(x, 401, "provider-body-secret"); return; }
        if (p.fault.equals("user-down")) { reply(x, 503, "provider-body-secret"); return; }
        if (p.fault.equals("user-redirect")) { x.getResponseHeaders().add("Location", base + "/must-not-follow"); reply(x, 302, "{}"); return; }
        var user = JSON.createObjectNode().put("id", p.fault.equals("subject") ? "foreign" : p.subject);
        if (!p.fault.equals("unconfirmed") && !p.fault.equals("metadata-only")) user.put("email_confirmed_at", "2026-10-01T00:00:00Z");
        if (!p.fault.equals("missing-email")) user.put("email", p.email);
        if (!p.fault.equals("missing-anonymous")) user.put("is_anonymous", p.fault.equals("anonymous"));
        user.putObject("user_metadata").put("email_verified", true);
        reply(x, 200, user.toString());
    }
    private static void reply(HttpExchange x, int status, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        try { x.getResponseBody().write(bytes); } finally { x.close(); }
    }
    private record Proof(String hash, String subject, String email, String accessToken, String refreshToken, String fault) {
        @Override public String toString() { return "Proof[REDACTED]"; }
    }
}
