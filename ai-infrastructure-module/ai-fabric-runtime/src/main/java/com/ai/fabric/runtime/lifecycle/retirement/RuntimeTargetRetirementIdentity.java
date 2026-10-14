package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RuntimeTargetRetirementIdentity {
    private final boolean enabled;
    private final Target target;
    private final String tenantId;
    private final boolean dedicatedStore;

    public RuntimeTargetRetirementIdentity(
        @Value("${loomai.runtime.target-retirement.enabled:${LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED:#{null}}}") Boolean enabled,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.deployment-id:${AI_FABRIC_RUNTIME_DEPLOYMENT_ID:}}") String deploymentId,
        @Value("${loomai.runtime.target-profile-id:${LOOMAI_RUNTIME_TARGET_PROFILE_ID:}}") String targetProfileId,
        @Value("${loomai.runtime.release-id:${LOOMAI_RUNTIME_RELEASE_ID:}}") String releaseId,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.tenant-id:${AI_FABRIC_RUNTIME_TENANT_ID:}}") String tenantId,
        @Value("${loomai.runtime.target-retirement.dedicated-store:${LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE:false}}") boolean dedicatedStore
    ) {
        this.enabled = enabled != null ? enabled : StringUtils.hasText(targetProfileId) || StringUtils.hasText(releaseId);
        this.target = new Target(deploymentId, targetProfileId, releaseId);
        this.tenantId = tenantId;
        this.dedicatedStore = dedicatedStore;
        if (this.enabled && (!validId(deploymentId) || !validId(targetProfileId)
            || !validId(releaseId) || !validId(tenantId))) {
            throw new IllegalStateException("Target retirement requires configured deployment, target profile, release and tenant IDs.");
        }
    }

    public boolean enabled() { return enabled; }
    public Target target() { return target; }
    public String tenantId() { return tenantId; }
    public boolean dedicatedStore() { return dedicatedStore; }

    public void requireTarget(Target supplied) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RUNTIME_TARGET_RETIREMENT_DISABLED");
        }
        if (!target.equals(supplied)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_TARGET_SCOPE_MISMATCH");
        }
    }

    private boolean validId(String value) {
        return StringUtils.hasText(value) && value.equals(value.trim()) && value.length() <= 128;
    }
}
