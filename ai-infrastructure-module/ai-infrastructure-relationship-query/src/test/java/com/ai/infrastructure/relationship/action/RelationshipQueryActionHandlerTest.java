package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipQueryActionHandlerTest {

    @Mock
    private ReliableRelationshipQueryService queryService;

    @InjectMocks
    private RelationshipQueryActionHandler handler;

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
            "queryMode", "ENHANCED",
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
        assertThat(options.getForceMode()).isEqualTo(QueryMode.ENHANCED);
        assertThat(options.getSimilarityThreshold()).isEqualTo(0.42);
    }

    @Test
    void executeActionShouldAllowAutoDetectWhenEntityTypesEmpty() {
        Map<String, Object> params = Map.of(
            "query", "Find documents about onboarding",
            "entityTypes", List.of()
        );

        when(queryService.execute(any(), isNull(), any(QueryOptions.class)))
            .thenReturn(RAGResponse.builder().success(true).build());

        ActionResult result = handler.executeAction(params, "user-123");

        assertThat(result.isSuccess()).isTrue();
        verify(queryService).execute(eq("Find documents about onboarding"), isNull(), any(QueryOptions.class));
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
        assertThat(handler.validateActionAllowed("user-123")).isTrue();
        assertThat(handler.validateActionAllowed(null)).isFalse();
        assertThat(handler.validateActionAllowed("")).isFalse();
    }
}
