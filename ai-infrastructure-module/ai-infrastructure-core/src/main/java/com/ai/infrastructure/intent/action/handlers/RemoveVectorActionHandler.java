package com.ai.infrastructure.intent.action.handlers;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.VectorDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveVectorActionHandler implements ActionHandler {

    private final VectorDatabaseService vectorDatabaseService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("remove_vector")
            .description("Remove a single vector from the vector database by entity type and id.")
            .category("vector")
            .parameters(Map.of(
                "entityType", "string (required)",
                "entityId", "string (required)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return true;
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Remove vector for entity '" + params.get("entityType") + ":" + params.get("entityId") + "'?";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String entityType = value(params.get("entityType"));
        String entityId = value(params.get("entityId"));
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return ActionResult.builder()
                .success(false)
                .message("Both 'entityType' and 'entityId' parameters are required.")
                .errorCode("MISSING_PARAMETERS")
                .build();
        }

        boolean removed = vectorDatabaseService.removeVector(entityType, entityId);
        log.info("Remove vector request entityType={} entityId={} user={} removed={}",
            entityType, entityId, userId, removed);
        return ActionResult.builder()
            .success(removed)
            .message(removed ? "Vector removed." : "Vector not found.")
            .data(Map.of(
                "entityType", entityType,
                "entityId", entityId,
                "removed", removed
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Failed to remove vector (user={})", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to remove vector: " + e.getMessage())
            .errorCode("VECTOR_REMOVE_FAILED")
            .build();
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }
}
