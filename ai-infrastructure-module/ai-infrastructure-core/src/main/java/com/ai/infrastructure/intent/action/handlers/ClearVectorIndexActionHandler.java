package com.ai.infrastructure.intent.action.handlers;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.VectorDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClearVectorIndexActionHandler implements ActionHandler {

    private final VectorDatabaseService vectorDatabaseService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("clear_vector_index")
            .description("Remove all vectors from the configured vector database.")
            .category("vector")
            .parameters(Map.of())
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        // In this module we do not maintain user permissions; downstream applications can wrap this handler.
        return true;
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "This will permanently delete all indexed vectors. Continue?";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        long removed = vectorDatabaseService.clearVectors();
        log.info("Cleared {} vectors from the vector database (requested by user={})", removed, userId);
        return ActionResult.builder()
            .success(true)
            .message(removed == 0 ? "Vector index already empty." : "Cleared " + removed + " vectors.")
            .data(Map.of("removed", removed))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Failed to clear vector index (user={})", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to clear vector index: " + e.getMessage())
            .errorCode("VECTOR_CLEAR_FAILED")
            .build();
    }
}
