package com.ai.fabric.platform.backend.partner;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.partner.entity.PartnerPackageTrialActivationEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportReplyEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerActionAuditRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerPackageTrialActivationRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportReplyRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
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
import com.sun.net.httpserver.HttpExchange;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final String PRODUCT_SERVICE_SECRET_NAME = "MANAGED_SHOPIFY_COMPANION_TEST_API_KEY";
    private static final String PRODUCT_SERVICE_TEST_KEY = "shopify-companion-product-service-test-key";
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
    private DeploymentRepository deploymentRepository;

    @Autowired
    private PlatformCustomerRepository customerRepository;

    @Autowired
    private PlatformTenantRepository tenantRepository;

    @Autowired
    private PlatformSecretService platformSecretService;

    @Autowired
    private PlatformSecretRepository platformSecretRepository;

    @Autowired
    private PartnerSupportReplyRepository replyRepository;

    @Autowired
    private PartnerActionAuditRepository auditRepository;

    @Autowired
    private PartnerPackageTrialActivationRepository packageTrialActivationRepository;

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

        mockMvc.perform(get("/api/partners/stores/psa-missing/product-controls")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/partners/stores/psa-missing/product-controls/widget-settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "launcherLabel": "Should not save",
                      "welcomeMessage": "This must not be accepted for an unassigned store.",
                      "shellModeProfile": "SHOPIFY_COMPANION",
                      "enabledSurfaces": ["ai-search"],
                      "defaultConversationMode": "navigator",
                      "allowedConversationModes": ["navigator"],
                      "pageModeMappings": {}
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/partners/stores/psa-missing/max-widget/chat/me/auth-context")
                .header("Authorization", "Bearer " + token)
                .param("authPath", "PLATFORM_PRIVATE"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/partners/stores/psa-missing/max-widget/chat/me/query")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"This must not reach the runtime.\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void merchantApprovalCreatesScopedAssignmentAndEscalationThreadHidesInternalReplies() throws Exception {
        String token = partnerJwt("approved-user", "approved-user@example.com");
        completeSignup(token, "Approved Partner Workspace");
        HttpServer runtimeServer = startPartnerWidgetRuntime();
        try {
        String deploymentId = "deployment-shopify-store-approved";
        createRuntimeDeployment(deploymentId, "http://localhost:" + runtimeServer.getAddress().getPort());
        createShopifyStore("shopify-store-approved", "approved-client.myshopify.com", "Approved Client");

        mockMvc.perform(get("/api/partners/eligible-stores")
                .param("query", "approved")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].storeConnectionId", is("shopify-store-approved")))
            .andExpect(jsonPath("$[0].shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$[0].enabledSurfaces", hasItem("product-faq")));

        var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientName": "Approved Client",
                      "contactEmail": "merchant@example.com",
                      "storeConnectionId": "shopify-store-approved",
                      "vertical": "fashion",
                      "knownIntegrations": ["reviews"],
                      "notes": "Full configured store access."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("WAITING_ON_MERCHANT")))
            .andExpect(jsonPath("$.storeConnectionId", is("shopify-store-approved")))
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.requestedTier", is("MERCHANT_CONFIGURED")))
            .andExpect(jsonPath("$.requestedSurfaces", hasItem("ai-search")))
            .andExpect(jsonPath("$.requestedSurfaces", hasItem("product-faq")))
            .andExpect(jsonPath("$.requestedSurfaces", not(hasItem("order-lookup"))))
            .andReturn();

        String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");
        String approvalUrl = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.approvalUrl");
        String approvalCode = approvalCodeFromUrl(approvalUrl);
        mockMvc.perform(get("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(implementationId)))
            .andExpect(jsonPath("$[0].status", is("WAITING_ON_MERCHANT")))
            .andExpect(jsonPath("$[0].shopDomain", is("approved-client.myshopify.com")));

        var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "approved-client.myshopify.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].implementationRequestId", is(implementationId)))
            .andExpect(jsonPath("$[0].partnerName", is("Approved Partner Workspace")))
            .andExpect(jsonPath("$[0].requestedTier", is("MERCHANT_CONFIGURED")))
            .andExpect(jsonPath("$[0].requestedScope", is("FULL_STORE_ACCESS")))
            .andExpect(jsonPath("$[0].status", is("WAITING_ON_MERCHANT")))
            .andReturn();
        String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");

        mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", PRODUCT_SERVICE_TEST_KEY)
                .param("shopDomain", "approved-client.myshopify.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].requestId", is(accessRequestId)))
            .andExpect(jsonPath("$[0].status", is("WAITING_ON_MERCHANT")));

        mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/invite", accessRequestId)
                .header("X-PLATFORM-API-KEY", PRODUCT_SERVICE_TEST_KEY)
                .param("shopDomain", "approved-client.myshopify.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId", is(accessRequestId)))
            .andExpect(jsonPath("$.status", is("RECORDED")))
            .andExpect(jsonPath("$.channel", is("EMAIL_DISABLED")))
            .andExpect(jsonPath("$.recipientEmail", is("merchant@example.com")))
            .andExpect(jsonPath("$.approvalUrl", is(approvalUrl)))
            .andExpect(jsonPath("$.inviteCount", is(1)));

        mockMvc.perform(get("/api/merchant/partner-access/{approvalCode}/workspace", approvalCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessRequest.requestId", is(accessRequestId)))
            .andExpect(jsonPath("$.accessRequest.status", is("WAITING_ON_MERCHANT")))
            .andExpect(jsonPath("$.accessRequest.inviteStatus", is("RECORDED")))
            .andExpect(jsonPath("$.accessRequest.inviteChannel", is("EMAIL_DISABLED")))
            .andExpect(jsonPath("$.availableActions", hasItem("APPROVE_PARTNER_ACCESS")))
            .andExpect(jsonPath("$.availableActions", hasItem("DENY_PARTNER_ACCESS")));

        mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", PRODUCT_SERVICE_TEST_KEY)
                .param("shopDomain", "unassigned-client.myshopify.com"))
            .andExpect(status().isForbidden());

        var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/approve", approvalCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
	                    {
	                      "approverName": "Merchant Owner",
	                      "approverEmail": "owner@example.com",
	                      "approvedScope": "FULL_STORE_ACCESS"
	                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andReturn();
        String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

        mockMvc.perform(get("/api/merchant/partner-access/{approvalCode}/workspace", approvalCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessRequest.status", is("APPROVED")))
            .andExpect(jsonPath("$.store.id", is(assignmentId)))
            .andExpect(jsonPath("$.launchReadiness.stagingReady", is(true)))
            .andExpect(jsonPath("$.availableActions", hasItem("REVOKE_PARTNER_ACCESS")))
            .andExpect(jsonPath("$.availableActions", hasItem("REQUEST_ROLLBACK")));

        mockMvc.perform(get("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(implementationId)))
            .andExpect(jsonPath("$[0].status", is("APPROVED")));

        mockMvc.perform(get("/api/partners/stores")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(assignmentId)))
            .andExpect(jsonPath("$[0].storeConnectionId", is("shopify-store-approved")))
            .andExpect(jsonPath("$[0].status", is("READY")))
            .andExpect(jsonPath("$[0].assignmentStatus", is("ACTIVE")))
            .andExpect(jsonPath("$[0].installStatus", is("INSTALLED")))
            .andExpect(jsonPath("$[0].widgetStatus", is("ENABLED")))
            .andExpect(jsonPath("$[0].permissions", hasItem("VERIFICATION_READ")))
            .andExpect(jsonPath("$[0].permissions", hasItem("PRODUCT_CONFIG_READ")))
            .andExpect(jsonPath("$[0].permissions", hasItem("STOREFRONT_SURFACE_CONTROL")))
            .andExpect(jsonPath("$[0].permissions", hasItem("KNOWLEDGE_SOURCE_CONTROL")))
            .andExpect(jsonPath("$[0].approvedAt", notNullValue()))
            .andExpect(jsonPath("$[0].packageProfile.profileKey", is("HIGH_QUALITY")))
            .andExpect(jsonPath("$[0].packageProfile.packageKey", is("ELITE")))
            .andExpect(jsonPath("$[0].packageProfile.tierKey", is("ELITE")))
            .andExpect(jsonPath("$[0].packageProfile.displayName", is("Elite high quality")))
            .andExpect(jsonPath("$[0].packageProfile.runtimeProfileKey").doesNotExist())
            .andExpect(jsonPath("$[0].packageProfile.vectorProfileKey").doesNotExist())
            .andExpect(jsonPath("$[0].packageProfile.vectorStrategy").doesNotExist())
            .andExpect(jsonPath("$[0].packageProfile.vectorProvisioningMode").doesNotExist())
            .andExpect(jsonPath("$[0].packageProfile.vectorStoragePosture").doesNotExist())
            .andExpect(jsonPath("$[0].packageProfile.vectorReindexRequired").doesNotExist())
            .andExpect(jsonPath("$[0].enabledSurfaces", not(hasItem("order-lookup"))));

        mockMvc.perform(get("/api/partners/stores/{storeId}/product-controls", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storeConnectionId", is("shopify-store-approved")))
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.sourceSettings.productsEnabled", is(true)))
            .andExpect(jsonPath("$.sourceSettings.enabledCategories", hasItem("products")))
            .andExpect(jsonPath("$.enabledSurfaces", hasItem("product-faq")))
            .andExpect(jsonPath("$.capabilities", hasItem("STOREFRONT_SURFACE_CONTROL")))
            .andExpect(jsonPath("$.supportProfile.merchantHandoffConfigured", is(false)));

        mockMvc.perform(get("/api/partners/stores/{storeId}/launch-readiness", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.stagingReady", is(true)))
            .andExpect(jsonPath("$.productionPromotionAllowed", is(true)))
            .andExpect(jsonPath("$.productionPromotionReady", is(false)))
            .andExpect(jsonPath("$.blockers", hasItem("A passing launch verification run is required before production promotion.")));

        mockMvc.perform(post("/api/partners/stores/{storeId}/production-promotions", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/production-promotions", approvalCode)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/partners/stores/{storeId}/product-controls/widget-settings", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "launcherLabel": "Ask Loom Companion",
                      "welcomeMessage": "Ask about products, collections, policy details, or size guidance.",
                      "shellModeProfile": "GUIDED_SUPPORT",
                      "enabledSurfaces": ["ai-search", "product-faq", "comparison"],
                      "defaultConversationMode": "navigator_deep",
                      "allowedConversationModes": ["navigator", "navigator_deep"],
                      "pageModeMappings": {
                        "product": "navigator_deep",
                        "collection": "navigator"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.widgetSettings.launcherLabel", is("Ask Loom Companion")))
            .andExpect(jsonPath("$.widgetSettings.assistantDockEnabled", is(true)))
            .andExpect(jsonPath("$.widgetSettings.askAssistantLauncherEnabled", is(false)))
            .andExpect(jsonPath("$.widgetSettings.enabledSurfaces", hasItem("comparison")))
            .andExpect(jsonPath("$.enabledSurfaces", hasItem("product-faq")));

        mockMvc.perform(post("/api/partners/stores/{storeId}/product-controls/source-settings", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productsEnabled": true,
                      "collectionsEnabled": true,
                      "pagesEnabled": true,
                      "policiesEnabled": true,
                      "articlesEnabled": true,
                      "metaobjectsEnabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceSettings.enabledCategories", hasItem("metaobjects")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andExpect(jsonPath("$.knowledgeSyncStatus", is("SYNCED")));

        mockMvc.perform(post("/api/partners/stores/{storeId}/product-controls/support-profile", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "contactEmail": "support@approved-client.test",
                      "contactUrl": "https://approved-client.test/contact",
                      "helpCenterUrl": "https://approved-client.test/help",
                      "orderLookupPageUrl": "/apps/order-lookup",
                      "supportPolicyNote": "Escalate policy edge cases to the merchant support queue."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supportProfile.contactEmail", is("support@approved-client.test")))
            .andExpect(jsonPath("$.supportProfile.merchantHandoffConfigured", is(true)));

        mockMvc.perform(get("/api/partners/stores/{storeId}/max-widget/chat/me/auth-context", assignmentId)
                .header("Authorization", "Bearer " + token)
                .param("authPath", "PLATFORM_PRIVATE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authMode", is("PLATFORM_PROXY_SESSION")))
            .andExpect(jsonPath("$.callerType", is("PLATFORM_PROXY")))
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)));

        mockMvc.perform(post("/api/partners/stores/{storeId}/max-widget/chat/me/query", assignmentId)
                .header("Authorization", "Bearer " + token)
                .param("authPath", "PLATFORM_PRIVATE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Run the partner Max widget live smoke test.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.conversationId", is("conv-partner-max")))
            .andExpect(jsonPath("$.result.answer", is("Partner Max widget live smoke ok")));

        mockMvc.perform(get("/api/shopify/stores/{shopDomain}", "approved-client.myshopify.com")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.widgetDetail.settings.launcherLabel", is("Ask Loom Companion")))
            .andExpect(jsonPath("$.widgetDetail.settings.defaultConversationMode", is("navigator_deep")))
            .andExpect(jsonPath("$.productsEnabled", is(true)))
            .andExpect(jsonPath("$.metaobjectsEnabled", is(true)));

        mockMvc.perform(get("/api/shopify/stores/{shopDomain}/support-profile", "approved-client.myshopify.com")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactEmail", is("support@approved-client.test")))
            .andExpect(jsonPath("$.merchantHandoffConfigured", is(true)));

        mockMvc.perform(get("/api/partners/verification-packs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id", is("starter-launch-readiness")))
            .andExpect(jsonPath("$[*].id", hasItem("shopify-companion-elite-readiness")));

        mockMvc.perform(get("/api/partners/stores/{storeId}/verification-pack", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is("shopify-companion-elite-readiness")))
            .andExpect(jsonPath("$.steps[?(@.stepId == 'elite-package-profile')].status", hasItem("PASSED")))
            .andExpect(jsonPath("$.steps[?(@.stepId == 'partner-product-control-assignment')].status", hasItem("PASSED")));

        var verificationResult = mockMvc.perform(post("/api/partners/stores/{storeId}/verification-runs", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "packId": "shopify-companion-elite-readiness"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PASSED")))
            .andExpect(jsonPath("$.evidenceBundleId", notNullValue()))
            .andExpect(jsonPath("$.steps[?(@.stepId == 'elite-surface-posture')].status", hasItem("PASSED")))
            .andReturn();
        String verificationRunId = JsonPath.read(verificationResult.getResponse().getContentAsString(), "$.id");
        String evidenceBundleId = JsonPath.read(verificationResult.getResponse().getContentAsString(), "$.evidenceBundleId");

        mockMvc.perform(get("/api/partners/verification-runs/{runId}", verificationRunId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(verificationRunId)))
            .andExpect(jsonPath("$.summary.redaction", is("merchant-safe")));

        mockMvc.perform(post("/api/partners/stores/{storeId}/verification-steps/{stepId}/complete", assignmentId, "merchant-screenshot-captured")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "PASSED",
                      "evidenceNote": "Merchant-safe storefront screenshot captured."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packId", is("manual-store-checklist")))
            .andExpect(jsonPath("$.steps[0].evidence[0]", is("Merchant-safe storefront screenshot captured.")));

        mockMvc.perform(get("/api/partners/evidence-bundles/{bundleId}", evidenceBundleId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationRunId", is(verificationRunId)))
            .andExpect(jsonPath("$.summary.redaction", is("merchant-safe")))
            .andExpect(jsonPath("$.attachments", hasItem("merchant-safe-summary.md")));

        mockMvc.perform(get("/api/partners/evidence-bundles/{bundleId}/export", evidenceBundleId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/partners/stores/{storeId}/evidence-bundles", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bundleKind": "LAUNCH_PACKET"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bundleKind", is("LAUNCH_PACKET")))
            .andExpect(jsonPath("$.summary.widgetStatus", is("ENABLED")));

        mockMvc.perform(get("/api/partners/stores/{storeId}/launch-readiness", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestVerificationStatus", is("PASSED")))
            .andExpect(jsonPath("$.evidenceReady", is(true)))
            .andExpect(jsonPath("$.latestEvidenceStatus", is("READY")));

        mockMvc.perform(get("/api/partners/templates")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'fashion-apparel-starter')].category", hasItem("VERTICAL_PLAYBOOK")));

        var templateApplicationResult = mockMvc.perform(post("/api/partners/templates/{templateId}/applications", "fashion-apparel-starter")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storeId": "%s"
                    }
                    """.formatted(assignmentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.templateId", is("fashion-apparel-starter")))
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andReturn();
        String templateApplicationId = JsonPath.read(templateApplicationResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/partners/templates/{templateId}/applications", "fashion-apparel-starter")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storeId": "%s"
                    }
                    """.formatted(assignmentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(templateApplicationId)))
            .andExpect(jsonPath("$.templateId", is("fashion-apparel-starter")));

        mockMvc.perform(get("/api/partners/template-applications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].templateId", is("fashion-apparel-starter")));

        mockMvc.perform(post("/api/partners/stores/{storeId}/notes", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bodyMarkdown": "Partner confirmed theme placement with merchant."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bodyMarkdown", is("Partner confirmed theme placement with merchant.")));

        mockMvc.perform(get("/api/partners/stores/{storeId}/notes", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bodyMarkdown", is("Partner confirmed theme placement with merchant.")));

        var rollbackResult = mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/rollback-requests", approvalCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requesterName": "Merchant Owner",
                      "requesterEmail": "owner@example.com",
                      "reason": "Merchant wants a controlled rollback after launch review."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storeId", is(assignmentId)))
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.status", is("REQUESTED")))
            .andExpect(jsonPath("$.escalationId", notNullValue()))
            .andReturn();
        String rollbackEscalationId = JsonPath.read(rollbackResult.getResponse().getContentAsString(), "$.escalationId");

        mockMvc.perform(get("/api/merchant/partner-access/{approvalCode}/workspace", approvalCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supportEscalations[?(@.id == '%s')].title".formatted(rollbackEscalationId), hasItem("Merchant rollback or deactivation request")));

        mockMvc.perform(get("/api/partners/members")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].role", is("PARTNER_ADMIN")));

        mockMvc.perform(patch("/api/partners/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Verified Partner Admin"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName", is("Verified Partner Admin")));

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
                      "nextAction": "Check app embed placement.",
                      "evidenceBundleIds": ["%s"]
                    }
                    """.formatted(evidenceBundleId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.evidenceBundleIds[0]", is(evidenceBundleId)))
            .andReturn();
        String escalationId = JsonPath.read(escalationResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/partners/escalations/{escalationId}/replies", escalationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bodyMarkdown": "Visible update for the partner.",
                      "evidenceBundleIds": ["%s"]
                    }
                    """.formatted(evidenceBundleId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attachments[0]", is(evidenceBundleId)));

        saveReply(escalationId, "OPERATOR_INTERNAL", "Operator-only note must not leak.");

        mockMvc.perform(get("/api/partners/escalations/{escalationId}/thread", escalationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replies.length()", is(1)))
            .andExpect(jsonPath("$.replies[0].bodyMarkdown", is("Visible update for the partner.")))
            .andExpect(jsonPath("$.replies[0].attachments[0]", is(evidenceBundleId)))
            .andExpect(jsonPath("$.replies[0].visibility", is("PARTNER_VISIBLE")));

        mockMvc.perform(get("/api/partners/activity")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("STORE_ACCESS_APPROVED")))
            .andExpect(jsonPath("$[*].action", hasItem("PRODUCT_WIDGET_SETTINGS_UPDATED")))
            .andExpect(jsonPath("$[*].action", hasItem("PRODUCT_SOURCE_SETTINGS_UPDATED")))
            .andExpect(jsonPath("$[*].action", hasItem("PRODUCT_SUPPORT_PROFILE_UPDATED")))
            .andExpect(jsonPath("$[*].action", hasItem("PARTNER_MAX_WIDGET_QUERY_RAN")))
            .andExpect(jsonPath("$[*].action", hasItem("VERIFICATION_RUN_CREATED")))
            .andExpect(jsonPath("$[*].action", hasItem("PRODUCTION_PROMOTION_BLOCKED")))
            .andExpect(jsonPath("$[*].action", hasItem("TEMPLATE_APPLICATION_REUSED")))
            .andExpect(jsonPath("$[*].action", hasItem("SUPPORT_REPLY_CREATED")));

        mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/revoke", approvalCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "approverEmail": "owner@example.com",
                      "decisionReason": "Merchant revoked active partner access."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentId", is(assignmentId)))
            .andExpect(jsonPath("$.shopDomain", is("approved-client.myshopify.com")))
            .andExpect(jsonPath("$.status", is("REVOKED")));

        mockMvc.perform(get("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(implementationId)))
            .andExpect(jsonPath("$[0].status", is("REVOKED")));

        mockMvc.perform(get("/api/partners/stores")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(assignmentId)))
            .andExpect(jsonPath("$[0].assignmentStatus", is("REVOKED")))
            .andExpect(jsonPath("$[0].status", is("REVOKED")));

        mockMvc.perform(get("/api/partners/stores/{storeId}", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/partners/stores/{storeId}/product-controls", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        assertThat(auditRepository.findAll())
            .extracting("action")
            .contains(
                "PARTNER_SIGNUP_COMPLETED",
                "STORE_ACCESS_APPROVED",
                "PRODUCT_WIDGET_SETTINGS_UPDATED",
                "PRODUCT_SOURCE_SETTINGS_UPDATED",
                "PRODUCT_SUPPORT_PROFILE_UPDATED",
                "PARTNER_MAX_WIDGET_QUERY_RAN",
                "VERIFICATION_RUN_CREATED",
                "PRODUCTION_PROMOTION_BLOCKED",
                "EVIDENCE_BUNDLE_CREATED",
                "TEMPLATE_APPLIED",
                "STORE_NOTE_CREATED",
                "PARTNER_PROFILE_UPDATED",
                "SUPPORT_ESCALATION_CREATED",
                "STORE_ACCESS_REVOKED"
            );
        } finally {
            runtimeServer.stop(0);
        }
    }

    @Test
    void storeVerificationPackUsesEliteWhenLegacyFreePackExposesGovernedSurface() throws Exception {
        String token = partnerJwt("legacy-pack-user", "legacy-pack-user@example.com");
        completeSignup(token, "Legacy Pack Partner Workspace");
        createShopifyStore("shopify-store-legacy-pack", "legacy-pack-client.myshopify.com", "Legacy Pack Client");

        ShopifyStoreConnectionEntity store = storeConnectionRepository.findById("shopify-store-legacy-pack").orElseThrow();
        store.setDetailsJson("""
            {
              "packageState": {
                "profileKey": "LOW_COST",
                "packageKey": "FREE",
                "tierKey": "FREE",
                "displayName": "Low cost",
                "costPosture": "LOW",
                "runtimeProfileKey": "LOW_COST",
                "vectorProfileKey": "QDRANT_SHARED",
                "verificationPackId": "shopify-companion-free-readiness",
                "lastReconciledAt": "2026-04-25T22:00:00Z"
              },
              "widget": {
                "settings": {
                  "enabledSurfaces": ["ai-search", "order-lookup"],
                  "allowedConversationModes": ["navigator"],
                  "defaultConversationMode": "navigator"
                }
              }
            }
            """);
        storeConnectionRepository.save(store);

        var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientName": "Legacy Pack Client",
                      "storeConnectionId": "shopify-store-legacy-pack"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");

        var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "legacy-pack-client.myshopify.com"))
            .andExpect(status().isOk())
            .andReturn();
        String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");

        var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/approve", accessRequestId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "legacy-pack-client.myshopify.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "approverEmail": "owner@example.com",
                      "approvedScope": "FULL_STORE_ACCESS"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

        mockMvc.perform(get("/api/partners/client-implementations/{requestId}", implementationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/partners/stores/{storeId}/verification-pack", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is("shopify-companion-elite-readiness")))
            .andExpect(jsonPath("$.steps[?(@.stepId == 'elite-package-profile')].status", hasItem("FAILED")))
            .andExpect(jsonPath("$.steps[?(@.stepId == 'elite-surface-posture')].status", hasItem("PASSED")));
    }

    @Test
    void packageTrialActivationRequiresPlatformGrantedPrivilegeAndManualPastDueDeactivation() throws Exception {
        String token = partnerJwt("trial-user", "trial-user@example.com");
        String sessionPayload = completeSignup(token, "Trial Partner Workspace");
        String partnerMemberId = JsonPath.read(sessionPayload, "$.member.id");
        AtomicInteger bridgeBillingStateRequests = new AtomicInteger();
        HttpServer bridgeServer = startBridgeAdminServer(bridgeBillingStateRequests);
        try {
            createShopifyStore("shopify-store-trial", "trial-client.myshopify.com", "Trial Client");
            bindShopifyBridgeBaseUrl("http://localhost:" + bridgeServer.getAddress().getPort());

            var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "clientName": "Trial Client",
                          "storeConnectionId": "shopify-store-trial"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
            String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");

            var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                    .header("X-PLATFORM-API-KEY", "operator-test-key")
                    .param("shopDomain", "trial-client.myshopify.com"))
                .andExpect(status().isOk())
                .andReturn();
            String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");
            var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/approve", accessRequestId)
                    .header("X-PLATFORM-API-KEY", "operator-test-key")
                    .param("shopDomain", "trial-client.myshopify.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "approverName": "Merchant Owner",
                          "approverEmail": "owner@example.com",
                          "approvedScope": "FULL_STORE_ACCESS"
                        }
                        """))
                .andExpect(status().isOk())
                .andReturn();
            String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

            mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials", assignmentId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "tierKey": "STARTER",
                          "trialDays": 7,
                          "reason": "Design partner launch trial"
                        }
                        """))
                .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/platform/partners/members/{memberId}", partnerMemberId)
                    .header("X-PLATFORM-API-KEY", "admin-test-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "privileges": ["PACKAGE_TRIAL_ACTIVATE"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privileges", hasItem("PACKAGE_TRIAL_ACTIVATE")))
                .andExpect(jsonPath("$.effectivePermissions", hasItem("PACKAGE_TRIAL_ACTIVATE")));

            mockMvc.perform(get("/api/partners/session")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("PACKAGE_TRIAL_ACTIVATE")));

            var activationResult = mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials", assignmentId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "tierKey": "STARTER",
                          "trialDays": 7,
                          "reason": "Design partner launch trial"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities", hasItem("PACKAGE_TRIAL_ACTIVATE")))
                .andExpect(jsonPath("$.activePackageTrial.tierKey", is("STARTER")))
                .andExpect(jsonPath("$.activePackageTrial.status", is("ACTIVE")))
                .andExpect(jsonPath("$.activePackageTrial.activationProvisioningJobId", notNullValue()))
                .andReturn();
            String trialId = JsonPath.read(activationResult.getResponse().getContentAsString(), "$.activePackageTrial.id");

            mockMvc.perform(get("/api/shopify/stores/{shopDomain}/billing-state", "trial-client.myshopify.com")
                    .header("X-PLATFORM-API-KEY", "operator-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierKey", is("STARTER")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.subscriptionId", is("partner-trial-" + trialId)));

            mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials/{trialId}/deactivate", assignmentId, trialId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Past due cleanup"
                        }
                        """))
                .andExpect(status().isConflict());

            PartnerPackageTrialActivationEntity trial = packageTrialActivationRepository.findById(trialId).orElseThrow();
            trial.setTrialEndsAt(Instant.now().minusSeconds(60));
            packageTrialActivationRepository.save(trial);

            mockMvc.perform(get("/api/partners/stores/{storeId}/product-controls", assignmentId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePackageTrial.status", is("PAST_DUE")))
                .andExpect(jsonPath("$.activePackageTrial.deactivationEligible", is(true)));

            mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials/{trialId}/deactivate", assignmentId, trialId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Past due cleanup"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePackageTrial").doesNotExist())
                .andExpect(jsonPath("$.packageTrialHistory[0].status", is("DEACTIVATED")))
                .andExpect(jsonPath("$.packageTrialHistory[0].deactivationProvisioningJobId", notNullValue()));

            mockMvc.perform(get("/api/shopify/stores/{shopDomain}/billing-state", "trial-client.myshopify.com")
                    .header("X-PLATFORM-API-KEY", "operator-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierKey", is("FREE")))
                .andExpect(jsonPath("$.reason", is("Past due cleanup")));

            assertThat(bridgeBillingStateRequests.get())
                .as("Partner package trials must not synchronously call Bridge billing-state inside the Platform transaction")
                .isZero();
        } finally {
            bridgeServer.stop(0);
        }
    }

    @Test
    void packageTrialActivationReconcilesBillingDriftBeforeBlockingNewTrial() throws Exception {
        String token = partnerJwt("trial-drift-user", "trial-drift-user@example.com");
        String sessionPayload = completeSignup(token, "Trial Drift Partner Workspace");
        String partnerMemberId = JsonPath.read(sessionPayload, "$.member.id");
        createShopifyStore("shopify-store-trial-drift", "trial-drift-client.myshopify.com", "Trial Drift Client");

        mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientName": "Trial Drift Client",
                      "storeConnectionId": "shopify-store-trial-drift"
                    }
                    """))
            .andExpect(status().isCreated());

        var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "trial-drift-client.myshopify.com"))
            .andExpect(status().isOk())
            .andReturn();
        String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");

        var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/approve", accessRequestId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "trial-drift-client.myshopify.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "approverEmail": "owner@example.com",
                      "approvedScope": "FULL_STORE_ACCESS"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

        mockMvc.perform(patch("/api/platform/partners/members/{memberId}", partnerMemberId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "privileges": ["PACKAGE_TRIAL_ACTIVATE"]
                    }
                    """))
            .andExpect(status().isOk());

        var firstActivation = mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tierKey": "ELITE",
                      "trialDays": 30,
                      "reason": "Initial design partner package trial"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePackageTrial.tierKey", is("ELITE")))
            .andExpect(jsonPath("$.activePackageTrial.status", is("ACTIVE")))
            .andReturn();
        String driftedTrialId = JsonPath.read(firstActivation.getResponse().getContentAsString(), "$.activePackageTrial.id");

        mockMvc.perform(post("/api/shopify/stores/{shopDomain}/billing-state", "trial-drift-client.myshopify.com")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tierKey": "FREE",
                      "status": "ACTIVE",
                      "reason": "Shopify billing inspection found no active paid subscription."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tierKey", is("FREE")));

        mockMvc.perform(get("/api/partners/stores/{storeId}/product-controls", assignmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePackageTrial").doesNotExist())
            .andExpect(jsonPath("$.packageTrialHistory[0].status", is("BILLING_DRIFT")));

        var secondActivation = mockMvc.perform(post("/api/partners/stores/{storeId}/package-trials", assignmentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tierKey": "STARTER",
                      "trialDays": 7,
                      "reason": "Reactivate after Shopify billing drift reconciliation"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePackageTrial.tierKey", is("STARTER")))
            .andExpect(jsonPath("$.activePackageTrial.status", is("ACTIVE")))
            .andExpect(jsonPath("$.packageTrialHistory[1].status", is("BILLING_DRIFT")))
            .andReturn();

        String activeTrialId = JsonPath.read(secondActivation.getResponse().getContentAsString(), "$.activePackageTrial.id");
        assertThat(packageTrialActivationRepository.findById(driftedTrialId).orElseThrow().getStatus())
            .isEqualTo("BILLING_DRIFT");
        assertThat(packageTrialActivationRepository.findById(activeTrialId).orElseThrow().getStatus())
            .isEqualTo("ACTIVE");

        mockMvc.perform(get("/api/shopify/stores/{shopDomain}/billing-state", "trial-drift-client.myshopify.com")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tierKey", is("STARTER")))
            .andExpect(jsonPath("$.subscriptionId", is("partner-trial-" + activeTrialId)));
    }

    @Test
    void merchantCanDenyInstalledStorePartnerAccessFromAdminFlow() throws Exception {
        String token = partnerJwt("denied-user", "denied-user@example.com");
        completeSignup(token, "Denied Partner Workspace");
        createShopifyStore("shopify-store-denied", "denied-client.myshopify.com", "Denied Client");

        var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
	                    {
	                      "clientName": "Denied Client",
	                      "storeConnectionId": "shopify-store-denied",
	                      "knownIntegrations": [],
	                      "notes": "Merchant should be able to deny this."
	                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("WAITING_ON_MERCHANT")))
            .andReturn();
        String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");
        String approvalUrl = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.approvalUrl");
        String approvalCode = approvalCodeFromUrl(approvalUrl);

        mockMvc.perform(post("/api/partners/client-implementations/{requestId}/merchant-invites", implementationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipientEmail": "denied-merchant@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("RECORDED")))
            .andExpect(jsonPath("$.channel", is("EMAIL_DISABLED")))
            .andExpect(jsonPath("$.recipientEmail", is("denied-merchant@example.com")))
            .andExpect(jsonPath("$.approvalUrl", is(approvalUrl)))
            .andExpect(jsonPath("$.inviteCount", is(1)));

        var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", PRODUCT_SERVICE_TEST_KEY)
                .param("shopDomain", "denied-client.myshopify.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].inviteStatus", is("RECORDED")))
            .andReturn();
        String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");

        mockMvc.perform(get("/api/merchant/partner-access/{approvalCode}/workspace", approvalCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessRequest.requestId", is(accessRequestId)))
            .andExpect(jsonPath("$.availableActions", hasItem("DENY_PARTNER_ACCESS")));

        mockMvc.perform(post("/api/merchant/partner-access/{approvalCode}/deny", approvalCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "decisionReason": "Merchant does not want partner access."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain", is("denied-client.myshopify.com")))
            .andExpect(jsonPath("$.status", is("DENIED")));

        mockMvc.perform(get("/api/partners/client-implementations/{requestId}", implementationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("DENIED")));

        mockMvc.perform(get("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(implementationId)))
            .andExpect(jsonPath("$[0].status", is("DENIED")));

        mockMvc.perform(get("/api/partners/stores")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void operatorCanRevokeApprovedStoreAccessAsEmergencyOverride() throws Exception {
        String token = partnerJwt("operator-revoke-user", "operator-revoke@example.com");
        completeSignup(token, "Operator Revoke Partner Workspace");
        createShopifyStore("shopify-store-operator-revoke", "operator-revoke-client.myshopify.com", "Operator Revoke Client");

        var implementationResult = mockMvc.perform(post("/api/partners/client-implementations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientName": "Operator Revoke Client",
                      "storeConnectionId": "shopify-store-operator-revoke",
                      "knownIntegrations": [],
                      "notes": "Operator should be able to revoke approved access."
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        String implementationId = JsonPath.read(implementationResult.getResponse().getContentAsString(), "$.id");

        var requestsResult = mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "operator-revoke-client.myshopify.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andReturn();
        String accessRequestId = JsonPath.read(requestsResult.getResponse().getContentAsString(), "$[0].requestId");

        var approvalResult = mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/approve", accessRequestId)
                .header("X-PLATFORM-API-KEY", PRODUCT_SERVICE_TEST_KEY)
                .param("shopDomain", "operator-revoke-client.myshopify.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Merchant Owner",
                      "approvedScope": "FULL_STORE_ACCESS"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andReturn();
        String assignmentId = JsonPath.read(approvalResult.getResponse().getContentAsString(), "$.assignmentId");

        mockMvc.perform(post("/api/merchant/partner-access/requests/{requestId}/revoke", accessRequestId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "operator-revoke-client.myshopify.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approverName": "Platform Operator",
                      "decisionReason": "Emergency operator override."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentId", is(assignmentId)))
            .andExpect(jsonPath("$.status", is("REVOKED")));

        mockMvc.perform(get("/api/partners/client-implementations/{requestId}", implementationId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("REVOKED")));

        mockMvc.perform(get("/api/merchant/partner-access/requests")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .param("shopDomain", "operator-revoke-client.myshopify.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].assignmentId", is(assignmentId)))
            .andExpect(jsonPath("$[0].status", is("REVOKED")))
            .andExpect(jsonPath("$[0].revokedAt", notNullValue()));
    }

    private String completeSignup(String token, String workspaceName) throws Exception {
        return mockMvc.perform(post("/api/partners/signup/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceName": "%s"
                    }
                    """.formatted(workspaceName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signupRequired", is(false)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private void createRuntimeDeployment(String deploymentId, String runtimeBaseUrl) {
        Instant now = Instant.now();
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("customer-shopify-store-approved");
        customer.setName("Approved Client");
        customer.setSlug("approved-client");
        customer.setDescription("Partner Max widget integration test customer.");
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(true);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        PlatformTenantEntity tenant = new PlatformTenantEntity();
        tenant.setId("tenant-shopify-store-approved");
        tenant.setCustomerId(customer.getId());
        tenant.setName("Approved Client Storefront");
        tenant.setSlug("approved-client-storefront");
        tenant.setDescription("Partner Max widget integration test tenant.");
        tenant.setStatus("ACTIVE");
        tenant.setPlatformManaged(true);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantRepository.save(tenant);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(deploymentId);
        deployment.setName("Partner Max Widget Runtime");
        deployment.setEnvironmentName("test");
        deployment.setTemplateId("shopify-companion");
        deployment.setStatus("RUNNING");
        deployment.setCustomerId("customer-shopify-store-approved");
        deployment.setTenantId("tenant-shopify-store-approved");
        deployment.setRuntimeBaseUrl(runtimeBaseUrl);
        deployment.setApprovalRequiredForApply(false);
        deployment.setApprovalRequiredForDelete(true);
        deployment.setCreatedAt(now);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);
        storeRuntimeSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY", "runtime-test-key");
        storeRuntimeSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY", "runtime-private-test-signing-key");
    }

    private void storeRuntimeSecret(String name, String value) {
        PlatformSecretEntity secret = platformSecretRepository.findById(name).orElseGet(PlatformSecretEntity::new);
        secret.setName(name);
        secret.setSecretValue(value);
        secret.setUpdatedAt(Instant.now());
        secret.setManagedByPlatform(false);
        platformSecretRepository.save(secret);
    }

    private void createShopifyStore(String id, String shopDomain, String displayName) {
        Instant now = Instant.now();
        PlatformManagedProductServiceEntity service = productServiceRepository.findById("shopify-companion").orElseGet(() -> {
            PlatformManagedProductServiceEntity created = new PlatformManagedProductServiceEntity();
            created.setId("shopify-companion");
            created.setServiceRef("shopify-companion");
            created.setDisplayName("Shopify Companion");
            created.setProductFamily("SHOPIFY_COMPANION");
            created.setServiceKind("SHOPIFY_APP");
            created.setDeploymentMode("MANAGED");
            created.setTenantMode("MULTI_TENANT");
            created.setEnvironmentScope("test");
            created.setDesiredReplicas(1);
            created.setActualReplicas(1);
            created.setStatus("ACTIVE");
            created.setDetailsJson("{}");
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            return productServiceRepository.save(created);
        });
        service.setSecretName(PRODUCT_SERVICE_SECRET_NAME);
        service.setStatus("ACTIVE");
        service.setUpdatedAt(now);
        productServiceRepository.save(service);
        platformSecretService.upsertManagedSecret(
            PRODUCT_SERVICE_SECRET_NAME,
            PRODUCT_SERVICE_TEST_KEY,
            Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
        );

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
        entity.setDetailsJson("""
            {
              "packageState": {
                "profileKey": "HIGH_QUALITY",
                "packageKey": "ELITE",
                "tierKey": "ELITE",
                "displayName": "Elite high quality",
                "costPosture": "HIGH",
                "runtimeProfileKey": "HIGH_QUALITY",
                "vectorProfileKey": "QDRANT_SHARED",
                "vectorStrategy": "qdrant",
                "vectorProvisioningMode": "EXTERNAL_EXISTING",
                "vectorStoragePosture": "SHARED",
                "lastProvisioningJobId": "spj-test",
                "vectorReindexRequired": true,
                "verificationPackId": "shopify-companion-elite-readiness",
                "lastReconciledAt": "2026-04-25T22:00:00Z"
              },
              "widget": {
                "settings": {
                  "enabledSurfaces": ["ai-search", "product-faq", "comparison"],
                  "allowedConversationModes": ["navigator"],
                  "defaultConversationMode": "navigator"
                }
              }
            }
            """);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        storeConnectionRepository.save(entity);
    }

    private void bindShopifyBridgeBaseUrl(String baseUrl) {
        PlatformManagedProductServiceEntity service = productServiceRepository.findById("shopify-companion").orElseThrow();
        service.setBaseUrl(baseUrl);
        service.setUpdatedAt(Instant.now());
        productServiceRepository.save(service);
    }

    private HttpServer startBridgeAdminServer() throws IOException {
        return startBridgeAdminServer(null);
    }

    private HttpServer startBridgeAdminServer(AtomicInteger billingStateRequests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/admin/stores", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())
                || !PRODUCT_SERVICE_TEST_KEY.equals(exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY"))
                || !exchange.getRequestURI().getPath().endsWith("/billing-state")) {
                writeJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            if (billingStateRequests != null) {
                billingStateRequests.incrementAndGet();
            }
            writeJson(exchange, 200, "{\"success\":true}");
        });
        server.start();
        return server;
    }

    private HttpServer startPartnerWidgetRuntime() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat/me/auth-context", exchange -> {
            if (!runtimeRequestAuthorized(exchange)) {
                writeJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            writeJson(exchange, 200, """
                {
                  "subjectType": "SYSTEM_PROCESS",
                  "authMode": "PLATFORM_PROXY_SESSION",
                  "callerType": "PLATFORM_PROXY",
                  "deploymentId": "deployment-shopify-store-approved",
                  "customerId": "customer-shopify-store-approved",
                  "tenantId": "tenant-shopify-store-approved",
                  "grantedScopes": [],
                  "warnings": []
                }
                """);
        });
        server.createContext("/api/chat/me/query", exchange -> {
            if (!runtimeRequestAuthorized(exchange)) {
                writeJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            writeJson(exchange, 200, """
                {
                  "success": true,
                  "message": "Partner Max widget live smoke ok",
                  "conversationId": "conv-partner-max",
                  "sessionId": "session-partner-max",
                  "result": {
                    "answer": "Partner Max widget live smoke ok"
                  }
                }
                """);
        });
        server.start();
        return server;
    }

    private boolean runtimeRequestAuthorized(HttpExchange exchange) {
        return "runtime-test-key".equals(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"))
            && exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION") != null;
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
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

    private String approvalCodeFromUrl(String approvalUrl) {
        int lastSlash = approvalUrl.lastIndexOf('/');
        return lastSlash >= 0 ? approvalUrl.substring(lastSlash + 1) : approvalUrl;
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
