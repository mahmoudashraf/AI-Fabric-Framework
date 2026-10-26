package com.ai.fabric.runtime.review;

import ai.fabric.evidence.AIEvidenceReference;
import ai.fabric.execution.action.ActionOutcomeProjector;
import ai.fabric.execution.action.ActionOutcomeProjectorRegistry;
import ai.fabric.execution.action.ActionOutcomeView;
import ai.fabric.execution.specialist.*;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.AIActionNames;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.capability.RequestedCapabilityProfile;
import ai.fabric.execution.specialist.manifest.SpecialistConversationBinding;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"ai.execution.reviews.enabled", "app.human-review.proposals.enabled"}, havingValue = "true")
@EnableConfigurationProperties(ReviewProposalProperties.class)
public class ReviewProposalConfiguration {
    public static final SpecialistId SPECIALIST_ID = SpecialistId.of("application-action-review", "1");
    public static final String SPECIALIST_SCOPE = "specialist:application-action-review@1";

    @Bean
    SpecialistDefinition<ReviewProposalInput, String> applicationActionReviewSpecialist(ReviewProposalProperties properties) {
        var actions = properties.allowedActions();
        var lookup = properties.targetLookup();
        Set<String> reads = lookup == null ? Set.of() : Set.of(lookup.action());
        Set<String> visible = new java.util.TreeSet<>(actions);
        visible.addAll(reads);
        return new SpecialistDefinition<>(
            new SpecialistIdentity(SPECIALIST_ID, "Application action review",
                "Proposes an authorized application action for a separate human decision."),
            new SpecialistInstructions(
                "Prepare one registered action proposal from the application request. "
                    + "Use the supplied request facts; ask for missing necessary information rather than inventing it. "
                    + "The application will submit a durable receipt for separate human review. "
                    + "Do not execute, confirm, approve or claim completion. "
                    + "Identity, tenant, deployment and authorization come only from trusted context.", null),
            new SpecialistExecutionProfile("executor",
                new RequestedCapabilityProfile(false, Set.of(), visible, reads, actions),
                ExecutionStrategy.SINGLE_PASS, SpecialistWritePolicy.CONFIRMATION_RECEIPT_REQUIRED),
            SpecialistLimits.defaults(),
            new SpecialistInputAdapter<>() {
                public Class<ReviewProposalInput> inputType() { return ReviewProposalInput.class; }
                public void validate(ReviewProposalInput input) {
                    if (input == null || input.question() == null || input.question().isBlank() || input.question().length() > 8000) {
                        throw new IllegalArgumentException("The action request must contain 1 to 8000 characters.");
                    }
                }
                public String renderModelInput(ReviewProposalInput input) { return input.question(); }
                public OrchestrationContext orchestrationContext(ReviewProposalInput input) {
                    return OrchestrationContext.builder().attachments(input.verifiedAttachments()).build();
                }
                public SpecialistConversationBinding conversationBinding() { return SpecialistConversationBinding.DISABLED; }
                public boolean recordValidatedTurns() { return false; }
            },
            new SpecialistOutputAdapter<>() {
                public Class<String> outputType() { return String.class; }
                public String project(OrchestrationResult result, List<AIEvidenceReference> evidence) {
                    return result.getMessage();
                }
                public void validate(String output) { }
            }
        );
    }

    @Bean
    ActionOutcomeProjectorRegistry applicationReviewOutcomeProjectors(
        ReviewProposalProperties properties, List<ActionOutcomeProjector> existing
    ) {
        List<ActionOutcomeProjector> projectors = new ArrayList<>(existing);
        for (String action : properties.allowedActions()) {
            if (existing.stream().anyMatch(projector -> action.equals(AIActionNames.normalize(projector.actionName())))) continue;
            // Never expose raw external handler maps, contact details or internal targets.
            projectors.add(new ActionOutcomeProjector() {
                public String actionName() { return action; }
                public ActionOutcomeView project(ActionResult result) {
                    boolean completed = result != null && result.isSuccess();
                    return new ActionOutcomeView(action,
                        completed ? "The application action completed." : "The application action did not complete.",
                        Map.of("completed", completed));
                }
            });
        }
        return new ActionOutcomeProjectorRegistry(projectors);
    }
}
