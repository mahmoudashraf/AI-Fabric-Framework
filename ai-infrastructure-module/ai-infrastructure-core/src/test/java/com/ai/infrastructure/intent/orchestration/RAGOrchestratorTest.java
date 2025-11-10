package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.config.PIIDetectionProperties;
import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.config.SmartSuggestionsProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.intent.history.IntentHistoryService;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.RAGService;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private RAGService ragService;

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

    @Mock
    private AuditService auditService;

    private ResponseSanitizer responseSanitizer;
    private SmartSuggestionsProperties smartSuggestionsProperties;
    private Clock clock;

    private RAGOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        smartSuggestionsProperties = new SmartSuggestionsProperties();
        ResponseSanitizationProperties sanitizationProperties = new ResponseSanitizationProperties();
        sanitizationProperties.setEnabled(false);
        PIIDetectionProperties piiDetectionProperties = new PIIDetectionProperties();
        piiDetectionProperties.setEnabled(true);
        piiDetectionProperties.setDetectionDirection(PIIDetectionProperties.PIIDetectionDirection.BOTH);
        PIIDetectionService piiDetectionService = new PIIDetectionService(piiDetectionProperties);
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
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        orchestrator = new RAGOrchestrator(
            intentQueryExtractor,
            actionHandlerRegistry,
            ragService,
            responseSanitizer,
            intentHistoryService,
            smartSuggestionsProperties,
            piiDetectionService,
            piiDetectionProperties,
            securityService,
            accessControlService,
            complianceService,
            auditService,
            clock
        );
    }

    @Test
    void shouldExecuteActionIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("cancel_subscription")
            .actionParams(Map.of("reason", "too expensive"))
            .build();
        when(intentQueryExtractor.extract(any(), any()))
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
        when(intentQueryExtractor.extract(any(), any()))
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
        when(intentQueryExtractor.extract(any(), any()))
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
        when(intentQueryExtractor.extract(any(), any()))
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
            .build();
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        RAGResponse ragResponse = RAGResponse.builder()
            .response("Refunds take 5-7 days.")
            .documents(List.of())
            .build();
        when(ragService.performRag(any(RAGRequest.class))).thenReturn(ragResponse);

        OrchestrationResult result = orchestrator.orchestrate("What is your refund policy?", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isEqualTo("Refunds take 5-7 days.");

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragService).performRag(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEntityType()).isEqualTo("policies");
    }

    @Test
    void shouldHandleOutOfScopeIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.OUT_OF_SCOPE)
            .actionParams(Map.of("reason", "Unsupported domain"))
            .build();
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        OrchestrationResult result = orchestrator.orchestrate("Build me a spaceship", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.OUT_OF_SCOPE);
        assertThat(result.isSuccess()).isTrue();
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

        when(intentQueryExtractor.extract(any(), any())).thenReturn(compound);
        when(actionHandlerRegistry.findHandler("cancel_subscription")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("cancel_subscription")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Cancelled").build());
        lenient().when(ragService.performRag(any())).thenReturn(RAGResponse.builder().response("info").build());

        OrchestrationResult result = orchestrator.orchestrate("Cancel and explain refund", "user");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.COMPOUND_HANDLED);
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
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(actionHandlerRegistry.findHandler("update_payment_method")).thenReturn(Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.getConfirmationMessage(any())).thenReturn("Confirm?");
        when(actionHandlerRegistry.findMetadata("update_payment_method")).thenReturn(Optional.empty());
        when(actionHandler.executeAction(any(), any()))
            .thenReturn(ActionResult.builder().success(true).message("Updated").build());
        when(ragService.performRag(any(RAGRequest.class))).thenReturn(RAGResponse.builder()
            .response("Your payment method is confirmed.")
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
        smartSuggestionsProperties.setMinConfidence(0.8d);

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

        when(intentQueryExtractor.extract(any(), any()))
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
        verify(ragService, never()).performRag(any(RAGRequest.class));
    }

    @Test
    void shouldNotInvokeSmartSuggestionsWhenDisabled() {
        smartSuggestionsProperties.setEnabled(false);

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

        when(intentQueryExtractor.extract(any(), any()))
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
        verify(ragService, never()).performRag(any(RAGRequest.class));
    }
}
