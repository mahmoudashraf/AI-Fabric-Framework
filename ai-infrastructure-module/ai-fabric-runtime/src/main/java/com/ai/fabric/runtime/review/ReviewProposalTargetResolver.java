package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.SpecialistCapabilityResolver;
import ai.fabric.execution.gateway.SpecialistCapabilityResolutionException;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.invocation.ActionConfirmationState;
import ai.fabric.intent.action.invocation.GovernedActionInvocation;
import ai.fabric.intent.action.invocation.GovernedActionInvocationService;
import ai.fabric.intent.action.invocation.GovernedActionInvocationStatus;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.pipeline.steps.OrchestrationPolicyResolutionStep;
import ai.fabric.intent.orchestration.request.ConversationPersistencePolicy;
import ai.fabric.intent.orchestration.request.OrchestrationRequest;
import ai.fabric.intent.orchestration.request.OrchestrationRequestPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = {"ai.execution.reviews.enabled", "app.human-review.proposals.enabled"}, havingValue = "true")
public class ReviewProposalTargetResolver {
    private final ReviewProposalProperties properties;
    private final AIActionRegistry actions;
    private final SpecialistRegistry specialists;
    private final OrchestrationPolicyResolutionStep policies;
    private final SpecialistCapabilityResolver capabilities;
    private final GovernedActionInvocationService invocations;

    public ReviewProposalTargetResolver(ReviewProposalProperties properties, AIActionRegistry actions,
        SpecialistRegistry specialists, OrchestrationPolicyResolutionStep policies,
        SpecialistCapabilityResolver capabilities, GovernedActionInvocationService invocations) {
        this.properties = properties; this.actions = actions; this.specialists = specialists;
        this.policies = policies; this.capabilities = capabilities; this.invocations = invocations;
    }

    public List<OrchestrationAttachment> resolve(String candidate, TrustedExecutionContext source) {
        var lookup = properties.targetLookup();
        if (lookup == null) {
            if (candidate != null && !candidate.isBlank()) throw unavailable("Target lookup is not configured.");
            return List.of();
        }
        if (candidate == null || candidate.isBlank() || candidate.length() > 200) {
            throw unavailable("A selected target reference is required for this workflow.");
        }
        var metadata = actions.findMetadata(lookup.action()).orElseThrow(() -> unavailable("The target lookup is not registered."));
        // The configured preflight must never become a business-write execution surface.
        if (metadata.getAccessMode() != ActionAccessMode.READ) throw unavailable("The target lookup must be READ-only.");
        var definition = specialists.require(ReviewProposalConfiguration.SPECIALIST_ID);
        var context = OrchestrationContext.builder().userId(source.subject().subjectId())
            .mode(definition.executionProfile().mode()).position(definition.executionProfile().mode()).build();
        var request = new OrchestrationRequest("Resolve the application-selected target.", context, source,
            ConversationPersistencePolicy.NEVER, null, null, null, OrchestrationRequestPurpose.SPECIALIST);
        var preflight = policies.process(PipelineContext.from(request));
        if (preflight.isShouldTerminate()) throw unavailable("Current policy denied target lookup.");
        ai.fabric.intent.orchestration.capability.EffectiveCapabilityProfile effective;
        try {
            effective = capabilities.resolve(definition, preflight, source);
        } catch (SpecialistCapabilityResolutionException denied) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current caller authority denied the target lookup.");
        }
        var outcome = invocations.invoke(new GovernedActionInvocation(lookup.action(),
            Map.of(lookup.parameter(), candidate.trim()),
            new ActionContext(preflight.getOrchestrationContext(), preflight), source, effective,
            ActionConfirmationState.NOT_CONFIRMED, List.of()));
        var result = outcome.actionResult();
        if (outcome.status() != GovernedActionInvocationStatus.EXECUTED
            || result == null || !result.isSuccess() || result.getPinnedTargets() == null || result.getPinnedTargets().size() != 1) {
            throw unavailable("The authorized lookup did not return one unambiguous target.");
        }
        var target = result.getPinnedTargets().getFirst();
        if (target == null || target.id() == null || target.id().isBlank()
            || !java.util.Objects.equals(lookup.vectorSpace(), target.vectorSpace())) {
            throw unavailable("The lookup returned an incompatible target projection.");
        }
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        if (target.metadata() != null) safeMetadata.putAll(target.metadata());
        safeMetadata.put("id", target.id());
        return List.of(OrchestrationAttachment.builder().id(target.id()).vectorSpace(target.vectorSpace())
            .contentText(target.contentText()).metadata(safeMetadata).source(lookup.action()).build());
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
