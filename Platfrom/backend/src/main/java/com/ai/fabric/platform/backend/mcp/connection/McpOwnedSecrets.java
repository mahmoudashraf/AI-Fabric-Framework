package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.secret.entity.*;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class McpOwnedSecrets {
    private final PlatformSecretRepository secrets;
    public McpOwnedSecrets(PlatformSecretRepository secrets) { this.secrets = secrets; }
    public String resolve(String deploymentId, String targetProfileId, String ref) {
        if (ref == null || ref.isBlank()) throw missing();
        var secret = secrets.findById(ref).orElseThrow(McpOwnedSecrets::missing);
        // Exact ownership, not a naming convention or possession of a global secret reference.
        if (!deploymentId.equals(secret.getDeploymentId()) || secret.getOwnerType() != PlatformSecretOwnerType.DEPLOYMENT
            || secret.getScopeType() != PlatformSecretScopeType.DEPLOYMENT_OVERRIDE || secret.isManagedByPlatform()
            || (secret.getTargetProfileId() != null && !targetProfileId.equals(secret.getTargetProfileId()))
            || secret.getSecretValue() == null || secret.getSecretValue().isBlank()) throw missing();
        return secret.getSecretValue();
    }
    public void check(String deploymentId, String targetProfileId, String ref) { resolve(deploymentId, targetProfileId, ref); }
    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an available integration key owned by this Smart Brain and environment.");
    }
}
