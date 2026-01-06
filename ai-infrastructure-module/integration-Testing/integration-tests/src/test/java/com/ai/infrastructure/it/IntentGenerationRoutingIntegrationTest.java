package com.ai.infrastructure.it;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.ResponseSanitizer;
import com.ai.infrastructure.spi.ContentRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for intent generation routing.
 * 
 * <p>Tests that the orchestrator correctly routes queries based on intent type
 * and the requiresGeneration flag.</p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.pii-detection.enabled=false",
    "ai.smart-suggestions.enabled=false",
    "spring.task.scheduling.enabled=false"
})
class IntentGenerationRoutingIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private List<PipelineStep> pipelineSteps;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

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

    private ContentRetriever mockContentRetriever;

    @BeforeEach
    void setUp() {
        // Configure security mocks
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

        // Create and configure ContentRetriever mock
        mockContentRetriever = mock(ContentRetriever.class);
        when(mockContentRetriever.isAvailable()).thenReturn(true);
        when(mockContentRetriever.retrieve(anyString(), anyString(), anyInt(), anyDouble(), any()))
            .thenReturn(ContentRetriever.RetrievalResult.success(
                List.of(new ContentRetriever.RetrievedDocument(
                    "doc-1", "Test content about products", "Product Guide", "document", 0.95, Map.of()
                )),
                1, 0.95, 0.95, 10L
            ));

        // Inject mock into IntentHandlingStep and SmartSuggestionsStep
        for (PipelineStep step : pipelineSteps) {
            if (step.getStepName().equals("IntentHandling") || step.getStepName().equals("SmartSuggestions")) {
                ReflectionTestUtils.setField(step, "contentRetriever", mockContentRetriever);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesSearchOnlyWhenGenerationNotRequired() {
        // Arrange
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("find_products")
            .vectorSpace("product")
            .optimizedQuery("Product entities with price_usd < 60 and stock_status = 'in_stock'")
            .requiresGeneration(false)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        // Act
        OrchestrationResult result = orchestrator.orchestrate("show me products under $60", "user-1");

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isNotNull();

        // Verify contentRetriever was called with correct metadata
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockContentRetriever).retrieve(anyString(), anyString(), anyInt(), anyDouble(), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue()).containsEntry("optimizedQuery", intent.getOptimizedQuery());
        assertThat(metadataCaptor.getValue()).containsEntry("requiresGeneration", false);
        
        // Verify intent doesn't require generation
        assertThat(intent.getRequiresGeneration()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesToGenerationWhenFlagged() {
        // Arrange
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("product")
            .optimizedQuery("Product entities with sentiment = 'positive'")
            .requiresGeneration(true)
            .build();
        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        // Act
        OrchestrationResult result = orchestrator.orchestrate("what should I buy next?", "user-2");

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isNotNull();

        // Verify contentRetriever was called with requiresGeneration flag
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockContentRetriever).retrieve(anyString(), anyString(), anyInt(), anyDouble(), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue()).containsEntry("requiresGeneration", true);
        
        // Verify intent requires generation
        assertThat(intent.getRequiresGeneration()).isTrue();
    }
}
