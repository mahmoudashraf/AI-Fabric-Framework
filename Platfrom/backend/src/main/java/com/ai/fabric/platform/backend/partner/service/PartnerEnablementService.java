package com.ai.fabric.platform.backend.partner.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentPocAuthPath;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocChatService;
import com.ai.fabric.platform.backend.partner.config.PartnerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.partner.entity.PartnerAccountEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerActionAuditEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerClientImplementationRequestEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerEvidenceBundleEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerMemberEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreNoteEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAccessApprovalEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAccessRequestEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAssignmentEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportEscalationEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportReplyEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerTemplateApplicationEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerVerificationRunEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerVerificationRunStepEntity;
import com.ai.fabric.platform.backend.partner.gateway.PartnerAuditPublisher;
import com.ai.fabric.platform.backend.partner.gateway.PartnerCatalogSource;
import com.ai.fabric.platform.backend.partner.gateway.PartnerShopifyStoreReadModel;
import com.ai.fabric.platform.backend.partner.gateway.PartnerStoreAccessGateway;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessRequestSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerAccountSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerActivityEventSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEligibleStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEvidenceBundleCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerEvidenceBundleSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerManualVerificationStepRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerMemberSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerMemberUpdateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerProductControlSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerProductSourceSettingsSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerProfileUpdateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSessionSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSignupCompleteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreAccessLinkSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreNoteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreNoteSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplyRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportThreadSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateApplicationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateApplicationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationPackSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationRunRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationRunSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationStepSummary;
import com.ai.fabric.platform.backend.partner.repository.PartnerAccountRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerActionAuditRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerClientImplementationRequestRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerEvidenceBundleRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAccessApprovalRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAccessRequestRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAssignmentRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreNoteRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportEscalationRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportReplyRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerTemplateApplicationRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerVerificationRunRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerVerificationRunStepRepository;
import com.ai.fabric.platform.backend.partner.security.PartnerForbiddenException;
import com.ai.fabric.platform.backend.partner.security.PartnerPrincipal;
import com.ai.fabric.platform.backend.partner.security.PartnerSecurityContext;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSettingsSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSourceSettingsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSupportProfileRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreConnectionService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourceSettingsService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSupportProfileService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWidgetSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PartnerEnablementService {

    private static final List<String> DEFAULT_ASSIGNMENT_PERMISSIONS = List.of(
        "STORE_READ",
        "CATALOG_READ",
        "PRODUCT_CONFIG_READ",
        "PRODUCT_CONFIG_WRITE",
        "PRODUCT_CONFIG_PUBLISH",
        "STOREFRONT_SURFACE_CONTROL",
        "KNOWLEDGE_SOURCE_CONTROL",
        "KNOWLEDGE_SYNC_TRIGGER",
        "APP_OWNED_CONTENT_WRITE",
        "VERIFICATION_READ",
        "VERIFICATION_RUN",
        "EVIDENCE_READ",
        "EVIDENCE_EXPORT",
        "ESCALATION_CREATE",
        "ESCALATION_REPLY",
        "SUPPORT_MANAGE",
        "AGGREGATE_ANALYTICS_READ",
        "PRODUCT_PAUSE_RESUME"
    );
    private static final List<String> PARTNER_PRODUCT_CONTROL_CAPABILITIES = List.of(
        "PRODUCT_CONFIG_READ",
        "PRODUCT_CONFIG_WRITE",
        "PRODUCT_CONFIG_PUBLISH",
        "STOREFRONT_SURFACE_CONTROL",
        "KNOWLEDGE_SOURCE_CONTROL",
        "KNOWLEDGE_SYNC_TRIGGER",
        "APP_OWNED_CONTENT_WRITE",
        "SUPPORT_MANAGE",
        "PRODUCT_PAUSE_RESUME"
    );
    private static final ShopifyStoreWidgetSettingsSummary DEFAULT_WIDGET_SETTINGS = new ShopifyStoreWidgetSettingsSummary(
        "Ask the store assistant",
        "Store assistant is ready. Ask about products, policies, or collections.",
        "SHOPIFY_COMPANION",
        List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
        "navigator",
        List.of("navigator"),
        Map.of()
    );
    private static final String MERCHANT_CONFIGURED_TIER = "MERCHANT_CONFIGURED";
    private static final String FULL_STORE_ACCESS_SCOPE = "FULL_STORE_ACCESS";
    private static final List<String> FORBIDDEN_PARTNER_SURFACES = List.of(
        "order-lookup",
        "governed-add-to-cart",
        "cart-update",
        "checkout-completion",
        "refund-initiation"
    );
    private static final Instant TEMPLATE_UPDATED_AT = Instant.parse("2026-04-25T00:00:00Z");

    private final PartnerSupabaseAuthProperties authProperties;
    private final PartnerAccountRepository accountRepository;
    private final PartnerMemberRepository memberRepository;
    private final PartnerClientImplementationRequestRepository implementationRequestRepository;
    private final PartnerStoreAccessRequestRepository storeAccessRequestRepository;
    private final PartnerStoreAccessApprovalRepository storeAccessApprovalRepository;
    private final PartnerStoreAssignmentRepository storeAssignmentRepository;
    private final PartnerSupportEscalationRepository escalationRepository;
    private final PartnerSupportReplyRepository replyRepository;
    private final PartnerEvidenceBundleRepository evidenceBundleRepository;
    private final PartnerVerificationRunRepository verificationRunRepository;
    private final PartnerVerificationRunStepRepository verificationRunStepRepository;
    private final PartnerTemplateApplicationRepository templateApplicationRepository;
    private final PartnerStoreNoteRepository storeNoteRepository;
    private final PartnerActionAuditRepository actionAuditRepository;
    private final PartnerStoreAccessGateway storeAccessGateway;
    private final PartnerCatalogSource catalogSource;
    private final PartnerAuditPublisher auditPublisher;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService;
    private final ShopifyStoreSourceSettingsService shopifyStoreSourceSettingsService;
    private final ShopifyStoreSupportProfileService shopifyStoreSupportProfileService;
    private final DeploymentPocChatService deploymentPocChatService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PartnerEnablementService(PartnerSupabaseAuthProperties authProperties,
                                    PartnerAccountRepository accountRepository,
                                    PartnerMemberRepository memberRepository,
                                    PartnerClientImplementationRequestRepository implementationRequestRepository,
                                    PartnerStoreAccessRequestRepository storeAccessRequestRepository,
                                    PartnerStoreAccessApprovalRepository storeAccessApprovalRepository,
                                    PartnerStoreAssignmentRepository storeAssignmentRepository,
                                    PartnerSupportEscalationRepository escalationRepository,
                                    PartnerSupportReplyRepository replyRepository,
                                    PartnerEvidenceBundleRepository evidenceBundleRepository,
                                    PartnerVerificationRunRepository verificationRunRepository,
                                    PartnerVerificationRunStepRepository verificationRunStepRepository,
                                    PartnerTemplateApplicationRepository templateApplicationRepository,
                                    PartnerStoreNoteRepository storeNoteRepository,
                                    PartnerActionAuditRepository actionAuditRepository,
                                    PartnerStoreAccessGateway storeAccessGateway,
                                    PartnerCatalogSource catalogSource,
                                    PartnerAuditPublisher auditPublisher,
                                    ShopifyStoreConnectionService shopifyStoreConnectionService,
                                    ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService,
                                    ShopifyStoreSourceSettingsService shopifyStoreSourceSettingsService,
                                    ShopifyStoreSupportProfileService shopifyStoreSupportProfileService,
                                    DeploymentPocChatService deploymentPocChatService,
                                    ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.implementationRequestRepository = implementationRequestRepository;
        this.storeAccessRequestRepository = storeAccessRequestRepository;
        this.storeAccessApprovalRepository = storeAccessApprovalRepository;
        this.storeAssignmentRepository = storeAssignmentRepository;
        this.escalationRepository = escalationRepository;
        this.replyRepository = replyRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.verificationRunStepRepository = verificationRunStepRepository;
        this.templateApplicationRepository = templateApplicationRepository;
        this.storeNoteRepository = storeNoteRepository;
        this.actionAuditRepository = actionAuditRepository;
        this.storeAccessGateway = storeAccessGateway;
        this.catalogSource = catalogSource;
        this.auditPublisher = auditPublisher;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.shopifyStoreWidgetSettingsService = shopifyStoreWidgetSettingsService;
        this.shopifyStoreSourceSettingsService = shopifyStoreSourceSettingsService;
        this.shopifyStoreSupportProfileService = shopifyStoreSupportProfileService;
        this.deploymentPocChatService = deploymentPocChatService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PartnerSessionSummary session() {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        Optional<PartnerMemberEntity> member = memberRepository.findBySupabaseUserId(principal.supabaseUserId());
        if (member.isEmpty()) {
            return new PartnerSessionSummary(true, true, null, null, 0, 0, List.of("SIGNUP_COMPLETE"));
        }
        PartnerMemberEntity entity = member.get();
        entity.setLastLoginAt(Instant.now());
        entity.setLastAuthProviderSeenAt(Instant.now());
        memberRepository.save(entity);
        ensureMemberActive(entity);
        PartnerAccountEntity account = requireActiveAccount(entity.getPartnerAccountId());
        long stores = storeAssignmentRepository.countByPartnerAccountIdAndStatus(account.getId(), "ACTIVE");
        long openEscalations = escalationRepository.findByPartnerAccountIdOrderByUpdatedAtDesc(account.getId()).stream()
            .filter(value -> !List.of("RESOLVED", "CLOSED").contains(value.getStatus()))
            .count();
        return new PartnerSessionSummary(
            true,
            false,
            toAccountSummary(account),
            toMemberSummary(entity),
            stores,
            openEscalations,
            permissionsFor(entity.getRole())
        );
    }

    @Transactional
    public PartnerSessionSummary completeSignup(PartnerSignupCompleteRequest request) {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        Optional<PartnerMemberEntity> existing = memberRepository.findBySupabaseUserId(principal.supabaseUserId());
        if (existing.isPresent()) {
            return session();
        }
        if (authProperties.requireEmailVerified() && !principal.emailVerified()) {
            throw new PartnerForbiddenException("Verify your partner login email before creating a workspace.");
        }
        Instant now = Instant.now();
        PartnerAccountEntity account = new PartnerAccountEntity();
        account.setId(id("pa"));
        account.setName(clean(request.workspaceName(), "workspaceName"));
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        PartnerMemberEntity member = new PartnerMemberEntity();
        member.setId(id("pm"));
        member.setPartnerAccountId(account.getId());
        member.setSupabaseUserId(principal.supabaseUserId());
        member.setAuthProvider(principal.provider());
        member.setAuthProviderSubject(principal.providerSubject());
        member.setEmail(principal.email());
        member.setEmailVerified(principal.emailVerified());
        member.setDisplayName(firstNonBlank(principal.displayName(), principal.email()));
        member.setAvatarUrl(principal.avatarUrl());
        member.setRole(PlatformRole.PARTNER_ADMIN.name());
        member.setStatus("ACTIVE");
        member.setLastLoginAt(now);
        member.setLastAuthProviderSeenAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberRepository.save(member);
        audit(account.getId(), member.getId(), "PARTNER_SIGNUP_COMPLETED", "PARTNER_ACCOUNT", account.getId(), "SUCCESS", "{}");
        return new PartnerSessionSummary(
            true,
            false,
            toAccountSummary(account),
            toMemberSummary(member),
            0,
            0,
            permissionsFor(member.getRole())
        );
    }

    @Transactional(readOnly = true)
    public List<PartnerStoreSummary> listStores() {
        PartnerContext context = requireProvisionedContext();
        return storeAssignmentRepository.findByPartnerAccountIdOrderByCreatedAtDesc(context.account().getId()).stream()
            .map(this::toStoreSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerEligibleStoreSummary> listEligibleStores(String query) {
        PartnerContext context = requireProvisionedContext();
        return storeAccessGateway.listInstalledStores(query, 50).stream()
            .filter(store -> isStoreEligibleForImplementation(context.account().getId(), store))
            .map(this::toEligibleStoreSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public PartnerStoreSummary getStore(String storeId) {
        PartnerContext context = requireProvisionedContext();
        return toStoreSummary(requireActiveAssignment(context.account().getId(), storeId));
    }

    @Transactional(readOnly = true)
    public PartnerProductControlSummary getProductControls(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        requireAssignmentCapability(assignment, "PRODUCT_CONFIG_READ");
        return toProductControlSummary(assignment, shopifyStoreConnectionService.getConnection(assignment.getShopDomain()));
    }

    @Transactional
    public PartnerProductControlSummary updateProductWidgetSettings(String storeId,
                                                                    UpdateShopifyStoreWidgetSettingsRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        requireAssignmentCapability(assignment, "STOREFRONT_SURFACE_CONTROL");
        requireInstalledStoreForProductControl(assignment);
        ShopifyStoreConnectionSummary summary = shopifyStoreWidgetSettingsService.update(assignment.getShopDomain(), request);
        audit(context, "PRODUCT_WIDGET_SETTINGS_UPDATED", "STORE_ASSIGNMENT", assignment.getId(), "SUCCESS", writeJson(Map.of(
            "shopDomain", assignment.getShopDomain(),
            "enabledSurfaces", summary.widgetDetail() == null || summary.widgetDetail().settings() == null
                ? List.of()
                : summary.widgetDetail().settings().enabledSurfaces()
        )));
        return toProductControlSummary(assignment, summary);
    }

    @Transactional
    public PartnerProductControlSummary updateProductSourceSettings(String storeId,
                                                                    UpdateShopifyStoreSourceSettingsRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        requireAssignmentCapability(assignment, "KNOWLEDGE_SOURCE_CONTROL");
        requireInstalledStoreForProductControl(assignment);
        ShopifyStoreConnectionSummary summary = shopifyStoreSourceSettingsService.update(assignment.getShopDomain(), request);
        audit(context, "PRODUCT_SOURCE_SETTINGS_UPDATED", "STORE_ASSIGNMENT", assignment.getId(), "SUCCESS", writeJson(Map.of(
            "shopDomain", assignment.getShopDomain(),
            "enabledCategories", enabledSourceCategories(summary)
        )));
        return toProductControlSummary(assignment, summary);
    }

    @Transactional
    public PartnerProductControlSummary updateProductSupportProfile(String storeId,
                                                                    UpdateShopifyStoreSupportProfileRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        requireAssignmentCapability(assignment, "SUPPORT_MANAGE");
        requireInstalledStoreForProductControl(assignment);
        ShopifyStoreSupportProfileSummary supportProfile = shopifyStoreSupportProfileService.update(assignment.getShopDomain(), request);
        ShopifyStoreConnectionSummary summary = shopifyStoreConnectionService.getConnection(assignment.getShopDomain());
        audit(context, "PRODUCT_SUPPORT_PROFILE_UPDATED", "STORE_ASSIGNMENT", assignment.getId(), "SUCCESS", writeJson(Map.of(
            "shopDomain", assignment.getShopDomain(),
            "merchantHandoffConfigured", supportProfile.merchantHandoffConfigured(),
            "contactEmailConfigured", supportProfile.contactEmail() != null && !supportProfile.contactEmail().isBlank(),
            "contactUrlConfigured", supportProfile.contactUrl() != null && !supportProfile.contactUrl().isBlank(),
            "helpCenterUrlConfigured", supportProfile.helpCenterUrl() != null && !supportProfile.helpCenterUrl().isBlank()
        )));
        return toProductControlSummary(assignment, summary, supportProfile);
    }

    @Transactional(readOnly = true)
    public JsonNode getPartnerMaxWidgetRuntimeAuthContext(String storeId, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        return deploymentPocChatService.widgetRuntimeAuthContextForTrustedPartner(context.deploymentId(), authPath);
    }

    @Transactional(readOnly = true)
    public JsonNode getPartnerMaxWidgetShellConfig(String storeId, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        return deploymentPocChatService.widgetShellConfigForTrustedPartner(context.deploymentId(), authPath);
    }

    @Transactional(readOnly = true)
    public JsonNode listPartnerMaxWidgetConversations(String storeId, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        return deploymentPocChatService.listConversationsForTrustedPartner(context.deploymentId(), authPath);
    }

    @Transactional(readOnly = true)
    public JsonNode getPartnerMaxWidgetConversation(String storeId, String conversationId, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        return deploymentPocChatService.widgetConversationForTrustedPartner(context.deploymentId(), conversationId, authPath);
    }

    @Transactional
    public JsonNode queryPartnerMaxWidget(String storeId, JsonNode request, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        JsonNode response = deploymentPocChatService.widgetQueryForTrustedPartner(context.deploymentId(), request, authPath);
        audit(context.partner(), "PARTNER_MAX_WIDGET_QUERY_RAN", "STORE_ASSIGNMENT", context.assignment().getId(), "SUCCESS", writeJson(Map.of(
            "shopDomain", context.assignment().getShopDomain(),
            "deploymentId", context.deploymentId(),
            "authPath", DeploymentPocAuthPath.defaultValue(authPath).name()
        )));
        return response;
    }

    @Transactional
    public JsonNode suggestPartnerMaxWidget(String storeId, JsonNode request, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        return deploymentPocChatService.widgetSuggestionsForTrustedPartner(context.deploymentId(), request, authPath);
    }

    @Transactional
    public void deletePartnerMaxWidgetConversation(String storeId, String conversationId, DeploymentPocAuthPath authPath) {
        PartnerMaxWidgetContext context = requirePartnerMaxWidgetContext(storeId);
        deploymentPocChatService.deleteConversationForTrustedPartner(context.deploymentId(), conversationId, authPath);
        audit(context.partner(), "PARTNER_MAX_WIDGET_CONVERSATION_RESET", "STORE_ASSIGNMENT", context.assignment().getId(), "SUCCESS", writeJson(Map.of(
            "shopDomain", context.assignment().getShopDomain(),
            "deploymentId", context.deploymentId(),
            "authPath", DeploymentPocAuthPath.defaultValue(authPath).name()
        )));
    }

    @Transactional(readOnly = true)
    public List<PartnerActivityEventSummary> listActivity() {
        PartnerContext context = requireProvisionedContext();
        return actionAuditRepository.findTop20ByPartnerAccountIdOrderByCreatedAtDesc(context.account().getId()).stream()
            .filter(this::partnerVisibleActivity)
            .map(this::toActivitySummary)
            .toList();
    }

    @Transactional
    public PartnerClientImplementationSummary createImplementation(PartnerClientImplementationRequest request) {
        PartnerContext context = requireProvisionedContext();
        Instant now = Instant.now();
        PartnerShopifyStoreReadModel store = requireEligibleInstalledStore(context.account().getId(), request.storeConnectionId());
        List<String> requestedSurfaces = storeConfiguredSurfaces(store);
        PartnerClientImplementationRequestEntity entity = new PartnerClientImplementationRequestEntity();
        entity.setId(id("pci"));
        entity.setPartnerAccountId(context.account().getId());
        entity.setCreatedByMemberId(context.member().getId());
        entity.setClientName(clean(request.clientName(), "clientName"));
        entity.setContactEmail(trimToNull(request.contactEmail()));
        entity.setStoreConnectionId(store.storeConnectionId());
        entity.setShopDomain(normalizeShopDomain(store.shopDomain()));
        entity.setVertical(trimToNull(request.vertical()));
        entity.setRequestedTier(MERCHANT_CONFIGURED_TIER);
        entity.setRequestedSurfacesJson(writeJson(requestedSurfaces));
        entity.setKnownIntegrationsJson(writeJson(safeList(request.knownIntegrations())));
        entity.setNotes(trimToNull(request.notes()));
        entity.setStatus("WAITING_ON_MERCHANT");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        implementationRequestRepository.save(entity);

        PartnerStoreAccessRequestEntity accessRequest = createAccessRequestForImplementation(context, entity, now);
        entity.setApprovalCode(accessRequest.getApprovalCode());
        entity.setApprovalUrl(accessRequest.getApprovalUrl());
        entity.setApprovalExpiresAt(accessRequest.getExpiresAt());
        entity.setUpdatedAt(now);
        implementationRequestRepository.save(entity);

        audit(context, "CLIENT_IMPLEMENTATION_CREATED", "CLIENT_IMPLEMENTATION", entity.getId(), "SUCCESS", "{}");
        audit(context, "STORE_ACCESS_REQUESTED", "STORE_ACCESS_REQUEST", accessRequest.getId(), "SUCCESS", "{}");
        return toImplementationSummary(entity);
    }

    @Transactional(readOnly = true)
    public PartnerClientImplementationSummary getImplementation(String requestId) {
        PartnerContext context = requireProvisionedContext();
        return toImplementationSummary(requireImplementation(context.account().getId(), requestId));
    }

    @Transactional(readOnly = true)
    public List<PartnerClientImplementationSummary> listImplementations() {
        PartnerContext context = requireProvisionedContext();
        return implementationRequestRepository.findByPartnerAccountIdOrderByCreatedAtDesc(context.account().getId()).stream()
            .map(this::toImplementationSummary)
            .toList();
    }

    @Transactional
    public PartnerStoreAccessLinkSummary createStoreAccessLink(String requestId) {
        PartnerContext context = requireProvisionedContext();
        PartnerClientImplementationRequestEntity implementation = requireImplementation(context.account().getId(), requestId);
        Optional<PartnerStoreAccessRequestEntity> existing = storeAccessRequestRepository.findFirstByImplementationRequestIdOrderByCreatedAtDesc(implementation.getId());
        if (existing.isPresent()) {
            PartnerStoreAccessRequestEntity accessRequest = existing.get();
            return new PartnerStoreAccessLinkSummary(
                accessRequest.getId(),
                implementation.getId(),
                accessRequest.getApprovalUrl(),
                accessRequest.getStatus(),
                accessRequest.getExpiresAt()
            );
        }
        Instant now = Instant.now();
        PartnerStoreAccessRequestEntity accessRequest = createAccessRequestForImplementation(context, implementation, now);

        implementation.setStatus("WAITING_ON_MERCHANT");
        implementation.setApprovalCode(accessRequest.getApprovalCode());
        implementation.setApprovalUrl(accessRequest.getApprovalUrl());
        implementation.setApprovalExpiresAt(accessRequest.getExpiresAt());
        implementation.setUpdatedAt(now);
        implementationRequestRepository.save(implementation);
        audit(context, "STORE_ACCESS_LINK_CREATED", "STORE_ACCESS_REQUEST", accessRequest.getId(), "SUCCESS", "{}");
        return new PartnerStoreAccessLinkSummary(accessRequest.getId(), implementation.getId(), accessRequest.getApprovalUrl(), accessRequest.getStatus(), accessRequest.getExpiresAt());
    }

    @Transactional
    public MerchantPartnerAccessApprovalSummary approveMerchantAccess(String approvalCode,
                                                                      MerchantPartnerAccessApprovalRequest request) {
        PartnerStoreAccessRequestEntity accessRequest = storeAccessRequestRepository.findByApprovalCode(approvalCode)
            .orElseThrow(() -> new IllegalArgumentException("Partner access approval code was not found."));
        MerchantPartnerAccessDecisionSummary decision = approveStoreAccessRequest(
            accessRequest,
            new MerchantPartnerAccessDecisionRequest(request.approverName(), request.approverEmail(), request.approvedScope(), null),
            "MERCHANT_APPROVAL_LINK"
        );
        return new MerchantPartnerAccessApprovalSummary(decision.assignmentId(), decision.shopDomain(), decision.status(), decision.decidedAt());
    }

    @Transactional(readOnly = true)
    public List<MerchantPartnerAccessRequestSummary> listMerchantAccessRequests(String shopDomain) {
        String normalizedShopDomain = normalizeShopDomain(shopDomain);
        return storeAccessRequestRepository.findByShopDomainIgnoreCaseOrderByCreatedAtDesc(normalizedShopDomain).stream()
            .limit(50)
            .map(this::toMerchantAccessRequestSummary)
            .toList();
    }

    @Transactional
    public MerchantPartnerAccessDecisionSummary approveMerchantAccessRequest(String requestId,
                                                                            String shopDomain,
                                                                            MerchantPartnerAccessDecisionRequest request) {
        PartnerStoreAccessRequestEntity accessRequest = requireMerchantAccessRequest(requestId, shopDomain);
        return approveStoreAccessRequest(accessRequest, request, "SHOPIFY_ADMIN_APPROVAL");
    }

    @Transactional
    public MerchantPartnerAccessDecisionSummary denyMerchantAccessRequest(String requestId,
                                                                         String shopDomain,
                                                                         MerchantPartnerAccessDecisionRequest request) {
        PartnerStoreAccessRequestEntity accessRequest = requireMerchantAccessRequest(requestId, shopDomain);
        if (!"WAITING_ON_MERCHANT".equals(accessRequest.getStatus())) {
            throw new IllegalArgumentException("Partner access request is not waiting on merchant review.");
        }
        Instant now = Instant.now();
        if (accessRequest.getExpiresAt().isBefore(now)) {
            accessRequest.setStatus("EXPIRED");
            accessRequest.setUpdatedAt(now);
            storeAccessRequestRepository.save(accessRequest);
            throw new IllegalArgumentException("Partner access request has expired.");
        }
        PartnerClientImplementationRequestEntity implementation = implementationRequestRepository
            .findById(accessRequest.getImplementationRequestId())
            .orElseThrow();
        accessRequest.setStatus("DENIED");
        accessRequest.setUpdatedAt(now);
        storeAccessRequestRepository.save(accessRequest);
        implementation.setStatus("DENIED");
        implementation.setUpdatedAt(now);
        implementationRequestRepository.save(implementation);
        audit(accessRequest.getPartnerAccountId(), null, "STORE_ACCESS_DENIED", "STORE_ACCESS_REQUEST", accessRequest.getId(), "SUCCESS", writeJson(Map.of(
            "sourceFlow", "SHOPIFY_ADMIN_APPROVAL",
            "approverName", clean(request.approverName(), "approverName"),
            "reason", firstNonBlank(request.decisionReason(), "not_provided")
        )));
        return new MerchantPartnerAccessDecisionSummary(accessRequest.getId(), null, accessRequest.getShopDomain(), accessRequest.getStatus(), now);
    }

    @Transactional
    public MerchantPartnerAccessDecisionSummary revokeMerchantAccessRequest(String requestId,
                                                                           String shopDomain,
                                                                           MerchantPartnerAccessDecisionRequest request) {
        PartnerStoreAccessRequestEntity accessRequest = requireMerchantAccessRequest(requestId, shopDomain);
        if (!"APPROVED".equals(accessRequest.getStatus())) {
            throw new IllegalArgumentException("Partner access request does not have active access to revoke.");
        }
        PartnerStoreAssignmentEntity assignment = requireActiveAssignmentForAccessRequest(accessRequest);
        PartnerClientImplementationRequestEntity implementation = implementationRequestRepository
            .findById(accessRequest.getImplementationRequestId())
            .orElseThrow();

        Instant now = Instant.now();
        accessRequest.setStatus("REVOKED");
        accessRequest.setRevokedAt(now);
        accessRequest.setUpdatedAt(now);
        storeAccessRequestRepository.save(accessRequest);

        assignment.setStatus("REVOKED");
        assignment.setRevokedAt(now);
        assignment.setUpdatedAt(now);
        storeAssignmentRepository.save(assignment);

        implementation.setStatus("REVOKED");
        implementation.setUpdatedAt(now);
        implementationRequestRepository.save(implementation);

        String sourceFlow = revokeSourceFlow();
        audit(accessRequest.getPartnerAccountId(), null, "STORE_ACCESS_REVOKED", "STORE_ASSIGNMENT", assignment.getId(), "SUCCESS", writeJson(Map.of(
            "sourceFlow", sourceFlow,
            "approverName", clean(request.approverName(), "approverName"),
            "reason", firstNonBlank(request.decisionReason(), "not_provided")
        )));
        return new MerchantPartnerAccessDecisionSummary(accessRequest.getId(), assignment.getId(), assignment.getShopDomain(), assignment.getStatus(), now);
    }

    private MerchantPartnerAccessDecisionSummary approveStoreAccessRequest(PartnerStoreAccessRequestEntity accessRequest,
                                                                          MerchantPartnerAccessDecisionRequest request,
                                                                          String sourceFlow) {
        if (!"WAITING_ON_MERCHANT".equals(accessRequest.getStatus())) {
            throw new IllegalArgumentException("Partner access request is not waiting on merchant review.");
        }
        Instant now = Instant.now();
        if (accessRequest.getExpiresAt().isBefore(now)) {
            accessRequest.setStatus("EXPIRED");
            accessRequest.setUpdatedAt(now);
            storeAccessRequestRepository.save(accessRequest);
            throw new IllegalArgumentException("Partner access request has expired.");
        }
        PartnerClientImplementationRequestEntity implementation = implementationRequestRepository
            .findById(accessRequest.getImplementationRequestId())
            .orElseThrow();
        PartnerShopifyStoreReadModel liveStore = requireInstalledStore(accessRequest);

        accessRequest.setStatus("APPROVED");
        accessRequest.setApprovedAt(now);
        accessRequest.setUpdatedAt(now);
        storeAccessRequestRepository.save(accessRequest);

        PartnerStoreAccessApprovalEntity approval = new PartnerStoreAccessApprovalEntity();
        approval.setId(id("psaa"));
        approval.setAccessRequestId(accessRequest.getId());
        approval.setPartnerAccountId(accessRequest.getPartnerAccountId());
        approval.setShopDomain(accessRequest.getShopDomain());
        approval.setApproverName(clean(request.approverName(), "approverName"));
        approval.setApproverEmail(trimToNull(request.approverEmail()));
        approval.setApprovedScope(firstNonBlank(request.approvedScope(), FULL_STORE_ACCESS_SCOPE));
        approval.setSourceFlow(sourceFlow);
        approval.setApprovedAt(now);
        approval.setDetailsJson(writeJson(Map.of(
            "storeConnectionId", liveStore.storeConnectionId(),
            "decisionReason", firstNonBlank(request.decisionReason(), "not_provided")
        )));
        storeAccessApprovalRepository.save(approval);

        PartnerStoreAssignmentEntity assignment = storeAssignmentRepository
            .findByPartnerAccountIdAndStoreConnectionId(accessRequest.getPartnerAccountId(), liveStore.storeConnectionId())
            .or(() -> storeAssignmentRepository.findByPartnerAccountIdAndShopDomainIgnoreCase(accessRequest.getPartnerAccountId(), accessRequest.getShopDomain()))
            .orElseGet(PartnerStoreAssignmentEntity::new);
        if (!StringUtils.hasText(assignment.getId())) {
            assignment.setId(id("psa"));
            assignment.setCreatedAt(now);
        }
        assignment.setPartnerAccountId(accessRequest.getPartnerAccountId());
        assignment.setStoreConnectionId(liveStore.storeConnectionId());
        assignment.setShopDomain(liveStore.shopDomain());
        assignment.setMerchantName(firstNonBlank(liveStore.displayName(), implementation.getClientName()));
        assignment.setStatus("ACTIVE");
        assignment.setAssignmentSource(sourceFlow);
        assignment.setApprovedBy(firstNonBlank(request.approverEmail(), request.approverName()));
        assignment.setPermissionsJson(writeJson(DEFAULT_ASSIGNMENT_PERMISSIONS));
        assignment.setApprovedAt(now);
        assignment.setRevokedAt(null);
        assignment.setSuspendedAt(null);
        assignment.setUpdatedAt(now);
        storeAssignmentRepository.save(assignment);

        implementation.setStatus("APPROVED");
        implementation.setUpdatedAt(now);
        implementationRequestRepository.save(implementation);
        audit(accessRequest.getPartnerAccountId(), null, "STORE_ACCESS_APPROVED", "STORE_ASSIGNMENT", assignment.getId(), "SUCCESS", writeJson(Map.of(
            "sourceFlow", sourceFlow,
            "storeConnectionId", liveStore.storeConnectionId()
        )));
        return new MerchantPartnerAccessDecisionSummary(accessRequest.getId(), assignment.getId(), assignment.getShopDomain(), assignment.getStatus(), now);
    }

    @Transactional(readOnly = true)
    public List<PartnerCatalogEntrySummary> listCatalog() {
        requireProvisionedContext();
        return catalogSource.listCatalog();
    }

    @Transactional(readOnly = true)
    public List<PartnerVerificationPackSummary> listVerificationPacks() {
        requireProvisionedContext();
        return verificationPacks().stream()
            .map(pack -> toPackSummary(pack, pack.steps().stream()
                .map(step -> stepSummary(step, "NOT_RUN", "Run this pack on an approved store to capture live status.", step.suggestedFix(), List.of(), null))
                .toList()))
            .toList();
    }

    @Transactional(readOnly = true)
    public PartnerVerificationPackSummary getStoreVerificationPack(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        PartnerShopifyStoreReadModel store = storeForAssignment(assignment);
        VerificationPack pack = defaultVerificationPack();
        return toPackSummary(pack, evaluatePack(pack, assignment, store, Instant.now()));
    }

    @Transactional
    public PartnerVerificationRunSummary runStoreVerification(String storeId, PartnerVerificationRunRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        PartnerShopifyStoreReadModel store = storeForAssignment(assignment);
        VerificationPack pack = verificationPack(firstNonBlank(request.packId(), defaultVerificationPack().id()));
        Instant now = Instant.now();
        List<PartnerVerificationStepSummary> evaluatedSteps = evaluatePack(pack, assignment, store, now);
        String status = aggregateVerificationStatus(evaluatedSteps);

        PartnerVerificationRunEntity run = new PartnerVerificationRunEntity();
        run.setId(id("pvr"));
        run.setPartnerAccountId(context.account().getId());
        run.setStoreAssignmentId(assignment.getId());
        run.setPackId(pack.id());
        run.setPackName(pack.name());
        run.setStatus(status);
        run.setSummaryJson(writeJson(verificationSummaryMap(assignment, store, status, evaluatedSteps)));
        run.setStartedByMemberId(context.member().getId());
        run.setStartedAt(now);
        run.setCompletedAt(now);
        verificationRunRepository.save(run);

        List<PartnerVerificationRunStepEntity> persistedSteps = evaluatedSteps.stream()
            .map(step -> toRunStepEntity(run.getId(), step))
            .toList();
        verificationRunStepRepository.saveAll(persistedSteps);

        PartnerEvidenceBundleEntity evidence = createEvidenceBundleForRun(context, assignment, store, run, evaluatedSteps, "VERIFICATION_PACK");
        run.setEvidenceBundleId(evidence.getId());
        verificationRunRepository.save(run);
        audit(context, "VERIFICATION_RUN_CREATED", "VERIFICATION_RUN", run.getId(), "SUCCESS", writeJson(Map.of(
            "packId", pack.id(),
            "status", status
        )));
        return toVerificationRunSummary(run, persistedSteps);
    }

    @Transactional(readOnly = true)
    public List<PartnerVerificationRunSummary> listVerificationRuns() {
        PartnerContext context = requireProvisionedContext();
        return verificationRunRepository.findByPartnerAccountIdOrderByStartedAtDesc(context.account().getId()).stream()
            .map(run -> toVerificationRunSummary(run, verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerVerificationRunSummary> listStoreVerificationRuns(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        return verificationRunRepository.findByPartnerAccountIdAndStoreAssignmentIdOrderByStartedAtDesc(context.account().getId(), assignment.getId()).stream()
            .map(run -> toVerificationRunSummary(run, verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public PartnerVerificationRunSummary getVerificationRun(String runId) {
        PartnerContext context = requireProvisionedContext();
        PartnerVerificationRunEntity run = requireVerificationRun(context.account().getId(), runId);
        requireActiveAssignment(context.account().getId(), run.getStoreAssignmentId());
        return toVerificationRunSummary(run, verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId()));
    }

    @Transactional
    public PartnerVerificationRunSummary completeManualVerificationStep(String storeId,
                                                                        String stepId,
                                                                        PartnerManualVerificationStepRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        Instant now = Instant.now();
        PartnerVerificationRunEntity run = StringUtils.hasText(request.runId())
            ? requireVerificationRun(context.account().getId(), request.runId())
            : createManualVerificationRun(context, assignment, now);
        if (!assignment.getId().equals(run.getStoreAssignmentId())) {
            throw new PartnerForbiddenException("Verification run is not scoped to this store.");
        }
        String normalizedStepId = clean(stepId, "stepId");
        String status = normalizeStepStatus(request.status());
        PartnerVerificationRunStepEntity step = verificationRunStepRepository.findByRunIdAndStepId(run.getId(), normalizedStepId)
            .orElseGet(() -> {
                PartnerVerificationRunStepEntity created = new PartnerVerificationRunStepEntity();
                created.setId(id("pvrs"));
                created.setRunId(run.getId());
                created.setStepId(normalizedStepId);
                created.setStepLabel(titleFromId(normalizedStepId));
                created.setSortOrder(verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId()).size() + 1);
                return created;
            });
        step.setStatus(status);
        step.setPartnerSafeMessage(firstNonBlank(request.partnerSafeMessage(), "Manual verification step marked " + status.toLowerCase(Locale.ROOT) + "."));
        step.setSuggestedFix(status.equals("PASSED") ? null : "Review the store setup checklist and attach evidence before escalating.");
        step.setEvidenceJson(writeJson(StringUtils.hasText(request.evidenceNote()) ? List.of(request.evidenceNote().trim()) : List.of()));
        step.setCheckedAt(now);
        verificationRunStepRepository.save(step);

        List<PartnerVerificationRunStepEntity> steps = verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId());
        run.setStatus(aggregateVerificationStatus(steps.stream().map(this::toVerificationStepSummary).toList()));
        run.setCompletedAt(now);
        run.setSummaryJson(writeJson(Map.of(
            "manual", true,
            "stepCount", steps.size(),
            "status", run.getStatus()
        )));
        verificationRunRepository.save(run);
        audit(context, "VERIFICATION_STEP_MARKED", "VERIFICATION_RUN", run.getId(), "SUCCESS", writeJson(Map.of(
            "stepId", normalizedStepId,
            "status", status
        )));
        return toVerificationRunSummary(run, steps);
    }

    @Transactional(readOnly = true)
    public List<PartnerEvidenceBundleSummary> listEvidenceBundles() {
        PartnerContext context = requireProvisionedContext();
        return evidenceBundleRepository.findByPartnerAccountIdOrderByGeneratedAtDesc(context.account().getId()).stream()
            .map(this::toEvidenceBundleSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerEvidenceBundleSummary> listStoreEvidenceBundles(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        return evidenceBundleRepository.findByPartnerAccountIdAndStoreAssignmentIdOrderByGeneratedAtDesc(context.account().getId(), assignment.getId()).stream()
            .map(this::toEvidenceBundleSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public PartnerEvidenceBundleSummary getEvidenceBundle(String bundleId) {
        PartnerContext context = requireProvisionedContext();
        PartnerEvidenceBundleEntity bundle = requireEvidenceBundle(context.account().getId(), bundleId);
        if (StringUtils.hasText(bundle.getStoreAssignmentId())) {
            requireActiveAssignment(context.account().getId(), bundle.getStoreAssignmentId());
        }
        return toEvidenceBundleSummary(bundle);
    }

    @Transactional
    public PartnerEvidenceBundleSummary createStoreEvidenceBundle(String storeId, PartnerEvidenceBundleCreateRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        PartnerShopifyStoreReadModel store = storeForAssignment(assignment);
        PartnerVerificationRunEntity run = null;
        List<PartnerVerificationStepSummary> steps = List.of();
        if (request != null && StringUtils.hasText(request.verificationRunId())) {
            run = requireVerificationRun(context.account().getId(), request.verificationRunId());
            if (!assignment.getId().equals(run.getStoreAssignmentId())) {
                throw new PartnerForbiddenException("Verification run is not scoped to this store.");
            }
            steps = verificationRunStepRepository.findByRunIdOrderBySortOrderAsc(run.getId()).stream()
                .map(this::toVerificationStepSummary)
                .toList();
        }
        PartnerEvidenceBundleEntity bundle = run == null
            ? createEvidenceBundleForStore(context, assignment, store, firstNonBlank(request == null ? null : request.bundleKind(), "LAUNCH_PACKET"))
            : createEvidenceBundleForRun(context, assignment, store, run, steps, firstNonBlank(request == null ? null : request.bundleKind(), "VERIFICATION_PACK"));
        audit(context, "EVIDENCE_BUNDLE_CREATED", "EVIDENCE_BUNDLE", bundle.getId(), "SUCCESS", writeJson(Map.of(
            "bundleKind", bundle.getBundleKind()
        )));
        return toEvidenceBundleSummary(bundle);
    }

    @Transactional(readOnly = true)
    public byte[] exportEvidenceBundle(String bundleId) {
        PartnerContext context = requireProvisionedContext();
        PartnerEvidenceBundleEntity bundle = requireEvidenceBundle(context.account().getId(), bundleId);
        if (StringUtils.hasText(bundle.getStoreAssignmentId())) {
            requireActiveAssignment(context.account().getId(), bundle.getStoreAssignmentId());
        }
        return evidenceBundleZip(bundle);
    }

    @Transactional(readOnly = true)
    public List<PartnerTemplateSummary> listTemplates() {
        requireProvisionedContext();
        return templates();
    }

    @Transactional(readOnly = true)
    public PartnerTemplateSummary getTemplate(String templateId) {
        requireProvisionedContext();
        return requireTemplate(templateId);
    }

    @Transactional
    public PartnerTemplateApplicationSummary applyTemplate(String templateId, PartnerTemplateApplicationRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerTemplateSummary template = requireTemplate(templateId);
        PartnerStoreAssignmentEntity assignment = request != null && StringUtils.hasText(request.storeId())
            ? requireActiveAssignment(context.account().getId(), request.storeId())
            : null;
        Instant now = Instant.now();
        Optional<PartnerTemplateApplicationEntity> existing = assignment == null
            ? templateApplicationRepository.findFirstByPartnerAccountIdAndStoreAssignmentIdIsNullAndTemplateIdAndStatusOrderByAppliedAtDesc(
                context.account().getId(),
                template.id(),
                "ACTIVE"
            )
            : templateApplicationRepository.findFirstByPartnerAccountIdAndStoreAssignmentIdAndTemplateIdAndStatusOrderByAppliedAtDesc(
                context.account().getId(),
                assignment.getId(),
                template.id(),
                "ACTIVE"
            );
        if (existing.isPresent()) {
            PartnerTemplateApplicationEntity application = existing.get();
            application.setTemplateName(template.name());
            application.setCategory(template.category());
            application.setChecklistJson(writeJson(template.checklist()));
            application.setAssumptionsJson(writeJson(template.assumptions()));
            application.setUpdatedAt(now);
            templateApplicationRepository.save(application);
            audit(context, "TEMPLATE_APPLICATION_REUSED", "TEMPLATE_APPLICATION", application.getId(), "SUCCESS", writeJson(Map.of(
                "templateId", template.id(),
                "storeAssignmentId", assignment == null ? "none" : assignment.getId()
            )));
            return toTemplateApplicationSummary(application);
        }
        PartnerTemplateApplicationEntity application = new PartnerTemplateApplicationEntity();
        application.setId(id("pta"));
        application.setPartnerAccountId(context.account().getId());
        application.setStoreAssignmentId(assignment == null ? null : assignment.getId());
        application.setTemplateId(template.id());
        application.setTemplateName(template.name());
        application.setCategory(template.category());
        application.setAppliedByMemberId(context.member().getId());
        application.setStatus("ACTIVE");
        application.setChecklistJson(writeJson(template.checklist()));
        application.setAssumptionsJson(writeJson(template.assumptions()));
        application.setAppliedAt(now);
        application.setUpdatedAt(now);
        templateApplicationRepository.save(application);
        audit(context, "TEMPLATE_APPLIED", "TEMPLATE_APPLICATION", application.getId(), "SUCCESS", writeJson(Map.of(
            "templateId", template.id(),
            "storeAssignmentId", assignment == null ? "none" : assignment.getId()
        )));
        return toTemplateApplicationSummary(application);
    }

    @Transactional(readOnly = true)
    public List<PartnerTemplateApplicationSummary> listTemplateApplications() {
        PartnerContext context = requireProvisionedContext();
        return templateApplicationRepository.findByPartnerAccountIdOrderByAppliedAtDesc(context.account().getId()).stream()
            .map(this::toTemplateApplicationSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerStoreNoteSummary> listStoreNotes(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        return storeNoteRepository.findByPartnerAccountIdAndStoreAssignmentIdOrderByCreatedAtDesc(context.account().getId(), assignment.getId()).stream()
            .map(this::toStoreNoteSummary)
            .toList();
    }

    @Transactional
    public PartnerStoreNoteSummary createStoreNote(String storeId, PartnerStoreNoteRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        Instant now = Instant.now();
        PartnerStoreNoteEntity note = new PartnerStoreNoteEntity();
        note.setId(id("psn"));
        note.setPartnerAccountId(context.account().getId());
        note.setStoreAssignmentId(assignment.getId());
        note.setCreatedByMemberId(context.member().getId());
        note.setBodyMarkdown(clean(request.bodyMarkdown(), "bodyMarkdown"));
        note.setStatus("ACTIVE");
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        storeNoteRepository.save(note);
        audit(context, "STORE_NOTE_CREATED", "STORE_NOTE", note.getId(), "SUCCESS", "{}");
        return toStoreNoteSummary(note);
    }

    @Transactional(readOnly = true)
    public List<PartnerMemberSummary> listMembers() {
        PartnerContext context = requireProvisionedContext();
        requireWorkspaceAdmin(context);
        return memberRepository.findByPartnerAccountIdOrderByCreatedAtAsc(context.account().getId()).stream()
            .map(this::toMemberSummary)
            .toList();
    }

    @Transactional
    public PartnerMemberSummary updateProfile(PartnerProfileUpdateRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerMemberEntity member = context.member();
        member.setDisplayName(firstNonBlank(request.displayName(), member.getDisplayName(), member.getEmail()));
        member.setAvatarUrl(trimToNull(request.avatarUrl()));
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);
        audit(context, "PARTNER_PROFILE_UPDATED", "PARTNER_MEMBER", member.getId(), "SUCCESS", "{}");
        return toMemberSummary(member);
    }

    @Transactional
    public PartnerMemberSummary updateMember(String memberId, PartnerMemberUpdateRequest request) {
        PartnerContext context = requireProvisionedContext();
        requireWorkspaceAdmin(context);
        PartnerMemberEntity target = memberRepository.findByIdAndPartnerAccountId(memberId, context.account().getId())
            .orElseThrow(() -> new PartnerForbiddenException("Partner member is not available to this workspace."));
        if (target.getId().equals(context.member().getId()) && StringUtils.hasText(request.status()) && !"ACTIVE".equalsIgnoreCase(request.status())) {
            throw new IllegalArgumentException("Partner admins cannot suspend or revoke their own member record.");
        }
        if (StringUtils.hasText(request.role())) {
            target.setRole(normalizePartnerRole(request.role()));
        }
        if (StringUtils.hasText(request.status())) {
            target.setStatus(normalizeMemberStatus(request.status()));
        }
        target.setUpdatedAt(Instant.now());
        memberRepository.save(target);
        audit(context, "PARTNER_MEMBER_UPDATED", "PARTNER_MEMBER", target.getId(), "SUCCESS", writeJson(Map.of(
            "role", target.getRole(),
            "status", target.getStatus()
        )));
        return toMemberSummary(target);
    }

    @Transactional(readOnly = true)
    public List<PartnerSupportEscalationSummary> listEscalations() {
        PartnerContext context = requireProvisionedContext();
        return escalationRepository.findByPartnerAccountIdOrderByUpdatedAtDesc(context.account().getId()).stream()
            .map(this::toEscalationSummary)
            .toList();
    }

    @Transactional
    public PartnerSupportEscalationSummary createEscalation(String storeId,
                                                            PartnerSupportEscalationCreateRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        Instant now = Instant.now();
        PartnerSupportEscalationEntity entity = new PartnerSupportEscalationEntity();
        entity.setId(id("pse"));
        entity.setPartnerAccountId(context.account().getId());
        entity.setStoreAssignmentId(assignment.getId());
        entity.setCreatedByMemberId(context.member().getId());
        entity.setTitle(clean(request.title(), "title"));
        entity.setSeverity(clean(request.severity(), "severity").toUpperCase(Locale.ROOT));
        entity.setStatus("OPEN");
        entity.setDescription(clean(request.description(), "description"));
        entity.setReproductionSteps(trimToNull(request.reproductionSteps()));
        entity.setExpectedBehavior(trimToNull(request.expectedBehavior()));
        entity.setActualBehavior(trimToNull(request.actualBehavior()));
        entity.setImpact(trimToNull(request.impact()));
        entity.setNextAction(trimToNull(request.nextAction()));
        entity.setEvidenceBundleIdsJson(writeJson(validatedEvidenceBundleIds(context.account().getId(), assignment.getId(), request.evidenceBundleIds())));
        entity.setDueAt(request.dueAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        escalationRepository.save(entity);
        audit(context, "SUPPORT_ESCALATION_CREATED", "SUPPORT_ESCALATION", entity.getId(), "SUCCESS", "{}");
        return toEscalationSummary(entity);
    }

    @Transactional(readOnly = true)
    public PartnerSupportThreadSummary getEscalationThread(String escalationId) {
        PartnerContext context = requireProvisionedContext();
        PartnerSupportEscalationEntity escalation = requireEscalation(context.account().getId(), escalationId);
        if (StringUtils.hasText(escalation.getStoreAssignmentId())) {
            requireActiveAssignment(context.account().getId(), escalation.getStoreAssignmentId());
        }
        List<PartnerSupportReplySummary> replies = replyRepository
            .findByEscalationIdAndVisibilityOrderByCreatedAtAsc(escalation.getId(), "PARTNER_VISIBLE")
            .stream()
            .map(this::toReplySummary)
            .toList();
        return new PartnerSupportThreadSummary(toEscalationSummary(escalation), replies);
    }

    @Transactional
    public PartnerSupportReplySummary addEscalationReply(String escalationId, PartnerSupportReplyRequest request) {
        PartnerContext context = requireProvisionedContext();
        PartnerSupportEscalationEntity escalation = requireEscalation(context.account().getId(), escalationId);
        if (StringUtils.hasText(escalation.getStoreAssignmentId())) {
            requireActiveAssignment(context.account().getId(), escalation.getStoreAssignmentId());
        }
        PartnerSupportReplyEntity reply = new PartnerSupportReplyEntity();
        reply.setId(id("psr"));
        reply.setEscalationId(escalation.getId());
        reply.setAuthorMemberId(context.member().getId());
        reply.setAuthorName(firstNonBlank(context.member().getDisplayName(), context.member().getEmail()));
        reply.setAuthorRole(context.member().getRole());
        reply.setVisibility("PARTNER_VISIBLE");
        reply.setBodyMarkdown(clean(request.bodyMarkdown(), "bodyMarkdown"));
        reply.setAttachmentsJson(writeJson(validatedEvidenceBundleIds(context.account().getId(), escalation.getStoreAssignmentId(), request.evidenceBundleIds())));
        reply.setCreatedAt(Instant.now());
        replyRepository.save(reply);
        escalation.setUpdatedAt(reply.getCreatedAt());
        escalationRepository.save(escalation);
        audit(context, "SUPPORT_REPLY_CREATED", "SUPPORT_ESCALATION", escalation.getId(), "SUCCESS", "{}");
        return toReplySummary(reply);
    }

    private List<VerificationPack> verificationPacks() {
        return List.of(
            new VerificationPack(
                "starter-launch-readiness",
                "Starter launch readiness",
                "Checks install, source readiness, storefront activation, read-only Starter boundary, and merchant-safe evidence readiness.",
                List.of(
                    new VerificationStepDefinition("companion-installed", "Companion installed", "Confirm the Shopify Companion install is active.", "Install or reconnect the Shopify Companion app."),
                    new VerificationStepDefinition("knowledge-sync-ready", "Knowledge Sync ready", "Confirm store content has completed Knowledge Sync.", "Run Knowledge Sync from merchant admin or ask an operator to investigate stale sources."),
                    new VerificationStepDefinition("source-readiness-ready", "Source readiness ready", "Confirm enabled store content is ready for grounded answers.", "Resolve missing source categories or incomplete content before launch."),
                    new VerificationStepDefinition("storefront-widget-enabled", "Storefront surfaces enabled", "Confirm storefront widget or app embed is enabled.", "Enable the Shopify app embed and place the configured app blocks."),
                    new VerificationStepDefinition("free-ai-search-boundary", "Free AI-search boundary", "Confirm AI search is available and forbidden governed surfaces are absent.", "Remove order/account/governed action surfaces from Free or Starter configuration."),
                    new VerificationStepDefinition("starter-read-only-boundary", "Starter read-only boundary", "Confirm Starter surfaces remain read-only and exclude order lookup.", "Keep account/order/guided-commerce actions gated outside Starter."),
                    new VerificationStepDefinition("evidence-contract-safe", "Evidence contract safe", "Confirm generated evidence is merchant-safe.", "Regenerate evidence through the partner bundle exporter.")
                )
            ),
            new VerificationPack(
                "free-ai-search",
                "Free AI search",
                "Checks the Free tier AI-search-only boundary and grounded source readiness.",
                List.of(
                    new VerificationStepDefinition("companion-installed", "Companion installed", "Confirm the Shopify Companion install is active.", "Install or reconnect the Shopify Companion app."),
                    new VerificationStepDefinition("knowledge-sync-ready", "Knowledge Sync ready", "Confirm product and policy content is synced.", "Run Knowledge Sync from merchant admin."),
                    new VerificationStepDefinition("free-ai-search-boundary", "Free AI-search boundary", "Confirm AI search is available and forbidden governed surfaces are absent.", "Remove order/account/governed action surfaces from Free configuration.")
                )
            ),
            new VerificationPack(
                "tier-boundary",
                "Tier boundary",
                "Checks Free and Starter launch truth boundaries against store-configured surfaces.",
                List.of(
                    new VerificationStepDefinition("free-ai-search-boundary", "Free AI-search boundary", "Confirm AI search remains the Free surface.", "Keep Free limited to AI search."),
                    new VerificationStepDefinition("starter-read-only-boundary", "Starter read-only boundary", "Confirm Starter surfaces remain read-only and exclude order lookup.", "Keep governed actions gated outside Starter."),
                    new VerificationStepDefinition("evidence-contract-safe", "Evidence contract safe", "Confirm generated evidence is merchant-safe.", "Regenerate evidence through the partner bundle exporter.")
                )
            )
        );
    }

    private VerificationPack defaultVerificationPack() {
        return verificationPacks().get(0);
    }

    private VerificationPack verificationPack(String packId) {
        return verificationPacks().stream()
            .filter(pack -> pack.id().equals(packId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Verification pack is not available."));
    }

    private PartnerVerificationPackSummary toPackSummary(VerificationPack pack, List<PartnerVerificationStepSummary> steps) {
        return new PartnerVerificationPackSummary(pack.id(), pack.name(), pack.description(), steps);
    }

    private List<PartnerVerificationStepSummary> evaluatePack(VerificationPack pack,
                                                              PartnerStoreAssignmentEntity assignment,
                                                              PartnerShopifyStoreReadModel store,
                                                              Instant checkedAt) {
        List<PartnerVerificationStepSummary> result = new ArrayList<>();
        int order = 1;
        for (VerificationStepDefinition step : pack.steps()) {
            result.add(evaluateStep(step, assignment, store, checkedAt, order++));
        }
        List<String> configuredSurfaces = storeConfiguredSurfaces(store);
        List<String> catalogSurfaces = catalogSource.listCatalog().stream()
            .map(PartnerCatalogEntrySummary::surfaceId)
            .toList();
        for (String surface : configuredSurfaces) {
            if (!catalogSurfaces.contains(surface) || FORBIDDEN_PARTNER_SURFACES.contains(surface)) {
                continue;
            }
            result.add(new PartnerVerificationStepSummary(
                "surface-" + surface,
                titleFromId(surface) + " surface",
                statusForSurface(store, surface),
                "Surface " + surface + " is configured for " + assignment.getShopDomain() + ".",
                "Confirm the app block placement in Shopify admin if the surface is not visible.",
                List.of("surface=" + surface, "shop=" + assignment.getShopDomain()),
                checkedAt,
                order++
            ));
        }
        return result;
    }

    private PartnerVerificationStepSummary evaluateStep(VerificationStepDefinition step,
                                                        PartnerStoreAssignmentEntity assignment,
                                                        PartnerShopifyStoreReadModel store,
                                                        Instant checkedAt,
                                                        int sortOrder) {
        String status;
        String message;
        List<String> evidence;
        switch (step.id()) {
            case "companion-installed" -> {
                status = "INSTALLED".equalsIgnoreCase(store.installStatus()) ? "PASSED" : "BLOCKED";
                message = status.equals("PASSED") ? "Shopify Companion install is active." : "Shopify Companion is not installed.";
                evidence = List.of("installStatus=" + safeEvidenceValue(store.installStatus()));
            }
            case "knowledge-sync-ready" -> {
                status = "SYNCED".equalsIgnoreCase(store.knowledgeSyncStatus()) ? "PASSED" : "BLOCKED";
                message = status.equals("PASSED") ? "Knowledge Sync is synced." : "Knowledge Sync needs merchant or operator attention.";
                evidence = List.of("knowledgeSyncStatus=" + safeEvidenceValue(store.knowledgeSyncStatus()));
            }
            case "source-readiness-ready" -> {
                status = "READY".equalsIgnoreCase(store.sourceReadinessStatus()) ? "PASSED" : "BLOCKED";
                message = status.equals("PASSED") ? "Enabled store sources are ready." : "Store source readiness is not ready.";
                evidence = List.of("sourceReadinessStatus=" + safeEvidenceValue(store.sourceReadinessStatus()));
            }
            case "storefront-widget-enabled" -> {
                status = "ENABLED".equalsIgnoreCase(store.widgetStatus()) ? "PASSED" : "BLOCKED";
                message = status.equals("PASSED") ? "Storefront widget/app embed is enabled." : "Storefront widget/app embed is not enabled.";
                evidence = List.of("widgetStatus=" + safeEvidenceValue(store.widgetStatus()));
            }
            case "free-ai-search-boundary" -> {
                boolean hasSearch = storeConfiguredSurfaces(store).contains("ai-search");
                boolean hasForbidden = containsForbiddenSurface(storeConfiguredSurfaces(store));
                status = hasSearch && !hasForbidden ? "PASSED" : "FAILED";
                message = status.equals("PASSED")
                    ? "AI search is configured and forbidden governed surfaces are absent."
                    : "AI search boundary failed because search is missing or a forbidden governed surface is configured.";
                evidence = List.of("enabledSurfaces=" + String.join(",", storeConfiguredSurfaces(store)));
            }
            case "starter-read-only-boundary" -> {
                boolean hasForbidden = containsForbiddenSurface(storeConfiguredSurfaces(store));
                status = hasForbidden ? "FAILED" : "PASSED";
                message = status.equals("PASSED")
                    ? "Starter configured surfaces are read-only and do not include order lookup."
                    : "Starter boundary failed because a governed or order surface is configured.";
                evidence = List.of("forbiddenSurfacesPresent=" + hasForbidden);
            }
            case "evidence-contract-safe" -> {
                status = "PASSED";
                message = "Evidence bundle output is generated from partner-safe store, run, and support summary fields.";
                evidence = List.of("redaction=merchant-safe", "shop=" + assignment.getShopDomain());
            }
            default -> {
                status = "BLOCKED";
                message = "Verification step requires manual partner review.";
                evidence = List.of();
            }
        }
        return stepSummary(step, status, message, status.equals("PASSED") ? null : step.suggestedFix(), evidence, checkedAt, sortOrder);
    }

    private PartnerVerificationStepSummary stepSummary(VerificationStepDefinition step,
                                                       String status,
                                                       String message,
                                                       String suggestedFix,
                                                       List<String> evidence,
                                                       Instant checkedAt) {
        return stepSummary(step, status, message, suggestedFix, evidence, checkedAt, 0);
    }

    private PartnerVerificationStepSummary stepSummary(VerificationStepDefinition step,
                                                       String status,
                                                       String message,
                                                       String suggestedFix,
                                                       List<String> evidence,
                                                       Instant checkedAt,
                                                       int sortOrder) {
        return new PartnerVerificationStepSummary(step.id(), step.label(), status, message, suggestedFix, evidence, checkedAt, sortOrder);
    }

    private String statusForSurface(PartnerShopifyStoreReadModel store, String surface) {
        if (!"READY".equalsIgnoreCase(store.sourceReadinessStatus())) {
            return "BLOCKED";
        }
        if (!"ENABLED".equalsIgnoreCase(store.widgetStatus())) {
            return "BLOCKED";
        }
        return FORBIDDEN_PARTNER_SURFACES.contains(surface) ? "FAILED" : "PASSED";
    }

    private boolean containsForbiddenSurface(List<String> surfaces) {
        return surfaces.stream().anyMatch(FORBIDDEN_PARTNER_SURFACES::contains);
    }

    private String aggregateVerificationStatus(List<PartnerVerificationStepSummary> steps) {
        if (steps.stream().anyMatch(step -> "FAILED".equals(step.status()))) {
            return "FAILED";
        }
        if (steps.stream().anyMatch(step -> "BLOCKED".equals(step.status()))) {
            return "BLOCKED";
        }
        if (steps.stream().anyMatch(step -> !"PASSED".equals(step.status()))) {
            return "PARTIAL";
        }
        return "PASSED";
    }

    private Map<String, Object> verificationSummaryMap(PartnerStoreAssignmentEntity assignment,
                                                       PartnerShopifyStoreReadModel store,
                                                       String status,
                                                       List<PartnerVerificationStepSummary> steps) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("shopDomain", assignment.getShopDomain());
        summary.put("storeConnectionId", assignment.getStoreConnectionId());
        summary.put("status", status);
        summary.put("knowledgeSyncStatus", store.knowledgeSyncStatus());
        summary.put("sourceReadinessStatus", store.sourceReadinessStatus());
        summary.put("widgetStatus", store.widgetStatus());
        summary.put("enabledSurfaces", storeConfiguredSurfaces(store));
        summary.put("passedSteps", steps.stream().filter(step -> "PASSED".equals(step.status())).count());
        summary.put("failedSteps", steps.stream().filter(step -> "FAILED".equals(step.status())).count());
        summary.put("blockedSteps", steps.stream().filter(step -> "BLOCKED".equals(step.status())).count());
        summary.put("redaction", "merchant-safe");
        return summary;
    }

    private PartnerVerificationRunStepEntity toRunStepEntity(String runId, PartnerVerificationStepSummary step) {
        PartnerVerificationRunStepEntity entity = new PartnerVerificationRunStepEntity();
        entity.setId(id("pvrs"));
        entity.setRunId(runId);
        entity.setStepId(step.stepId());
        entity.setStepLabel(step.label());
        entity.setStatus(step.status());
        entity.setPartnerSafeMessage(step.partnerSafeMessage());
        entity.setSuggestedFix(step.suggestedFix());
        entity.setEvidenceJson(writeJson(step.evidence()));
        entity.setSortOrder(step.sortOrder());
        entity.setCheckedAt(firstNonNull(step.checkedAt(), Instant.now()));
        return entity;
    }

    private PartnerVerificationRunEntity createManualVerificationRun(PartnerContext context,
                                                                     PartnerStoreAssignmentEntity assignment,
                                                                     Instant now) {
        PartnerVerificationRunEntity run = new PartnerVerificationRunEntity();
        run.setId(id("pvr"));
        run.setPartnerAccountId(context.account().getId());
        run.setStoreAssignmentId(assignment.getId());
        run.setPackId("manual-store-checklist");
        run.setPackName("Manual store checklist");
        run.setStatus("PARTIAL");
        run.setSummaryJson(writeJson(Map.of("manual", true)));
        run.setStartedByMemberId(context.member().getId());
        run.setStartedAt(now);
        run.setCompletedAt(now);
        return verificationRunRepository.save(run);
    }

    private PartnerVerificationRunEntity requireVerificationRun(String accountId, String runId) {
        return verificationRunRepository.findByIdAndPartnerAccountId(clean(runId, "runId"), accountId)
            .orElseThrow(() -> new PartnerForbiddenException("Verification run is not available to this partner."));
    }

    private PartnerVerificationRunSummary toVerificationRunSummary(PartnerVerificationRunEntity run,
                                                                  List<PartnerVerificationRunStepEntity> steps) {
        List<PartnerVerificationStepSummary> stepSummaries = steps.stream()
            .sorted(Comparator.comparingInt(PartnerVerificationRunStepEntity::getSortOrder))
            .map(this::toVerificationStepSummary)
            .toList();
        String shopDomain = storeAssignmentRepository.findById(run.getStoreAssignmentId())
            .map(PartnerStoreAssignmentEntity::getShopDomain)
            .orElse(null);
        return new PartnerVerificationRunSummary(
            run.getId(),
            run.getStoreAssignmentId(),
            shopDomain,
            run.getPackId(),
            run.getPackName(),
            run.getStatus(),
            stepSummaries.size(),
            (int) stepSummaries.stream().filter(step -> "PASSED".equals(step.status())).count(),
            (int) stepSummaries.stream().filter(step -> "FAILED".equals(step.status())).count(),
            (int) stepSummaries.stream().filter(step -> "BLOCKED".equals(step.status())).count(),
            run.getEvidenceBundleId(),
            readMap(run.getSummaryJson()),
            run.getStartedAt(),
            run.getCompletedAt(),
            stepSummaries
        );
    }

    private PartnerVerificationStepSummary toVerificationStepSummary(PartnerVerificationRunStepEntity entity) {
        return new PartnerVerificationStepSummary(
            entity.getStepId(),
            entity.getStepLabel(),
            entity.getStatus(),
            entity.getPartnerSafeMessage(),
            entity.getSuggestedFix(),
            readList(entity.getEvidenceJson()),
            entity.getCheckedAt(),
            entity.getSortOrder()
        );
    }

    private PartnerEvidenceBundleEntity createEvidenceBundleForRun(PartnerContext context,
                                                                   PartnerStoreAssignmentEntity assignment,
                                                                   PartnerShopifyStoreReadModel store,
                                                                   PartnerVerificationRunEntity run,
                                                                   List<PartnerVerificationStepSummary> steps,
                                                                   String bundleKind) {
        Map<String, Object> summary = verificationSummaryMap(assignment, store, run.getStatus(), steps);
        summary.put("verificationRunId", run.getId());
        summary.put("packId", run.getPackId());
        summary.put("packName", run.getPackName());
        List<String> attachments = List.of(
            "manifest.json",
            "verification-run.json",
            "merchant-safe-summary.md"
        );
        return saveEvidenceBundle(
            context,
            assignment,
            run.getId(),
            run.getPackName() + " evidence - " + assignment.getShopDomain(),
            normalizeBundleKind(bundleKind),
            summary,
            attachments,
            Instant.now()
        );
    }

    private PartnerEvidenceBundleEntity createEvidenceBundleForStore(PartnerContext context,
                                                                     PartnerStoreAssignmentEntity assignment,
                                                                     PartnerShopifyStoreReadModel store,
                                                                     String bundleKind) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("shopDomain", assignment.getShopDomain());
        summary.put("storeConnectionId", assignment.getStoreConnectionId());
        summary.put("merchantName", firstNonBlank(assignment.getMerchantName(), store.displayName(), assignment.getShopDomain()));
        summary.put("knowledgeSyncStatus", store.knowledgeSyncStatus());
        summary.put("sourceReadinessStatus", store.sourceReadinessStatus());
        summary.put("widgetStatus", store.widgetStatus());
        summary.put("enabledSourceCategories", store.enabledSourceCategories());
        summary.put("enabledSurfaces", storeConfiguredSurfaces(store));
        summary.put("redaction", "merchant-safe");
        return saveEvidenceBundle(
            context,
            assignment,
            null,
            titleFromId(bundleKind) + " - " + assignment.getShopDomain(),
            normalizeBundleKind(bundleKind),
            summary,
            List.of("manifest.json", "store-readiness.json", "merchant-safe-summary.md"),
            Instant.now()
        );
    }

    private PartnerEvidenceBundleEntity saveEvidenceBundle(PartnerContext context,
                                                           PartnerStoreAssignmentEntity assignment,
                                                           String verificationRunId,
                                                           String name,
                                                           String kind,
                                                           Map<String, Object> summary,
                                                           List<String> attachments,
                                                           Instant now) {
        PartnerEvidenceBundleEntity bundle = new PartnerEvidenceBundleEntity();
        bundle.setId(id("peb"));
        bundle.setPartnerAccountId(context.account().getId());
        bundle.setStoreAssignmentId(assignment.getId());
        bundle.setGeneratedByMemberId(context.member().getId());
        bundle.setVerificationRunId(verificationRunId);
        bundle.setBundleName(name);
        bundle.setBundleKind(kind);
        bundle.setStatus("READY");
        bundle.setSummaryJson(writeJson(summary));
        bundle.setAttachmentsJson(writeJson(attachments));
        bundle.setGeneratedAt(now);
        bundle.setExpiresAt(now.plusSeconds(60L * 60L * 24L * 30L));
        return evidenceBundleRepository.save(bundle);
    }

    private PartnerEvidenceBundleEntity requireEvidenceBundle(String accountId, String bundleId) {
        return evidenceBundleRepository.findByIdAndPartnerAccountId(clean(bundleId, "bundleId"), accountId)
            .orElseThrow(() -> new PartnerForbiddenException("Evidence bundle is not available to this partner."));
    }

    private PartnerEvidenceBundleSummary toEvidenceBundleSummary(PartnerEvidenceBundleEntity entity) {
        String shopDomain = StringUtils.hasText(entity.getStoreAssignmentId())
            ? storeAssignmentRepository.findById(entity.getStoreAssignmentId()).map(PartnerStoreAssignmentEntity::getShopDomain).orElse(null)
            : null;
        String generatedBy = StringUtils.hasText(entity.getGeneratedByMemberId())
            ? memberRepository.findById(entity.getGeneratedByMemberId()).map(member -> firstNonBlank(member.getDisplayName(), member.getEmail())).orElse(null)
            : null;
        return new PartnerEvidenceBundleSummary(
            entity.getId(),
            entity.getStoreAssignmentId(),
            shopDomain,
            entity.getVerificationRunId(),
            entity.getBundleName(),
            entity.getBundleKind(),
            entity.getStatus(),
            generatedBy,
            readMap(entity.getSummaryJson()),
            readList(entity.getAttachmentsJson()),
            entity.getGeneratedAt(),
            entity.getExpiresAt()
        );
    }

    private byte[] evidenceBundleZip(PartnerEvidenceBundleEntity bundle) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("bundleId", bundle.getId());
                manifest.put("bundleName", bundle.getBundleName());
                manifest.put("bundleKind", bundle.getBundleKind());
                manifest.put("status", bundle.getStatus());
                manifest.put("generatedAt", bundle.getGeneratedAt().toString());
                manifest.put("expiresAt", bundle.getExpiresAt() == null ? null : bundle.getExpiresAt().toString());
                manifest.put("redaction", "merchant-safe");
                writeZipEntry(zip, "manifest.json", writeJson(manifest));
                writeZipEntry(zip, "summary.json", bundle.getSummaryJson());
                writeZipEntry(zip, "attachments.json", bundle.getAttachmentsJson());
                writeZipEntry(zip, "merchant-safe-summary.md", evidenceMarkdown(bundle));
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not export evidence bundle.");
        }
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String evidenceMarkdown(PartnerEvidenceBundleEntity bundle) {
        Map<String, Object> summary = readMap(bundle.getSummaryJson());
        return "# " + bundle.getBundleName() + "\n\n"
            + "- Status: " + bundle.getStatus() + "\n"
            + "- Kind: " + bundle.getBundleKind() + "\n"
            + "- Generated: " + bundle.getGeneratedAt() + "\n"
            + "- Shop: " + summary.getOrDefault("shopDomain", "store scoped") + "\n"
            + "- Redaction: merchant-safe; excludes secrets, provider data, runtime internals, raw diagnostics, and operator-only notes.\n\n"
            + "## Summary\n\n```json\n" + bundle.getSummaryJson() + "\n```\n";
    }

    private List<String> validatedEvidenceBundleIds(String accountId, String storeAssignmentId, List<String> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) {
            return List.of();
        }
        return bundleIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .peek(bundleId -> {
                PartnerEvidenceBundleEntity bundle = requireEvidenceBundle(accountId, bundleId);
                if (StringUtils.hasText(storeAssignmentId) && StringUtils.hasText(bundle.getStoreAssignmentId()) && !storeAssignmentId.equals(bundle.getStoreAssignmentId())) {
                    throw new PartnerForbiddenException("Evidence bundle is not scoped to this store.");
                }
            })
            .toList();
    }

    private List<PartnerTemplateSummary> templates() {
        return List.of(
            template("fashion-apparel-starter", "Fashion/apparel Starter playbook", "VERTICAL_PLAYBOOK", "Fashion/apparel", List.of("ai-search", "product-faq", "product-insight", "policy-strip"),
                "Use this playbook for sizing, fit, care, review, and policy-heavy apparel storefronts. Keep all shopper guidance read-only unless the merchant is on an approved governed-action tier.",
                List.of("Merchant controls tier and billing.", "Store has product, policy, and review/source coverage.", "No order lookup is exposed in Starter."),
                List.of("Confirm Companion install", "Enable AI search and product FAQ", "Sync products, policies, and pages", "Place product insight block on product template", "Run Starter launch readiness verification", "Export launch evidence")),
            template("electronics-comparison-starter", "Electronics comparison playbook", "VERTICAL_PLAYBOOK", "Electronics", List.of("ai-search", "comparison", "product-insight", "policy-strip"),
                "Use this playbook for specification, compatibility, and comparison-heavy electronics storefronts.",
                List.of("Comparable product attributes are present.", "Compatibility claims must come from merchant-provided content.", "Checkout and cart updates remain outside Starter."),
                List.of("Confirm specification source coverage", "Enable comparison surface", "Add compatibility notes to product content", "Run tier boundary verification", "Export verification evidence")),
            template("health-beauty-safe-claims", "Health/beauty safe-claims playbook", "VERTICAL_PLAYBOOK", "Health/beauty", List.of("ai-search", "product-faq", "contextual-pill", "policy-strip"),
                "Use this playbook for ingredient, routine, use-case, and policy questions while avoiding unsupported medical or regulated claims.",
                List.of("Claims must be grounded in merchant content.", "No diagnosis, prescription, or account/order support is presented.", "Escalate unsafe content gaps before launch."),
                List.of("Review product and ingredient source data", "Enable FAQ and contextual pill", "Run Knowledge Sync readiness verification", "Capture safe-claims evidence", "Escalate unsupported claim gaps")),
            template("home-furniture-launch", "Home/furniture launch checklist", "LAUNCH_CHECKLIST", "Home/furniture", List.of("ai-search", "comparison", "product-faq", "policy-strip"),
                "Use this checklist for dimensions, materials, delivery, return, and room-fit guidance.",
                List.of("Dimension/material data is available.", "Delivery/return policy pages are current.", "No checkout or order mutation is exposed."),
                List.of("Confirm dimensions and material fields", "Sync policy pages", "Enable product FAQ and comparison", "Run Starter launch readiness verification", "Export launch packet")),
            template("support-handoff-standard", "Standard support handoff", "SUPPORT_HANDOFF", "All", List.of("ai-search", "read-only-chat"),
                "Use this handoff when a partner needs to escalate setup, verification, or evidence blockers to LoomAI support.",
                List.of("Partner-visible replies must stay product-safe.", "Operator-only diagnostics are not included in merchant exports."),
                List.of("Attach verification run or evidence bundle", "Describe reproduction steps", "Record expected and actual behavior", "Set impact and next action", "Submit escalation"))
        );
    }

    private PartnerTemplateSummary template(String id,
                                            String name,
                                            String category,
                                            String vertical,
                                            List<String> surfaceIds,
                                            String body,
                                            List<String> assumptions,
                                            List<String> checklist) {
        return new PartnerTemplateSummary(id, name, category, vertical, surfaceIds, body, assumptions, checklist, TEMPLATE_UPDATED_AT);
    }

    private PartnerTemplateSummary requireTemplate(String templateId) {
        return templates().stream()
            .filter(template -> template.id().equals(templateId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Template is not available."));
    }

    private PartnerTemplateApplicationSummary toTemplateApplicationSummary(PartnerTemplateApplicationEntity entity) {
        String shopDomain = StringUtils.hasText(entity.getStoreAssignmentId())
            ? storeAssignmentRepository.findById(entity.getStoreAssignmentId()).map(PartnerStoreAssignmentEntity::getShopDomain).orElse(null)
            : null;
        return new PartnerTemplateApplicationSummary(
            entity.getId(),
            entity.getTemplateId(),
            entity.getTemplateName(),
            entity.getCategory(),
            entity.getStoreAssignmentId(),
            shopDomain,
            entity.getStatus(),
            readList(entity.getChecklistJson()),
            readList(entity.getAssumptionsJson()),
            entity.getAppliedAt(),
            entity.getUpdatedAt()
        );
    }

    private PartnerStoreNoteSummary toStoreNoteSummary(PartnerStoreNoteEntity entity) {
        String author = memberRepository.findById(entity.getCreatedByMemberId())
            .map(member -> firstNonBlank(member.getDisplayName(), member.getEmail()))
            .orElse("Partner member");
        return new PartnerStoreNoteSummary(
            entity.getId(),
            entity.getStoreAssignmentId(),
            author,
            entity.getBodyMarkdown(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private PartnerShopifyStoreReadModel storeForAssignment(PartnerStoreAssignmentEntity assignment) {
        return storeAccessGateway.findByStoreConnectionId(assignment.getStoreConnectionId())
            .or(() -> storeAccessGateway.findByShopDomain(assignment.getShopDomain()))
            .orElse(new PartnerShopifyStoreReadModel(
                assignment.getStoreConnectionId(),
                assignment.getShopDomain(),
                assignment.getMerchantName(),
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                assignment.getUpdatedAt(),
                assignment.getUpdatedAt(),
                List.of(),
                List.of()
            ));
    }

    private String normalizeStepStatus(String status) {
        String normalized = clean(status, "status").toUpperCase(Locale.ROOT);
        if (!List.of("PASSED", "FAILED", "BLOCKED", "PARTIAL").contains(normalized)) {
            throw new IllegalArgumentException("Verification step status is not supported.");
        }
        return normalized;
    }

    private String normalizeBundleKind(String kind) {
        String normalized = clean(kind, "bundleKind").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!List.of("LAUNCH_PACKET", "VERIFICATION_PACK", "SUPPORT_BUNDLE", "LIFECYCLE_PACKET").contains(normalized)) {
            throw new IllegalArgumentException("Evidence bundle kind is not supported.");
        }
        return normalized;
    }

    private String normalizePartnerRole(String role) {
        String normalized = clean(role, "role").toUpperCase(Locale.ROOT);
        if (!List.of("PARTNER_ADMIN", "PARTNER_IMPLEMENTER", "PARTNER_DEVELOPER", "PARTNER_SUPPORT").contains(normalized)) {
            throw new IllegalArgumentException("Partner role is not supported.");
        }
        return normalized;
    }

    private String normalizeMemberStatus(String status) {
        String normalized = clean(status, "status").toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "SUSPENDED", "REVOKED").contains(normalized)) {
            throw new IllegalArgumentException("Partner member status is not supported.");
        }
        return normalized;
    }

    private void requireWorkspaceAdmin(PartnerContext context) {
        if (!PlatformRole.PARTNER_ADMIN.name().equals(context.member().getRole())) {
            throw new PartnerForbiddenException("Partner admin permission is required.");
        }
    }

    private PartnerContext requireProvisionedContext() {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        if (!principal.provisioned()) {
            throw new PartnerForbiddenException("Complete partner signup before using the partner workspace.");
        }
        PartnerMemberEntity member = memberRepository.findById(principal.partnerMemberId())
            .orElseThrow(() -> new PartnerForbiddenException("Partner member was not found."));
        ensureMemberActive(member);
        PartnerAccountEntity account = requireActiveAccount(member.getPartnerAccountId());
        return new PartnerContext(account, member);
    }

    private void ensureMemberActive(PartnerMemberEntity member) {
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new PartnerForbiddenException("Partner member is not active.");
        }
    }

    private PartnerAccountEntity requireActiveAccount(String accountId) {
        PartnerAccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> new PartnerForbiddenException("Partner account was not found."));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new PartnerForbiddenException("Partner account is not active.");
        }
        return account;
    }

    private PartnerClientImplementationRequestEntity requireImplementation(String accountId, String requestId) {
        return implementationRequestRepository.findByIdAndPartnerAccountId(requestId, accountId)
            .orElseThrow(() -> new PartnerForbiddenException("Client implementation request is not available to this partner."));
    }

    private PartnerStoreAccessRequestEntity requireMerchantAccessRequest(String requestId, String shopDomain) {
        return storeAccessRequestRepository.findByIdAndShopDomainIgnoreCase(requestId, normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new IllegalArgumentException("Partner access request was not found for this shop."));
    }

    private PartnerStoreAssignmentEntity requireActiveAssignmentForAccessRequest(PartnerStoreAccessRequestEntity accessRequest) {
        Optional<PartnerStoreAssignmentEntity> byConnection = StringUtils.hasText(accessRequest.getStoreConnectionId())
            ? storeAssignmentRepository.findByPartnerAccountIdAndStoreConnectionId(accessRequest.getPartnerAccountId(), accessRequest.getStoreConnectionId())
            : Optional.empty();
        PartnerStoreAssignmentEntity assignment = byConnection
            .or(() -> storeAssignmentRepository.findByPartnerAccountIdAndShopDomainIgnoreCase(accessRequest.getPartnerAccountId(), accessRequest.getShopDomain()))
            .orElseThrow(() -> new IllegalArgumentException("Active partner store assignment was not found for this access request."));
        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new IllegalArgumentException("Partner store assignment is not active.");
        }
        return assignment;
    }

    private PartnerShopifyStoreReadModel requireEligibleInstalledStore(String accountId, String storeConnectionId) {
        PartnerShopifyStoreReadModel store = storeAccessGateway.findByStoreConnectionId(clean(storeConnectionId, "storeConnectionId"))
            .orElseThrow(() -> new IllegalArgumentException("Installed Shopify store was not found."));
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw new IllegalArgumentException("Shopify store must be installed before a partner implementation can be requested.");
        }
        if (!isStoreEligibleForImplementation(accountId, store)) {
            throw new IllegalArgumentException("Shopify store already has an active or pending partner implementation for this workspace.");
        }
        return store;
    }

    private PartnerShopifyStoreReadModel requireInstalledStore(PartnerStoreAccessRequestEntity accessRequest) {
        Optional<PartnerShopifyStoreReadModel> byConnection = storeAccessGateway.findByStoreConnectionId(accessRequest.getStoreConnectionId());
        PartnerShopifyStoreReadModel store = byConnection
            .or(() -> storeAccessGateway.findByShopDomain(accessRequest.getShopDomain()))
            .orElseThrow(() -> new IllegalArgumentException("Installed Shopify store was not found for this access request."));
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw new IllegalArgumentException("Shopify store must still be installed before partner access can be approved.");
        }
        return store;
    }

    private boolean isStoreEligibleForImplementation(String accountId, PartnerShopifyStoreReadModel store) {
        if (store == null || !StringUtils.hasText(store.storeConnectionId())) {
            return false;
        }
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            return false;
        }
        return !storeAssignmentRepository.existsByPartnerAccountIdAndStoreConnectionIdAndStatus(
            accountId,
            store.storeConnectionId(),
            "ACTIVE"
        ) && !storeAccessRequestRepository.existsByPartnerAccountIdAndStoreConnectionIdAndStatusIn(
            accountId,
            store.storeConnectionId(),
            List.of("WAITING_ON_MERCHANT")
        ) && !implementationRequestRepository.existsByPartnerAccountIdAndStoreConnectionIdAndStatusIn(
            accountId,
            store.storeConnectionId(),
            List.of("WAITING_ON_MERCHANT")
        );
    }

    private PartnerStoreAccessRequestEntity createAccessRequestForImplementation(PartnerContext context,
                                                                                 PartnerClientImplementationRequestEntity implementation,
                                                                                 Instant now) {
        String approvalCode = approvalCode();
        Instant expiresAt = now.plus(authProperties.merchantApprovalTtl());
        String approvalUrl = authProperties.partnerAppUrl().replaceAll("/+$", "")
            + "/merchant/partner-access/" + approvalCode;

        PartnerStoreAccessRequestEntity accessRequest = new PartnerStoreAccessRequestEntity();
        accessRequest.setId(id("psar"));
        accessRequest.setPartnerAccountId(context.account().getId());
        accessRequest.setImplementationRequestId(implementation.getId());
        accessRequest.setRequestedByMemberId(context.member().getId());
        accessRequest.setStoreConnectionId(implementation.getStoreConnectionId());
        accessRequest.setShopDomain(implementation.getShopDomain());
        accessRequest.setRequestedScope(FULL_STORE_ACCESS_SCOPE);
        accessRequest.setStatus("WAITING_ON_MERCHANT");
        accessRequest.setApprovalCode(approvalCode);
        accessRequest.setApprovalUrl(approvalUrl);
        accessRequest.setExpiresAt(expiresAt);
        accessRequest.setCreatedAt(now);
        accessRequest.setUpdatedAt(now);
        return storeAccessRequestRepository.save(accessRequest);
    }

    private PartnerStoreAssignmentEntity requireActiveAssignment(String accountId, String storeId) {
        PartnerStoreAssignmentEntity assignment = storeAssignmentRepository.findByIdAndPartnerAccountId(storeId, accountId)
            .or(() -> storeAssignmentRepository.findByPartnerAccountIdAndShopDomainIgnoreCase(accountId, storeId))
            .orElseThrow(() -> new PartnerForbiddenException("Store is not assigned to this partner."));
        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new PartnerForbiddenException("Store assignment is not active.");
        }
        return assignment;
    }

    private void requireAssignmentCapability(PartnerStoreAssignmentEntity assignment, String capability) {
        if (!readList(assignment.getPermissionsJson()).contains(capability)) {
            throw new PartnerForbiddenException("Partner assignment does not include " + capability + ".");
        }
    }

    private void requireInstalledStoreForProductControl(PartnerStoreAssignmentEntity assignment) {
        PartnerShopifyStoreReadModel store = storeForAssignment(assignment);
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw new PartnerForbiddenException("Shopify Companion must be installed before partner product controls can be changed.");
        }
    }

    private PartnerMaxWidgetContext requirePartnerMaxWidgetContext(String storeId) {
        PartnerContext context = requireProvisionedContext();
        PartnerStoreAssignmentEntity assignment = requireActiveAssignment(context.account().getId(), storeId);
        requireAssignmentCapability(assignment, "PRODUCT_CONFIG_READ");
        ShopifyStoreConnectionSummary store = shopifyStoreConnectionService.getConnection(assignment.getShopDomain());
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw new ResponseStatusException(CONFLICT, "Shopify Companion must be installed before live Max widget testing.");
        }
        if (!StringUtils.hasText(store.deploymentId())) {
            throw new ResponseStatusException(CONFLICT, "Assigned store is not linked to a Platform deployment.");
        }
        return new PartnerMaxWidgetContext(context, assignment, store.deploymentId().trim());
    }

    private PartnerSupportEscalationEntity requireEscalation(String accountId, String escalationId) {
        return escalationRepository.findByIdAndPartnerAccountId(escalationId, accountId)
            .orElseThrow(() -> new PartnerForbiddenException("Escalation is not available to this partner."));
    }

    private PartnerStoreSummary toStoreSummary(PartnerStoreAssignmentEntity assignment) {
        Optional<PartnerShopifyStoreReadModel> store = storeAccessGateway.findByStoreConnectionId(assignment.getStoreConnectionId())
            .or(() -> storeAccessGateway.findByShopDomain(assignment.getShopDomain()));
        String knowledgeSync = store.map(PartnerShopifyStoreReadModel::knowledgeSyncStatus).orElse("UNKNOWN");
        String readiness = store.map(PartnerShopifyStoreReadModel::sourceReadinessStatus).orElse("WAITING_ON_MERCHANT");
        String widget = store.map(PartnerShopifyStoreReadModel::widgetStatus).orElse("UNKNOWN");
        return new PartnerStoreSummary(
            assignment.getId(),
            assignment.getStoreConnectionId(),
            assignment.getShopDomain(),
            firstNonBlank(assignment.getMerchantName(), store.map(PartnerShopifyStoreReadModel::displayName).orElse(null), assignment.getShopDomain()),
            "Merchant configured",
            statusFor(readiness, widget, assignment.getStatus()),
            store.map(PartnerShopifyStoreReadModel::enabledSurfaces).orElse(List.of()),
            knowledgeSync,
            readiness,
            blockerFor(knowledgeSync, readiness, widget),
            firstNonNull(store.map(PartnerShopifyStoreReadModel::lastSyncAt).orElse(null), assignment.getUpdatedAt()),
            assignment.getStatus(),
            assignment.getAssignmentSource(),
            assignment.getApprovedBy(),
            assignment.getApprovedAt(),
            assignment.getRevokedAt(),
            assignment.getCreatedAt(),
            assignment.getUpdatedAt(),
            store.map(PartnerShopifyStoreReadModel::installStatus).orElse("UNKNOWN"),
            widget,
            store.map(PartnerShopifyStoreReadModel::lastSyncAt).orElse(null),
            store.map(PartnerShopifyStoreReadModel::lastWebhookAt).orElse(null),
            store.map(PartnerShopifyStoreReadModel::enabledSourceCategories).orElse(List.of()),
            readList(assignment.getPermissionsJson())
        );
    }

    private PartnerProductControlSummary toProductControlSummary(PartnerStoreAssignmentEntity assignment,
                                                                 ShopifyStoreConnectionSummary store) {
        return toProductControlSummary(
            assignment,
            store,
            shopifyStoreSupportProfileService.get(store.shopDomain())
        );
    }

    private PartnerProductControlSummary toProductControlSummary(PartnerStoreAssignmentEntity assignment,
                                                                 ShopifyStoreConnectionSummary store,
                                                                 ShopifyStoreSupportProfileSummary supportProfile) {
        ShopifyStoreWidgetSettingsSummary widgetSettings = widgetSettings(store);
        return new PartnerProductControlSummary(
            assignment.getId(),
            store.id(),
            store.shopDomain(),
            firstNonBlank(assignment.getMerchantName(), store.displayName(), store.shopDomain()),
            assignment.getStatus(),
            store.installStatus(),
            store.widgetStatus(),
            store.syncStatus(),
            store.sourceReadinessStatus(),
            new PartnerProductSourceSettingsSummary(
                store.productsEnabled(),
                store.collectionsEnabled(),
                store.pagesEnabled(),
                store.policiesEnabled(),
                store.articlesEnabled(),
                store.metaobjectsEnabled(),
                enabledSourceCategories(store),
                store.lastSourcePreflightAt(),
                store.lastSyncAt()
            ),
            widgetSettings.enabledSurfaces(),
            widgetSettings,
            supportProfile,
            productControlCapabilities(assignment),
            store.updatedAt()
        );
    }

    private ShopifyStoreWidgetSettingsSummary widgetSettings(ShopifyStoreConnectionSummary store) {
        if (store.widgetDetail() != null && store.widgetDetail().settings() != null) {
            return store.widgetDetail().settings();
        }
        return new ShopifyStoreWidgetSettingsSummary(
            DEFAULT_WIDGET_SETTINGS.launcherLabel(),
            DEFAULT_WIDGET_SETTINGS.welcomeMessage(),
            DEFAULT_WIDGET_SETTINGS.shellModeProfile(),
            DEFAULT_WIDGET_SETTINGS.enabledSurfaces(),
            DEFAULT_WIDGET_SETTINGS.defaultConversationMode(),
            DEFAULT_WIDGET_SETTINGS.allowedConversationModes(),
            DEFAULT_WIDGET_SETTINGS.pageModeMappings()
        );
    }

    private List<String> enabledSourceCategories(ShopifyStoreConnectionSummary store) {
        List<String> categories = new ArrayList<>();
        if (store.productsEnabled()) {
            categories.add("products");
        }
        if (store.collectionsEnabled()) {
            categories.add("collections");
        }
        if (store.pagesEnabled()) {
            categories.add("pages");
        }
        if (store.policiesEnabled()) {
            categories.add("policies");
        }
        if (store.articlesEnabled()) {
            categories.add("articles");
        }
        if (store.metaobjectsEnabled()) {
            categories.add("metaobjects");
        }
        return List.copyOf(categories);
    }

    private List<String> productControlCapabilities(PartnerStoreAssignmentEntity assignment) {
        return readList(assignment.getPermissionsJson()).stream()
            .filter(PARTNER_PRODUCT_CONTROL_CAPABILITIES::contains)
            .toList();
    }

    private boolean partnerVisibleActivity(PartnerActionAuditEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getAction())) {
            return false;
        }
        String action = entity.getAction();
        return action.startsWith("STORE_ACCESS_")
            || action.startsWith("CLIENT_IMPLEMENTATION_")
            || action.startsWith("VERIFICATION_")
            || action.startsWith("EVIDENCE_BUNDLE_")
            || action.startsWith("TEMPLATE_")
            || action.startsWith("STORE_NOTE_")
            || action.startsWith("SUPPORT_")
            || action.startsWith("PRODUCT_")
            || action.startsWith("KNOWLEDGE_")
            || action.startsWith("PARTNER_MAX_WIDGET_");
    }

    private PartnerActivityEventSummary toActivitySummary(PartnerActionAuditEntity entity) {
        return new PartnerActivityEventSummary(
            entity.getId(),
            entity.getAction(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getResult(),
            partnerSafeDetails(readMap(entity.getDetailsJson())),
            entity.getCreatedAt()
        );
    }

    private Map<String, Object> partnerSafeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("key")
                || normalized.contains("password")
                || normalized.contains("credential")) {
                return;
            }
            safe.put(key, value);
        });
        return safe;
    }

    private String statusFor(String readiness, String widget, String assignmentStatus) {
        if (!"ACTIVE".equals(assignmentStatus)) {
            return "REVOKED";
        }
        if ("READY".equalsIgnoreCase(readiness) && "ENABLED".equalsIgnoreCase(widget)) {
            return "READY";
        }
        if ("CHECK_FAILED".equalsIgnoreCase(readiness)) {
            return "BLOCKED";
        }
        return "NEEDS_SETUP";
    }

    private String blockerFor(String knowledgeSync, String readiness, String widget) {
        if (!"SYNCED".equalsIgnoreCase(knowledgeSync)) {
            return "Knowledge Sync needs attention.";
        }
        if (!"READY".equalsIgnoreCase(readiness)) {
            return "Source readiness needs review.";
        }
        if (!"ENABLED".equalsIgnoreCase(widget)) {
            return "Storefront surfaces need activation.";
        }
        return "No active blocker.";
    }

    private PartnerEligibleStoreSummary toEligibleStoreSummary(PartnerShopifyStoreReadModel store) {
        return new PartnerEligibleStoreSummary(
            store.storeConnectionId(),
            store.shopDomain(),
            firstNonBlank(store.displayName(), store.shopDomain()),
            store.installStatus(),
            store.knowledgeSyncStatus(),
            store.sourceReadinessStatus(),
            store.widgetStatus(),
            firstNonNull(store.lastWebhookAt(), store.lastSyncAt()),
            store.enabledSourceCategories(),
            store.enabledSurfaces()
        );
    }

    private MerchantPartnerAccessRequestSummary toMerchantAccessRequestSummary(PartnerStoreAccessRequestEntity accessRequest) {
        PartnerClientImplementationRequestEntity implementation = implementationRequestRepository
            .findById(accessRequest.getImplementationRequestId())
            .orElse(null);
        PartnerAccountEntity account = accountRepository.findById(accessRequest.getPartnerAccountId()).orElse(null);
        String storeConnectionId = firstNonBlank(accessRequest.getStoreConnectionId(), implementation == null ? null : implementation.getStoreConnectionId());
        String assignmentId = findAssignmentForAccessRequest(accessRequest, storeConnectionId)
            .map(PartnerStoreAssignmentEntity::getId)
            .orElse(null);
        return new MerchantPartnerAccessRequestSummary(
            accessRequest.getId(),
            accessRequest.getImplementationRequestId(),
            accessRequest.getPartnerAccountId(),
            account == null ? "Partner workspace" : account.getName(),
            implementation == null ? accessRequest.getShopDomain() : implementation.getClientName(),
            implementation == null ? null : implementation.getContactEmail(),
            storeConnectionId,
            assignmentId,
            accessRequest.getShopDomain(),
            implementation == null ? null : implementation.getRequestedTier(),
            implementation == null ? List.of() : readList(implementation.getRequestedSurfacesJson()),
            implementation == null ? List.of() : readList(implementation.getKnownIntegrationsJson()),
            implementation == null ? null : implementation.getNotes(),
            accessRequest.getRequestedScope(),
            accessRequest.getStatus(),
            accessRequest.getCreatedAt(),
            accessRequest.getExpiresAt(),
            accessRequest.getApprovedAt(),
            accessRequest.getRevokedAt(),
            accessRequest.getUpdatedAt()
        );
    }

    private Optional<PartnerStoreAssignmentEntity> findAssignmentForAccessRequest(PartnerStoreAccessRequestEntity accessRequest,
                                                                                 String storeConnectionId) {
        Optional<PartnerStoreAssignmentEntity> byConnection = StringUtils.hasText(storeConnectionId)
            ? storeAssignmentRepository.findByPartnerAccountIdAndStoreConnectionId(accessRequest.getPartnerAccountId(), storeConnectionId)
            : Optional.empty();
        return byConnection.or(() -> storeAssignmentRepository.findByPartnerAccountIdAndShopDomainIgnoreCase(
            accessRequest.getPartnerAccountId(),
            accessRequest.getShopDomain()
        ));
    }

    private String revokeSourceFlow() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            return "UNKNOWN_REVOKE";
        }
        if (principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return "PLATFORM_ADMIN_REVOKE";
        }
        if (principal.role() == PlatformRole.PLATFORM_OPERATOR) {
            return "PLATFORM_OPERATOR_REVOKE";
        }
        if (principal.role() == PlatformRole.PLATFORM_PRODUCT_SERVICE) {
            return "SHOPIFY_ADMIN_REVOKE";
        }
        return principal.role().name() + "_REVOKE";
    }

    private PartnerClientImplementationSummary toImplementationSummary(PartnerClientImplementationRequestEntity entity) {
        return new PartnerClientImplementationSummary(
            entity.getId(),
            entity.getClientName(),
            entity.getContactEmail(),
            entity.getStoreConnectionId(),
            entity.getShopDomain(),
            entity.getVertical(),
            entity.getRequestedTier(),
            readList(entity.getRequestedSurfacesJson()),
            readList(entity.getKnownIntegrationsJson()),
            entity.getStatus(),
            entity.getApprovalUrl(),
            entity.getApprovalExpiresAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private PartnerSupportEscalationSummary toEscalationSummary(PartnerSupportEscalationEntity entity) {
        String shopDomain = null;
        if (StringUtils.hasText(entity.getStoreAssignmentId())) {
            shopDomain = storeAssignmentRepository.findById(entity.getStoreAssignmentId())
                .map(PartnerStoreAssignmentEntity::getShopDomain)
                .orElse(null);
        }
        return new PartnerSupportEscalationSummary(
            entity.getId(),
            entity.getStoreAssignmentId(),
            shopDomain,
            entity.getTitle(),
            entity.getSeverity(),
            entity.getStatus(),
            entity.getNextAction(),
            entity.getDueAt(),
            entity.getDescription(),
            entity.getResolutionSummary(),
            readList(entity.getEvidenceBundleIdsJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private PartnerSupportReplySummary toReplySummary(PartnerSupportReplyEntity entity) {
        return new PartnerSupportReplySummary(
            entity.getId(),
            entity.getAuthorName(),
            entity.getAuthorRole(),
            entity.getVisibility(),
            entity.getBodyMarkdown(),
            readList(entity.getAttachmentsJson()),
            entity.getCreatedAt()
        );
    }

    private PartnerAccountSummary toAccountSummary(PartnerAccountEntity account) {
        return new PartnerAccountSummary(account.getId(), account.getName(), account.getStatus());
    }

    private PartnerMemberSummary toMemberSummary(PartnerMemberEntity member) {
        return new PartnerMemberSummary(
            member.getId(),
            member.getEmail(),
            member.isEmailVerified(),
            member.getDisplayName(),
            member.getAvatarUrl(),
            member.getRole(),
            member.getStatus()
        );
    }

    private List<String> permissionsFor(String role) {
        if (PlatformRole.PARTNER_ADMIN.name().equals(role)) {
            List<String> permissions = new ArrayList<>(List.of(
                "WORKSPACE_ADMIN",
                "IMPLEMENTATION_CREATE",
                "STORE_READ",
                "ESCALATION_CREATE",
                "ESCALATION_REPLY",
                "CATALOG_READ"
            ));
            permissions.addAll(PARTNER_PRODUCT_CONTROL_CAPABILITIES);
            permissions.addAll(List.of("VERIFICATION_READ", "VERIFICATION_RUN", "EVIDENCE_READ", "EVIDENCE_EXPORT"));
            return List.copyOf(permissions);
        }
        List<String> permissions = new ArrayList<>(List.of(
            "IMPLEMENTATION_CREATE",
            "STORE_READ",
            "ESCALATION_CREATE",
            "ESCALATION_REPLY",
            "CATALOG_READ",
            "PRODUCT_CONFIG_READ"
        ));
        permissions.addAll(List.of("VERIFICATION_READ", "VERIFICATION_RUN", "EVIDENCE_READ", "EVIDENCE_EXPORT"));
        if (PlatformRole.PARTNER_IMPLEMENTER.name().equals(role) || PlatformRole.PARTNER_DEVELOPER.name().equals(role)) {
            permissions.addAll(PARTNER_PRODUCT_CONTROL_CAPABILITIES.stream()
                .filter(permission -> !"PRODUCT_CONFIG_READ".equals(permission))
                .toList());
        }
        if (PlatformRole.PARTNER_SUPPORT.name().equals(role)) {
            permissions.add("SUPPORT_MANAGE");
        }
        return List.copyOf(permissions);
    }

    private List<String> storeConfiguredSurfaces(PartnerShopifyStoreReadModel store) {
        if (store.enabledSurfaces() == null || store.enabledSurfaces().isEmpty()) {
            return List.of();
        }
        return store.enabledSurfaces().stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private String normalizeShopDomain(String value) {
        return clean(value, "shopDomain").toLowerCase(Locale.ROOT);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize partner value.");
        }
    }

    private void audit(PartnerContext context, String action, String targetType, String targetId, String result, String detailsJson) {
        audit(context.account().getId(), context.member().getId(), action, targetType, targetId, result, detailsJson);
    }

    private void audit(String accountId, String memberId, String action, String targetType, String targetId, String result, String detailsJson) {
        auditPublisher.publish(accountId, memberId, action, targetType, targetId, result, detailsJson);
    }

    private String approvalCode() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String clean(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary == null ? fallback : primary;
    }

    private String safeEvidenceValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "UNKNOWN";
    }

    private String titleFromId(String value) {
        if (!StringUtils.hasText(value)) {
            return "Store check";
        }
        String[] words = value.replace('_', '-').split("-");
        List<String> titled = new ArrayList<>();
        for (String word : words) {
            if (!StringUtils.hasText(word)) {
                continue;
            }
            titled.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", titled);
    }

    private record PartnerContext(PartnerAccountEntity account, PartnerMemberEntity member) {
    }

    private record PartnerMaxWidgetContext(PartnerContext partner,
                                           PartnerStoreAssignmentEntity assignment,
                                           String deploymentId) {
    }

    private record VerificationPack(
        String id,
        String name,
        String description,
        List<VerificationStepDefinition> steps
    ) {
    }

    private record VerificationStepDefinition(
        String id,
        String label,
        String description,
        String suggestedFix
    ) {
    }
}
