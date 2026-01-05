package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccessControlStep}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlStep")
class AccessControlStepTest {
    
    @Mock
    private AIAccessControlService accessControlService;
    
    @Captor
    private ArgumentCaptor<AIAccessControlRequest> requestCaptor;
    
    private AccessControlStep step;
    
    @BeforeEach
    void setUp() {
        step = new AccessControlStep(accessControlService);
    }
    
    @Nested
    @DisplayName("metadata")
    class Metadata {
        
        @Test
        @DisplayName("Should have correct step name")
        void shouldHaveCorrectStepName() {
            assertThat(step.getStepName()).isEqualTo("AccessControl");
        }
        
        @Test
        @DisplayName("Should have correct order (after security)")
        void shouldHaveCorrectOrder() {
            assertThat(step.getOrder()).isEqualTo(20);
        }
    }
    
    @Nested
    @DisplayName("process()")
    class ProcessMethod {
        
        @Test
        @DisplayName("Should pass through when access granted")
        void shouldPassThroughWhenAccessGranted() {
            // Arrange
            when(accessControlService.checkAccess(any()))
                .thenReturn(AIAccessControlResponse.builder()
                    .accessGranted(true)
                    .build());
            
            PipelineContext context = PipelineContext.from(
                "Find orders",
                OrchestrationContext.forUser("user-123")
            );
            
            // Act
            PipelineContext result = step.process(context);
            
            // Assert
            assertThat(result.isShouldTerminate()).isFalse();
        }
        
        @Test
        @DisplayName("Should terminate when access denied (fail-closed)")
        void shouldTerminateWhenAccessDenied() {
            // Arrange
            when(accessControlService.checkAccess(any()))
                .thenReturn(AIAccessControlResponse.builder()
                    .accessGranted(false)
                    .build());
            
            PipelineContext context = PipelineContext.from(
                "Find orders",
                OrchestrationContext.forUser("user-123")
            );
            
            // Act
            PipelineContext result = step.process(context);
            
            // Assert - Fail-closed: entire request denied
            assertThat(result.isShouldTerminate()).isTrue();
            assertThat(result.getEarlyTerminationResult()).isNotNull();
            assertThat(result.getEarlyTerminationResult().isSuccess()).isFalse();
            assertThat(result.getEarlyTerminationResult().getMessage())
                .isEqualTo("Access denied by policy.");
        }
        
        @Test
        @DisplayName("Should request access to rag:intent resource")
        void shouldRequestAccessToRagIntentResource() {
            // Arrange
            when(accessControlService.checkAccess(any()))
                .thenReturn(AIAccessControlResponse.builder()
                    .accessGranted(true)
                    .build());
            
            PipelineContext context = PipelineContext.from(
                "Query",
                OrchestrationContext.forUser("user-123")
            );
            
            // Act
            step.process(context);
            
            // Assert
            verify(accessControlService).checkAccess(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getResourceId()).isEqualTo("rag:intent");
            assertThat(requestCaptor.getValue().getOperationType()).isEqualTo("READ");
        }
        
        @Test
        @DisplayName("Should include RAG_ORCHESTRATOR entry point in metadata")
        void shouldIncludeEntryPointInMetadata() {
            // Arrange
            when(accessControlService.checkAccess(any()))
                .thenReturn(AIAccessControlResponse.builder()
                    .accessGranted(true)
                    .build());
            
            PipelineContext context = PipelineContext.from(
                "Query",
                OrchestrationContext.forUser("user-123")
            );
            
            // Act
            step.process(context);
            
            // Assert
            verify(accessControlService).checkAccess(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getMetadata())
                .containsEntry("entryPoint", "RAG_ORCHESTRATOR");
        }
        
        @Test
        @DisplayName("Should handle null accessGranted as denied")
        void shouldHandleNullAccessGrantedAsDenied() {
            // Arrange
            when(accessControlService.checkAccess(any()))
                .thenReturn(AIAccessControlResponse.builder()
                    .accessGranted(null) // Null should be treated as denied
                    .build());
            
            PipelineContext context = PipelineContext.from(
                "Query",
                OrchestrationContext.forUser("user-123")
            );
            
            // Act
            PipelineContext result = step.process(context);
            
            // Assert - Fail-closed: null treated as denied
            assertThat(result.isShouldTerminate()).isTrue();
        }
    }
}
