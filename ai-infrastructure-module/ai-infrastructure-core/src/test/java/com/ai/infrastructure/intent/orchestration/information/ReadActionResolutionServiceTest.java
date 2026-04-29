package com.ai.infrastructure.intent.orchestration.information;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplate;
import com.ai.infrastructure.prompt.PromptTemplateKey;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import com.ai.infrastructure.prompt.ResolvedPromptTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadActionResolutionServiceTest {

    @Test
    void shouldPreferPurposeBuiltAvailabilityActionForExplicitSkuAvailabilityRequests() {
        AICoreService aiCoreService = mock(AICoreService.class);
        AIActionRegistry actionRegistry = mock(AIActionRegistry.class);
        PromptTemplateResolver templateResolver = mock(PromptTemplateResolver.class);

        AIActionMetaData availability = AIActionMetaData.builder()
            .name("check_availability")
            .description("Check product stock by SKU.")
            .category("commerce")
            .accessMode(ActionAccessMode.READ)
            .groundingEligible(true)
            .readActionResolutionEligible(true)
            .requiredParameters(Set.of("sku"))
            .build();
        AIActionMetaData productDetails = AIActionMetaData.builder()
            .name("get_product_details")
            .description("Get full product details by SKU.")
            .category("commerce")
            .accessMode(ActionAccessMode.READ)
            .groundingEligible(true)
            .readActionResolutionEligible(true)
            .requiredParameters(Set.of("sku"))
            .build();

        AIActionHandler availabilityHandler = mock(AIActionHandler.class);
        AIActionHandler detailsHandler = mock(AIActionHandler.class);
        when(availabilityHandler.validateActionAllowed(any(ActionContext.class))).thenReturn(true);
        when(availabilityHandler.executeAction(eq(Map.of("sku", "SKU-AAA-100")), any(ActionContext.class)))
            .thenReturn(ActionResult.builder()
                .success(true)
                .message("Availability")
                .data(ActionPayload.object(Map.of(
                    "sku", "SKU-AAA-100",
                    "available", true,
                    "inventory", 21
                )))
                .build());
        when(availabilityHandler.buildPostActionLlmFacts(any(ActionResult.class), any(ActionContext.class))).thenReturn(
            Optional.of(Map.of(
                "sku", "SKU-AAA-100",
                "available", true,
                "inventory", 21
            ))
        );

        when(actionRegistry.getAllMetadata()).thenReturn(List.of(availability, productDetails));
        when(actionRegistry.findHandler("check_availability")).thenReturn(Optional.of(availabilityHandler));
        when(actionRegistry.findHandler("get_product_details")).thenReturn(Optional.of(detailsHandler));
        when(actionRegistry.findMetadata("check_availability")).thenReturn(Optional.of(availability));
        when(templateResolver.resolve("orchestration/read-action-resolution", "system"))
            .thenReturn(resolvedTemplate("system", ""));
        when(templateResolver.resolve("orchestration/read-action-resolution", "user"))
            .thenReturn(resolvedTemplate("user",
                "mode={{mode}}\nquery={{query}}\nintent={{intent_json}}\nactions={{eligible_actions_json}}\nprior={{prior_evidence_json}}\n"
                    + "max={{max_actions_per_iteration}}\ntotal={{max_total_actions}}\nrag={{rag_cooperation_mode}}\n"
                    + "iteration={{iteration}}\niterations={{max_iterations}}"));
        when(aiCoreService.generateContent(any(), eq(LlmPurpose.ORCHESTRATION))).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "decision": "EXECUTE_READ_ACTIONS_AND_RAG",
                      "actions": [
                        {"name": "get_product_details", "params": {"sku": "SKU-AAA-100"}, "priority": 1}
                      ],
                      "needsMoreSteps": false
                    }
                    """)
                .build()
        );

        ReadActionResolutionService service = new ReadActionResolutionService(
            aiCoreService,
            actionRegistry,
            new IntentExtractionJsonSupport(new ObjectMapper()),
            templateResolver,
            new PromptRenderer()
        );

        ReadActionResolutionService.ResolutionOutcome outcome = service.resolve(
            Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("Is SKU-AAA-100 available right now?")
                .optimizedQuery("Check availability for SKU-AAA-100.")
                .build(),
            OrchestrationContext.forUser("user-1"),
            PipelineContext.from("Is SKU-AAA-100 available right now?", OrchestrationContext.forUser("user-1"))
                .toBuilder()
                .orchestrationPolicy(readActionPolicy("resolver_assistant", List.of("check_availability", "get_product_details"),
                    OrchestrationProperties.ReadActionResolutionPlanningMode.SINGLE_PASS,
                    OrchestrationProperties.ReadActionResolutionRagCooperationMode.RAG_IF_ACTIONS_INSUFFICIENT))
                .build()
        );

        assertThat(outcome.attempted()).isTrue();
        assertThat(outcome.useRag()).isFalse();
        assertThat(outcome.canAnswerFromActionEvidenceOnly()).isTrue();
        assertThat(outcome.executedActions()).hasSize(1);
        assertThat(outcome.executedActions().getFirst().actionName()).isEqualTo("check_availability");
        assertThat(outcome.diagnostics()).containsEntry("executedActionsCount", 1);
        assertThat(outcome.diagnostics()).containsEntry("finalDecision", "EXECUTE_READ_ACTIONS");
        verify(availabilityHandler).executeAction(eq(Map.of("sku", "SKU-AAA-100")), any(ActionContext.class));
        verify(detailsHandler, never()).executeAction(any(), any(ActionContext.class));
    }

    @Test
    void shouldExecuteOnlyPlannerEligibleReadActionsFromAllowlist() {
        AICoreService aiCoreService = mock(AICoreService.class);
        AIActionRegistry actionRegistry = mock(AIActionRegistry.class);
        PromptTemplateResolver templateResolver = mock(PromptTemplateResolver.class);

        AIActionMetaData eligibleRead = AIActionMetaData.builder()
            .name("get_policy")
            .description("Return the store refund policy.")
            .category("policy")
            .accessMode(ActionAccessMode.READ)
            .groundingEligible(true)
            .readActionResolutionEligible(true)
            .requiredParameters(Set.of("policyType"))
            .build();
        AIActionMetaData ineligibleWrite = AIActionMetaData.builder()
            .name("delete_order")
            .description("Delete an order.")
            .category("orders")
            .accessMode(ActionAccessMode.WRITE_ONLY)
            .groundingEligible(false)
            .readActionResolutionEligible(false)
            .requiredParameters(Set.of("orderId"))
            .build();

        AIActionHandler readHandler = mock(AIActionHandler.class);
        AIActionHandler writeHandler = mock(AIActionHandler.class);
        when(readHandler.validateActionAllowed(any(ActionContext.class))).thenReturn(true);
        when(readHandler.executeAction(eq(Map.of("policyType", "refund")), any(ActionContext.class))).thenReturn(
            ActionResult.builder()
                .success(true)
                .message("Refund policy loaded.")
                .data(ActionPayload.object(Map.of("windowDays", 30)))
                .build()
        );
        when(readHandler.buildPostActionLlmFacts(any(ActionResult.class), any(ActionContext.class))).thenReturn(
            Optional.of(Map.of("policyType", "refund", "windowDays", 30))
        );

        when(actionRegistry.getAllMetadata()).thenReturn(List.of(eligibleRead, ineligibleWrite));
        when(actionRegistry.findHandler("get_policy")).thenReturn(Optional.of(readHandler));
        when(actionRegistry.findHandler("delete_order")).thenReturn(Optional.of(writeHandler));
        when(actionRegistry.findMetadata("get_policy")).thenReturn(Optional.of(eligibleRead));
        when(templateResolver.resolve("orchestration/read-action-resolution", "system"))
            .thenReturn(resolvedTemplate("system", ""));
        when(templateResolver.resolve("orchestration/read-action-resolution", "user"))
            .thenReturn(resolvedTemplate("user",
                "mode={{mode}}\nquery={{query}}\nintent={{intent_json}}\nactions={{eligible_actions_json}}\nprior={{prior_evidence_json}}\n"
                    + "max={{max_actions_per_iteration}}\ntotal={{max_total_actions}}\nrag={{rag_cooperation_mode}}\n"
                    + "iteration={{iteration}}\niterations={{max_iterations}}"));
        when(aiCoreService.generateContent(any(), eq(LlmPurpose.ORCHESTRATION))).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "decision": "EXECUTE_READ_ACTIONS",
                      "actions": [
                        {"name": "delete_order", "params": {"orderId": "ord-1"}, "priority": 1},
                        {"name": "get_policy", "params": {"policyType": "refund"}, "priority": 2}
                      ],
                      "needsMoreSteps": false
                    }
                    """)
                .build()
        );

        ReadActionResolutionService service = new ReadActionResolutionService(
            aiCoreService,
            actionRegistry,
            new IntentExtractionJsonSupport(new ObjectMapper()),
            templateResolver,
            new PromptRenderer()
        );

        ReadActionResolutionService.ResolutionOutcome outcome = service.resolve(
            Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("What is the refund policy?")
                .optimizedQuery("refund policy")
                .build(),
            OrchestrationContext.forUser("user-1"),
            PipelineContext.from("What is the refund policy?", OrchestrationContext.forUser("user-1"))
                .toBuilder()
                .orchestrationPolicy(readActionPolicy("resolver_assistant", List.of("get_policy"),
                    OrchestrationProperties.ReadActionResolutionPlanningMode.SINGLE_PASS,
                    OrchestrationProperties.ReadActionResolutionRagCooperationMode.NONE))
                .build()
        );

        assertThat(outcome.attempted()).isTrue();
        assertThat(outcome.useRag()).isFalse();
        assertThat(outcome.canAnswerFromActionEvidenceOnly()).isTrue();
        assertThat(outcome.executedActions()).hasSize(1);
        assertThat(outcome.executedActions().getFirst().actionName()).isEqualTo("get_policy");
        assertThat(outcome.diagnostics()).containsEntry("executedActionsCount", 1);
        verify(readHandler).executeAction(eq(Map.of("policyType", "refund")), any(ActionContext.class));
        verify(writeHandler, never()).executeAction(any(), any(ActionContext.class));
    }

    @Test
    void shouldRefusePlannerProposalWhenRequiredParamsAreMissingAndFallBackToRag() {
        AICoreService aiCoreService = mock(AICoreService.class);
        AIActionRegistry actionRegistry = mock(AIActionRegistry.class);
        PromptTemplateResolver templateResolver = mock(PromptTemplateResolver.class);

        AIActionMetaData availability = AIActionMetaData.builder()
            .name("check_availability")
            .description("Check product stock.")
            .category("catalog")
            .accessMode(ActionAccessMode.READ)
            .groundingEligible(true)
            .readActionResolutionEligible(true)
            .requiredParameters(Set.of("sku"))
            .build();
        AIActionHandler availabilityHandler = mock(AIActionHandler.class);

        when(actionRegistry.getAllMetadata()).thenReturn(List.of(availability));
        when(actionRegistry.findHandler("check_availability")).thenReturn(Optional.of(availabilityHandler));
        when(actionRegistry.findMetadata("check_availability")).thenReturn(Optional.of(availability));
        when(templateResolver.resolve("orchestration/read-action-resolution", "system"))
            .thenReturn(resolvedTemplate("system", ""));
        when(templateResolver.resolve("orchestration/read-action-resolution", "user"))
            .thenReturn(resolvedTemplate("user",
                "mode={{mode}}\nquery={{query}}\nintent={{intent_json}}\nactions={{eligible_actions_json}}\nprior={{prior_evidence_json}}\n"
                    + "max={{max_actions_per_iteration}}\ntotal={{max_total_actions}}\nrag={{rag_cooperation_mode}}\n"
                    + "iteration={{iteration}}\niterations={{max_iterations}}"));
        when(aiCoreService.generateContent(any(), eq(LlmPurpose.ORCHESTRATION))).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "decision": "EXECUTE_READ_ACTIONS",
                      "actions": [
                        {"name": "check_availability", "params": {}, "priority": 1}
                      ],
                      "needsMoreSteps": false
                    }
                    """)
                .build()
        );

        ReadActionResolutionService service = new ReadActionResolutionService(
            aiCoreService,
            actionRegistry,
            new IntentExtractionJsonSupport(new ObjectMapper()),
            templateResolver,
            new PromptRenderer()
        );

        ReadActionResolutionService.ResolutionOutcome outcome = service.resolve(
            Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("Is SKU-0001 available?")
                .optimizedQuery("SKU-0001 availability")
                .build(),
            OrchestrationContext.forUser("user-1"),
            PipelineContext.from("Is SKU-0001 available?", OrchestrationContext.forUser("user-1"))
                .toBuilder()
                .orchestrationPolicy(readActionPolicy("resolver_assistant", List.of("check_availability"),
                    OrchestrationProperties.ReadActionResolutionPlanningMode.SINGLE_PASS,
                    OrchestrationProperties.ReadActionResolutionRagCooperationMode.RAG_IF_ACTIONS_INSUFFICIENT))
                .build()
        );

        assertThat(outcome.attempted()).isTrue();
        assertThat(outcome.executedActions()).isEmpty();
        assertThat(outcome.useRag()).isTrue();
        verify(availabilityHandler, never()).executeAction(any(), any(ActionContext.class));
    }

    private ResolvedPromptTemplate resolvedTemplate(String name, String body) {
        return new ResolvedPromptTemplate(
            new PromptTemplate(new PromptTemplateKey("orchestration/read-action-resolution", "v1", name), body),
            List.of("v1")
        );
    }

    private OrchestrationPolicy readActionPolicy(String mode,
                                                 List<String> allowedActions,
                                                 OrchestrationProperties.ReadActionResolutionPlanningMode planningMode,
                                                 OrchestrationProperties.ReadActionResolutionRagCooperationMode ragMode) {
        return new OrchestrationPolicy(
            OrchestrationProfile.PRODUCTION_CHAT,
            mode,
            null,
            OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE,
            OrchestrationPolicy.OrchestrationCapabilities.defaults(),
            new OrchestrationPolicy.ReadActionResolutionPolicy(
                true,
                planningMode,
                allowedActions,
                true,
                2,
                2,
                3,
                1,
                4_000,
                1_200,
                ragMode,
                true
            ),
            OrchestrationPolicy.RagBudgets.defaults()
        );
    }
}
