package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Reuse existing row locks; no new client revision or edit-approval contract. */
@Component
public class AuthoringDraftLock {
    private final EntityManager entities;
    public AuthoringDraftLock(EntityManager entities) { this.entities = entities; }
    public void lock(String kind, String resourceId, String draftId) {
        if (!"DEPLOYMENT_DRAFT".equals(kind)) return;
        var deployment = entities.find(DeploymentEntity.class, resourceId, LockModeType.PESSIMISTIC_WRITE);
        if (deployment == null) throw changed();
        entities.refresh(deployment, LockModeType.PESSIMISTIC_WRITE);
        if (!draftId.equals(deployment.getActiveDraftId())) throw changed();
        var draft = entities.find(DeploymentDraftEntity.class, draftId, LockModeType.PESSIMISTIC_WRITE);
        if (draft == null || !resourceId.equals(draft.getDeploymentId())) throw changed();
        entities.refresh(draft, LockModeType.PESSIMISTIC_WRITE);
    }
    private static ResponseStatusException changed() { return new ResponseStatusException(CONFLICT, "The editable draft changed. Reopen this resource."); }
}
