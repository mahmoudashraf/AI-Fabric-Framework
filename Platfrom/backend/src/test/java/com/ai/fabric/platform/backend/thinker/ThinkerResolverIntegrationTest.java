package com.ai.fabric.platform.backend.thinker;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.partner.entity.PartnerAccountEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerMemberEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAssignmentEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerAccountRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerEvidenceBundleRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAssignmentRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportEscalationRepository;
import com.ai.fabric.platform.backend.partner.security.PartnerPrincipal;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionDetail;
import com.ai.fabric.platform.backend.thinker.service.ThinkerResolverService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ThinkerResolverIntegrationTest {

    private static final String ADMIN_KEY = "admin-test-key";
    private static final String DEPLOYMENT_ID = "dep-thinker-resolver-test";
    private static final String SHOP_DOMAIN = "thinker-resolver-test.myshopify.com";
    private static final String PARTNER_ACCOUNT_ID = "partner-thinker-resolver";
    private static final String PARTNER_MEMBER_ID = "partner-member-thinker-resolver";
    private static final String STORE_ASSIGNMENT_ID = "psa-thinker-resolver";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private ShopifyStoreConnectionRepository storeConnectionRepository;
    @Autowired
    private PartnerAccountRepository partnerAccountRepository;
    @Autowired
    private PartnerMemberRepository partnerMemberRepository;
    @Autowired
    private PartnerStoreAssignmentRepository partnerStoreAssignmentRepository;
    @Autowired
    private PartnerEvidenceBundleRepository evidenceBundleRepository;
    @Autowired
    private PartnerSupportEscalationRepository supportEscalationRepository;
    @Autowired
    private PlatformManagedProductServiceRepository productServiceRepository;
    @Autowired
    private PlatformCustomerRepository platformCustomerRepository;
    @Autowired
    private PlatformTenantRepository platformTenantRepository;
    @Autowired
    private ThinkerResolverService thinkerResolverService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void governedThinkerResolverFlowPersistsEvidenceExecutesSupportEscalationAndRedactsPartnerView() throws Exception {
        seedStoreAndPartnerAssignment(List.of("STORE_READ", "PRODUCT_CONFIG_READ", "SUPPORT_MANAGE"));

        mockMvc.perform(put("/api/operator/thinker/deployments/{deploymentId}/control", DEPLOYMENT_ID)
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "thinkerEnabled": true,
                      "resolverPreviewEnabled": true,
                      "governedExecutionEnabled": true,
                      "disabledActionFamilies": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thinkerEnabled", is(true)))
            .andExpect(jsonPath("$.resolverPreviewEnabled", is(true)))
            .andExpect(jsonPath("$.governedExecutionEnabled", is(true)));

        String sessionJson = mockMvc.perform(post("/api/operator/thinker/sessions")
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deploymentId": "dep-thinker-resolver-test",
                      "shopDomain": "thinker-resolver-test.myshopify.com",
                      "channel": "OPERATOR_TEST",
                      "mode": "THINKER_DEEP",
                      "userQuestion": "A shopper needs evidence for why a product recommendation is grounded.",
                      "userSafeAnswer": "The answer is grounded by current store evidence.",
                      "evidence": [
                        {
                          "kind": "READ_ACTION_RESULT",
                          "sourceKind": "INTEGRATION_TEST",
                          "sourceIdentifier": "test-read-action",
                          "freshness": "FRESH",
                          "confidence": 0.93,
                          "summary": "Operator-visible source path proves read-only evidence was used.",
                          "safeSummary": "Read-only evidence was used.",
                          "operatorRawReference": "/runtime/result/readActionResolution/executedActions/0"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("RESOLVED")))
            .andExpect(jsonPath("$.evidenceCount", is(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionId = JsonPath.read(sessionJson, "$.id");

        mockMvc.perform(post("/api/operator/thinker/sessions")
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deploymentId": "dep-thinker-resolver-test",
                      "shopDomain": "thinker-resolver-test.myshopify.com",
                      "userQuestion": "What is the return or refund policy for this product?",
                      "evidence": [
                        {
                          "kind": "READ_ACTION_RESULT",
                          "sourceKind": "INTEGRATION_TEST",
                          "sourceIdentifier": "refund-policy-read-proof",
                          "summary": "The request asks to read policy evidence.",
                          "safeSummary": "The request asks to read policy evidence."
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("RESOLVED")))
            .andExpect(jsonPath("$.issueCategory", is("POLICY_LOOKUP")))
            .andExpect(jsonPath("$.riskClass", is("READ_ONLY")));

        mockMvc.perform(post("/api/operator/thinker/sessions")
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deploymentId": "dep-thinker-resolver-test",
                      "shopDomain": "thinker-resolver-test.myshopify.com",
                      "userQuestion": "Please change the return policy and publish the update.",
                      "evidence": [
                        {
                          "kind": "READ_ACTION_RESULT",
                          "sourceKind": "INTEGRATION_TEST",
                          "sourceIdentifier": "write-required-proof",
                          "summary": "The request asks for a write.",
                          "safeSummary": "The request asks for a write."
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("WRITE_REQUIRED_ESCALATED")));

        String evidenceJson = mockMvc.perform(get("/api/operator/thinker/sessions/{sessionId}/evidence", sessionId)
                .header("X-PLATFORM-API-KEY", ADMIN_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].operatorRawReference", is("/runtime/result/readActionResolution/executedActions/0")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String evidenceId = JsonPath.read(evidenceJson, "$[0].id");

        String proposalJson = mockMvc.perform(post("/api/operator/resolver/proposals")
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId": "%s",
                      "actionId": "create_support_escalation",
                      "actionFamily": "SUPPORT_ESCALATION",
                      "targetDomain": "PARTNER_ENABLEMENT",
                      "productBoundary": "SHOPIFY_COMPANION",
                      "parameters": {
                        "title": "Thinker support handoff",
                        "severity": "LOW",
                        "nextAction": "Review the evidence and confirm merchant-safe guidance."
                      },
                      "sourceEvidenceItemIds": ["%s"],
                      "proposalText": "Create a redacted partner support escalation."
                    }
                    """.formatted(sessionId, evidenceId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("DRY_RUN_READY")))
            .andExpect(jsonPath("$.latestPolicyDecision.outcome", is("ALLOWED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String proposalId = JsonPath.read(proposalJson, "$.id");

        String dryRunJson = mockMvc.perform(post("/api/operator/resolver/proposals/{proposalId}/dry-run", proposalId)
                .header("X-PLATFORM-API-KEY", ADMIN_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String dryRunId = JsonPath.read(dryRunJson, "$.id");

        String executionJson = mockMvc.perform(post("/api/operator/resolver/proposals/{proposalId}/execute", proposalId)
                .header("X-PLATFORM-API-KEY", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "dryRunId": "%s",
                      "idempotencyKey": "thinker-resolver-test-idempotency",
                      "confirmationText": "CREATE SUPPORT ESCALATION"
                    }
                    """.formatted(dryRunId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.executionResult.supportEscalationId", notNullValue()))
            .andExpect(jsonPath("$.executionResult.evidenceBundleId", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String supportEscalationId = JsonPath.read(executionJson, "$.executionResult.supportEscalationId");
        String evidenceBundleId = JsonPath.read(executionJson, "$.executionResult.evidenceBundleId");
        assertThat(supportEscalationRepository.findById(supportEscalationId)).isPresent();
        assertThat(evidenceBundleRepository.findById(evidenceBundleId)).isPresent();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PartnerPrincipal(
                "supabase-thinker",
                "thinker-partner@example.com",
                true,
                "Thinker Partner",
                null,
                "email",
                "supabase-thinker",
                PARTNER_ACCOUNT_ID,
                PARTNER_MEMBER_ID,
                PlatformRole.PARTNER_ADMIN,
                "ACTIVE"
            ),
            null,
            List.of(new SimpleGrantedAuthority(PlatformRole.PARTNER_ADMIN.authority()))
        ));
        ThinkerIssueSessionDetail partnerDetail = thinkerResolverService.getPartnerSession(sessionId);
        assertThat(partnerDetail.session().tenantId()).isNull();
        assertThat(partnerDetail.session().customerId()).isNull();
        assertThat(partnerDetail.evidence()).allSatisfy(item -> assertThat(item.operatorRawReference()).isNull());
        assertThat(partnerDetail.resolverProposals()).allSatisfy(item -> assertThat(item.operatorParameters()).isNull());
    }

    private void seedStoreAndPartnerAssignment(List<String> permissions) {
        Instant now = Instant.now();
        seedCustomerTenant(now);
        seedProductService(now);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setName("Thinker Resolver Test Deployment");
        deployment.setEnvironmentName("test");
        deployment.setTemplateId("shopify-companion");
        deployment.setStatus("RUNNING");
        deployment.setCustomerId("customer-thinker-resolver");
        deployment.setTenantId("tenant-thinker-resolver");
        deployment.setRuntimeBaseUrl("https://runtime.test");
        deployment.setApprovalRequiredForApply(false);
        deployment.setApprovalRequiredForDelete(false);
        deployment.setCreatedAt(now);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();
        store.setId("ssc-thinker-resolver");
        store.setShopDomain(SHOP_DOMAIN);
        store.setDisplayName("Thinker Resolver Test Store");
        store.setProductServiceId("shopify-companion");
        store.setCustomerId("customer-thinker-resolver");
        store.setDeploymentId(DEPLOYMENT_ID);
        store.setConsumerId("consumer-thinker-resolver");
        store.setInstallStatus("INSTALLED");
        store.setSyncStatus("SYNCED");
        store.setSourceReadinessStatus("READY");
        store.setWidgetStatus("ENABLED");
        store.setOnboardingStatus("READY");
        store.setProductsEnabled(true);
        store.setCollectionsEnabled(true);
        store.setPagesEnabled(true);
        store.setPoliciesEnabled(true);
        store.setArticlesEnabled(true);
        store.setMetaobjectsEnabled(true);
        store.setDetailsJson("""
            {
              "billingState": {
                "tierKey": "ELITE",
                "status": "ACTIVE"
              }
            }
            """);
        store.setCreatedAt(now);
        store.setUpdatedAt(now);
        storeConnectionRepository.save(store);

        PartnerAccountEntity account = new PartnerAccountEntity();
        account.setId(PARTNER_ACCOUNT_ID);
        account.setName("Thinker Resolver Partner");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        partnerAccountRepository.save(account);

        PartnerMemberEntity member = new PartnerMemberEntity();
        member.setId(PARTNER_MEMBER_ID);
        member.setPartnerAccountId(PARTNER_ACCOUNT_ID);
        member.setSupabaseUserId("supabase-thinker");
        member.setAuthProvider("email");
        member.setAuthProviderSubject("supabase-thinker");
        member.setEmail("thinker-partner@example.com");
        member.setEmailVerified(true);
        member.setDisplayName("Thinker Partner");
        member.setRole(PlatformRole.PARTNER_ADMIN.name());
        member.setStatus("ACTIVE");
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        partnerMemberRepository.save(member);

        PartnerStoreAssignmentEntity assignment = new PartnerStoreAssignmentEntity();
        assignment.setId(STORE_ASSIGNMENT_ID);
        assignment.setPartnerAccountId(PARTNER_ACCOUNT_ID);
        assignment.setStoreConnectionId(store.getId());
        assignment.setShopDomain(SHOP_DOMAIN);
        assignment.setMerchantName("Thinker Resolver Test Store");
        assignment.setStatus("ACTIVE");
        assignment.setAssignmentSource("MERCHANT_APPROVAL");
        assignment.setApprovedBy("Merchant Admin");
        assignment.setPermissionsJson("[\"" + String.join("\",\"", permissions) + "\"]");
        assignment.setApprovedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        partnerStoreAssignmentRepository.save(assignment);
    }

    private void seedCustomerTenant(Instant now) {
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("customer-thinker-resolver");
        customer.setName("Thinker Resolver Customer");
        customer.setSlug("thinker-resolver-customer");
        customer.setDescription("Integration test customer boundary.");
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(false);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        platformCustomerRepository.save(customer);

        PlatformTenantEntity tenant = new PlatformTenantEntity();
        tenant.setId("tenant-thinker-resolver");
        tenant.setCustomerId(customer.getId());
        tenant.setName("Thinker Resolver Tenant");
        tenant.setSlug("thinker-resolver-tenant");
        tenant.setDescription("Integration test tenant boundary.");
        tenant.setStatus("ACTIVE");
        tenant.setPlatformManaged(false);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        platformTenantRepository.save(tenant);
    }

    private void seedProductService(Instant now) {
        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("shopify-companion");
        service.setServiceRef("shopify-companion");
        service.setDisplayName("Shopify Companion");
        service.setProductFamily("SHOPIFY_COMPANION");
        service.setServiceKind("SHOPIFY_BRIDGE");
        service.setDeploymentMode("SHARED");
        service.setTenantMode("MULTI_TENANT");
        service.setEnvironmentScope("test");
        service.setStatus("ACTIVE");
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        productServiceRepository.save(service);
    }
}
