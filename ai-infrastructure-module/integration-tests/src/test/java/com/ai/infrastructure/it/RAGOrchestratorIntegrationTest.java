package com.ai.infrastructure.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.ResponseSanitizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.pii-detection.enabled=false",
    "ai.smart-suggestions.enabled=false"
})
class RAGOrchestratorIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @MockBean
    private AISecurityService securityService;

    @MockBean
    private AIAccessControlService accessControlService;

    @MockBean
    private AIComplianceService complianceService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @MockBean
    private ActionHandlerRegistry actionHandlerRegistry;

    @MockBean(name = "RAGService")
    private RAGService ragServiceUpper;

    @MockBean(name = "ragService")
    private RAGService ragService;

    @MockBean
    private ResponseSanitizer responseSanitizer;

    private ActionHandler actionHandler;

    @BeforeEach
    void setupDefaults() {
        actionHandler = mock(ActionHandler.class);

        when(securityService.analyzeRequest(any())).thenReturn(
            AISecurityResponse.builder()
                .requestId("sec")
                .userId("user")
                .threatsDetected(List.of())
                .securityScore(100.0)
                .accessAllowed(true)
                .rateLimitExceeded(false)
                .shouldBlock(false)
                .success(true)
                .build()
        );

        when(accessControlService.checkAccess(any())).thenReturn(
            AIAccessControlResponse.builder()
                .accessGranted(true)
                .fromCache(false)
                .success(true)
                .build()
        );

        when(complianceService.checkCompliance(any())).thenReturn(
            AIComplianceResponse.builder()
                .overallCompliant(true)
                .success(true)
                .build()
        );

        Intent informationIntent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("find_data")
            .vectorSpace("default")
            .build();

        when(intentQueryExtractor.extract(any(), any())).thenReturn(
            MultiIntentResponse.builder()
                .compound(false)
                .intents(List.of(informationIntent))
                .build()
        );

        when(ragService.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .response("ok")
                .documents(List.of())
                .success(true)
                .build()
        );
        when(ragServiceUpper.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .response("ok")
                .documents(List.of())
                .success(true)
                .build()
        );

        when(responseSanitizer.sanitize(any(), any())).thenReturn(Map.of());
        when(actionHandlerRegistry.findHandler(any())).thenReturn(java.util.Optional.of(actionHandler));
        when(actionHandler.validateActionAllowed(any())).thenReturn(true);
        when(actionHandler.executeAction(any(), any())).thenReturn(
            ActionResult.builder()
                .success(true)
                .message("done")
                .build()
        );
        when(actionHandler.getConfirmationMessage(any())).thenReturn("confirmed");
    }

    @Test
    void hooksInvokedInOrder() {
        OrchestrationResult result = orchestrator.orchestrate("hello world", "user");

        assertTrue(result.isSuccess());

        InOrder order = inOrder(securityService, accessControlService, complianceService, ragService);
        order.verify(securityService).analyzeRequest(any());
        order.verify(accessControlService).checkAccess(any());
        order.verify(complianceService).checkCompliance(any());
        order.verify(ragService).performRag(any());
    }

    @Test
    void securityFailureShortCircuitsFlow() {
        when(securityService.analyzeRequest(any())).thenReturn(
            AISecurityResponse.builder()
                .requestId("sec")
                .userId("user")
                .threatsDetected(List.of("INJECTION_ATTACK"))
                .securityScore(10.0)
                .accessAllowed(false)
                .rateLimitExceeded(false)
                .shouldBlock(true)
                .success(true)
                .build()
        );

        OrchestrationResult result = orchestrator.orchestrate("malicious", "user");

        assertFalse(result.isSuccess());
        InOrder order = inOrder(securityService, accessControlService, complianceService, ragService);
        order.verify(securityService).analyzeRequest(any());
        order.verify(accessControlService, never()).checkAccess(any());
        order.verify(complianceService, never()).checkCompliance(any());
        order.verify(ragService, never()).performRag(any());
    }

    @Test
    void complianceFailureStopsRagExecution() {
        when(complianceService.checkCompliance(any())).thenReturn(
            AIComplianceResponse.builder()
                .overallCompliant(false)
                .violations(List.of("GDPR"))
                .success(true)
                .build()
        );

        OrchestrationResult result = orchestrator.orchestrate("hello world", "user");

        assertFalse(result.isSuccess());
        InOrder order = inOrder(securityService, accessControlService, complianceService, ragService);
        order.verify(securityService).analyzeRequest(any());
        order.verify(accessControlService).checkAccess(any());
        order.verify(complianceService).checkCompliance(any());
        order.verify(ragService, never()).performRag(any());
    }
}
