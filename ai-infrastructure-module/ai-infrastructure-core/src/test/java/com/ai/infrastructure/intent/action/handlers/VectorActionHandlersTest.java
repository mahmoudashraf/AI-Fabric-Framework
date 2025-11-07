package com.ai.infrastructure.intent.action.handlers;

import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorActionHandlersTest {

    private VectorDatabaseService vectorDatabaseService;
    private ClearVectorIndexActionHandler clearHandler;
    private RemoveVectorActionHandler removeHandler;

    @BeforeEach
    void setUp() {
        vectorDatabaseService = Mockito.mock(VectorDatabaseService.class);
        clearHandler = new ClearVectorIndexActionHandler(vectorDatabaseService);
        removeHandler = new RemoveVectorActionHandler(vectorDatabaseService);
    }

    @Test
    void clearHandlerShouldReportRemovedCount() {
        when(vectorDatabaseService.clearVectors()).thenReturn(3L);

        ActionResult result = clearHandler.executeAction(Map.of(), "user");

        verify(vectorDatabaseService).clearVectors();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Cleared 3 vectors");
        assertThat(((Map<?, ?>) result.getData()).get("removed")).isEqualTo(3L);
    }

    @Test
    void removeHandlerShouldValidateParameters() {
        ActionResult result = removeHandler.executeAction(Map.of(), "user");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MISSING_PARAMETERS");
    }

    @Test
    void removeHandlerShouldCallVectorService() {
        when(vectorDatabaseService.removeVector("doc", "123")).thenReturn(true);

        ActionResult result = removeHandler.executeAction(
            Map.of("entityType", "doc", "entityId", "123"),
            "user"
        );

        verify(vectorDatabaseService).removeVector("doc", "123");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Vector removed.");
    }
}
