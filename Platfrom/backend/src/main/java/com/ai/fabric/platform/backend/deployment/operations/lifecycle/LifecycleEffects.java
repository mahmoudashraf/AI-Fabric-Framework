package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Native effects only; the independent service chooses workflow phases and timing. */
public interface LifecycleEffects {
    record Result(LifecycleContracts.Status status, String code, List<String> blockers) {
        public static Result succeeded(String code) { return new Result(LifecycleContracts.Status.SUCCEEDED, code, List.of()); }
    }
    JsonNode freeze(String deploymentId, String targetProfileId, String releaseId);
    void requireUnchanged(LifecycleCommandStore.Scope scope);
    Result execute(LifecycleCommandStore.Work work);
}
