package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Server configuration only. No customer-selected runtime destination or credential fallback. */
@Component
public class SupportSettings {
    private final Environment environment;
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;

    public SupportSettings(Environment environment, DeploymentRepository deployments, DeploymentTargetProfileRepository targets) {
        this.environment = environment; this.deployments = deployments; this.targets = targets;
    }

    public void requireEnabled() {
        if (!environment.getProperty("platform.support.enabled", Boolean.class, false)) throw SupportPolicy.unavailable();
    }

    public RuntimeBinding binding() {
        requireEnabled();
        var binding = new RuntimeBinding(config("customer-id"), config("tenant-id"), config("deployment-id"),
            config("target-profile-id"), config("assertion-issuer"), config("assertion-audience"));
        var deployment = deployments.findById(binding.deploymentId()).orElseThrow(SupportPolicy::unavailable);
        if (!binding.customerId().equals(deployment.getCustomerId()) || !binding.tenantId().equals(deployment.getTenantId())
            || deployment.getArchivedAt() != null || !targets.existsById(binding.targetProfileId())) {
            throw SupportPolicy.unavailable();
        }
        return binding;
    }

    public String diagnosticServiceKeySecretName() {
        requireEnabled();
        return config("diagnostics.service-key-secret-name");
    }

    public String deskBindingId() { requireEnabled(); return config("desk.binding-id"); }
    public String deskContextSecretName() { requireEnabled(); return config("desk.context-service-key-secret-name"); }
    public boolean deskResolveEnabled() {
        requireEnabled(); return environment.getProperty("platform.support.desk.resolve-enabled", Boolean.class, false);
    }

    private String config(String key) {
        String value;
        try { value = environment.getProperty("platform.support." + key); }
        catch (IllegalArgumentException unresolved) { throw SupportPolicy.unavailable(); }
        if (value == null || value.isBlank() || value.contains("${")) throw SupportPolicy.unavailable();
        return value.trim();
    }
}
