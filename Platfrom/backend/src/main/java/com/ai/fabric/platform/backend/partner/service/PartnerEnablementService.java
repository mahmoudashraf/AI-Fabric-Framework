package com.ai.fabric.platform.backend.partner.service;

import com.ai.fabric.platform.backend.partner.config.PartnerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.partner.entity.PartnerAccountEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerClientImplementationRequestEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerMemberEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAccessApprovalEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAccessRequestEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAssignmentEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportEscalationEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportReplyEntity;
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
import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEligibleStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerMemberSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSessionSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSignupCompleteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreAccessLinkSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplyRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportThreadSummary;
import com.ai.fabric.platform.backend.partner.repository.PartnerAccountRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerClientImplementationRequestRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAccessApprovalRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAccessRequestRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAssignmentRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportEscalationRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportReplyRepository;
import com.ai.fabric.platform.backend.partner.security.PartnerForbiddenException;
import com.ai.fabric.platform.backend.partner.security.PartnerPrincipal;
import com.ai.fabric.platform.backend.partner.security.PartnerSecurityContext;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartnerEnablementService {

    private static final List<String> DEFAULT_ASSIGNMENT_PERMISSIONS = List.of(
        "STORE_READ",
        "CATALOG_READ",
        "VERIFICATION_READ",
        "EVIDENCE_READ",
        "ESCALATION_CREATE",
        "ESCALATION_REPLY"
    );
    private static final String MERCHANT_CONFIGURED_TIER = "MERCHANT_CONFIGURED";
    private static final String FULL_STORE_ACCESS_SCOPE = "FULL_STORE_ACCESS";

    private final PartnerSupabaseAuthProperties authProperties;
    private final PartnerAccountRepository accountRepository;
    private final PartnerMemberRepository memberRepository;
    private final PartnerClientImplementationRequestRepository implementationRequestRepository;
    private final PartnerStoreAccessRequestRepository storeAccessRequestRepository;
    private final PartnerStoreAccessApprovalRepository storeAccessApprovalRepository;
    private final PartnerStoreAssignmentRepository storeAssignmentRepository;
    private final PartnerSupportEscalationRepository escalationRepository;
    private final PartnerSupportReplyRepository replyRepository;
    private final PartnerStoreAccessGateway storeAccessGateway;
    private final PartnerCatalogSource catalogSource;
    private final PartnerAuditPublisher auditPublisher;
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
                                    PartnerStoreAccessGateway storeAccessGateway,
                                    PartnerCatalogSource catalogSource,
                                    PartnerAuditPublisher auditPublisher,
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
        this.storeAccessGateway = storeAccessGateway;
        this.catalogSource = catalogSource;
        this.auditPublisher = auditPublisher;
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
        reply.setAttachmentsJson("[]");
        reply.setCreatedAt(Instant.now());
        replyRepository.save(reply);
        escalation.setUpdatedAt(reply.getCreatedAt());
        escalationRepository.save(escalation);
        audit(context, "SUPPORT_REPLY_CREATED", "SUPPORT_ESCALATION", escalation.getId(), "SUCCESS", "{}");
        return toReplySummary(reply);
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
            assignment.getShopDomain(),
            firstNonBlank(assignment.getMerchantName(), store.map(PartnerShopifyStoreReadModel::displayName).orElse(null), assignment.getShopDomain()),
            "Merchant configured",
            statusFor(readiness, widget, assignment.getStatus()),
            store.map(PartnerShopifyStoreReadModel::enabledSurfaces).orElse(List.of()),
            knowledgeSync,
            readiness,
            blockerFor(knowledgeSync, readiness, widget),
            firstNonNull(store.map(PartnerShopifyStoreReadModel::lastSyncAt).orElse(null), assignment.getUpdatedAt()),
            assignment.getStatus()
        );
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
            return List.of("WORKSPACE_ADMIN", "IMPLEMENTATION_CREATE", "STORE_READ", "ESCALATION_CREATE", "ESCALATION_REPLY", "CATALOG_READ");
        }
        return List.of("IMPLEMENTATION_CREATE", "STORE_READ", "ESCALATION_CREATE", "ESCALATION_REPLY", "CATALOG_READ");
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

    private record PartnerContext(PartnerAccountEntity account, PartnerMemberEntity member) {
    }
}
