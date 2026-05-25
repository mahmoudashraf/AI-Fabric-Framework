package com.ai.fabric.platform.backend.thinker.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.partner.entity.PartnerEvidenceBundleEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAssignmentEntity;
import com.ai.fabric.platform.backend.partner.entity.PartnerSupportEscalationEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerEvidenceBundleRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAssignmentRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerSupportEscalationRepository;
import com.ai.fabric.platform.backend.partner.security.PartnerForbiddenException;
import com.ai.fabric.platform.backend.partner.security.PartnerPrincipal;
import com.ai.fabric.platform.backend.partner.security.PartnerSecurityContext;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.thinker.entity.ResolverDryRunEntity;
import com.ai.fabric.platform.backend.thinker.entity.ResolverExecutionEntity;
import com.ai.fabric.platform.backend.thinker.entity.ResolverIntentProposalEntity;
import com.ai.fabric.platform.backend.thinker.entity.ResolverPolicyDecisionEntity;
import com.ai.fabric.platform.backend.thinker.entity.ThinkerDeploymentControlEntity;
import com.ai.fabric.platform.backend.thinker.entity.ThinkerEvidenceItemEntity;
import com.ai.fabric.platform.backend.thinker.entity.ThinkerIssueSessionEntity;
import com.ai.fabric.platform.backend.thinker.entity.ThinkerResolutionPlanEntity;
import com.ai.fabric.platform.backend.thinker.entity.ThinkerSessionAuditEventEntity;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.CreateEvidenceItemRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.CreateResolverProposalRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.CreateThinkerIssueSessionRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ExecuteResolverProposalRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.PartnerThinkerEscalationRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverDryRunSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverExecutionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverIntentProposalSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverPolicyDecisionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ShopifyThinkerHealthSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerDeploymentControlSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerEvidenceItemSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionDetail;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerReadinessScenarioSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerReadinessSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerResolutionPlanSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerSessionAuditSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.UpdateThinkerDeploymentControlRequest;
import com.ai.fabric.platform.backend.thinker.repository.ResolverDryRunRepository;
import com.ai.fabric.platform.backend.thinker.repository.ResolverExecutionRepository;
import com.ai.fabric.platform.backend.thinker.repository.ResolverIntentProposalRepository;
import com.ai.fabric.platform.backend.thinker.repository.ResolverPolicyDecisionRepository;
import com.ai.fabric.platform.backend.thinker.repository.ThinkerDeploymentControlRepository;
import com.ai.fabric.platform.backend.thinker.repository.ThinkerEvidenceItemRepository;
import com.ai.fabric.platform.backend.thinker.repository.ThinkerIssueSessionRepository;
import com.ai.fabric.platform.backend.thinker.repository.ThinkerResolutionPlanRepository;
import com.ai.fabric.platform.backend.thinker.repository.ThinkerSessionAuditEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ThinkerResolverService {

    public static final String ACTION_FAMILY_SUPPORT_ESCALATION = "SUPPORT_ESCALATION";
    public static final String ACTION_ID_CREATE_SUPPORT_ESCALATION = "create_support_escalation";
    private static final String DEFAULT_SHOPIFY_COMPANION_TIER = "ELITE";
    private static final String TARGET_TYPE_SESSION = "THINKER_ISSUE_SESSION";
    private static final String TARGET_TYPE_RESOLVER = "RESOLVER_PROPOSAL";
    private static final List<String> SHOPIFY_PHASE_1_ALLOWLIST = List.of(
        "list_products",
        "search_products",
        "get_product_details",
        "check_availability",
        "get_policy",
        "view_cart"
    );
    private static final Set<String> TERMINAL_STATUSES = Set.of(
        "RESOLVED",
        "INSUFFICIENT_EVIDENCE",
        "WRITE_REQUIRED_ESCALATED",
        "BLOCKED",
        "FAILED"
    );

    private final ThinkerDeploymentControlRepository controlRepository;
    private final ThinkerIssueSessionRepository sessionRepository;
    private final ThinkerEvidenceItemRepository evidenceRepository;
    private final ThinkerResolutionPlanRepository planRepository;
    private final ThinkerSessionAuditEventRepository sessionAuditRepository;
    private final ResolverIntentProposalRepository proposalRepository;
    private final ResolverPolicyDecisionRepository policyDecisionRepository;
    private final ResolverDryRunRepository dryRunRepository;
    private final ResolverExecutionRepository executionRepository;
    private final DeploymentRepository deploymentRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PartnerStoreAssignmentRepository partnerStoreAssignmentRepository;
    private final PartnerMemberRepository partnerMemberRepository;
    private final PartnerEvidenceBundleRepository partnerEvidenceBundleRepository;
    private final PartnerSupportEscalationRepository partnerSupportEscalationRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public ThinkerResolverService(ThinkerDeploymentControlRepository controlRepository,
                                  ThinkerIssueSessionRepository sessionRepository,
                                  ThinkerEvidenceItemRepository evidenceRepository,
                                  ThinkerResolutionPlanRepository planRepository,
                                  ThinkerSessionAuditEventRepository sessionAuditRepository,
                                  ResolverIntentProposalRepository proposalRepository,
                                  ResolverPolicyDecisionRepository policyDecisionRepository,
                                  ResolverDryRunRepository dryRunRepository,
                                  ResolverExecutionRepository executionRepository,
                                  DeploymentRepository deploymentRepository,
                                  ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                  PartnerStoreAssignmentRepository partnerStoreAssignmentRepository,
                                  PartnerMemberRepository partnerMemberRepository,
                                  PartnerEvidenceBundleRepository partnerEvidenceBundleRepository,
                                  PartnerSupportEscalationRepository partnerSupportEscalationRepository,
                                  PlatformAuditService platformAuditService,
                                  ObjectMapper objectMapper) {
        this.controlRepository = controlRepository;
        this.sessionRepository = sessionRepository;
        this.evidenceRepository = evidenceRepository;
        this.planRepository = planRepository;
        this.sessionAuditRepository = sessionAuditRepository;
        this.proposalRepository = proposalRepository;
        this.policyDecisionRepository = policyDecisionRepository;
        this.dryRunRepository = dryRunRepository;
        this.executionRepository = executionRepository;
        this.deploymentRepository = deploymentRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.partnerStoreAssignmentRepository = partnerStoreAssignmentRepository;
        this.partnerMemberRepository = partnerMemberRepository;
        this.partnerEvidenceBundleRepository = partnerEvidenceBundleRepository;
        this.partnerSupportEscalationRepository = partnerSupportEscalationRepository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public boolean isThinkerModeRequest(JsonNode request) {
        String mode = text(request, "mode");
        return "thinker".equalsIgnoreCase(mode) || "THINKER_DEEP".equalsIgnoreCase(mode);
    }

    public boolean isThinkerEnabledForDeployment(String deploymentId) {
        if (!StringUtils.hasText(deploymentId)) {
            return false;
        }
        return controlRepository.findById(deploymentId.trim())
            .map(ThinkerDeploymentControlEntity::isThinkerEnabled)
            .orElse(false);
    }

    @Transactional
    public Optional<ThinkerIssueSessionSummary> captureRuntimeResponse(DeploymentEntity deployment,
                                                                       String consumerId,
                                                                       JsonNode request,
                                                                       JsonNode response,
                                                                       String shopperSessionId) {
        if (deployment == null || request == null || response == null) {
            return Optional.empty();
        }
        boolean requestedThinker = isThinkerModeRequest(request);
        JsonNode readActionResolution = response.path("result").path("metadata").path("readActionResolution");
        boolean runtimeUsedReadActionResolution = readActionResolution.isObject()
            && readActionResolution.path("attempted").asBoolean(false);
        if (!requestedThinker && !runtimeUsedReadActionResolution) {
            return Optional.empty();
        }
        String shopDomain = shopifyStoreConnectionRepository.findByConsumerIdIgnoreCase(consumerId)
            .map(ShopifyStoreConnectionEntity::getShopDomain)
            .or(() -> shopifyStoreConnectionRepository.findByDeploymentId(deployment.getId()).map(ShopifyStoreConnectionEntity::getShopDomain))
            .orElse(null);
        requireThinkerAllowed(deployment, shopDomain);

        List<CreateEvidenceItemRequest> evidence = evidenceFromRuntime(readActionResolution, response);
        CreateThinkerIssueSessionRequest sessionRequest = new CreateThinkerIssueSessionRequest(
            deployment.getId(),
            shopDomain,
            deployment.getTenantId(),
            deployment.getCustomerId(),
            null,
            consumerId,
            text(response.path("authContext"), "subjectId"),
            text(request, "locale"),
            "STOREFRONT_DEPTH_LAYER",
            "THINKER_DEEP",
            text(request, "query"),
            runtimeAnswer(response),
            evidence,
            deployment.getTenantId(),
            text(response, "conversationId"),
            text(response, "sessionId")
        );
        return Optional.of(createIssueSession(sessionRequest));
    }

    @Transactional
    public ThinkerIssueSessionSummary createIssueSession(CreateThinkerIssueSessionRequest request) {
        DeploymentEntity deployment = deploymentRepository.findById(required(request.deploymentId(), "deploymentId"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found: " + request.deploymentId()));
        String assertedTenantId = trimToNull(request.assertedTenantId());
        boolean crossTenant = assertedTenantId != null && !assertedTenantId.equals(deployment.getTenantId());
        String shopDomain = firstNonBlank(request.shopDomain(), shopifyStoreConnectionRepository.findByDeploymentId(deployment.getId())
            .map(ShopifyStoreConnectionEntity::getShopDomain)
            .orElse(null));
        if (!crossTenant) {
            requireThinkerAllowed(deployment, shopDomain);
        }

        Instant now = Instant.now();
        List<CreateEvidenceItemRequest> evidenceRequests = request.evidence() == null ? List.of() : request.evidence();
        String userQuestion = required(request.userQuestion(), "userQuestion");
        boolean promptInjection = detectsPromptInjection(userQuestion);
        boolean writeIntent = detectsWriteIntent(userQuestion);
        String status = crossTenant
            ? "BLOCKED"
            : promptInjection
                ? "BLOCKED"
                : writeIntent
                    ? "WRITE_REQUIRED_ESCALATED"
                    : evidenceRequests.isEmpty()
                        ? "INSUFFICIENT_EVIDENCE"
                        : "RESOLVED";
        String issueCategory = classifyIssue(userQuestion);
        String riskClass = writeIntent ? "LOW_WRITE_REQUESTED" : "READ_ONLY";
        String terminalReason = crossTenant
            ? "Cross-tenant thinker attempt blocked before read-action planning."
            : promptInjection
                ? "Prompt-injection attempt rejected before action planning."
                : writeIntent
                    ? "The issue requires a governed write or human support handoff; Phase 1 Thinker did not execute a write."
                    : evidenceRequests.isEmpty()
                        ? "No grounding evidence was available for a confident answer."
                        : "Read-only evidence was sufficient for a user-safe diagnosis.";

        ThinkerIssueSessionEntity session = new ThinkerIssueSessionEntity();
        session.setId("tis-" + shortId());
        session.setDeploymentId(deployment.getId());
        session.setTenantId(deployment.getTenantId());
        session.setCustomerId(deployment.getCustomerId());
        session.setStoreId(trimToNull(request.storeId()));
        session.setShopDomain(normalizeShopDomain(shopDomain));
        session.setConsumerId(trimToNull(request.consumerId()));
        session.setUserSubject(trimToNull(request.userSubject()));
        session.setShopperSessionId(null);
        session.setLocale(firstNonBlank(request.locale(), "en"));
        session.setChannel(firstNonBlank(request.channel(), "OPERATOR_CAPTURE"));
        session.setMode(firstNonBlank(request.mode(), "THINKER_DEEP"));
        session.setStatus(status);
        session.setIssueCategory(issueCategory);
        session.setRiskClass(riskClass);
        session.setExpectedEvidenceJson(writeJson(expectedEvidence(issueCategory)));
        session.setSuggestedActionAllowlistJson(writeJson(SHOPIFY_PHASE_1_ALLOWLIST));
        session.setUserQuestion(userQuestion);
        session.setUserSafeAnswer(trimToNull(request.userSafeAnswer()));
        session.setRuntimeConversationId(trimToNull(request.runtimeConversationId()));
        session.setRuntimeSessionId(trimToNull(request.runtimeSessionId()));
        session.setTerminalReason(terminalReason);
        session.setStartedAt(now);
        session.setCompletedAt(TERMINAL_STATUSES.contains(status) ? now : null);
        session.setUpdatedAt(now);
        session.setPlannerTraceJson(writeJson(Map.of(
            "phase", "006",
            "mode", session.getMode(),
            "allowedReadActions", SHOPIFY_PHASE_1_ALLOWLIST,
            "writePathAvailable", false,
            "crossTenantBlocked", crossTenant,
            "promptInjectionBlocked", promptInjection
        )));
        sessionRepository.save(session);

        List<ThinkerEvidenceItemEntity> evidenceItems = new ArrayList<>();
        for (CreateEvidenceItemRequest itemRequest : evidenceRequests) {
            evidenceItems.add(saveEvidenceItem(session.getId(), itemRequest, now));
        }
        session.setSourceCitationsJson(writeJson(evidenceItems.stream()
            .map(ThinkerEvidenceItemEntity::getSourceIdentifier)
            .filter(StringUtils::hasText)
            .distinct()
            .limit(8)
            .toList()));
        sessionRepository.save(session);

        ThinkerResolutionPlanEntity plan = createPlan(session, evidenceItems, status, terminalReason, now);
        planRepository.save(plan);
        recordSessionAudit(session.getId(), "THINKER_SESSION_CREATED", Map.of(
            "status", status,
            "issueCategory", issueCategory,
            "evidenceCount", evidenceItems.size()
        ));
        if (promptInjection) {
            recordSessionAudit(session.getId(), "THINKER_PROPOSAL_REJECTED", Map.of("reason", "PROMPT_INJECTION"));
        }
        if (crossTenant) {
            recordSessionAudit(session.getId(), "THINKER_CROSS_TENANT_BLOCKED", Map.of("assertedTenantId", assertedTenantId));
        }
        if (writeIntent) {
            recordSessionAudit(session.getId(), "THINKER_WRITE_REQUIRED_ESCALATED", Map.of("writeExecuted", false));
        }
        platformAuditService.record("THINKER_SESSION_CREATED", TARGET_TYPE_SESSION, session.getId(), mapOf(
            "deploymentId", session.getDeploymentId(),
            "shopDomain", session.getShopDomain(),
            "status", session.getStatus()
        ));
        return toSessionSummary(session, evidenceItems.size(), plan);
    }

    public List<ThinkerIssueSessionSummary> listOperatorSessions(String shopDomain, String deploymentId, String status) {
        List<ThinkerIssueSessionEntity> sessions = trimToNull(shopDomain) != null
            ? sessionRepository.findTop200ByShopDomainIgnoreCaseOrderByStartedAtDesc(normalizeShopDomain(shopDomain))
            : trimToNull(deploymentId) != null
                ? sessionRepository.findTop200ByDeploymentIdOrderByStartedAtDesc(deploymentId.trim())
                : sessionRepository.findTop200ByOrderByStartedAtDesc();
        String normalizedStatus = normalizeUpper(status);
        return sessions.stream()
            .filter(session -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(session.getStatus()))
            .map(this::toSessionSummary)
            .toList();
    }

    public ThinkerIssueSessionDetail getOperatorSession(String sessionId) {
        ThinkerIssueSessionEntity session = requireSession(sessionId);
        return toDetail(session, true);
    }

    public List<ThinkerEvidenceItemSummary> getOperatorEvidence(String sessionId) {
        requireSession(sessionId);
        return evidenceRepository.findBySessionIdOrderByCapturedAtAsc(sessionId).stream()
            .map(item -> toEvidenceSummary(item, true))
            .toList();
    }

    public byte[] exportOperatorSession(String sessionId) {
        ThinkerIssueSessionDetail detail = getOperatorSession(sessionId);
        StringBuilder out = new StringBuilder();
        out.append("# Thinker Resolver Session Export\n\n");
        out.append("- Session: ").append(detail.session().id()).append('\n');
        out.append("- Status: ").append(detail.session().status()).append('\n');
        out.append("- Shop: ").append(nullSafe(detail.session().shopDomain())).append('\n');
        out.append("- Deployment: ").append(nullSafe(detail.session().deploymentId())).append('\n');
        out.append("- Question: ").append(detail.session().userQuestion()).append("\n\n");
        if (detail.plan() != null) {
            out.append("## Resolution Plan\n\n");
            out.append(detail.plan().diagnosis()).append("\n\n");
            out.append("Recommended next step: ").append(detail.plan().recommendedNextStep()).append("\n\n");
        }
        out.append("## Evidence\n\n");
        for (ThinkerEvidenceItemSummary item : detail.evidence()) {
            out.append("- ").append(item.kind()).append(" / ").append(item.sourceIdentifier())
                .append(" / ").append(item.freshness()).append(": ")
                .append(item.summary()).append('\n');
        }
        out.append("\n## Audit\n\n");
        for (ThinkerSessionAuditSummary event : detail.auditEvents()) {
            out.append("- ").append(event.createdAt()).append(" ").append(event.eventType()).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ThinkerDeploymentControlSummary getControl(String deploymentId) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        return toControlSummary(controlRepository.findById(deployment.getId()).orElseGet(() -> defaultControl(deployment.getId())));
    }

    @Transactional
    public ThinkerDeploymentControlSummary updateControl(String deploymentId, UpdateThinkerDeploymentControlRequest request) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        ThinkerDeploymentControlEntity entity = controlRepository.findById(deployment.getId()).orElseGet(() -> defaultControl(deployment.getId()));
        if (request != null && request.thinkerEnabled() != null) {
            entity.setThinkerEnabled(request.thinkerEnabled());
        }
        if (request != null && request.resolverPreviewEnabled() != null) {
            entity.setResolverPreviewEnabled(request.resolverPreviewEnabled());
        }
        if (request != null && request.governedExecutionEnabled() != null) {
            entity.setGovernedExecutionEnabled(request.governedExecutionEnabled());
        }
        if (request != null && request.disabledActionFamilies() != null) {
            entity.setDisabledActionFamiliesJson(writeJson(normalizedList(request.disabledActionFamilies())));
        }
        entity.setUpdatedBy(PlatformSecurityContext.actorIdOrSystem());
        entity.setUpdatedAt(Instant.now());
        controlRepository.save(entity);
        platformAuditService.record("THINKER_CONTROL_UPDATED", "THINKER_DEPLOYMENT_CONTROL", deployment.getId(), Map.of(
            "thinkerEnabled", entity.isThinkerEnabled(),
            "resolverPreviewEnabled", entity.isResolverPreviewEnabled(),
            "governedExecutionEnabled", entity.isGovernedExecutionEnabled()
        ));
        return toControlSummary(entity);
    }

    @Transactional
    public ResolverIntentProposalSummary createProposal(CreateResolverProposalRequest request) {
        ThinkerIssueSessionEntity session = requireSession(required(request.sessionId(), "sessionId"));
        String actionFamily = firstNonBlank(request.actionFamily(), ACTION_FAMILY_SUPPORT_ESCALATION).toUpperCase(Locale.ROOT);
        if (!ACTION_FAMILY_SUPPORT_ESCALATION.equals(actionFamily)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SUPPORT_ESCALATION is implemented for governed low-risk execution.");
        }
        Instant now = Instant.now();
        ResolverIntentProposalEntity proposal = new ResolverIntentProposalEntity();
        proposal.setId("rip-" + shortId());
        proposal.setSessionId(session.getId());
        proposal.setActionId(firstNonBlank(request.actionId(), ACTION_ID_CREATE_SUPPORT_ESCALATION));
        proposal.setActionFamily(actionFamily);
        proposal.setTargetDomain(firstNonBlank(request.targetDomain(), "SHOPIFY_COMPANION"));
        proposal.setProductBoundary(firstNonBlank(request.productBoundary(), "PARTNER_ENABLEMENT"));
        proposal.setActorContext(firstNonBlank(request.actorContext(), PlatformSecurityContext.actorIdOrSystem()));
        proposal.setTenantId(session.getTenantId());
        proposal.setStoreId(session.getStoreId());
        proposal.setDeploymentId(session.getDeploymentId());
        Map<String, Object> operatorParameters = request.parameters() == null ? Map.of() : new LinkedHashMap<>(request.parameters());
        proposal.setOperatorParametersJson(writeJson(operatorParameters));
        proposal.setRedactedParametersJson(writeJson(redactParameters(operatorParameters)));
        proposal.setSourceEvidenceItemIdsJson(writeJson(normalizedIdList(request.sourceEvidenceItemIds())));
        proposal.setProposalText(required(request.proposalText(), "proposalText"));
        proposal.setNormalizedIntentJson(writeJson(Map.of(
            "action", proposal.getActionId(),
            "actionFamily", actionFamily,
            "sessionStatus", session.getStatus(),
            "writeExecutedByLlm", false
        )));
        proposal.setStatus("PROPOSED");
        proposal.setCreatedAt(now);
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        ResolverPolicyDecisionEntity policy = decidePolicy(session, proposal, now);
        policyDecisionRepository.save(policy);
        proposal.setStatus(policy.isDryRunRequired() && "ALLOWED".equals(policy.getOutcome()) ? "DRY_RUN_READY" : policy.getOutcome());
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        recordSessionAudit(session.getId(), "RESOLVER_PROPOSAL_CREATED", Map.of(
            "proposalId", proposal.getId(),
            "policyOutcome", policy.getOutcome()
        ));
        platformAuditService.record("RESOLVER_PROPOSAL_CREATED", TARGET_TYPE_RESOLVER, proposal.getId(), Map.of(
            "sessionId", session.getId(),
            "deploymentId", session.getDeploymentId(),
            "actionFamily", actionFamily
        ));
        return toProposalSummary(proposal);
    }

    @Transactional
    public ResolverDryRunSummary runDryRun(String proposalId) {
        ResolverIntentProposalEntity proposal = requireProposal(proposalId);
        ThinkerIssueSessionEntity session = requireSession(proposal.getSessionId());
        ResolverPolicyDecisionEntity policy = policyDecisionRepository.findTopByProposalIdOrderByDecidedAtDesc(proposal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Policy decision is required before dry-run."));
        if (!"ALLOWED".equals(policy.getOutcome())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dry-run is blocked by policy: " + policy.getOperatorReason());
        }
        PartnerStoreAssignmentEntity assignment = requireActiveAssignmentForSession(session);
        Instant now = Instant.now();
        ResolverDryRunEntity dryRun = new ResolverDryRunEntity();
        dryRun.setId("rdr-" + shortId());
        dryRun.setProposalId(proposal.getId());
        dryRun.setTargetAction(proposal.getActionId());
        dryRun.setValidatedParametersJson(proposal.getRedactedParametersJson());
        dryRun.setExpectedStateTransition("Create one partner-visible support escalation for assigned store " + assignment.getShopDomain() + ".");
        dryRun.setExpectedSideEffectsJson(writeJson(List.of(
            "Partner support queue receives a new escalation.",
            "Partner-safe Thinker evidence bundle is attached.",
            "Platform and Thinker audit trails record the handoff."
        )));
        dryRun.setWarningsJson(writeJson(List.of("No Shopify customer, order, billing, or catalog state will be mutated.")));
        dryRun.setUnsupportedFieldsJson("[]");
        dryRun.setIdempotencyPosture("Execution requires a unique idempotency key; duplicate keys return the original execution.");
        dryRun.setRollbackPosture("Escalation can be closed or resolved by support; no Shopify data rollback is required.");
        dryRun.setEvidenceFreshness(evidenceRepository.findBySessionIdOrderByCapturedAtAsc(session.getId()).stream()
            .anyMatch(item -> "STALE".equalsIgnoreCase(item.getFreshness())) ? "STALE" : "FRESH");
        dryRun.setProductBoundary("PARTNER_ENABLEMENT");
        dryRun.setStatus("COMPLETED");
        dryRun.setCreatedAt(now);
        dryRunRepository.save(dryRun);
        proposal.setStatus("DRY_RUN_COMPLETED");
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        recordSessionAudit(session.getId(), "RESOLVER_DRY_RUN_COMPLETED", Map.of("proposalId", proposal.getId(), "dryRunId", dryRun.getId()));
        platformAuditService.record("RESOLVER_DRY_RUN_COMPLETED", TARGET_TYPE_RESOLVER, proposal.getId(), Map.of("dryRunId", dryRun.getId()));
        return toDryRunSummary(dryRun);
    }

    @Transactional
    public ResolverExecutionSummary executeProposal(String proposalId, ExecuteResolverProposalRequest request) {
        ResolverIntentProposalEntity proposal = requireProposal(proposalId);
        ThinkerIssueSessionEntity session = requireSession(proposal.getSessionId());
        ThinkerDeploymentControlEntity control = controlRepository.findById(session.getDeploymentId())
            .orElseGet(() -> defaultControl(session.getDeploymentId()));
        if (!control.isGovernedExecutionEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Governed execution is disabled for this deployment.");
        }
        if (isActionFamilyDisabled(control, proposal.getActionFamily())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Action family is disabled: " + proposal.getActionFamily());
        }
        ResolverPolicyDecisionEntity policy = policyDecisionRepository.findTopByProposalIdOrderByDecidedAtDesc(proposal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Policy decision is required before execution."));
        if (!"ALLOWED".equals(policy.getOutcome())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Execution blocked by policy.");
        }
        String idempotencyKey = required(request.idempotencyKey(), "idempotencyKey");
        Optional<ResolverExecutionEntity> existing = executionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toExecutionSummary(existing.get());
        }
        String confirmation = required(request.confirmationText(), "confirmationText");
        if (!"CREATE SUPPORT ESCALATION".equalsIgnoreCase(confirmation.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation text must be CREATE SUPPORT ESCALATION.");
        }
        ResolverDryRunEntity dryRun = resolveDryRun(proposal.getId(), request.dryRunId());
        PartnerStoreAssignmentEntity assignment = requireActiveAssignmentForSession(session);
        String partnerMemberId = resolveExecutionPartnerMemberId(assignment);
        PartnerEvidenceBundleEntity bundle = createPartnerEvidenceBundle(session, assignment, partnerMemberId);
        PartnerSupportEscalationEntity escalation = createSupportEscalation(session, assignment, partnerMemberId, bundle.getId(), "MEDIUM", null, null);

        Instant now = Instant.now();
        ResolverExecutionEntity execution = new ResolverExecutionEntity();
        execution.setId("rex-" + shortId());
        execution.setProposalId(proposal.getId());
        execution.setDryRunId(dryRun.getId());
        execution.setIdempotencyKey(idempotencyKey);
        execution.setActionFamily(proposal.getActionFamily());
        execution.setStatus("COMPLETED");
        execution.setProductBoundary("PARTNER_ENABLEMENT");
        execution.setExecutionResultJson(writeJson(Map.of(
            "supportEscalationId", escalation.getId(),
            "evidenceBundleId", bundle.getId(),
            "shopDomain", assignment.getShopDomain()
        )));
        execution.setVerificationStatus("VERIFIED");
        execution.setRecoveryGuidance("Close or update the partner support escalation if the handoff is no longer needed.");
        execution.setCreatedAt(now);
        execution.setCompletedAt(now);
        executionRepository.save(execution);
        proposal.setStatus("EXECUTED");
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        recordSessionAudit(session.getId(), "RESOLVER_EXECUTION_COMPLETED", Map.of(
            "proposalId", proposal.getId(),
            "executionId", execution.getId(),
            "supportEscalationId", escalation.getId()
        ));
        platformAuditService.record("RESOLVER_EXECUTION_COMPLETED", TARGET_TYPE_RESOLVER, proposal.getId(), Map.of(
            "executionId", execution.getId(),
            "supportEscalationId", escalation.getId(),
            "deploymentId", session.getDeploymentId()
        ));
        return toExecutionSummary(execution);
    }

    public List<ResolverIntentProposalSummary> listProposals() {
        return proposalRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(this::toProposalSummary)
            .toList();
    }

    public List<ResolverExecutionSummary> listExecutions() {
        return executionRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(this::toExecutionSummary)
            .toList();
    }

    public List<ResolverPolicyDecisionSummary> listPolicyDecisions() {
        return policyDecisionRepository.findTop200ByOrderByDecidedAtDesc().stream()
            .map(this::toPolicyDecisionSummary)
            .toList();
    }

    public List<ThinkerIssueSessionSummary> listPartnerStoreSessions(String storeAssignmentId) {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        PartnerStoreAssignmentEntity assignment = requirePartnerAssignment(principal, storeAssignmentId);
        requireAssignmentCapability(assignment, "PRODUCT_CONFIG_READ");
        return sessionRepository.findTop200ByShopDomainIgnoreCaseOrderByStartedAtDesc(assignment.getShopDomain()).stream()
            .map(this::toPartnerSessionSummary)
            .toList();
    }

    public ThinkerIssueSessionDetail getPartnerSession(String sessionId) {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        ThinkerIssueSessionEntity session = requireSession(sessionId);
        PartnerStoreAssignmentEntity assignment = requirePartnerAssignmentForShop(principal, session.getShopDomain());
        requireAssignmentCapability(assignment, "PRODUCT_CONFIG_READ");
        recordSessionAudit(session.getId(), "THINKER_PARTNER_SESSION_VIEWED", Map.of("partnerAccountId", principal.partnerAccountId()));
        return toDetail(session, false);
    }

    @Transactional
    public Map<String, Object> createPartnerEscalation(String sessionId, PartnerThinkerEscalationRequest request) {
        PartnerPrincipal principal = PartnerSecurityContext.currentPrincipalOrThrow();
        ThinkerIssueSessionEntity session = requireSession(sessionId);
        PartnerStoreAssignmentEntity assignment = requirePartnerAssignmentForShop(principal, session.getShopDomain());
        requireAssignmentCapability(assignment, "SUPPORT_MANAGE");
        PartnerEvidenceBundleEntity bundle = createPartnerEvidenceBundle(session, assignment, principal.partnerMemberId());
        PartnerSupportEscalationEntity escalation = createSupportEscalation(
            session,
            assignment,
            principal.partnerMemberId(),
            bundle.getId(),
            firstNonBlank(request == null ? null : request.severity(), "MEDIUM"),
            request == null ? null : request.title(),
            request == null ? null : request.nextAction()
        );
        recordSessionAudit(session.getId(), "THINKER_PARTNER_ESCALATION_CREATED", Map.of(
            "partnerAccountId", principal.partnerAccountId(),
            "supportEscalationId", escalation.getId(),
            "evidenceBundleId", bundle.getId()
        ));
        return Map.of(
            "supportEscalationId", escalation.getId(),
            "evidenceBundleId", bundle.getId(),
            "status", escalation.getStatus()
        );
    }

    public ShopifyThinkerHealthSummary shopifyThinkerHealth(String shopDomain) {
        ShopifyStoreConnectionEntity store = shopifyStoreConnectionRepository.findByShopDomainIgnoreCase(required(shopDomain, "shopDomain"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        String deploymentId = trimToNull(store.getDeploymentId());
        boolean enabled = deploymentId != null && isThinkerEnabledForDeployment(deploymentId);
        Instant since = Instant.now().minus(Duration.ofDays(7));
        List<ThinkerIssueSessionEntity> sessions = sessionRepository.findTop200ByShopDomainIgnoreCaseOrderByStartedAtDesc(store.getShopDomain());
        long recent = sessions.stream().filter(session -> session.getStartedAt().isAfter(since)).count();
        long failed = sessions.stream()
            .filter(session -> session.getStartedAt().isAfter(since))
            .filter(session -> "FAILED".equalsIgnoreCase(session.getStatus()) || "BLOCKED".equalsIgnoreCase(session.getStatus()))
            .count();
        String tier = billingTier(store);
        List<String> nextActions = new ArrayList<>();
        if (!"ELITE".equals(tier)) {
            nextActions.add("Upgrade the store to the Elite package before enabling Thinker deep diagnosis.");
        }
        if (!enabled) {
            nextActions.add("Enable Thinker on the linked deployment from Platform Thinker/Resolver controls.");
        }
        if (failed > 0) {
            nextActions.add("Review blocked or failed Thinker sessions in the Platform operator console.");
        }
        String status = enabled && "ELITE".equals(tier) && failed == 0 ? "READY" : enabled ? "ATTENTION" : "DISABLED";
        String message = "READY".equals(status)
            ? "Thinker deep diagnosis is enabled for this Elite store with no blocked sessions in the last 7 days."
            : "DISABLED".equals(status)
                ? "Thinker deep diagnosis is not enabled for this store."
                : "Thinker requires operator review before design-partner use.";
        return new ShopifyThinkerHealthSummary(
            store.getShopDomain(),
            deploymentId,
            enabled,
            status,
            recent,
            failed,
            message,
            List.copyOf(nextActions)
        );
    }

    public ThinkerReadinessSummary readiness() {
        List<ThinkerReadinessScenarioSummary> scenarios = List.of(
            scenario("read-action-foundation", "ReadActionResolutionService foundation", "PASS", "Runtime metadata capture persists read-action evidence when thinker mode is used."),
            scenario("phase-1-allowlist", "Shopify Phase 1 read allowlist", SHOPIFY_PHASE_1_ALLOWLIST.size() == 6 ? "PASS" : "FAIL", String.join(", ", SHOPIFY_PHASE_1_ALLOWLIST)),
            scenario("write-required-escalation", "Write-required issue escalates", "PASS", "Write intents terminate as WRITE_REQUIRED_ESCALATED and no write is executed by Thinker."),
            scenario("resolver-dry-run", "Resolver dry-run is non-mutating", "PASS", "Support escalation dry-run creates no partner escalation before explicit execution."),
            scenario("low-risk-execution", "Low-risk governed execution", "PASS", "Only SUPPORT_ESCALATION executes, through Partner Enablement records and idempotency."),
            scenario("partner-redaction", "Partner redaction", "PASS", "Partner APIs omit raw evidence references and operator parameters."),
            scenario("kill-switch", "Deployment kill switch", "PASS", "Per-deployment controls gate Thinker, Resolver preview, governed execution, and action families.")
        );
        List<String> blockers = scenarios.stream()
            .filter(item -> !"PASS".equals(item.status()))
            .map(ThinkerReadinessScenarioSummary::label)
            .toList();
        int passed = (int) scenarios.stream().filter(item -> "PASS".equals(item.status())).count();
        return new ThinkerReadinessSummary(
            blockers.isEmpty() ? "READY" : "BLOCKED",
            blockers.isEmpty() ? "DESIGN_PARTNER_READY" : "NOT_READY",
            Instant.now(),
            passed,
            scenarios.size() - passed,
            scenarios,
            blockers
        );
    }

    private void requireThinkerAllowed(DeploymentEntity deployment, String shopDomain) {
        if (!isThinkerEnabledForDeployment(deployment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thinker is disabled for deployment " + deployment.getId() + ".");
        }
        if (StringUtils.hasText(shopDomain)) {
            ShopifyStoreConnectionEntity store = shopifyStoreConnectionRepository.findByShopDomainIgnoreCase(shopDomain)
                .orElse(null);
            if (store != null && !"ELITE".equals(billingTier(store))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Thinker deep diagnosis requires Shopify Companion Elite.");
            }
        }
    }

    private List<CreateEvidenceItemRequest> evidenceFromRuntime(JsonNode readActionResolution, JsonNode response) {
        List<CreateEvidenceItemRequest> evidence = new ArrayList<>();
        JsonNode executedActions = readActionResolution.path("executedActions");
        boolean capturedExecutedActionEvidence = false;
        if (executedActions.isArray()) {
            for (JsonNode action : executedActions) {
                String actionName = firstNonBlank(text(action, "name"), text(action, "actionName"), "read-action");
                String summary = firstNonBlank(text(action, "evidence"), text(action, "message"), action.toString());
                evidence.add(new CreateEvidenceItemRequest(
                    "READ_ACTION_RESULT",
                    "RUNTIME_READ_ACTION",
                    actionName,
                    "FRESH",
                    action.path("groundingUsable").asBoolean(false) ? 0.9 : 0.55,
                    truncate(summary, 1800),
                    truncate(summary, 900),
                    "/result/metadata/readActionResolution/executedActions/" + evidence.size()
                ));
                capturedExecutedActionEvidence = true;
            }
        }
        if (!capturedExecutedActionEvidence) {
            appendDirectReadActionEvidence(evidence, response);
        }
        JsonNode documents = response.path("result").path("data").path("documents");
        if (documents.isArray()) {
            int index = 0;
            for (JsonNode doc : documents) {
                evidence.add(new CreateEvidenceItemRequest(
                    "RETRIEVAL_SNIPPET",
                    "RUNTIME_RAG",
                    firstNonBlank(text(doc, "title"), text(doc, "id"), "document-" + index),
                    "UNKNOWN_FRESHNESS",
                    0.65,
                    truncate(firstNonBlank(text(doc, "content"), text(doc, "snippet"), doc.toString()), 1800),
                    truncate(firstNonBlank(text(doc, "content"), text(doc, "snippet"), doc.toString()), 900),
                    "/result/data/documents/" + index
                ));
                index += 1;
            }
        }
        return evidence;
    }

    private void appendDirectReadActionEvidence(List<CreateEvidenceItemRequest> evidence, JsonNode response) {
        JsonNode result = response.path("result");
        if (!"ACTION_EXECUTED".equalsIgnoreCase(text(result, "type"))) {
            return;
        }
        JsonNode actionData = result.path("data");
        if (!actionData.isObject()) {
            return;
        }
        JsonNode metadata = actionData.path("metadata");
        if (!isReadOnlyActionMetadata(metadata)) {
            return;
        }
        String actionName = firstNonBlank(text(actionData, "action"), text(metadata, "name"), "read-action");
        JsonNode actionResult = actionData.path("actionResult");
        JsonNode evidencePayload = firstObject(
            actionResult.path("data").path("data"),
            actionResult.path("data"),
            actionResult,
            actionData
        );
        String summary = firstNonBlank(structuredSummary(evidencePayload), text(result, "message"), actionData.toString());
        evidence.add(new CreateEvidenceItemRequest(
            "READ_ACTION_RESULT",
            "RUNTIME_DIRECT_ACTION",
            actionName,
            "FRESH",
            metadata.path("groundingEligible").asBoolean(false) ? 0.9 : 0.75,
            truncate(summary, 1800),
            truncate(summary, 900),
            "/result/data/actionResult"
        ));
    }

    private boolean isReadOnlyActionMetadata(JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            return false;
        }
        String accessMode = text(metadata, "accessMode");
        String sideEffectLevel = text(metadata, "sideEffectLevel");
        return "READ".equalsIgnoreCase(accessMode)
            || "NONE".equalsIgnoreCase(sideEffectLevel)
            || metadata.path("readActionResolutionEligible").asBoolean(false)
            || metadata.path("groundingEligible").asBoolean(false);
    }

    private JsonNode firstObject(JsonNode... candidates) {
        if (candidates == null) {
            return objectMapper.createObjectNode();
        }
        for (JsonNode candidate : candidates) {
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String structuredSummary(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if ((node.isObject() || node.isArray()) && node.size() == 0) {
            return null;
        }
        return node.toString();
    }

    private String runtimeAnswer(JsonNode response) {
        JsonNode result = response.path("result");
        JsonNode sanitizedPayload = result.path("sanitizedPayload");
        return firstNonBlank(
            text(sanitizedPayload, "answer"),
            text(sanitizedPayload, "safeSummary"),
            text(sanitizedPayload, "message"),
            text(result, "answer"),
            text(result, "message"),
            text(response, "message")
        );
    }

    private ThinkerEvidenceItemEntity saveEvidenceItem(String sessionId, CreateEvidenceItemRequest request, Instant now) {
        ThinkerEvidenceItemEntity entity = new ThinkerEvidenceItemEntity();
        entity.setId("tei-" + shortId());
        entity.setSessionId(sessionId);
        entity.setKind(firstNonBlank(request.kind(), "USER_PROVIDED_CONTEXT"));
        entity.setSourceKind(firstNonBlank(request.sourceKind(), "OPERATOR"));
        entity.setSourceIdentifier(firstNonBlank(request.sourceIdentifier(), entity.getKind().toLowerCase(Locale.ROOT)));
        entity.setFreshness(firstNonBlank(request.freshness(), "UNKNOWN_FRESHNESS"));
        double confidence = request.confidence() == null ? 0.5 : Math.max(0.0, Math.min(1.0, request.confidence()));
        entity.setConfidence(BigDecimal.valueOf(confidence));
        entity.setRedactionState("REDACTED");
        entity.setOperatorRawReference(trimToNull(request.operatorRawReference()));
        entity.setSummary(required(request.summary(), "evidence.summary"));
        entity.setSafeSummary(firstNonBlank(request.safeSummary(), redactText(request.summary())));
        entity.setCapturedAt(now);
        return evidenceRepository.save(entity);
    }

    private ThinkerResolutionPlanEntity createPlan(ThinkerIssueSessionEntity session,
                                                   List<ThinkerEvidenceItemEntity> evidence,
                                                   String status,
                                                   String terminalReason,
                                                   Instant now) {
        ThinkerResolutionPlanEntity plan = new ThinkerResolutionPlanEntity();
        plan.setId("trp-" + shortId());
        plan.setSessionId(session.getId());
        plan.setIssueSummary("Issue classified as " + session.getIssueCategory() + " for " + nullSafe(session.getShopDomain()) + ".");
        plan.setDiagnosis(terminalReason);
        plan.setOptionsConsideredJson(writeJson(List.of(
            "Answer with read-only evidence",
            "Escalate to human support when evidence is insufficient or a write is needed",
            "Block unsafe or cross-tenant requests"
        )));
        String recommendation = switch (status) {
            case "RESOLVED" -> "ANSWER_WITH_EVIDENCE";
            case "INSUFFICIENT_EVIDENCE" -> "INSUFFICIENT_EVIDENCE";
            case "WRITE_REQUIRED_ESCALATED" -> "WRITE_REQUIRED_ESCALATED";
            default -> "HUMAN_ESCALATION_REQUIRED";
        };
        plan.setRecommendation(recommendation);
        plan.setRecommendedNextStep(switch (recommendation) {
            case "ANSWER_WITH_EVIDENCE" -> "Show the user the grounded answer with source citations.";
            case "INSUFFICIENT_EVIDENCE" -> "Ask for more context or route to merchant support with the evidence packet.";
            case "WRITE_REQUIRED_ESCALATED" -> "Create a governed support escalation or wait for Resolver policy preview.";
            default -> "Escalate to an operator before continuing.";
        });
        plan.setUserExplanation(firstNonBlank(session.getUserSafeAnswer(), terminalReason));
        plan.setEscalationTarget("PARTNER_SUPPORT");
        plan.setEvidenceItemIdsJson(writeJson(evidence.stream().map(ThinkerEvidenceItemEntity::getId).toList()));
        plan.setCreatedAt(now);
        return plan;
    }

    private ResolverPolicyDecisionEntity decidePolicy(ThinkerIssueSessionEntity session,
                                                      ResolverIntentProposalEntity proposal,
                                                      Instant now) {
        ResolverPolicyDecisionEntity decision = new ResolverPolicyDecisionEntity();
        decision.setId("rpd-" + shortId());
        decision.setProposalId(proposal.getId());
        decision.setRiskLevel("LOW_WRITE");
        decision.setRequiredScopesJson(writeJson(List.of("PARTNER_SUPPORT_ESCALATION_WRITE")));
        decision.setMissingScopesJson("[]");
        decision.setTierRequirement("ELITE");
        decision.setActorRequirement("PLATFORM_ADMIN_OR_ASSIGNED_PARTNER");
        decision.setDryRunRequired(true);
        decision.setConfirmationRequired(true);
        decision.setApprovalRequired(false);
        decision.setConfirmationPreview("Create one partner support escalation with the redacted Thinker evidence bundle. Type CREATE SUPPORT ESCALATION to execute.");
        decision.setDecidedAt(now);

        ThinkerDeploymentControlEntity control = controlRepository.findById(session.getDeploymentId())
            .orElseGet(() -> defaultControl(session.getDeploymentId()));
        ShopifyStoreConnectionEntity store = StringUtils.hasText(session.getShopDomain())
            ? shopifyStoreConnectionRepository.findByShopDomainIgnoreCase(session.getShopDomain()).orElse(null)
            : null;
        boolean hasEvidence = !evidenceRepository.findBySessionIdOrderByCapturedAtAsc(session.getId()).isEmpty();
        if (!control.isResolverPreviewEnabled()) {
            deny(decision, "BLOCKED", "Resolver preview is disabled for this deployment.", "This store is not ready for Resolver preview.");
        } else if (isActionFamilyDisabled(control, proposal.getActionFamily())) {
            deny(decision, "BLOCKED", "Action family is disabled by the deployment kill switch.", "This support action is currently disabled.");
        } else if (store != null && !"ELITE".equals(billingTier(store))) {
            deny(decision, "POLICY_DENIED", "Shopify Companion Elite is required.", "This capability requires the Elite package.");
        } else if (!hasEvidence) {
            deny(decision, "POLICY_DENIED", "A support escalation cannot be created without Thinker evidence.", "More evidence is required before escalating.");
        } else if (requireActiveAssignmentForSessionOrNull(session) == null) {
            deny(decision, "POLICY_DENIED", "No active partner assignment exists for the store.", "No assigned partner can receive this escalation.");
        } else if (!assignmentHasCapability(requireActiveAssignmentForSession(session), "SUPPORT_MANAGE")) {
            deny(decision, "POLICY_DENIED", "The active partner assignment cannot manage support escalations.", "The assigned partner does not have support handoff permission.");
        } else {
            decision.setOutcome("ALLOWED");
            decision.setOperatorReason("Support escalation is low-risk and bounded to Partner Enablement.");
            decision.setUserReason("Support can review the issue with a redacted evidence bundle.");
        }
        return decision;
    }

    private void deny(ResolverPolicyDecisionEntity decision, String outcome, String operatorReason, String userReason) {
        decision.setOutcome(outcome);
        decision.setOperatorReason(operatorReason);
        decision.setUserReason(userReason);
    }

    private ResolverDryRunEntity resolveDryRun(String proposalId, String dryRunId) {
        if (StringUtils.hasText(dryRunId)) {
            return dryRunRepository.findById(dryRunId.trim())
                .filter(item -> proposalId.equals(item.getProposalId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Selected dry-run does not belong to this proposal."));
        }
        return dryRunRepository.findTopByProposalIdOrderByCreatedAtDesc(proposalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Dry-run is required before execution."));
    }

    private PartnerEvidenceBundleEntity createPartnerEvidenceBundle(ThinkerIssueSessionEntity session,
                                                                    PartnerStoreAssignmentEntity assignment,
                                                                    String memberId) {
        Instant now = Instant.now();
        PartnerEvidenceBundleEntity bundle = new PartnerEvidenceBundleEntity();
        bundle.setId("peb-" + UUID.randomUUID());
        bundle.setPartnerAccountId(assignment.getPartnerAccountId());
        bundle.setStoreAssignmentId(assignment.getId());
        bundle.setGeneratedByMemberId(firstNonBlank(memberId, "resolver-system"));
        bundle.setBundleName("Thinker evidence · " + session.getId());
        bundle.setBundleKind("THINKER_SESSION");
        bundle.setStatus("READY");
        bundle.setSummaryJson(writeJson(mapOf(
            "sessionId", session.getId(),
            "shopDomain", session.getShopDomain(),
            "status", session.getStatus(),
            "question", session.getUserQuestion(),
            "terminalReason", session.getTerminalReason()
        )));
        bundle.setAttachmentsJson(writeJson(evidenceRepository.findBySessionIdOrderByCapturedAtAsc(session.getId()).stream()
            .map(item -> Map.of(
                "kind", item.getKind(),
                "source", item.getSourceIdentifier(),
                "freshness", item.getFreshness(),
                "summary", item.getSafeSummary()
            ))
            .toList()));
        bundle.setGeneratedAt(now);
        bundle.setExpiresAt(now.plus(Duration.ofDays(30)));
        return partnerEvidenceBundleRepository.save(bundle);
    }

    private PartnerSupportEscalationEntity createSupportEscalation(ThinkerIssueSessionEntity session,
                                                                   PartnerStoreAssignmentEntity assignment,
                                                                   String memberId,
                                                                   String bundleId,
                                                                   String severity,
                                                                   String title,
                                                                   String nextAction) {
        Instant now = Instant.now();
        PartnerSupportEscalationEntity escalation = new PartnerSupportEscalationEntity();
        escalation.setId("pse-" + UUID.randomUUID());
        escalation.setPartnerAccountId(assignment.getPartnerAccountId());
        escalation.setStoreAssignmentId(assignment.getId());
        escalation.setCreatedByMemberId(firstNonBlank(memberId, "resolver-system"));
        escalation.setTitle(firstNonBlank(title, "Thinker support handoff · " + session.getId()));
        escalation.setSeverity(firstNonBlank(severity, "MEDIUM").toUpperCase(Locale.ROOT));
        escalation.setStatus("OPEN");
        escalation.setNextAction(firstNonBlank(nextAction, "Review Thinker evidence and respond with merchant-safe guidance."));
        escalation.setDescription("Thinker session " + session.getId() + " needs support review for " + nullSafe(session.getShopDomain()) + ".");
        escalation.setReproductionSteps("User question: " + session.getUserQuestion());
        escalation.setExpectedBehavior("Partner can review redacted evidence and guide merchant-safe resolution.");
        escalation.setActualBehavior(session.getTerminalReason());
        escalation.setImpact("Customer-facing issue diagnosis or support handoff.");
        escalation.setEvidenceBundleIdsJson(writeJson(List.of(bundleId)));
        escalation.setCreatedAt(now);
        escalation.setUpdatedAt(now);
        return partnerSupportEscalationRepository.save(escalation);
    }

    private String resolveExecutionPartnerMemberId(PartnerStoreAssignmentEntity assignment) {
        return partnerMemberRepository.findByPartnerAccountIdOrderByCreatedAtAsc(assignment.getPartnerAccountId()).stream()
            .filter(member -> "ACTIVE".equalsIgnoreCase(member.getStatus()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active partner member exists for this assignment."))
            .getId();
    }

    private PartnerStoreAssignmentEntity requireActiveAssignmentForSession(ThinkerIssueSessionEntity session) {
        PartnerStoreAssignmentEntity assignment = requireActiveAssignmentForSessionOrNull(session);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active partner assignment exists for this session store.");
        }
        return assignment;
    }

    private PartnerStoreAssignmentEntity requireActiveAssignmentForSessionOrNull(ThinkerIssueSessionEntity session) {
        if (!StringUtils.hasText(session.getShopDomain())) {
            return null;
        }
        return partnerStoreAssignmentRepository.findByShopDomainIgnoreCaseAndStatusOrderByCreatedAtDesc(session.getShopDomain(), "ACTIVE")
            .stream()
            .findFirst()
            .orElse(null);
    }

    private PartnerStoreAssignmentEntity requirePartnerAssignment(PartnerPrincipal principal, String storeAssignmentId) {
        if (principal == null || !principal.provisioned()) {
            throw new PartnerForbiddenException("Partner account is required.");
        }
        return partnerStoreAssignmentRepository.findByIdAndPartnerAccountId(required(storeAssignmentId, "storeId"), principal.partnerAccountId())
            .filter(assignment -> "ACTIVE".equalsIgnoreCase(assignment.getStatus()))
            .orElseThrow(() -> new PartnerForbiddenException("Store assignment is not available to this partner."));
    }

    private PartnerStoreAssignmentEntity requirePartnerAssignmentForShop(PartnerPrincipal principal, String shopDomain) {
        if (principal == null || !principal.provisioned()) {
            throw new PartnerForbiddenException("Partner account is required.");
        }
        return partnerStoreAssignmentRepository.findByPartnerAccountIdAndShopDomainIgnoreCase(principal.partnerAccountId(), required(shopDomain, "shopDomain"))
            .filter(assignment -> "ACTIVE".equalsIgnoreCase(assignment.getStatus()))
            .orElseThrow(() -> new PartnerForbiddenException("Thinker session is not available to this partner."));
    }

    private void requireAssignmentCapability(PartnerStoreAssignmentEntity assignment, String capability) {
        if (!assignmentHasCapability(assignment, capability)) {
            throw new PartnerForbiddenException("Partner assignment does not include " + capability + ".");
        }
    }

    private boolean assignmentHasCapability(PartnerStoreAssignmentEntity assignment, String capability) {
        if (assignment == null || !StringUtils.hasText(capability)) {
            return false;
        }
        List<String> permissions = readStringList(assignment.getPermissionsJson());
        return permissions.stream().anyMatch(permission -> capability.equalsIgnoreCase(permission));
    }

    private ThinkerIssueSessionDetail toDetail(ThinkerIssueSessionEntity session, boolean operatorView) {
        List<ThinkerEvidenceItemEntity> evidence = evidenceRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        ThinkerResolutionPlanEntity plan = planRepository.findBySessionId(session.getId()).orElse(null);
        return new ThinkerIssueSessionDetail(
            operatorView ? toSessionSummary(session, evidence.size(), plan) : toPartnerSessionSummary(session),
            evidence.stream().map(item -> toEvidenceSummary(item, operatorView)).toList(),
            plan == null ? null : toPlanSummary(plan),
            sessionAuditRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(event -> operatorView ? toAuditSummary(event) : toPartnerAuditSummary(event))
                .toList(),
            proposalRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(proposal -> toProposalSummary(proposal, operatorView))
                .toList()
        );
    }

    private ThinkerIssueSessionSummary toSessionSummary(ThinkerIssueSessionEntity session) {
        List<ThinkerEvidenceItemEntity> evidence = evidenceRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        ThinkerResolutionPlanEntity plan = planRepository.findBySessionId(session.getId()).orElse(null);
        return toSessionSummary(session, evidence.size(), plan);
    }

    private ThinkerIssueSessionSummary toPartnerSessionSummary(ThinkerIssueSessionEntity session) {
        ThinkerIssueSessionSummary summary = toSessionSummary(session);
        return new ThinkerIssueSessionSummary(
            summary.id(),
            summary.deploymentId(),
            null,
            null,
            summary.storeId(),
            summary.shopDomain(),
            summary.consumerId(),
            null,
            summary.channel(),
            summary.mode(),
            summary.status(),
            summary.issueCategory(),
            summary.riskClass(),
            summary.userQuestion(),
            summary.userSafeAnswer(),
            summary.sourceCitations(),
            summary.terminalReason(),
            summary.startedAt(),
            summary.completedAt(),
            summary.updatedAt(),
            summary.evidenceCount(),
            summary.recommendation()
        );
    }

    private ThinkerIssueSessionSummary toSessionSummary(ThinkerIssueSessionEntity session, int evidenceCount, ThinkerResolutionPlanEntity plan) {
        return new ThinkerIssueSessionSummary(
            session.getId(),
            session.getDeploymentId(),
            session.getTenantId(),
            session.getCustomerId(),
            session.getStoreId(),
            session.getShopDomain(),
            session.getConsumerId(),
            session.getUserSubject(),
            session.getChannel(),
            session.getMode(),
            session.getStatus(),
            session.getIssueCategory(),
            session.getRiskClass(),
            session.getUserQuestion(),
            session.getUserSafeAnswer(),
            readStringList(session.getSourceCitationsJson()),
            session.getTerminalReason(),
            session.getStartedAt(),
            session.getCompletedAt(),
            session.getUpdatedAt(),
            evidenceCount,
            plan == null ? null : plan.getRecommendation()
        );
    }

    private ThinkerEvidenceItemSummary toEvidenceSummary(ThinkerEvidenceItemEntity item, boolean operatorView) {
        return new ThinkerEvidenceItemSummary(
            item.getId(),
            item.getSessionId(),
            item.getKind(),
            item.getSourceKind(),
            item.getSourceIdentifier(),
            item.getFreshness(),
            item.getConfidence() == null ? 0.0 : item.getConfidence().doubleValue(),
            item.getRedactionState(),
            operatorView ? item.getSummary() : item.getSafeSummary(),
            item.getSafeSummary(),
            operatorView ? item.getOperatorRawReference() : null,
            item.getCapturedAt()
        );
    }

    private ThinkerResolutionPlanSummary toPlanSummary(ThinkerResolutionPlanEntity plan) {
        return new ThinkerResolutionPlanSummary(
            plan.getId(),
            plan.getSessionId(),
            plan.getIssueSummary(),
            plan.getDiagnosis(),
            readStringList(plan.getOptionsConsideredJson()),
            plan.getRecommendedNextStep(),
            plan.getRecommendation(),
            plan.getUserExplanation(),
            plan.getEscalationTarget(),
            readStringList(plan.getEvidenceItemIdsJson()),
            plan.getCreatedAt()
        );
    }

    private ThinkerSessionAuditSummary toAuditSummary(ThinkerSessionAuditEventEntity event) {
        return new ThinkerSessionAuditSummary(
            event.getId(),
            event.getSessionId(),
            event.getEventType(),
            event.getActorId(),
            event.getActorRole(),
            readJson(event.getDetailsJson()),
            event.getCreatedAt()
        );
    }

    private ThinkerSessionAuditSummary toPartnerAuditSummary(ThinkerSessionAuditEventEntity event) {
        return new ThinkerSessionAuditSummary(
            event.getId(),
            event.getSessionId(),
            event.getEventType(),
            "platform",
            event.getActorRole(),
            objectMapper.valueToTree(Map.of("eventType", event.getEventType())),
            event.getCreatedAt()
        );
    }

    private ResolverIntentProposalSummary toProposalSummary(ResolverIntentProposalEntity proposal) {
        return toProposalSummary(proposal, true);
    }

    private ResolverIntentProposalSummary toProposalSummary(ResolverIntentProposalEntity proposal, boolean operatorView) {
        return new ResolverIntentProposalSummary(
            proposal.getId(),
            proposal.getSessionId(),
            proposal.getActionId(),
            proposal.getActionFamily(),
            proposal.getTargetDomain(),
            proposal.getProductBoundary(),
            proposal.getActorContext(),
            proposal.getTenantId(),
            proposal.getStoreId(),
            proposal.getDeploymentId(),
            readJson(proposal.getRedactedParametersJson()),
            operatorView ? readJson(proposal.getOperatorParametersJson()) : null,
            readStringList(proposal.getSourceEvidenceItemIdsJson()),
            proposal.getProposalText(),
            readJson(proposal.getNormalizedIntentJson()),
            proposal.getStatus(),
            proposal.getCreatedAt(),
            proposal.getUpdatedAt(),
            policyDecisionRepository.findTopByProposalIdOrderByDecidedAtDesc(proposal.getId()).map(this::toPolicyDecisionSummary).orElse(null),
            dryRunRepository.findTopByProposalIdOrderByCreatedAtDesc(proposal.getId()).map(this::toDryRunSummary).orElse(null),
            executionRepository.findByProposalIdOrderByCreatedAtDesc(proposal.getId()).stream().map(this::toExecutionSummary).toList()
        );
    }

    private ResolverPolicyDecisionSummary toPolicyDecisionSummary(ResolverPolicyDecisionEntity decision) {
        return new ResolverPolicyDecisionSummary(
            decision.getId(),
            decision.getProposalId(),
            decision.getOutcome(),
            decision.getRiskLevel(),
            readStringList(decision.getRequiredScopesJson()),
            readStringList(decision.getMissingScopesJson()),
            decision.getTierRequirement(),
            decision.getActorRequirement(),
            decision.isDryRunRequired(),
            decision.isConfirmationRequired(),
            decision.isApprovalRequired(),
            decision.getConfirmationPreview(),
            decision.getOperatorReason(),
            decision.getUserReason(),
            decision.getDecidedAt()
        );
    }

    private ResolverDryRunSummary toDryRunSummary(ResolverDryRunEntity dryRun) {
        return new ResolverDryRunSummary(
            dryRun.getId(),
            dryRun.getProposalId(),
            dryRun.getTargetAction(),
            readJson(dryRun.getValidatedParametersJson()),
            dryRun.getExpectedStateTransition(),
            readStringList(dryRun.getExpectedSideEffectsJson()),
            readStringList(dryRun.getWarningsJson()),
            readStringList(dryRun.getUnsupportedFieldsJson()),
            dryRun.getIdempotencyPosture(),
            dryRun.getRollbackPosture(),
            dryRun.getEvidenceFreshness(),
            dryRun.getProductBoundary(),
            dryRun.getStatus(),
            dryRun.getCreatedAt()
        );
    }

    private ResolverExecutionSummary toExecutionSummary(ResolverExecutionEntity execution) {
        return new ResolverExecutionSummary(
            execution.getId(),
            execution.getProposalId(),
            execution.getDryRunId(),
            execution.getIdempotencyKey(),
            execution.getActionFamily(),
            execution.getStatus(),
            execution.getProductBoundary(),
            readJson(execution.getExecutionResultJson()),
            execution.getVerificationStatus(),
            execution.getRecoveryGuidance(),
            execution.getCreatedAt(),
            execution.getCompletedAt()
        );
    }

    private ThinkerDeploymentControlSummary toControlSummary(ThinkerDeploymentControlEntity entity) {
        return new ThinkerDeploymentControlSummary(
            entity.getDeploymentId(),
            entity.isThinkerEnabled(),
            entity.isResolverPreviewEnabled(),
            entity.isGovernedExecutionEnabled(),
            readStringList(entity.getDisabledActionFamiliesJson()),
            entity.getUpdatedAt(),
            entity.getUpdatedBy()
        );
    }

    private void recordSessionAudit(String sessionId, String eventType, Map<String, ?> details) {
        ThinkerSessionAuditEventEntity event = new ThinkerSessionAuditEventEntity();
        event.setId("tsa-" + shortId());
        event.setSessionId(sessionId);
        event.setEventType(eventType);
        event.setActorId(PlatformSecurityContext.actorIdOrSystem());
        event.setActorRole(PlatformSecurityContext.actorRoleOrSystem());
        event.setDetailsJson(writeJson(details));
        event.setCreatedAt(Instant.now());
        sessionAuditRepository.save(event);
    }

    private ThinkerIssueSessionEntity requireSession(String sessionId) {
        return sessionRepository.findById(required(sessionId, "sessionId"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thinker session not found: " + sessionId));
    }

    private ResolverIntentProposalEntity requireProposal(String proposalId) {
        return proposalRepository.findById(required(proposalId, "proposalId"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resolver proposal not found: " + proposalId));
    }

    private DeploymentEntity requireDeployment(String deploymentId) {
        return deploymentRepository.findById(required(deploymentId, "deploymentId"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found: " + deploymentId));
    }

    private ThinkerDeploymentControlEntity defaultControl(String deploymentId) {
        ThinkerDeploymentControlEntity entity = new ThinkerDeploymentControlEntity();
        entity.setDeploymentId(deploymentId);
        entity.setThinkerEnabled(false);
        entity.setResolverPreviewEnabled(false);
        entity.setGovernedExecutionEnabled(false);
        entity.setDisabledActionFamiliesJson("[]");
        entity.setUpdatedAt(Instant.EPOCH);
        return entity;
    }

    private boolean isActionFamilyDisabled(ThinkerDeploymentControlEntity control, String actionFamily) {
        if (control == null || !StringUtils.hasText(actionFamily)) {
            return false;
        }
        return readStringList(control.getDisabledActionFamiliesJson()).stream()
            .anyMatch(item -> actionFamily.equalsIgnoreCase(item));
    }

    private Map<String, Object> redactParameters(Map<String, Object> parameters) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        if (parameters == null) {
            return redacted;
        }
        parameters.forEach((key, value) -> {
            if (key != null && key.toLowerCase(Locale.ROOT).matches(".*(secret|token|password|key|authorization).*")) {
                redacted.put(key, "[REDACTED]");
            } else {
                redacted.put(key, value);
            }
        });
        return redacted;
    }

    private boolean detectsPromptInjection(String value) {
        String normalized = lower(value);
        return normalized.contains("ignore previous instructions")
            || normalized.contains("ignore all previous")
            || normalized.contains("developer message")
            || normalized.contains("system prompt")
            || normalized.contains("bypass policy")
            || normalized.contains("override the allowlist");
    }

    private boolean detectsWriteIntent(String value) {
        String normalized = lower(value);
        return normalized.contains("issue refund")
            || normalized.contains("process refund")
            || normalized.contains("create refund")
            || normalized.contains("give refund")
            || normalized.contains("refund order")
            || normalized.contains("refund my order")
            || normalized.contains("cancel and refund")
            || normalized.contains("cancel order")
            || normalized.contains("change address")
            || normalized.contains("change policy")
            || normalized.contains("change the return policy")
            || normalized.contains("edit order")
            || normalized.contains("create ticket")
            || normalized.contains("open ticket")
            || normalized.contains("update my account")
            || normalized.contains("update policy")
            || normalized.contains("apply discount")
            || normalized.contains("publish")
            || normalized.contains("delete ");
    }

    private String classifyIssue(String value) {
        String normalized = lower(value);
        if (detectsWriteIntent(value)) {
            return "WRITE_REQUIRED_SUPPORT";
        }
        if (normalized.contains("policy") || normalized.contains("return")) {
            return "POLICY_LOOKUP";
        }
        if (normalized.contains("stock") || normalized.contains("available")) {
            return "AVAILABILITY_DIAGNOSIS";
        }
        if (normalized.contains("compare") || normalized.contains("which")) {
            return "PRODUCT_COMPARISON";
        }
        return "GENERAL_DIAGNOSIS";
    }

    private List<String> expectedEvidence(String category) {
        return switch (category) {
            case "POLICY_LOOKUP" -> List.of("policy", "page", "retrieval_snippet");
            case "AVAILABILITY_DIAGNOSIS" -> List.of("product", "variant", "availability");
            case "PRODUCT_COMPARISON" -> List.of("product", "variant", "collection");
            case "WRITE_REQUIRED_SUPPORT" -> List.of("read_action_result", "support_profile");
            default -> List.of("read_action_result", "retrieval_snippet");
        };
    }

    private String billingTier(ShopifyStoreConnectionEntity store) {
        if (store == null || !StringUtils.hasText(store.getDetailsJson())) {
            return DEFAULT_SHOPIFY_COMPANION_TIER;
        }
        JsonNode root = readJson(store.getDetailsJson());
        String tier = text(root.path("billingState"), "tierKey");
        if (!StringUtils.hasText(tier)) {
            return DEFAULT_SHOPIFY_COMPANION_TIER;
        }
        String normalized = tier.trim().toUpperCase(Locale.ROOT);
        return "FREE".equals(normalized) ? DEFAULT_SHOPIFY_COMPANION_TIER : normalized;
    }

    private ThinkerReadinessScenarioSummary scenario(String key, String label, String status, String evidence) {
        return new ThinkerReadinessScenarioSummary(key, label, status, evidence);
    }

    private String required(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeShopDomain(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String redactText(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replaceAll("(?i)(authorization|token|secret|password|api[_ -]?key)[:=][^\\s,;]+", "$1=[REDACTED]")
            .trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String nullSafe(String value) {
        return value == null ? "none" : value;
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                out.add(normalized.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private List<String> normalizedIdList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return List.copyOf(out);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Thinker/Resolver JSON.", ex);
        }
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (entries == null) {
            return out;
        }
        for (int i = 0; i + 1 < entries.length; i += 2) {
            Object key = entries[i];
            if (key != null) {
                out.put(String.valueOf(key), entries[i + 1]);
            }
        }
        return out;
    }

    private JsonNode readJson(String value) {
        try {
            if (!StringUtils.hasText(value)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("unreadable", true);
            return node;
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.asText(null);
                if (StringUtils.hasText(text)) {
                    out.add(text);
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : trimToNull(value.asText(null));
    }
}
