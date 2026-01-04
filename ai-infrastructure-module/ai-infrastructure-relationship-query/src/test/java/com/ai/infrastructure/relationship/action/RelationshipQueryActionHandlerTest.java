package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.spi.RelationshipQueryAccessControlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationshipQueryActionHandlerTest {

    @Mock
    private ReliableRelationshipQueryService queryService;

    @Mock
    private RelationshipQueryAccessControlPolicy accessControlPolicy;

    @InjectMocks
    private RelationshipQueryActionHandler handler;

    @BeforeEach
    void setUp() {
        // Default: allow all access (tests can override)
        when(accessControlPolicy.canUserExecuteRelationshipQueries(anyString())).thenReturn(true);
        when(accessControlPolicy.canUserQueryEntityType(anyString(), anyString())).thenReturn(true);
        when(accessControlPolicy.getAllowedEntityTypesForUser(anyString())).thenReturn(List.of());
    }

    @Test
    void metadataShouldExposeRelationshipQuery() {
        assertThat(handler.getActionMetadata().getName()).isEqualTo("relationship_query");
    }

    @Test
    void executeActionShouldPassQueryOptionsAndSucceed() {
        Map<String, Object> params = Map.of(
            "query", "Find premium users",
            "entityTypes", List.of("user"),
            "limit", 25,
            "returnMode", "FULL",
            "similarityThreshold", 0.42
        );

        RAGResponse response = RAGResponse.builder()
            .success(true)
            .totalResults(3)
            .processingTimeMs(123L)
            .build();
        when(queryService.execute(any(), anyList(), any(QueryOptions.class))).thenReturn(response);

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Found");

        ArgumentCaptor<QueryOptions> optionsCaptor = ArgumentCaptor.forClass(QueryOptions.class);
        verify(queryService).execute(eq("Find premium users"), eq(List.of("user")), optionsCaptor.capture());

        QueryOptions options = optionsCaptor.getValue();
        assertThat(options.getLimit()).isEqualTo(25);
        assertThat(options.getReturnMode()).isEqualTo(ReturnMode.FULL);
        assertThat(options.getSimilarityThreshold()).isEqualTo(0.42);
        // Note: QueryMode is determined by LLM's needsSemanticSearch flag, not by parameters
    }

    @Test
    void executeActionShouldAllowAutoDetectWhenEntityTypesEmpty() {
        // When entity types are empty, policy provides allowed types for auto-detect
        when(accessControlPolicy.getAllowedEntityTypesForUser("user-123"))
            .thenReturn(List.of("document", "product"));  // Policy returns allowed types
        
        Map<String, Object> params = Map.of(
            "query", "Find documents about onboarding",
            "entityTypes", List.of()
        );

        when(queryService.execute(any(), anyList(), any(QueryOptions.class)))
            .thenReturn(RAGResponse.builder().success(true).build());

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isTrue();
        
        // Verify policy was consulted for allowed types
        verify(accessControlPolicy).getAllowedEntityTypesForUser("user-123");
        
        // Verify query executed with policy-provided allowed types
        verify(queryService).execute(
            eq("Find documents about onboarding"), 
            eq(List.of("document", "product")),  // Policy-provided types
            any(QueryOptions.class)
        );
    }

    @Test
    void executeActionShouldFailWhenQueryMissing() {
        Map<String, Object> params = Map.of("entityTypes", List.of("user"));

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAMETERS");
        verify(queryService, never()).execute(any(), anyList(), any());
    }

    @Test
    void executeActionShouldFailWhenEntityTypesInvalid() {
        Map<String, Object> params = Map.of(
            "query", "Find users",
            "entityTypes", 123
        );

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAMETERS");
        verify(queryService, never()).execute(any(), anyList(), any());
    }

    @Test
    void handleErrorShouldReturnFailure() {
        Map<String, Object> params = Map.of(
            "query", "Find users",
            "entityTypes", List.of("user")
        );

        when(queryService.execute(any(), anyList(), any(QueryOptions.class)))
            .thenThrow(new RuntimeException("boom"));

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("EXECUTION_FAILED");
    }

    @Test
    void confirmationMessageShouldReflectQueryAndEntities() {
        Map<String, Object> params = Map.of(
            "query", "Find premium users",
            "entityTypes", List.of("user", "order")
        );

        String message = handler.getConfirmationMessage(params);

        assertThat(message).contains("premium users");
        assertThat(message).contains("user");
        assertThat(message).contains("order");
    }

    @Test
    void validateActionAllowedRequiresUserId() {
        assertThat(handler.validateActionAllowed(null)).isFalse();
        assertThat(handler.validateActionAllowed("")).isFalse();
        assertThat(handler.validateActionAllowed("   ")).isFalse();
    }

    @Test
    void validateActionAllowedDelegatesToAccessControlPolicy() {
        when(accessControlPolicy.canUserExecuteRelationshipQueries("user-123")).thenReturn(true);
        when(accessControlPolicy.canUserExecuteRelationshipQueries("user-456")).thenReturn(false);

        assertThat(handler.validateActionAllowed("user-123")).isTrue();
        assertThat(handler.validateActionAllowed("user-456")).isFalse();

        verify(accessControlPolicy).canUserExecuteRelationshipQueries("user-123");
        verify(accessControlPolicy).canUserExecuteRelationshipQueries("user-456");
    }

    @Test
    void executeActionShouldDenyWhenSomeEntityTypesNotAllowed() {
        // Policy allows only "user" entity type, not "order"
        when(accessControlPolicy.canUserQueryEntityType("user-123", "user")).thenReturn(true);
        when(accessControlPolicy.canUserQueryEntityType("user-123", "order")).thenReturn(false);

        Map<String, Object> params = Map.of(
            "query", "Find users and orders",
            "entityTypes", List.of("user", "order")
        );

        ActionResult result = handler.executeAction(params, "user-123");

        // Should DENY the request (fail-closed security model)
        // User requested ["user", "order"] but only allowed ["user"]
        // We deny the entire request rather than executing partial query
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(result.getMessage()).contains("Access denied");
        assertThat(result.getData()).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data).containsKeys("requestedEntityTypes", "allowedEntityTypes", "deniedEntityTypes");
        assertThat(data.get("deniedEntityTypes")).isEqualTo(List.of("order"));

        // Verify query service was never called (security enforcement)
        verify(queryService, never()).execute(any(), anyList(), any(QueryOptions.class));
    }

    @Test
    void executeActionShouldDenyWhenNoEntityTypesAllowed() {
        // Policy denies all entity types
        when(accessControlPolicy.canUserQueryEntityType("user-123", "user")).thenReturn(false);
        when(accessControlPolicy.canUserQueryEntityType("user-123", "order")).thenReturn(false);

        Map<String, Object> params = Map.of(
            "query", "Find users and orders",
            "entityTypes", List.of("user", "order")
        );

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(result.getMessage()).contains("Access denied");

        verify(queryService, never()).execute(any(), anyList(), any());
    }

    @Test
    void executeActionShouldUseAllowedEntityTypesWhenEmptyListProvided() {
        // When entity types are empty, policy provides allowed entity types
        when(accessControlPolicy.getAllowedEntityTypesForUser("user-123"))
            .thenReturn(List.of("user", "product"));

        Map<String, Object> params = Map.of(
            "query", "Find stuff",
            "entityTypes", List.of()
        );

        when(queryService.execute(any(), anyList(), any(QueryOptions.class)))
            .thenReturn(RAGResponse.builder().success(true).build());

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isTrue();

        // Verify allowed entity types from policy were passed
        ArgumentCaptor<List<String>> entityTypesCaptor = ArgumentCaptor.forClass(List.class);
        verify(queryService).execute(any(), entityTypesCaptor.capture(), any(QueryOptions.class));

        assertThat(entityTypesCaptor.getValue()).containsExactly("user", "product");
    }
}
