package com.ai.fabric.platform.backend.thinker.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ThinkerResolverModels {
    private ThinkerResolverModels() {
    }

    public record ThinkerDeploymentControlSummary(
        String deploymentId,
        boolean thinkerEnabled,
        boolean resolverPreviewEnabled,
        boolean governedExecutionEnabled,
        List<String> disabledActionFamilies,
        Instant updatedAt,
        String updatedBy
    ) {
    }

    public record UpdateThinkerDeploymentControlRequest(
        Boolean thinkerEnabled,
        Boolean resolverPreviewEnabled,
        Boolean governedExecutionEnabled,
        List<String> disabledActionFamilies
    ) {
    }

    public record CreateThinkerIssueSessionRequest(
        @NotBlank String deploymentId,
        String shopDomain,
        String tenantId,
        String customerId,
        String storeId,
        String consumerId,
        String userSubject,
        String locale,
        String channel,
        String mode,
        @NotBlank String userQuestion,
        String userSafeAnswer,
        List<CreateEvidenceItemRequest> evidence,
        String assertedTenantId,
        String runtimeConversationId,
        String runtimeSessionId
    ) {
    }

    public record CreateEvidenceItemRequest(
        String kind,
        String sourceKind,
        String sourceIdentifier,
        String freshness,
        Double confidence,
        String summary,
        String safeSummary,
        String operatorRawReference
    ) {
    }

    public record ThinkerIssueSessionSummary(
        String id,
        String deploymentId,
        String tenantId,
        String customerId,
        String storeId,
        String shopDomain,
        String consumerId,
        String userSubject,
        String channel,
        String mode,
        String status,
        String issueCategory,
        String riskClass,
        String userQuestion,
        String userSafeAnswer,
        List<String> sourceCitations,
        String terminalReason,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt,
        int evidenceCount,
        String recommendation
    ) {
    }

    public record ThinkerIssueSessionDetail(
        ThinkerIssueSessionSummary session,
        List<ThinkerEvidenceItemSummary> evidence,
        ThinkerResolutionPlanSummary plan,
        List<ThinkerSessionAuditSummary> auditEvents,
        List<ResolverIntentProposalSummary> resolverProposals
    ) {
    }

    public record ThinkerEvidenceItemSummary(
        String id,
        String sessionId,
        String kind,
        String sourceKind,
        String sourceIdentifier,
        String freshness,
        double confidence,
        String redactionState,
        String summary,
        String safeSummary,
        String operatorRawReference,
        Instant capturedAt
    ) {
    }

    public record ThinkerResolutionPlanSummary(
        String id,
        String sessionId,
        String issueSummary,
        String diagnosis,
        List<String> optionsConsidered,
        String recommendedNextStep,
        String recommendation,
        String userExplanation,
        String escalationTarget,
        List<String> evidenceItemIds,
        Instant createdAt
    ) {
    }

    public record ThinkerSessionAuditSummary(
        String id,
        String sessionId,
        String eventType,
        String actorId,
        String actorRole,
        JsonNode details,
        Instant createdAt
    ) {
    }

    public record CreateResolverProposalRequest(
        @NotBlank String sessionId,
        String actionId,
        String actionFamily,
        String targetDomain,
        String productBoundary,
        String actorContext,
        Map<String, Object> parameters,
        List<String> sourceEvidenceItemIds,
        @NotBlank String proposalText
    ) {
    }

    public record ResolverIntentProposalSummary(
        String id,
        String sessionId,
        String actionId,
        String actionFamily,
        String targetDomain,
        String productBoundary,
        String actorContext,
        String tenantId,
        String storeId,
        String deploymentId,
        JsonNode redactedParameters,
        JsonNode operatorParameters,
        List<String> sourceEvidenceItemIds,
        String proposalText,
        JsonNode normalizedIntent,
        String status,
        Instant createdAt,
        Instant updatedAt,
        ResolverPolicyDecisionSummary latestPolicyDecision,
        ResolverDryRunSummary latestDryRun,
        List<ResolverExecutionSummary> executions
    ) {
    }

    public record ResolverPolicyDecisionSummary(
        String id,
        String proposalId,
        String outcome,
        String riskLevel,
        List<String> requiredScopes,
        List<String> missingScopes,
        String tierRequirement,
        String actorRequirement,
        boolean dryRunRequired,
        boolean confirmationRequired,
        boolean approvalRequired,
        String confirmationPreview,
        String operatorReason,
        String userReason,
        Instant decidedAt
    ) {
    }

    public record ResolverDryRunSummary(
        String id,
        String proposalId,
        String targetAction,
        JsonNode validatedParameters,
        String expectedStateTransition,
        List<String> expectedSideEffects,
        List<String> warnings,
        List<String> unsupportedFields,
        String idempotencyPosture,
        String rollbackPosture,
        String evidenceFreshness,
        String productBoundary,
        String status,
        Instant createdAt
    ) {
    }

    public record ExecuteResolverProposalRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String confirmationText,
        String dryRunId
    ) {
    }

    public record ResolverExecutionSummary(
        String id,
        String proposalId,
        String dryRunId,
        String idempotencyKey,
        String actionFamily,
        String status,
        String productBoundary,
        JsonNode executionResult,
        String verificationStatus,
        String recoveryGuidance,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record PartnerThinkerEscalationRequest(
        String severity,
        String title,
        String nextAction
    ) {
    }

    public record ThinkerReadinessSummary(
        String status,
        String decision,
        Instant generatedAt,
        int passed,
        int failed,
        List<ThinkerReadinessScenarioSummary> scenarios,
        List<String> blockers
    ) {
    }

    public record ThinkerReadinessScenarioSummary(
        String key,
        String label,
        String status,
        String evidence
    ) {
    }

    public record ShopifyThinkerHealthSummary(
        String shopDomain,
        String deploymentId,
        boolean thinkerEnabled,
        String status,
        long sessionsLast7Days,
        long failedSessionsLast7Days,
        String message,
        List<String> nextActions
    ) {
    }
}
