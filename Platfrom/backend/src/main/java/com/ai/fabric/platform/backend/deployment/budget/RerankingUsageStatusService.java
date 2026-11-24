package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Read-only exact-target status. No inference, allowance issuance, credential readback or target fallback. */
@Service
public class RerankingUsageStatusService {
    private final DeploymentTargetContextService targets;
    private final DeploymentRuntimeAuthorityService authority;
    private final ProviderUsageRuntimeArtifactGate artifacts;
    private final ProviderUsageRuntimeClient runtime;
    private final ProviderUsageAllowanceCodec codec;

    public RerankingUsageStatusService(DeploymentTargetContextService targets, DeploymentRuntimeAuthorityService authority,
        ProviderUsageRuntimeArtifactGate artifacts, ProviderUsageRuntimeClient runtime, ObjectMapper mapper) {
        this.targets = targets; this.authority = authority; this.artifacts = artifacts; this.runtime = runtime;
        this.codec = new ProviderUsageAllowanceCodec(mapper);
    }

    public Status read(String deploymentId, String targetProfileId) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED);
        var context = targets.resolveCurrent(deploymentId, targetProfileId, false);
        if (!RerankingUsageConfiguration.enabled(context.version().getProviderConfigJson(), codec)) {
            return new Status(deploymentId, targetProfileId, context.release().getId(), "OFF", null, null, null, null);
        }
        artifacts.requireReranking(context.release());
        var headers = authority.issuePlatformProxyHeaders(deploymentId, context.target().getId(), context.release().getId(),
            context.release().getRuntimeAuthorityFingerprint(), List.of("runtime:admin:overview"), Duration.ofMinutes(2));
        var observed = runtime.read(context.runtimeBaseUrl(), headers, ProviderUsagePurpose.RERANKING);
        if (observed == null || !context.deployment().getCustomerId().equals(observed.customerId()) || !deploymentId.equals(observed.deploymentId())
            || !targetProfileId.equals(observed.targetProfileId()) || !context.target().getEnvironmentName().equals(observed.environment())
            || !ProviderUsageRuntimeProfile.RERANKING_RATE_CARD.equals(observed.rateCardReference())) {
            throw new ResponseStatusException(CONFLICT, "Reranking usage does not match this environment.");
        }
        var expiry = observed.effectiveExpiresAt() == null ? observed.expiresAt() : observed.effectiveExpiresAt();
        var limits = observed.effectiveLimits() == null ? observed.limits() : observed.effectiveLimits();
        var usage = observed.accountedUsage();
        boolean exhausted = limits != null && usage != null && (usage.costNanoUsd() >= limits.costNanoUsd()
            || Math.addExact(usage.inputTokens(), usage.outputTokens()) >= limits.totalTokens());
        String status = Boolean.TRUE.equals(observed.stopped()) ? "STOPPED" : expiry != null && !expiry.isAfter(Instant.now())
            ? "EXPIRED" : "ACTIVE".equals(observed.state()) ? exhausted ? "EXHAUSTED" : "ACTIVE" : "NOT_ACTIVE";
        return new Status(deploymentId, targetProfileId, context.release().getId(), status,
            limits, usage,
            expiry, observed.providerAttempts());
    }

    public record Status(String deploymentId, String targetProfileId, String releaseId, String status,
        ProviderUsageAllowanceContracts.Limits limits, ProviderUsageRuntimeClient.Usage accountedUsage,
        Instant expiresAt, Long providerAttempts) { }
}
