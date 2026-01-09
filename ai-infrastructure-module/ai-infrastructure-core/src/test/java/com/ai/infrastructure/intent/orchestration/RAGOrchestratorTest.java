package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.config.PIIDetectionProperties;
import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.config.SmartSuggestionsProperties;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.history.IntentHistoryService;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.pipeline.DefaultOrchestrationPipeline;
import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.AccessControlStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.ComplianceCheckStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.HistoryPersistenceStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.IntentExtractionStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.IntentHandlingStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.MetadataBuildingStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.OrchestrationResultNormalizationStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.PIIDetectionStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.ResponseSanitizationStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.SecurityAnalysisStep;
import com.ai.infrastructure.intent.orchestration.pipeline.steps.SmartSuggestionsStep;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.security.ResponseSanitizer;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AISecurityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGOrchestratorTest {

    @Mock
    private IntentQueryExtractor intentQueryExtractor;

    @Mock
    private ActionHandlerRegistry actionHandlerRegistry;

    @Mock
    private RAGProvider ragProvider;

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private AIServiceConfig aiServiceConfig;

    @Mock
    private ObjectProvider<AdvancedRAGProvider> advancedRagProvider;

    @Mock
    private ActionHandler actionHandler;

    @Mock
    private IntentHistoryService intentHistoryService;

    @Mock
    private AISecurityService securityService;

    @Mock
    private AIAccessControlService accessControlService;

    @Mock
    private AIComplianceService complianceService;

    private ResponseSanitizer responseSanitizer;
    private SmartSuggestionsProperties smartSuggestionsProperties;
    private PIIDetectionService piiDetectionService;
    private PIIDetectionProperties piiDetectionProperties;

    private RAGOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        smartSuggestionsProperties = new SmartSuggestionsProperties();
        ResponseSanitizationProperties sanitizationProperties = new ResponseSanitizationProperties();
        sanitizationProperties.setEnabled(false);
        piiDetectionProperties = new PIIDetectionProperties();
        piiDetectionProperties.setEnabled(true);
        piiDetectionProperties.setDetectionDirection(PIIDetectionProperties.PIIDetectionDirection.INPUT_OUTPUT);
        piiDetectionService = new PIIDetectionService(piiDetectionProperties);
        responseSanitizer = new ResponseSanitizer(piiDetectionService, sanitizationProperties);
        when(intentHistoryService.recordIntent(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(securityService.analyzeRequest(any())).thenReturn(
            AISecurityResponse.builder()
                .shouldBlock(false)
                .accessAllowed(true)
                .success(true)
                .build()
        );
        when(accessControlService.checkAccess(any())).thenReturn(
            AIAccessControlResponse.builder()
                .accessGranted(true)
                .success(true)
                .build()
        );
        when(complianceService.checkCompliance(any())).thenReturn(
            AIComplianceResponse.builder()
                .overallCompliant(true)
                .success(true)
                .build()
        );
        
        // Create pipeline with all steps
        Pipeline pipeline = createPipeline();
        orchestrator = new RAGOrchestrator(pipeline);
    }
    
    /**
     * Creates a pipeline with all required steps for testing.
     */
    private Pipeline createPipeline() {
        var normalizationProperties = new com.ai.infrastructure.config.OrchestrationResultNormalizationProperties();
        normalizationProperties.setEnabled(true);
        normalizationProperties.setDebugSnapshotEnabled(false);
        var normalizer = new OrchestrationResultNormalizer();

        List<PipelineStep> steps = List.of(
            new SecurityAnalysisStep(securityService),
            new AccessControlStep(accessControlService),
            new PIIDetectionStep(piiDetectionService, piiDetectionProperties),
            new ComplianceCheckStep(complianceService),
            new IntentExtractionStep(intentQueryExtractor),
            new IntentHandlingStep(actionHandlerRegistry, ragProvider, aiCoreService, aiServiceConfig, advancedRagProvider),
            new OrchestrationResultNormalizationStep(normalizer, normalizationProperties),
            new MetadataBuildingStep(),
            new SmartSuggestionsStep(smartSuggestionsProperties, ragProvider),
            new ResponseSanitizationStep(responseSanitizer, piiDetectionProperties),
            new HistoryPersistenceStep(intentHistoryService)
        );
        return new DefaultOrchestrationPipeline(steps);
    }

    @Test
    void shouldExecuteActionIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .actionParams(Map.of("reason", "too expensive"))
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("cancel_subscription")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed("user-1")).thenReturn(true);
        when(actionHandler.getConfirmationMessage(intent.getActionParams())).thenReturn("Confirm cancellation?");
        when(actionHandlerRegistry.findMetadata("cancel_subscription")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(intent.getActionParams(), "user-1"))
            .thenReturn(ActionResult.builder().success(true).message("Cancelled").build());

        OrchestrationResult result = orchestrator.orchestrate("Cancel my plan", "user-1");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Cancelled");
        assertThat(result.getData()).containsEntry("confirmationMessage", "Confirm cancellation?");
        assertThat(result.getSanitizedPayload()).isNotEmpty();
        verify(intentHistoryService).recordIntent(eq("user-1"), any(), eq("Cancel my plan"), any(), any());
    }

    @Test
    void shouldReturnErrorWhenHandlerMissing() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("unknown_action")
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("unknown_action")).thenReturn(Optional.empty());

        OrchestrationResult result = orchestrator.orchestrate("Do something", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void shouldHandleActionValidationFailure() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("cancel_subscription")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed("user")).thenReturn(false);

        OrchestrationResult result = orchestrator.orchestrate("Cancel", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_DENIED);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void shouldInvokeHandleErrorWhenExecutionThrows() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("cancel_subscription")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed("user")).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("cancel_subscription")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), eq("user"))).thenThrow(new IllegalStateException("boom"));
        when(actionHandler.handleError(any(), eq("user")))
            .thenReturn(ActionResult.builder().success(false).message("boom").build());

        OrchestrationResult result = orchestrator.orchestrate("Cancel", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(result.getMessage()).isEqualTo("boom");
    }

    @Test
    void shouldProcessInformationIntentViaRag() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace("policies")
            .requiresGeneration(true)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        RAGResponse ragResponse = RAGResponse.builder()
            .context("Refunds take 5-7 days.")
            .documents(List.of())
            .build();
        when(ragProvider.performRAGQuery(any(RAGRequest.class))).thenReturn(ragResponse);
        when(aiCoreService.generateText(anyString())).thenReturn("Refunds take 5-7 days.");

        OrchestrationResult result = orchestrator.orchestrate("What is your refund policy?", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Refunds take 5-7 days.");

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider).performRAGQuery(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEntityType()).isEqualTo("policies");
    }

    @Test
    void shouldRouteInformationIntentToGenerationWhenRequested() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("product_recommendations")
            .optimizedQuery("Product entities where price_usd < 100 and stock_status = 'in_stock'")
            .requiresGeneration(true)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        RAGResponse ragResponse = RAGResponse.builder()
            .context("Top picks context.")
            .documents(List.of())
            .success(true)
            .build();
        when(ragProvider.performRAGQuery(any(RAGRequest.class))).thenReturn(ragResponse);
        when(aiCoreService.generateText(anyString())).thenReturn("Here are top picks.");

        OrchestrationResult result = orchestrator.orchestrate("Recommend products under $100", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Here are top picks.");

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider).performRAGQuery(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMetadata()).containsEntry("optimizedQuery", intent.getOptimizedQuery());
        verify(ragProvider, never()).performRag(any(RAGRequest.class));
    }

    @Test
    void shouldUseAdvancedRagWhenLlmRequestsIt_andFallbackToOrchestratorGenerationWhenProviderOmitsResponse() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Recommend the best audio gear for commuting with noise cancellation and battery life considerations.")
            .requiresGeneration(true)
            .needsAdvancedRAG(true)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        AIServiceConfig.FeatureFlags features = AIServiceConfig.FeatureFlags.builder()
            .enableAdvancedRAG(true)
            .autoEnableAdvancedRAGForComplexQueries(false)
            .build();
        when(aiServiceConfig.getFeatures()).thenReturn(features);

        AdvancedRAGProvider provider = mock(AdvancedRAGProvider.class);
        when(advancedRagProvider.getIfAvailable()).thenReturn(provider);

        when(provider.performAdvancedRAG(any(AdvancedRAGRequest.class))).thenReturn(
            AdvancedRAGResponse.builder()
                .success(true)
                .context("Some relevant context from advanced retrieval.")
                .response(null) // Force orchestrator fallback generation
                .documents(List.of())
                .build()
        );
        when(aiCoreService.generateText(anyString())).thenReturn("Generated answer from orchestrator.");

        OrchestrationResult result = orchestrator.orchestrate("What should I buy for commuting?", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Generated answer from orchestrator.");

        verify(provider).performAdvancedRAG(any(AdvancedRAGRequest.class));
        verify(ragProvider, never()).performRAGQuery(any(RAGRequest.class));
        verify(ragProvider, never()).performRag(any(RAGRequest.class));
        verify(aiCoreService).generateText(anyString());
    }

    @Test
    void shouldNotUseAdvancedRagWhenLlmDeclinesEvenIfHeuristicsWouldMatch() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Can you recommend a wireless headset with ANC, low latency, multi-device pairing, and long battery life?")
            .requiresGeneration(true)
            .needsAdvancedRAG(false) // LLM explicitly says no
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        AIServiceConfig.FeatureFlags features = AIServiceConfig.FeatureFlags.builder()
            .enableAdvancedRAG(true)
            .autoEnableAdvancedRAGForComplexQueries(true) // Would enable heuristics if LLM didn't decide
            .build();
        when(aiServiceConfig.getFeatures()).thenReturn(features);

        AdvancedRAGProvider provider = mock(AdvancedRAGProvider.class);
        when(advancedRagProvider.getIfAvailable()).thenReturn(provider);

        when(ragProvider.performRAGQuery(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .context("Basic retrieval context.")
                .documents(List.of())
                .success(true)
                .build()
        );
        when(aiCoreService.generateText(anyString())).thenReturn("Basic generated answer.");

        OrchestrationResult result = orchestrator.orchestrate("Recommend audio gear", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Basic generated answer.");

        verify(provider, never()).performAdvancedRAG(any(AdvancedRAGRequest.class));
        verify(ragProvider).performRAGQuery(any(RAGRequest.class));
    }

    @Test
    void shouldNotUseAdvancedRagWhenConfigDisablesIt_evenIfLlmRequestsIt() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Recommend audio gear for commuting with ANC, comfort, and battery life.")
            .requiresGeneration(true)
            .needsAdvancedRAG(true)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        AIServiceConfig.FeatureFlags features = AIServiceConfig.FeatureFlags.builder()
            .enableAdvancedRAG(false) // Config constraint: disable advanced
            .autoEnableAdvancedRAGForComplexQueries(true)
            .build();
        when(aiServiceConfig.getFeatures()).thenReturn(features);

        AdvancedRAGProvider provider = mock(AdvancedRAGProvider.class);
        when(advancedRagProvider.getIfAvailable()).thenReturn(provider);

        when(ragProvider.performRAGQuery(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .context("Basic retrieval context.")
                .documents(List.of())
                .success(true)
                .build()
        );
        when(aiCoreService.generateText(anyString())).thenReturn("Basic generated answer.");

        OrchestrationResult result = orchestrator.orchestrate("Recommend audio gear", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Basic generated answer.");

        verify(provider, never()).performAdvancedRAG(any(AdvancedRAGRequest.class));
        verify(ragProvider).performRAGQuery(any(RAGRequest.class));
    }

    @Test
    void shouldFallBackToHeuristicsWhenLlmDoesNotProvideAdvancedDecision() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Can you recommend a wireless headset with ANC, low latency, multi-device pairing, and long battery life?")
            .requiresGeneration(true)
            .build(); // needsAdvancedRAG is null (LLM did not provide)
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        AIServiceConfig.FeatureFlags features = AIServiceConfig.FeatureFlags.builder()
            .enableAdvancedRAG(true)
            .autoEnableAdvancedRAGForComplexQueries(true)
            .build();
        when(aiServiceConfig.getFeatures()).thenReturn(features);

        AdvancedRAGProvider provider = mock(AdvancedRAGProvider.class);
        when(advancedRagProvider.getIfAvailable()).thenReturn(provider);
        when(provider.performAdvancedRAG(any(AdvancedRAGRequest.class))).thenReturn(
            AdvancedRAGResponse.builder()
                .success(true)
                .context("Advanced provider context.")
                .response("Advanced provider answer.")
                .documents(List.of())
                .build()
        );

        OrchestrationResult result = orchestrator.orchestrate("Recommend audio gear", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Advanced provider answer.");

        verify(provider).performAdvancedRAG(any(AdvancedRAGRequest.class));
        verify(aiCoreService, never()).generateText(anyString());
        verify(ragProvider, never()).performRAGQuery(any(RAGRequest.class));
    }

    @Test
    void shouldHandleOutOfScopeIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.OUT_OF_SCOPE)
            .actionParams(Map.of("reason", "Unsupported domain"))
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        OrchestrationResult result = orchestrator.orchestrate("Build me a spaceship", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.OUT_OF_SCOPE);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldDenyActionForAnonymousSession() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        OrchestrationContext anonymous = OrchestrationContext.forSession("sess-123");

        OrchestrationResult result = orchestrator.orchestrate("Cancel my plan", anonymous);

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_DENIED);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void shouldHandleCompoundIntents() {
        Intent actionIntent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .build();
        Intent infoIntent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .build();
        MultiIntentResponse compound = MultiIntentResponse.builder()
            .intents(List.of(actionIntent, infoIntent))
            .compound(true)
            .build();

        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class))).thenReturn(compound);
        when(actionHandlerRegistry.findHandler("cancel_subscription")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("cancel_subscription")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Cancelled").build());
        lenient().when(ragProvider.performRag(any())).thenReturn(RAGResponse.builder()
            .context("info")
            .success(true)
            .build());

        OrchestrationResult result = orchestrator.orchestrate("Cancel and explain refund", "user");

        // Provider-agnostic contract: compound wrappers are normalized into the primary intent type.
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.getChildren()).hasSize(2);
    }

    @Test
    void shouldIncludeNextStepRecommendations() {
        NextStepRecommendation recommendation = NextStepRecommendation.builder()
            .intent("view_billing_history")
            .confidence(0.71)
            .build();
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("update_payment_method")
            .nextStepRecommended(recommendation)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("update_payment_method")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("update_payment_method")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Updated").build());
        when(ragProvider.performRag(any(RAGRequest.class))).thenReturn(RAGResponse.builder()
            .context("Your payment method is confirmed.")
            .documents(List.of())
            .build());

        OrchestrationResult result = orchestrator.orchestrate("Update my payment method", "user");

        assertThat(result.getNextSteps()).containsExactly(recommendation);
        assertThat(result.getSmartSuggestion())
            .containsEntry("intent", "view_billing_history")
            .containsEntry("response", "Your payment method is confirmed.");
        assertThat(result.getData()).containsKey("smartSuggestion");
    }

    @Test
    void shouldSkipSmartSuggestionWhenConfidenceBelowThreshold() {
        // Recreate pipeline with updated properties
        smartSuggestionsProperties.setMinConfidence(0.8d);
        Pipeline pipeline = createPipeline();
        orchestrator = new RAGOrchestrator(pipeline);

        NextStepRecommendation recommendation = NextStepRecommendation.builder()
            .intent("view_billing_history")
            .confidence(0.75)
            .query("Show my billing history")
            .build();
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("update_payment_method")
            .nextStepRecommended(recommendation)
            .build();

        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("update_payment_method")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("update_payment_method")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Updated").build());

        OrchestrationResult result = orchestrator.orchestrate("Update my payment method", "user");

        assertThat(result.getNextSteps()).containsExactly(recommendation);
        assertThat(result.getSmartSuggestion()).isEmpty();
        verify(ragProvider, never()).performRag(any(RAGRequest.class));
    }

    @Test
    void shouldNotInvokeSmartSuggestionsWhenDisabled() {
        // Recreate pipeline with updated properties
        smartSuggestionsProperties.setEnabled(false);
        Pipeline pipeline = createPipeline();
        orchestrator = new RAGOrchestrator(pipeline);

        NextStepRecommendation recommendation = NextStepRecommendation.builder()
            .intent("view_billing_history")
            .confidence(0.9)
            .query("Show my billing history")
            .build();
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("update_payment_method")
            .nextStepRecommended(recommendation)
            .build();

        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("update_payment_method")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("update_payment_method")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Updated").build());

        OrchestrationResult result = orchestrator.orchestrate("Update my payment method", "user");

        assertThat(result.getNextSteps()).containsExactly(recommendation);
        assertThat(result.getSmartSuggestion()).isEmpty();
        verify(ragProvider, never()).performRag(any(RAGRequest.class));
    }
}
