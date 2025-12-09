package com.ai.infrastructure.it;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.ResponseSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.pii-detection.enabled=false",
    "ai.smart-suggestions.enabled=false"
})
class IntentGenerationRoutingIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @MockBean(name = "ragService")
    private RAGService ragService;

    @MockBean
    private AISecurityService securityService;

    @MockBean
    private AIAccessControlService accessControlService;

    @MockBean
    private AIComplianceService complianceService;

    @MockBean
    private ResponseSanitizer responseSanitizer;

    @MockBean
    private ActionHandlerRegistry actionHandlerRegistry;

    @BeforeEach
    void setUp() {
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
        when(responseSanitizer.sanitize(any(), any())).thenReturn(Map.of("sanitization", Map.of()));
    }

    @Test
    void routesSearchOnlyWhenGenerationNotRequired() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("find_products")
            .vectorSpace("product")
            .optimizedQuery("Product entities with price_usd < 60 and stock_status = 'in_stock'")
            .requiresGeneration(false)
            .build();
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(ragService.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder().response("search-only").documents(List.of()).success(true).build()
        );

        OrchestrationResult result = orchestrator.orchestrate("show me products under $60", "user-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("search-only");

        ArgumentCaptor<RAGRequest> captor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragService).performRag(captor.capture());
        assertThat(captor.getValue().getMetadata()).containsEntry("optimizedQuery", intent.getOptimizedQuery());
        verify(ragService, never()).performRAGQuery(any());
    }

    @Test
    void routesToGenerationWhenFlagged() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Product entities with sentiment = 'positive'")
            .requiresGeneration(true)
            .build();
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());
        when(ragService.performRAGQuery(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder().response("llm-needed").documents(List.of()).success(true).build()
        );

        OrchestrationResult result = orchestrator.orchestrate("what should I buy next?", "user-2");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("llm-needed");

        ArgumentCaptor<RAGRequest> captor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragService).performRAGQuery(captor.capture());
        assertThat(captor.getValue().getMetadata()).containsEntry("requiresGeneration", true);
        verify(ragService, never()).performRag(any());
    }
}
