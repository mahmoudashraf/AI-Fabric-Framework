package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import java.util.LinkedHashMap;
import java.util.Set;

/** Native product adapters exist only on the operator-bound managed assistant, not arbitrary customer drafts. */
public final class AuthoringNativeCapabilities {
    public static final Set<String> READS = Set.of("authoring_get_context", "authoring_read_selected_sources", "authoring_check_candidate", "authoring_get_work_status");
    public static final Set<String> WRITES = Set.of("authoring_update_draft", "authoring_undo_request");
    private AuthoringNativeCapabilities() {}
    public static SpecialistAssistanceCompiler.Authority add(DeploymentEntity deployment, AuthoringProperties properties,
        SpecialistAssistanceCompiler.Authority installed) {
        if (properties == null || !properties.getDeploymentId().equals(deployment.getId())
            || !properties.getCustomerId().equals(deployment.getCustomerId()) || !properties.getTenantId().equals(deployment.getTenantId())) return installed;
        var reads = new LinkedHashMap<>(installed.readActionScopes()); var writes = new LinkedHashMap<>(installed.writeActionScopes());
        READS.forEach(name -> reads.put(name, Set.of("configuration:author")));
        WRITES.forEach(name -> writes.put(name, Set.of("configuration:author")));
        return new SpecialistAssistanceCompiler.Authority(reads, installed.vectorSpaces(), writes);
    }
}
