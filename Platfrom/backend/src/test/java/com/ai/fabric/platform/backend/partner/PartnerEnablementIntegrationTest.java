package com.ai.fabric.platform.backend.partner;

import com.ai.fabric.platform.backend.partner.entity.PartnerSupportReplyEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerActionAuditRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportReplyRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.bootstrap.sample-enabled=false",
    "platform.auth.supabase.enabled=true",
    "platform.auth.supabase.issuer=http://supabase.test/project",
    "platform.auth.supabase.audience=authenticated",
    "platform.auth.supabase.require-email-verified=true",
    "platform.auth.supabase.partner-app-url=http://partners.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerEnablementIntegrationTest {

    private static final String ISSUER = "http://supabase.test/project";
    private static final ECKey EC_KEY = createEcKey();
    private static HttpServer jwksServer;
    private static String jwksUri;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopifyStoreConnectionRepository storeConnectionRepository;

    @Autowired
    private PlatformManagedProductServiceRepository productServiceRepository;

    @Autowired
    private PartnerSupportReplyRepository replyRepository;

    @Autowired
    private PartnerActionAuditRepository auditRepository;

    @DynamicPropertySource
    static void partnerAuthProperties(DynamicPropertyRegistry registry) {
        ensureJwksServer();
        registry.add("platform.auth.supabase.jwks-uri", () -> jwksUri);
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void partnerSessionRequiresValidSupabaseJwtAndExistingOperatorAuthStillWorks() throws Exception {
        mockMvc.perform(get("/api/partners/session"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/partners/session")
                .header("Authorization", "Bearer " + partnerJwt("bad-issuer-user", "bad-issuer@example.com", "http://wrong-issuer.test", true)))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deployments")
                .header("Authorization", "Bearer " + partnerJwt("partner-on-operator-api", "partner-operator-boundary@example.com")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk());
    }

    @Test
    void selfServiceSignupCreatesEmptyWorkspaceAndStarterSafeCatalog() throws Exception {
        String token = partnerJwt("signup-user", "signup-user@example.com");

        mockMvc.perform(get("/api/partners/session")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.signupRequired", is(true)))
            .andExpect(jsonPath("$.assignedStoreCount", is(0)));

        mockMvc.perform(post("/api/partners/signup/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceName": "Signup Partner Workspace"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signupRequired", is(false)))
            .andExpect(jsonPath("$.account.name", is("Signup Partner Workspace")))
            .andExpect(jsonPath("$.assignedStoreCount", is(0)));

        mockMvc.perform(get("/api/partners/stores")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(get("/api/partners/catalog")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.surfaceId == 'ai-search')].tier", hasItem("Free")))
            .andExpect(jsonPath("$[*].surfaceId", not(hasItem("order-lookup"))));
    }

    @Test
    void partnerSessionAcceptsSupabaseEmailVerificationFromUserMetadata() throws Exception {
        String token = partnerJwtWithUserMetadataEmailVerified("metadata-verified-user", "metadata-verified@example.com");

        mockMvc.perform(get("/api/partners/session")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.signupRequired", is(true)));
    }

    @Test
    void unassignedPartnerCannotAccessStoreDataOrCreateEscalation() throws Exception {
        String token = partnerJwt("unassigned-user", "unassigned-user@example.com");
        completeSignup(token, "Unassigned Partner Workspace");

        mockMvc.perform(get("/api/partners/stores/psa-missing")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/partners/stores/psa-missing/escalations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Cannot see storefront surface",
                      "severity": "HIGH",
                      "description": "The partner should not be able to create this case for an unassigned store."
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void merchantApprovalCreatesScopedAssignmentAndEscalationThreadHidesInternalReplies() throws Exception {
        String token = partnerJwt("approved-user", "approved-user@example.com");
        completeSignup(token, "Approved Partner Workspace");
        createShopifyStore("shopify-store-approved", "approved-client.myshopify.com", "Approved Client");

        var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientName": "Approved Client",
                      "contactEmail": "merchant@example.com",
                      "shopDomain": "approved-client.myshopify.com",
                      "vertical": "fashion",
                      "requestedTier": "STARTER",
                      "requestedSurfaces": ["ai-search", "product-faq", "order-lookup"],
                      "knownIntegrations": ["reviews"],
                      "notes": "Starter implementation only."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("DRAFT")))
            .andExpect(jsonPath("$.requestedSurfaces", hasItem("ai-search")))
            .andExpect(jsonPath("$.requestedSurfaces", not(hasItem("order-lookup"))))
            .andReturn();

        String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");
        var linkResult = mockMvc.perform(post("/api/partners/client-implementations/{requestId}/store-access-links", implementationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.approvalUrl", startsWith("http://partners.test/merchant/partner-access/")))
            .andReturn();
        String approvalUrl = JsonPath.read(linkResult.getResponse().getContentAsString(), "$.approvalUrl");
        String approvalCode = approvalUrl.substring(approvalUrl.lastIndexOf('/') + 1);

        var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/approve", approvalCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "approverEmail": "owner@example.com",
                      "approvedScope": "IMPLEMENTATION_SUPPORT"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andReturn();
        String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

        mockMvc.perform(get("/api/partners/stores")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(assignmentId)))
            .andExpect(jsonPath("$[0].status", is("READY")))
            .andExpect(jsonPath("$[0].enabledSurfaces", not(hasItem("order-lookup"))));

        var escalationResult = mockMvc.perform(post("/api/partners/stores/{storeId}/escalations", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "FAQ block did not render",
                      "severity": "HIGH",
                      "description": "The product FAQ block is missing on the approved client storefront.",
                      "reproductionSteps": "Open the product page and inspect the FAQ placement.",
                      "expectedBehavior": "FAQ block renders.",
                      "actualBehavior": "FAQ block is absent.",
                      "impact": "Launch is blocked.",
                      "nextAction": "Check app embed placement."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andReturn();
        String escalationId = JsonPath.read(escalationResult.getResponse().getContentAsString(), "$.id");

        saveReply(escalationId, "PARTNER_VISIBLE", "Visible update for the partner.");
        saveReply(escalationId, "OPERATOR_INTERNAL", "Operator-only note must not leak.");

        mockMvc.perform(get("/api/partners/escalations/{escalationId}/thread", escalationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replies.length()", is(1)))
            .andExpect(jsonPath("$.replies[0].bodyMarkdown", is("Visible update for the partner.")))
            .andExpect(jsonPath("$.replies[0].visibility", is("PARTNER_VISIBLE")));

        assertThat(auditRepository.findAll())
            .extracting("action")
            .contains("PARTNER_SIGNUP_COMPLETED", "STORE_ACCESS_APPROVED", "SUPPORT_ESCALATION_CREATED");
    }

    private void completeSignup(String token, String workspaceName) throws Exception {
        mockMvc.perform(post("/api/partners/signup/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceName": "%s"
                    }
                    """.formatted(workspaceName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signupRequired", is(false)));
    }

    private void createShopifyStore(String id, String shopDomain, String displayName) {
        Instant now = Instant.now();
        productServiceRepository.findById("shopify-companion").orElseGet(() -> {
            PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
            service.setId("shopify-companion");
            service.setServiceRef("shopify-companion");
            service.setDisplayName("Shopify Companion");
            service.setProductFamily("SHOPIFY_COMPANION");
            service.setServiceKind("SHOPIFY_APP");
            service.setDeploymentMode("MANAGED");
            service.setTenantMode("MULTI_TENANT");
            service.setEnvironmentScope("test");
            service.setDesiredReplicas(1);
            service.setActualReplicas(1);
            service.setStatus("ACTIVE");
            service.setDetailsJson("{}");
            service.setCreatedAt(now);
            service.setUpdatedAt(now);
            return productServiceRepository.save(service);
        });
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId(id);
        entity.setShopDomain(shopDomain);
        entity.setDisplayName(displayName);
        entity.setProductServiceId("shopify-companion");
        entity.setCustomerId("customer-" + id);
        entity.setDeploymentId("deployment-" + id);
        entity.setConsumerId("consumer-" + id);
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("READY");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setArticlesEnabled(true);
        entity.setMetaobjectsEnabled(true);
        entity.setLastSourcePreflightAt(now);
        entity.setLastSyncAt(now);
        entity.setLastWebhookAt(now);
        entity.setDetailsJson("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        storeConnectionRepository.save(entity);
    }

    private void saveReply(String escalationId, String visibility, String body) {
        PartnerSupportReplyEntity reply = new PartnerSupportReplyEntity();
        reply.setId("reply-" + visibility.toLowerCase() + "-" + Instant.now().toEpochMilli());
        reply.setEscalationId(escalationId);
        reply.setAuthorMemberId("operator");
        reply.setAuthorName("Support");
        reply.setAuthorRole("PLATFORM_OPERATOR");
        reply.setVisibility(visibility);
        reply.setBodyMarkdown(body);
        reply.setAttachmentsJson("[]");
        reply.setCreatedAt(Instant.now());
        replyRepository.save(reply);
    }

    private static String partnerJwt(String subject, String email) throws Exception {
        return partnerJwt(subject, email, ISSUER, true);
    }

    private static String partnerJwt(String subject, String email, String issuer, boolean emailVerified) throws Exception {
        return partnerJwt(subject, email, issuer, emailVerified, null);
    }

    private static String partnerJwtWithUserMetadataEmailVerified(String subject, String email) throws Exception {
        return partnerJwt(subject, email, ISSUER, false, Map.of("email_verified", true));
    }

    private static String partnerJwt(String subject,
                                     String email,
                                     String issuer,
                                     boolean emailVerified,
                                     Map<String, Object> userMetadata) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .audience("authenticated")
            .issueTime(Date.from(now.minusSeconds(5)))
            .expirationTime(Date.from(now.plusSeconds(600)))
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .claim("name", "Test Partner")
            .claim("app_metadata", Map.of("provider", "email"));
        if (userMetadata != null) {
            claims.claim("user_metadata", userMetadata);
        }
        SignedJWT signed = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(JOSEObjectType.JWT)
                .keyID(EC_KEY.getKeyID())
                .build(),
            claims.build()
        );
        signed.sign(new ECDSASigner(EC_KEY.toECPrivateKey()));
        return signed.serialize();
    }

    private static ECKey createEcKey() {
        try {
            return new ECKeyGenerator(Curve.P_256)
                .keyID("partner-test-key")
                .generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create partner test key.", exception);
        }
    }

    private static synchronized void ensureJwksServer() {
        if (jwksServer != null) {
            return;
        }
        try {
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = ("{\"keys\":[" + EC_KEY.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            jwksServer.start();
            jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start JWKS test server.", exception);
        }
    }
}
